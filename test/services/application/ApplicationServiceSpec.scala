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

import model.Commands.PhoneNumber
import model.EvaluationResults.{Green, Red}
import model.Exceptions.{LastSchemeWithdrawException, PassMarkEvaluationNotFound, SiftExpiredException}
import model.ProgressStatuses.ProgressStatus
import model._
import model.command._
import model.exchange.sift.SiftAnswersStatus
import model.persisted.{ContactDetails, FsbTestGroup, PassmarkEvaluation, SchemeEvaluationResult}
import model.stc.AuditEvents
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.onlinetesting._
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
import testkit.MockitoImplicits._
import testkit.{ExtendedTimeout, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ApplicationServiceSpec extends UnitSpec with ExtendedTimeout {

  "fix" must {
    "process all issues we have examples of" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess2)
      when(appRepositoryMock.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.fix(candidate1, FixBatch(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate1)))
      when(appRepositoryMock.fix(candidate2, FixBatch(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate2)))
      when(appRepositoryMock.fix(candidate3, FixBatch(ResetPhase1TestInvitedSubmitted, 1))).
        thenReturn(Future.successful(Some(candidate3)))

      underTest.fix(FixBatch(PassToPhase2, 1) :: FixBatch(ResetPhase1TestInvitedSubmitted, 1) :: Nil)(hc, rh).futureValue

      verify(appRepositoryMock, times(3)).fix(any[Candidate], any[FixBatch])
      verify(auditEventHandlerMock, times(3)).handle(any[AuditEvents.FixedProdData])(
        any[HeaderCarrier], any[RequestHeader], any[ExecutionContext])
      verifyNoInteractions(pdRepositoryMock, cdRepositoryMock, dataStoreEventHandlerMock, emailEventHandlerMock)
      verifyNoMoreInteractions(auditEventHandlerMock)
    }

    "don't fix anything if no issues are detected" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixEmpty)

      underTest.fix(FixBatch(PassToPhase2, 1) :: Nil)(hc, rh).futureValue

      verify(appRepositoryMock, never).fix(any[Candidate], any[FixBatch])
      verifyNoInteractions(auditEventHandlerMock)
    }

    "proceed with the other searches if one of them fails" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1))).thenReturn(failure)
      when(appRepositoryMock.fix(candidate3, FixBatch(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate3)))

      val result = underTest.fix(FixBatch(PassToPhase2, 1) :: FixBatch(ResetPhase1TestInvitedSubmitted, 1) :: Nil)(hc, rh)
      result.failed.futureValue mustBe generalException

      verify(appRepositoryMock, times(1)).fix(candidate3, FixBatch(PassToPhase2, 1))
      verify(auditEventHandlerMock).handle(any[AuditEvents.FixedProdData])(any[HeaderCarrier], any[RequestHeader], any[ExecutionContext])
      verifyNoMoreInteractions(auditEventHandlerMock)
    }

    "retrieve passed schemes for Faststream application" in new TestFixture {
      val faststreamApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Faststream,
        userId, testAccountId, ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None,
        List(SchemeEvaluationResult(SchemeId(commercial), "Green"),
          SchemeEvaluationResult(SchemeId(governmentOperationalResearchService), "Red")),
        "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(commercial))
    }

    "retrieve passed schemes for Faststream application with fast pass approved" in new TestFixture {
      val faststreamApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Faststream,
        userId, testAccountId, ProgressResponse(applicationId, fastPassAccepted = true), None, None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(schemePreferencesRepoMock.find(eqTo(applicationId))).thenReturn(Future.successful(SelectedSchemes(List(SchemeId(commercial)),
        orderAgreed = true, eligible = true)))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(commercial))
    }

    "retrieve passed schemes for Edip application" in new TestFixture {
      val edipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Edip, userId, testAccountId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId("Edip"), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(edipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(edip))
    }

    "retrieve passed schemes for Sdip application" in new TestFixture {
      val sdipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Sdip, userId, testAccountId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId(sdip), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(sdipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(sdip))
    }

    "retrieve passed schemes for SdipFaststream application" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId, testAccountId,
        ProgressResponse(applicationId), None, None
      )
      val phase1PassmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId(sdip), "Green"),
        SchemeEvaluationResult(SchemeId(finance), "Green")), "", None)

      val phase3PassmarkEvaluation = PassmarkEvaluation("", None,
        List(SchemeEvaluationResult(SchemeId(commercial), "Green"),
          SchemeEvaluationResult(SchemeId(governmentOperationalResearchService), "Red"),
          SchemeEvaluationResult(SchemeId(finance), "Red")
        ), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(application))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(phase3PassmarkEvaluation))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(sdip))
    }

    "retrieve schemes for SdipFaststream when the applicant has failed Faststream prior to Phase 3 tests" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId, testAccountId,
        ProgressResponse(applicationId), None, None
      )
      val phase1PassmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId(sdip), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(application))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.failed(PassMarkEvaluationNotFound(applicationId)))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(sdip))
    }
  }

  "Override submission deadline" must {
    "update the submission deadline in the repository" in new TestFixture {
      val newDeadline = new DateTime(2016, 5, 21, 23, 59, 59)

      when(appRepositoryMock.updateSubmissionDeadline(applicationId, newDeadline)).thenReturnAsync()

      underTest.overrideSubmissionDeadline(applicationId, newDeadline)

      verify(appRepositoryMock, times(1)).updateSubmissionDeadline(eqTo(applicationId), eqTo(newDeadline))
    }
  }

  "withdraw" must {
    "withdraw an application" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green")
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
        SchemeEvaluationResult(SchemeId(commercial), Green.toString),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), Green.toString) // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId(commercial), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue
      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "withdraw from a scheme and progress to FSAC when a siftable scheme is left (not sdip) which requires a form to be filled in " +
      "and we have filled in the form and submitted that scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId(commercial), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "not progress the candidate to FSAC after withdrawing from a scheme after filling in the forms and a single numeric scheme is left " +
      "which requires evaluation and a test and it has not been evaluated" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "leave the candidate in FSAC if the candidate is awaiting allocation and withdraws from a scheme when a siftable scheme is left " +
      "which requires a form to be filled in and we have filled in the form for that scheme (we are in FSAC)" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftTestResultsReceived)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.ASSESSMENT_CENTRE, ApplicationRoute.Faststream,
          Some(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId(commercial), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress to FSAC allocation if only non-evaluation schemes are left and we have not filled in forms" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green"), // form to be filled in, no evaluation required
        SchemeEvaluationResult(SchemeId(generalist), "Green")            // no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress to FSAC allocation if only non-evaluation schemes are left and we have filled in forms" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green"), // form to be filled in, no evaluation required
        SchemeEvaluationResult(SchemeId(generalist), "Green")            // no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "not progress to FSAC allocation if I have one scheme which requires a form to be filled in but I have not completed the form " +
      "and I have other schemes which require numeric test and I withdraw from those" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        // GovernmentEconomicsService - Form to be filled in, evaluation required (will stop us moving to FSAC)
        SchemeEvaluationResult(SchemeId(governmentEconomicsService), "Green"), // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress to FSAC allocation if I have one scheme which requires a form to be filled in but no evaluation is needed and I have submitted " +
      "the forms and I have other schemes which require numeric test and I withdraw from those" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green"), // form to be filled in, no evaluation required
        SchemeEvaluationResult(SchemeId(commercial), "Green")            // numeric test, evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      val withdraw = WithdrawScheme(SchemeId(commercial), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress to FSAC allocation if I am an sdip faststream candidate with the two non-sift schemes and I have not completed the " +
      "sdip form and I withdraw from the sdip scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),           // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(generalist), "Green"),     // no sift requirement, no evaluation required
        SchemeEvaluationResult(SchemeId(humanResources), "Green")  // no sift requirement, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(sdip), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress to FSAC allocation if I am a sdip faststream candidate with 1 other scheme which requires a form to be filled in " +
      "but no evaluation and I have completed the form and I then withdraw from sdip" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(sdip), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
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
        SchemeEvaluationResult(SchemeId(sdip), "Green"),      // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(commercial), "Green") // numeric test, evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream, Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(SchemeId(sdip), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress sdip faststream candidate to fsb awaiting allocation who is awaiting allocation to an assessment centre after withdrawing from " +
      "all fast stream schemes and just leaving sdip" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftCompleted)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.FSB_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress sdip faststream candidate to fsb awaiting allocation who has been sifted in for sdip but has not yet been invited to an " +
      "assessment centre after withdrawing from all fast stream schemes (or being sifted with a fail) and just leaving sdip" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(commercial), "Red"),            // numeric test, evaluation required (already been sifted with a fail)
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.FSB_AWAITING_ALLOCATION))
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
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
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required (sifted with a pass)
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required (sifted with a pass)
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])
    }

    "progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from all form based schemes but is still in the running for " +
      "numeric schemes and schemes that require no sift and the numeric test has been completed" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftTestResultsReceived)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(generalist), "Green"),          // nothing required
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from all form based schemes but is still in the running for " +
      "numeric schemes and the numeric test has been completed" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftTestResultsReceived)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "progress candidate to SIFT_READY who has completed a numeric test and submitted forms, which need no evaluation, withdraws from " +
      "the numeric schemes and is only in the running for schemes that need evaluation" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),       // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(governmentEconomicsService), "Green") // form to be filled in, evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_TEST_RESULTS_READY), None, None))

      val withdraw = WithdrawScheme(SchemeId(commercial), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "progress candidate to FSAC allocation who is in SIFT_ENTERED and has withdrawn from all form based schemes and is only " +
      "in the running for schemes that require no sift" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(generalist), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))

      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
          eqTo(ProgressStatuses.SIFT_READY))
    }

    "not progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from a form based scheme but is still in the running " +
      "for others as well as numeric schemes and schemes that require no sift" in  new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(Some(SiftAnswersStatus.SUBMITTED)) // Form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(generalist), "Green"),          // nothing required
        SchemeEvaluationResult(SchemeId(governmentEconomicsService), "Green"),   // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalDataTechnologyAndCyber), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "throw an exception when withdrawing from the last scheme" in new TestFixture {
      when(siftServiceMock.isSiftExpired(any[String])).thenReturnAsync(false)

      when(siftAnswersServiceMock.findSiftAnswersStatus(any[String])).thenReturnAsync(None) // No form saved
      when(appRepositoryMock.findProgress(any[String])).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green")
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId(commercial), "reason", "Candidate")

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
        SchemeEvaluationResult(SchemeId(commercial), "Green"),
        SchemeEvaluationResult(SchemeId(governmentEconomicsService), "Green")
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId(commercial), "reason", "Candidate")

      whenReady(underTest.withdraw(applicationId, withdraw).failed) { r =>
        r mustBe a[SiftExpiredException]
      }
    }
  }

  "current scheme status with failure details" must {
    "return no failure reasons when all schemes are green" in new TestFixture {
      List(phase1EvaluationRepositoryMock, phase2EvaluationRepositoryMock, phase3EvaluationRepositoryMock).foreach { repo =>
        when(repo.getPassMarkEvaluation(any[String])(any[ExecutionContext])).thenReturnAsync(
          PassmarkEvaluation(
            "version-1",
            None,
            List(
              SchemeEvaluationResult(business, Green.toString),
              SchemeEvaluationResult(commercial, Green.toString),
              SchemeEvaluationResult(finance, Green.toString)
            ),
            "resultVersion-1",
            None
          )
        )
      }

      when(siftRepoMock.getSiftEvaluations(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(commercial, Green.toString),
          SchemeEvaluationResult(finance, Green.toString)
        )
      )

      when(fsacRepoMock.getFsacEvaluatedSchemes(any[String]())).thenReturnAsync(
        Some(Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(commercial, Green.toString),
          SchemeEvaluationResult(finance, Green.toString)
        ))
      )

      when(fsbRepoMock.findByApplicationId(any[String]())).thenReturnAsync(
        Some(FsbTestGroup(
          List(
            SchemeEvaluationResult(business, Green.toString),
            SchemeEvaluationResult(commercial, Green.toString),
            SchemeEvaluationResult(finance, Green.toString)
          )
        ))
      )

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(commercial, Green.toString),
          SchemeEvaluationResult(finance, Green.toString)
        )
      )

      whenReady(underTest.currentSchemeStatusWithFailureDetails("application-1")) { ready =>
        ready.forall(_.failedAt.isEmpty) mustBe true
        ready.forall(_.result == Green.toString) mustBe true
      }
    }

    "return a failure reason against all red schemes when one is failed" in new TestFixture {
      List(phase1EvaluationRepositoryMock, phase2EvaluationRepositoryMock, phase3EvaluationRepositoryMock).foreach { repo =>
        when(repo.getPassMarkEvaluation(any[String])(any[ExecutionContext])).thenReturnAsync(
          PassmarkEvaluation(
            "version-1",
            None,
            List(
              SchemeEvaluationResult(business, Green.toString),
              SchemeEvaluationResult(commercial, Green.toString),
              SchemeEvaluationResult(finance, Green.toString)
            ),
            "resultVersion-1",
            None
          )
        )
      }

      when(siftRepoMock.getSiftEvaluations(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(commercial, Green.toString),
          SchemeEvaluationResult(finance, Green.toString)
        )
      )

      when(fsacRepoMock.getFsacEvaluatedSchemes(any[String]())).thenReturnAsync(
        Some(Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(commercial, Green.toString),
          SchemeEvaluationResult(finance, Red.toString)
        ))
      )

      when(fsbRepoMock.findByApplicationId(any[String]())).thenReturnAsync(
        Some(FsbTestGroup(
          List(
            SchemeEvaluationResult(business, Green.toString),
            SchemeEvaluationResult(commercial, Green.toString),
            SchemeEvaluationResult(finance, Red.toString)
          )
        ))
      )

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(commercial, Green.toString),
          SchemeEvaluationResult(finance, Red.toString)
        )
      )

      whenReady(underTest.currentSchemeStatusWithFailureDetails("application-1")) { ready =>
        ready.count(_.failedAt.isDefined) mustBe 1
        ready.find(_.failedAt.isDefined).get.failedAt mustBe Some("assessment centre")
      }
    }

    "return all failure reasons when all schemes are red" in new TestFixture {
      List(phase1EvaluationRepositoryMock, phase2EvaluationRepositoryMock, phase3EvaluationRepositoryMock).foreach { repo =>
        when(repo.getPassMarkEvaluation(any[String])(any[ExecutionContext])).thenReturnAsync(
          PassmarkEvaluation(
            "version-1",
            None,
            List(
              SchemeEvaluationResult(business, Green.toString),
              SchemeEvaluationResult(governmentOperationalResearchService, Green.toString),
              SchemeEvaluationResult(commercial, Green.toString),
              SchemeEvaluationResult(finance, Red.toString)
            ),
            "resultVersion-1",
            None
          )
        )
      }

      when(siftRepoMock.getSiftEvaluations(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(governmentOperationalResearchService, Red.toString),
          SchemeEvaluationResult(commercial, Green.toString),
          SchemeEvaluationResult(finance, Red.toString)
        )
      )

      when(fsacRepoMock.getFsacEvaluatedSchemes(any[String]())).thenReturnAsync(
        Some(Seq(
          SchemeEvaluationResult(business, Green.toString),
          SchemeEvaluationResult(governmentOperationalResearchService, Red.toString),
          SchemeEvaluationResult(commercial, Red.toString),
          SchemeEvaluationResult(finance, Red.toString)
        ))
      )

      when(fsbRepoMock.findByApplicationId(any[String]())).thenReturnAsync(
        Some(FsbTestGroup(
          List(
            SchemeEvaluationResult(business, Red.toString),
            SchemeEvaluationResult(governmentOperationalResearchService, Red.toString),
            SchemeEvaluationResult(commercial, Red.toString),
            SchemeEvaluationResult(finance, Red.toString)
          )
        ))
      )

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(business, Red.toString),
          SchemeEvaluationResult(governmentOperationalResearchService, Red.toString),
          SchemeEvaluationResult(commercial, Red.toString),
          SchemeEvaluationResult(finance, Red.toString)
        )
      )

      whenReady(underTest.currentSchemeStatusWithFailureDetails("application-1")) { ready =>
        ready.forall(_.failedAt.isDefined) mustBe true
        ready.find(_.schemeId == SchemeId(business)).get.failedAt.get mustBe "final selection board"
        ready.find(_.schemeId == SchemeId(governmentOperationalResearchService)).get.failedAt.get mustBe "sift stage"
        ready.find(_.schemeId == SchemeId(commercial)).get.failedAt.get mustBe "assessment centre"
        ready.find(_.schemeId == SchemeId(finance)).get.failedAt.get mustBe "online tests"
      }
    }
  }

  trait TestFixture extends StcEventServiceFixture {

    val appRepositoryMock         = mock[GeneralApplicationRepository]
    val pdRepositoryMock          = mock[PersonalDetailsRepository]
    val cdRepositoryMock          = mock[ContactDetailsRepository]
    val schemePreferencesRepoMock = mock[SchemePreferencesRepository]
    val mediaRepoMock             = mock[MediaRepository]
    val evalPhase1ResultMock      = mock[EvaluatePhase1ResultService]
    val evalPhase2ResultMock      = mock[EvaluatePhase2ResultService]
    val evalPhase3ResultMock      = mock[EvaluatePhase3ResultService]

    val phase1EvaluationRepositoryMock = mock[OnlineTestEvaluationRepository]
    val phase2EvaluationRepositoryMock = mock[OnlineTestEvaluationRepository]
    val phase3EvaluationRepositoryMock = mock[OnlineTestEvaluationRepository]

//    val phase1TestRepository2Mock = mock[Phase1TestMongoRepository2]
//    val phase2TestRepository2Mock = mock[Phase2TestMongoRepository2]

    val siftServiceMock          = mock[ApplicationSiftService]
    val siftAnswersServiceMock   = mock[SiftAnswersService]
//    val schemeRepoMock           = mock[SchemeRepository2]

    val phase1TestRepositoryMock = mock[Phase1TestRepository]
    val phase2TestRepositoryMock = mock[Phase2TestRepository]
    val phase3TestRepositoryMock = mock[Phase3TestRepository]

    val siftRepoMock             = mock[ApplicationSiftRepository]
    val fsacRepoMock             = mock[AssessmentCentreRepository]
    val eventsServiceMock        = mock[EventsService]
    val fsbRepoMock              = mock[FsbRepository]
    val civilServiceExperienceRepositoryMock   = mock[CivilServiceExperienceDetailsRepository]
    val candidateAllocationServiceMock         = mock[CandidateAllocationService]
    val assistanceDetailsRepositoryMock = mock[AssistanceDetailsRepository]
    val assessorAssessmentScoresRepositoryMock = mock[AssessorAssessmentScoresMongoRepository]
    val reviewerAssessmentScoresRepositoryMock = mock[ReviewerAssessmentScoresMongoRepository]
    val assistanceDetailsRepoMock = mock[AssistanceDetailsRepository]

    val business = "Business"
    val commercial = "Commercial"
    val digitalDataTechnologyAndCyber = "DigitalDataTechnologyAndCyber"
    val governmentEconomicsService = "GovernmentEconomicsService"
    val edip = "Edip"
    val finance = "Finance"
    val generalist = "Generalist"
    val housesOfParliament = "HousesOfParliament"
    val governmentCommunicationService = "GovernmentCommunicationService"
    val governmentOperationalResearchService = "GovernmentOperationalResearchService"
    val humanResources = "HumanResources"
    val projectDelivery = "ProjectDelivery"
    val scienceAndEngineering = "ScienceAndEngineering"
    val sdip = "Sdip"

    val schemeRepoMock = new TestSchemeRepository {
      override lazy val siftableSchemeIds = Seq(
        SchemeId(commercial), SchemeId(digitalDataTechnologyAndCyber),SchemeId(governmentEconomicsService)
      )
      override lazy val noSiftEvaluationRequiredSchemeIds = Seq(SchemeId(digitalDataTechnologyAndCyber), SchemeId(edip), SchemeId(generalist),
        SchemeId(governmentCommunicationService), SchemeId(housesOfParliament), SchemeId(humanResources), SchemeId(projectDelivery),
        SchemeId(scienceAndEngineering)
      )
      override lazy val nonSiftableSchemeIds = Seq(SchemeId(generalist), SchemeId(humanResources))
      override lazy val numericTestSiftRequirementSchemeIds = Seq(SchemeId(commercial), SchemeId(finance))
      override lazy val formMustBeFilledInSchemeIds = Seq(SchemeId(digitalDataTechnologyAndCyber), SchemeId(governmentEconomicsService))
      override lazy val siftableAndEvaluationRequiredSchemeIds = Seq(SchemeId(commercial), SchemeId(governmentEconomicsService))
    }

    val underTest = new ApplicationService(
      appRepositoryMock,
      pdRepositoryMock,
      cdRepositoryMock,
      schemePreferencesRepoMock,
      mediaRepoMock,
      evalPhase1ResultMock,
      evalPhase2ResultMock,
      evalPhase3ResultMock,
      phase1EvaluationRepositoryMock,
      phase2EvaluationRepositoryMock,
      phase3EvaluationRepositoryMock,
//      phase1TestRepository2Mock,
//      phase2TestRepository2Mock,
      siftServiceMock,
      siftAnswersServiceMock,
      schemeRepoMock,
      phase1TestRepositoryMock,
      phase2TestRepositoryMock,
      phase3TestRepositoryMock,
      siftRepoMock,
      fsacRepoMock,
      eventsServiceMock,
      fsbRepoMock,
      civilServiceExperienceRepositoryMock,
      candidateAllocationServiceMock,
      assistanceDetailsRepositoryMock,
      assessorAssessmentScoresRepositoryMock,
      reviewerAssessmentScoresRepositoryMock,
      stcEventServiceMock
    )

    val userId = "userId"
    val applicationId = "appId"
    val testAccountId = "testAccountId"
    val frameworkId = ""

    val candidate1 = Candidate(userId = "user123", applicationId = Some("appId234"), testAccountId = None, email = Some("test1@localhost"),
      None, None, None, None, None, None, None, None, None)

    val cd1 = ContactDetails(outsideUk = false, Address("line1"), None, None, "email@email.com", "123":PhoneNumber)

    val candidate2 = Candidate(userId = "user456", applicationId = Some("appId4567"), testAccountId = None, email = Some("test2@localhost"),
      None, None, None, None, None, None, None, None, None)

    val candidate3 = Candidate(userId = "user569", applicationId = Some("appId84512"), testAccountId = None, email = Some("test3@localhost"),
      None, None, None, None, None, None, None, None, None)

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
