/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services.application

import model.*
import model.Commands.PhoneNumber
import model.EvaluationResults.{Amber, Green, Red}
import model.Exceptions.{LastSchemeWithdrawException, PassMarkEvaluationNotFound, SiftExpiredException}
import model.ProgressStatuses.ProgressStatus
import model.command.*
import model.exchange.sift.SiftAnswersStatus
import model.persisted.{ContactDetails, FsbTestGroup, PassmarkEvaluation, SchemeEvaluationResult}
import model.stc.AuditEvents
import org.mockito.ArgumentMatchers.{any, eq as eqTo, *}
import org.mockito.Mockito.*
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.onlinetesting.*
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import repositories.sift.ApplicationSiftRepository
import repositories.{AssessorAssessmentScoresMongoRepository, MediaRepository, ReviewerAssessmentScoresMongoRepository, TestSchemeRepository}
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{PassToPhase2, ResetPhase1TestInvitedSubmitted}
import services.allocation.CandidateAllocationService
import services.events.EventsService
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import services.onlinetesting.phase2.EvaluatePhase2ResultService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import services.sift.{ApplicationSiftService, SiftAnswersService}
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits.*
import testkit.{ExtendedTimeout, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{OffsetDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class WithdrawServiceSpec extends UnitSpec with ExtendedTimeout with Schemes {

  "withdraw" must {
    "withdraw an application" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString)
      ))
      when(appRepositoryMock.withdraw(any[String], any[WithdrawApplication])).thenReturnAsync()
      when(candidateAllocationServiceMock.allocationsForApplication(any[String])(any[HeaderCarrier])).thenReturnAsync(Nil)
      when(candidateAllocationServiceMock
        .unAllocateCandidates(any[List[model.persisted.CandidateAllocation]], eligibleForReallocation = anyBoolean())
      (any[HeaderCarrier]))
        .thenReturnAsync()
      val withdraw = WithdrawApplication("reason", None, "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdraw(applicationId, withdraw)
    }

    "withdraw from a scheme and stay in sift (not progress) when a siftable scheme is left which requires a form to be filled in " +
      "and we have not filled in the form for that scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(Commercial, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue
      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "withdraw from a scheme and progress to FSAC when a siftable scheme is left (not sdip) which requires a form to be filled in " +
      "and we have filled in the form and submitted that scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(Commercial, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress the candidate to FSAC after withdrawing from a scheme after filling in the forms and a single numeric scheme is left " +
      "which requires evaluation and a test and it has not been evaluated" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "leave the candidate in FSAC if the candidate is awaiting allocation and withdraws from a scheme when a siftable scheme is left " +
      "which requires a form to be filled in and we have filled in the form for that scheme (we are in FSAC)" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftTestResultsReceived)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.ASSESSMENT_CENTRE, ApplicationRoute.Faststream,
          Some(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(Commercial, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress to FSAC allocation if only non-evaluation schemes are left and we have not filled in forms" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // form to be filled in, no evaluation required
        SchemeEvaluationResult(OperationalDelivery, Green.toString)            // no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress to FSAC allocation if only non-evaluation schemes are left and we have filled in forms" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // form to be filled in, no evaluation required
        SchemeEvaluationResult(OperationalDelivery, Green.toString)            // no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress to FSAC allocation if I have one scheme which requires a form to be filled in but I have not completed the form " +
      "and I have other schemes which require numeric test and I withdraw from those" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        // GovernmentEconomicsService - Form to be filled in, evaluation required (will stop us moving to FSAC)
        SchemeEvaluationResult(GovernmentEconomicsService, Green.toString),   // form to be filled in, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress to FSAC allocation if I have one scheme which requires a form to be filled in but no evaluation is needed and I have submitted " +
      "the forms and I have other schemes which require numeric test and I withdraw from those" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // form to be filled in, no evaluation required
        SchemeEvaluationResult(Commercial, Green.toString)            // numeric test, evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      val withdraw = WithdrawScheme(Commercial, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress to FSAC allocation if I am an sdip faststream candidate with the two non-sift schemes and I have not completed the " +
      "sdip form and I withdraw from the sdip scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Sdip, Green.toString),           // form to be filled in, evaluation required
        SchemeEvaluationResult(OperationalDelivery, Green.toString),     // no sift requirement, no evaluation required
        SchemeEvaluationResult(HumanResources, Green.toString)  // no sift requirement, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(Sdip, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress to FSAC allocation if I am a sdip faststream candidate with 1 other scheme which requires a form to be filled in " +
      "but no evaluation and I have completed the form and I then withdraw from sdip" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Sdip, Green.toString),                // form to be filled in, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(Sdip, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    //Sdip scheme should never go to assessment centre
    "not progress to FSAC allocation if I am a sdip faststream candidate with 1 other scheme which requires a numeric test and " +
      "evaluation and I have completed the sdip form and the test and been sifted (SIFT_COMPLETED) and I then withdraw from " +
      "sdip" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftCompleted)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Sdip, Green.toString),      // form to be filled in, evaluation required
        SchemeEvaluationResult(Commercial, Green.toString) // numeric test, evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream, Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(Sdip, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress sdip faststream candidate to fsb awaiting allocation who is awaiting allocation to an assessment centre after withdrawing from " +
      "all fast stream schemes and just leaving sdip" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftCompleted)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Sdip, Green.toString),                // form to be filled in, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.FSB_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress sdip faststream candidate to fsb awaiting allocation who has been sifted in for sdip but has not yet been invited to an " +
      "assessment centre after withdrawing from all fast stream schemes (or being sifted with a fail) and just leaving sdip" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Sdip, Green.toString),                // form to be filled in, evaluation required
        SchemeEvaluationResult(Commercial, Red.toString),            // numeric test, evaluation required (already been sifted with a fail)
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.FSB_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress sdip faststream candidate out of sift who has been sifted in for all schemes that require a sift but who also has" +
      "a scheme that does not require a sift. He withdraws from that one, leaving the 2 schemes sifted with a pass. In this scenario" +
      "the candidate will be picked up by the assessment centre scheduled job and moved to " +
      "ASSESSMENT_CENTRE_AWAITING_INVITATION" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Sdip, Green.toString),                // form to be filled in, evaluation required (sifted with a pass)
        SchemeEvaluationResult(Commercial, Green.toString),          // numeric test, evaluation required (sifted with a pass)
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])
    }

    "progress candidate to Sift who is in PHASE1_TESTS_PASSED but still has an amber banded scheme and now withdraws from " +
      "the amber scheme so now is only in the running for a scheme that requires a sift form" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InPhase1TestsPassed)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // sift form needed
        SchemeEvaluationResult(RiskManagement, Amber.toString), // no sift needed
        SchemeEvaluationResult(OperationalDelivery, Green.toString) // no sift needed
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationRoute.Faststream,
          Some(ProgressStatuses.PHASE1_TESTS_PASSED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(RiskManagement, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from all form based schemes but is still in the running for " +
      "numeric schemes and schemes that require no sift and the numeric test has been completed" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftTestResultsReceived)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(OperationalDelivery, Green.toString),          // nothing required
        SchemeEvaluationResult(Commercial, Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from all form based schemes but is still in the running for " +
      "numeric schemes and the numeric test has been completed" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftTestResultsReceived)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString),                   // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress candidate to SIFT_READY who has completed a numeric test and submitted forms, which need no evaluation, withdraws from " +
      "the numeric schemes and is only in the running for schemes that need evaluation" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString),                // numeric test, evaluation required
        SchemeEvaluationResult(GovernmentEconomicsService, Green.toString) // form to be filled in, evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_TEST_RESULTS_READY), None, None))

      val withdraw = WithdrawScheme(Commercial, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress candidate to FSAC allocation who is in SIFT_ENTERED and has withdrawn from all form based schemes and is only " +
      "in the running for schemes that require no sift" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(OperationalDelivery, Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
          eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress candidate out of PHASE1_TESTS who is in PHASE1_TESTS_INVITED and has withdrawn from all form based schemes and is only " +
      "in the running for schemes that require no sift" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InPhase1TestsInvited)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(OperationalDelivery, Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.PHASE1_TESTS, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
          eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
          eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress candidate who is in PHASE1_TESTS_PASSED but still has an amber banded scheme and now withdraws from " +
      "the green 1st preference, which put them in PHASE1_TESTS_PASSED" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InPhase1TestsPassed)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // sift form needed
        SchemeEvaluationResult(RiskManagement, Amber.toString) // no sift needed
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationRoute.Faststream,
          Some(ProgressStatuses.PHASE1_TESTS_PASSED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress candidate who is in PHASE1_TESTS_PASSED but still has 2 amber banded scheme and now withdraws from " +
      "the green 1st preference, which put them in PHASE1_TESTS_PASSED" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InPhase1TestsPassed)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // sift form needed
        SchemeEvaluationResult(HumanResources, Amber.toString), // no sift needed
        SchemeEvaluationResult(RiskManagement, Amber.toString) // no sift needed
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationRoute.Faststream,
          Some(ProgressStatuses.PHASE1_TESTS_PASSED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress candidate to SIFT who is in PHASE1_TESTS_PASSED and after withdrawing the 1st pref, which put them into PHASE1_TESTS_PASSED " +
      "is left with an Amber scheme and a Green scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InPhase1TestsPassed)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // sift form needed
        SchemeEvaluationResult(HumanResources, Amber.toString), // no sift needed
        SchemeEvaluationResult(GovernmentEconomicsService, Green.toString) // sift form and eval needed
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationRoute.Faststream,
          Some(ProgressStatuses.PHASE1_TESTS_PASSED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress candidate to FSAC who is in PHASE1_TESTS_PASSED and after withdrawing the 1st pref, which put them into PHASE1_TESTS_PASSED " +
      "is left with an Amber scheme and a Green scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InPhase1TestsPassed)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // sift form needed
        SchemeEvaluationResult(HumanResources, Amber.toString), // no sift needed
        SchemeEvaluationResult(OperationalDelivery, Green.toString) // no sift needed
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationRoute.Faststream,
          Some(ProgressStatuses.PHASE1_TESTS_PASSED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "progress candidate to FSAC who is in PHASE1_TESTS_PASSED but still has an amber banded scheme and now withdraws from " +
      "the amber scheme so now is only in the running for schemes that require no sift" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InPhase1TestsPassed)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Digital, Green.toString), // no sift needed
        SchemeEvaluationResult(RiskManagement, Amber.toString) // no sift needed
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationRoute.Faststream,
          Some(ProgressStatuses.PHASE1_TESTS_PASSED), statusDate = None, overrideSubmissionDeadline = None))

      val withdraw = WithdrawScheme(RiskManagement, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "not progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from a form based scheme but is still in the running " +
      "for others as well as numeric schemes and schemes that require no sift" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(OperationalDelivery, Green.toString),          // nothing required
        SchemeEvaluationResult(GovernmentEconomicsService, Green.toString),   // form to be filled in, evaluation required
        SchemeEvaluationResult(Commercial, Green.toString),                   // numeric test, evaluation required
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "throw an exception when withdrawing from the last scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString)
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()

      val withdraw = WithdrawScheme(Commercial, "reason", "Candidate")

      whenReady(underTest.withdraw(applicationId, withdraw).failed) { r =>
        r mustBe a[LastSchemeWithdrawException]
      }
    }

    "throw an exception when withdrawing after sift has expired" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(true)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(GovernmentEconomicsService, Green.toString)
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()

      val withdraw = WithdrawScheme(Commercial, "reason", "Candidate")

      whenReady(underTest.withdraw(applicationId, withdraw).failed) { r =>
        r mustBe a[SiftExpiredException]
      }
    }

    "withdraw from a scheme at FSB and be offered a job if the next scheme is Green and does not need a FSB" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // Needs a FSB
        SchemeEvaluationResult(OperationalDelivery, Green.toString)       // No FSB
      ))

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)

      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.FSB, ApplicationRoute.Faststream, Some(ProgressStatuses.FSB_ALLOCATION_CONFIRMED),
          statusDate = None, overrideSubmissionDeadline = None
        )
      )

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InFsbAllocationConfirmed)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      when(candidateAllocationServiceMock.getCandidateAllocation(any[String], any[SchemeId])).thenReturnAsync(None)

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue
      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId), eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
    }

    "withdraw from a scheme at FSAC and be offered a job if the next scheme is Green and does not need a FSB" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Amber.toString), // This is why we remain at FSAC
        SchemeEvaluationResult(OperationalDelivery, Green.toString)       // No FSB
      ))

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)

      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(
          ApplicationStatus.ASSESSMENT_CENTRE, ApplicationRoute.Faststream, Some(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED),
          statusDate = None, overrideSubmissionDeadline = None
        )
      )

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InFsbAllocationConfirmed)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue
      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId), eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "withdraw from a scheme at FSAC and not be offered a job if the candidate has not yet passed FSAC even" +
      "though the next residual preference needs no FSB so would result in a job offer" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // FSB
        SchemeEvaluationResult(OperationalDelivery, Green.toString)       // No FSB
      ))

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)

      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(
          ApplicationStatus.ASSESSMENT_CENTRE, ApplicationRoute.Faststream, Some(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED),
          statusDate = None, overrideSubmissionDeadline = None
        )
      )

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InFsacScoresEntered)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue
      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "withdraw from a scheme at FSB and not be offered a job if the next scheme is Green and also needs a FSB" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // Needs a FSB
        SchemeEvaluationResult(HousesOfParliament, Green.toString)        // Needs a FSB
      ))

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)

      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.FSB, ApplicationRoute.Faststream, Some(ProgressStatuses.FSB_ALLOCATION_CONFIRMED),
          statusDate = None, overrideSubmissionDeadline = None
        )
      )

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InFsbAllocationConfirmed)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      when(candidateAllocationServiceMock.getCandidateAllocation(any[String], any[SchemeId])).thenReturnAsync(None)

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue
      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(
        eqTo(applicationId), eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "withdraw from a scheme at FSB and not be offered a job if the next scheme is Amber" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString), // Needs a FSB
        SchemeEvaluationResult(GovernmentPolicy, Amber.toString) // Amber so should not be offered a job
      ))

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)

      when(appRepositoryMock.findStatus(any[String], any[Boolean])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.FSB, ApplicationRoute.Faststream, Some(ProgressStatuses.FSB_ALLOCATION_CONFIRMED),
          statusDate = None, overrideSubmissionDeadline = None
        )
      )

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InFsbAllocationConfirmed)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val candidateAllocation = model.persisted.CandidateAllocation(
        id = applicationId,
        eventId = "eventId",
        sessionId = "sessionId",
        status = AllocationStatuses.CONFIRMED,
        version = "v1",
        removeReason = None,
        createdAt = java.time.LocalDate.of(2025, 7, 20),
        reminderSent = false
      )

      val updated = candidateAllocation.copy(
        status = AllocationStatuses.REMOVED,
        removeReason = Some("Candidate withdrew scheme (DiplomaticAndDevelopment)")
      )

      when(candidateAllocationServiceMock.getCandidateAllocation(any[String], any[SchemeId])).thenReturnAsync(Some(candidateAllocation))
      when(candidateAllocationServiceMock.saveCandidateAllocations(eqTo(Seq(updated)))).thenReturnAsync()
      when(appRepositoryMock.removeProgressStatuses(eqTo(applicationId),
        eqTo(List(ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED, ProgressStatuses.FSB_ALLOCATION_CONFIRMED)))
      ).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(eqTo(applicationId), eqTo(ProgressStatuses.FSB_AWAITING_ALLOCATION)))
        .thenReturnAsync()

      val withdraw = WithdrawScheme(DiplomaticAndDevelopment, "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue
      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw), any[Seq[SchemeEvaluationResult]])
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(
        eqTo(applicationId), eqTo(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
      )

      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_ENTERED))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }
  }

  trait TestFixture extends StcEventServiceFixture {

    val appRepositoryMock         = mock[GeneralApplicationRepository]
    val cdRepositoryMock          = mock[ContactDetailsRepository]
    val siftServiceMock          = mock[ApplicationSiftService]
    val siftAnswersServiceMock   = mock[SiftAnswersService]
    val siftRepoMock             = mock[ApplicationSiftRepository]
    val eventsServiceMock        = mock[EventsService]
    val candidateAllocationServiceMock         = mock[CandidateAllocationService]

    val schemeRepoMock = new TestSchemeRepository {
      override lazy val siftableSchemeIds = Seq(Commercial, DiplomaticAndDevelopment, GovernmentEconomicsService)
      override lazy val noSiftEvaluationRequiredSchemeIds = Seq(DiplomaticAndDevelopment, Edip, OperationalDelivery,
        GovernmentCommunicationService, HousesOfParliament, HumanResources, ProjectDelivery,
        ScienceAndEngineering
      )
      override lazy val nonSiftableSchemeIds = Seq(Digital, HumanResources, OperationalDelivery)
      override lazy val numericTestSiftRequirementSchemeIds = Seq(Commercial, Finance)
      override lazy val formMustBeFilledInSchemeIds = Seq(DiplomaticAndDevelopment, GovernmentEconomicsService)
      override lazy val siftableAndEvaluationRequiredSchemeIds = Seq(Commercial, GovernmentEconomicsService)

      override def schemeRequiresFsb(id: SchemeId) = id match {
        // Only needed for the fsb specific scheme withdrawal tests
        case DiplomaticAndDevelopment => true
        case OperationalDelivery => false
        case HousesOfParliament => true
        // All other tests
        case _ => false
      }
    }

    val underTest = new WithdrawService(
      appRepositoryMock,
      cdRepositoryMock,
      siftServiceMock,
      siftAnswersServiceMock,
      schemeRepoMock,
      eventsServiceMock,
      candidateAllocationServiceMock,
      stcEventServiceMock
    )

    val userId = "userId"
    val applicationId = "appId"
    val testAccountId = "testAccountId"
    val frameworkId = ""

    val candidate1 = Candidate(userId = "user123", applicationId = Some("appId234"), testAccountId = None, email = Some("test1@localhost"),
      firstName = None, lastName = None, preferredName = None, dateOfBirth = None, address = None, postCode = None, country = None,
      applicationRoute = None, applicationStatus = None
    )

    val cd1 = ContactDetails(outsideUk = false, Address("line1"), postCode = None, country = None, "email@email.com", "123":PhoneNumber)

    val candidate2 = Candidate(userId = "user456", applicationId = Some("appId4567"), testAccountId = None, email = Some("test2@localhost"),
      None, None, None, None, None, None, None, None, None)

    val candidate3 = Candidate(userId = "user569", applicationId = Some("appId84512"), testAccountId = None, email = Some("test3@localhost"),
      firstName = None, lastName = None, preferredName = None, dateOfBirth = None, address = None, postCode = None, country = None,
      applicationRoute = None, applicationStatus = None
    )

    val generalException = new RuntimeException("something went wrong")
    val failure = Future.failed(generalException)

    val getApplicationsToFixSuccess2: Future[List[Candidate]] = Future.successful(candidate1 :: candidate2 :: Nil)
    val getApplicationsToFixSuccess1: Future[List[Candidate]] = Future.successful(candidate3 :: Nil)
    val getApplicationsToFixFailure: Future[List[Candidate]] = Future.failed(generalException)
    val getApplicationsToFixEmpty: Future[List[Candidate]] = Future.successful(Nil)
    val success = Future.successful(())
  }

  // Moved out of the trait to here as not getting picked up by the withdraw tests if they are run in isolation
  // with all other tests commented out
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val rh: RequestHeader = mock[RequestHeader]
}
