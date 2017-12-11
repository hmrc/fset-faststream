/*
 * Copyright 2017 HM Revenue & Customs
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
import model.EvaluationResults.{ Green, Red }
import model.Exceptions.{ LastSchemeWithdrawException, PassMarkEvaluationNotFound }
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.{ ApplicationStatusDetails, ProgressResponse, WithdrawApplication, WithdrawScheme }
import model.persisted.{ ContactDetails, FsbTestGroup, PassmarkEvaluation, SchemeEvaluationResult }
import model.stc.AuditEvents
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import repositories.{ MediaRepository, SchemeRepository }
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{ PassToPhase2, ResetPhase1TestInvitedSubmitted }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.fsb.FsbRepository
import repositories.onlinetesting._
import repositories.sift.ApplicationSiftRepository
import services.allocation.CandidateAllocationService
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import services.onlinetesting.phase2.EvaluatePhase2ResultService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import services.sift.ApplicationSiftService
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits._
import testkit.{ ExtendedTimeout, UnitSpec }

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

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
      verify(underTest.auditEventHandlerMock, times(3)).handle(any[AuditEvents.FixedProdData])(any[HeaderCarrier], any[RequestHeader])
      verifyZeroInteractions(pdRepositoryMock, cdRepositoryMock, underTest.dataStoreEventHandlerMock, underTest.emailEventHandlerMock)
      verifyNoMoreInteractions(underTest.auditEventHandlerMock)
    }

    "don't fix anything if no issues are detected" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixEmpty)

      underTest.fix(FixBatch(PassToPhase2, 1) :: Nil)(hc, rh).futureValue

      verify(appRepositoryMock, never).fix(any[Candidate], any[FixBatch])
      verifyZeroInteractions(underTest.auditEventHandlerMock)
    }

    "proceed with the other searches if one of them fails" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1))).thenReturn(failure)
      when(appRepositoryMock.fix(candidate3, FixBatch(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate3)))

      val result = underTest.fix(FixBatch(PassToPhase2, 1) :: FixBatch(ResetPhase1TestInvitedSubmitted, 1) :: Nil)(hc, rh)
      result.failed.futureValue mustBe generalException

      verify(appRepositoryMock, times(1)).fix(candidate3, FixBatch(PassToPhase2, 1))
      verify(underTest.auditEventHandlerMock).handle(any[AuditEvents.FixedProdData])(any[HeaderCarrier], any[RequestHeader])
      verifyZeroInteractions(underTest.auditEventHandlerMock)
    }

    "retrieve passed schemes for Faststream application" in new TestFixture {
      val faststreamApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Faststream,
        userId,ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None,
        List(SchemeEvaluationResult(SchemeId(commercial), "Green"),
          SchemeEvaluationResult(SchemeId(governmentOperationalResearchService), "Red")),
        "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(commercial))
    }

    "retrieve passed schemes for Faststream application with fast pass approved" in new TestFixture {
      val faststreamApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Faststream,
        userId,ProgressResponse(applicationId, fastPassAccepted = true), None, None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(schemeRepositoryMock.find(eqTo(applicationId))).thenReturn(Future.successful(SelectedSchemes(List(SchemeId(commercial)),
        orderAgreed = true, eligible = true)))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(commercial))
    }

    "retrieve passed schemes for Edip application" in new TestFixture {
      val edipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Edip, userId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId("Edip"), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(edipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(edip))
    }

    "retrieve passed schemes for Sdip application" in new TestFixture {
      val sdipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Sdip, userId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId(sdip), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(sdipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(sdip))
    }

    "retrieve passed schemes for SdipFaststream application" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId,
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
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(phase3PassmarkEvaluation))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(sdip))
    }

    "retrieve schemes for SdipFaststream when the applicant has failed Faststream prior to Phase 3 tests" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId,
        ProgressResponse(applicationId), None, None
      )
      val phase1PassmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId(sdip), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(application))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.failed(PassMarkEvaluationNotFound(applicationId)))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId(sdip))
    }
  }

  "Override submission deadline" must {
    "update the submission deadline in the repository" in new TestFixture {
      val newDeadline = new DateTime(2016, 5, 21, 23, 59, 59)

      when(appRepositoryMock.updateSubmissionDeadline(applicationId, newDeadline)).thenReturn(Future.successful(()))

      underTest.overrideSubmissionDeadline(applicationId, newDeadline)

      verify(appRepositoryMock, times(1)).updateSubmissionDeadline(eqTo(applicationId), eqTo(newDeadline))
    }
  }

  "withdraw" must {
    "withdraw an application" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green")
      ))
      when(appRepositoryMock.withdraw(any[String], any[WithdrawApplication])).thenReturnAsync()
      when(candidateAllocationServiceMock.allocationsForApplication(any[String])(any[HeaderCarrier])).thenReturnAsync(Nil)
      when(candidateAllocationServiceMock.unAllocateCandidates(any[List[model.persisted.CandidateAllocation]])(any[HeaderCarrier]))
        .thenReturnAsync()
      val withdraw = WithdrawApplication("reason", None, "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdraw(applicationId, withdraw)
    }

    "withdraw from a scheme and stay in sift when a siftable scheme is left which requires a form to be filled in " +
      "and we have not filled in the form for that scheme" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
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
    }

    "withdraw from a scheme and progress to FSAC when a siftable scheme is left (not sdip) which requires a form to be filled in " +
      "and we have filled in the form and submitted for that scheme" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
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
    }

    "not progress the candidate to FSAC after withdrawing from a scheme after filling in the forms and a single scheme is left " +
      "which requires evaluation and it has not been evaluated (sifted)" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "not progress the candidate to FSAC if the candidate is awaiting allocation and withdraws from a scheme when a siftable scheme is left " +
      "which requires a form to be filled in and we have filled in the form for that scheme (we are in FSAC)" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
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
    }

    "progress to FSAC allocation if only non-siftable schemes are left and we have not filled in forms (SIFT_ENTERED)" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green"), // form to be filled in, no evaluation required
        SchemeEvaluationResult(SchemeId(generalist), "Green")            // no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "progress to FSAC allocation if only non-siftable schemes are left and we have filled in forms (SIFT_READY)" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green"), // form to be filled in, no evaluation required
        SchemeEvaluationResult(SchemeId(generalist), "Green")            // no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_READY), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "not progress to FSAC allocation if I have one scheme which requires a form to be filled in but I have not completed the form " +
      "(SIFT_ENTERED) and I have other schemes which require numeric test and I withdraw from those" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        // DiplomaticService - Form to be filled in, evaluation required (will stop us moving to FSAC)
        SchemeEvaluationResult(SchemeId(diplomaticService), "Green"),
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream, Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "progress to FSAC allocation if I have one scheme which requires a form to be filled in and I have completed the forms " +
      "(SIFT_READY) and no evaluation is required and I have other schemes which require numeric test and " +
      "I withdraw from those" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green"), // form to be filled in, no evaluation required
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
    }

    "progress to FSAC allocation if I am an sdip faststream candidate with the two non-sift schemes and I have not completed the " +
      "sdip form (SIFT_ENTERED) and I withdraw from the sdip scheme" in new TestFixture {
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
    }

    "progress to FSAC allocation if I am a sdip faststream candidate with 1 other scheme which requires a form to be filled in " +
      "but no evaluation and I have completed the form (SIFT_READY) and I then withdraw from sdip" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
        any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream, Some(ProgressStatuses.SIFT_READY), None, None))

      val withdraw = WithdrawScheme(SchemeId(sdip), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
    }

    "progress to FSAC allocation if I am a sdip faststream candidate with 1 other scheme which requires a numeric test " +
      "and evaluation and I have completed the form and been sifted (SIFT_COMPLETED) and I then withdraw from sdip" in new TestFixture {
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
    }

    "progress sdip faststream candidate to fsb awaiting allocation who is awaiting allocation to an assessment centre after withdrawing from " +
      "all fast stream schemes and just leaving sdip" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.FSB_AWAITING_ALLOCATION))
    }

    "progress sdip faststream candidate to fsb awaiting allocation who has been sifted in for sdip but has not yet been invited to an " +
      "assessment centre after withdrawing from all fast stream schemes (or being sifted with a fail) and just leaving sdip" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(commercial), "Red"),            // numeric test, evaluation required (already been sifted with a fail)
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.FSB_AWAITING_ALLOCATION))
    }

    "not progress sdip faststream candidate out of sift who has been sifted in for all schemes that require a sift but who also has" +
      "a scheme that does not require a sift. He withdraws from that one, leaving the 2 schemes sifted with a pass. In this scenario" +
      "the candidate will be picked up by the assessment centre scheduled job and moved to " +
      "ASSESSMENT_CENTRE_AWAITING_INVITATION" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(sdip), "Green"),                // form to be filled in, evaluation required (sifted with a pass)
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required (sifted with a pass)
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.SdipFaststream,
          Some(ProgressStatuses.SIFT_COMPLETED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])
    }

    "progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from all form based schemes but is still in the running for " +
      "numeric schemes and schemes that require no sift" in  new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(generalist), "Green"),          // nothing required
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from all form based schemes but is still in the running for " +
      "only numeric schemes" in  new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "not progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from all form based schemes and is only in the running " +
      "for schemes that require no sift" in  new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(generalist), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "not progress candidate to SIFT_READY who is in SIFT_ENTERED and has withdrawn from a form based scheme but is still in the running " +
      "for others as well as numeric schemes and schemes that require no sift" in  new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId(generalist), "Green"),          // nothing required
        SchemeEvaluationResult(SchemeId(diplomaticService), "Green"),   // form to be filled in, evaluation required
        SchemeEvaluationResult(SchemeId(commercial), "Green"),          // numeric test, evaluation required
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), "Green") // form to be filled in, no evaluation required
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any[Seq[SchemeEvaluationResult]]
      )).thenReturnAsync()
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(appRepositoryMock.findStatus(any[String])).thenReturnAsync(
        ApplicationStatusDetails(ApplicationStatus.SIFT, ApplicationRoute.Faststream,
          Some(ProgressStatuses.SIFT_ENTERED), None, None))

      val withdraw = WithdrawScheme(SchemeId(digitalAndTechnology), "reason", "Candidate")

      underTest.withdraw(applicationId, withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo(applicationId), eqTo(withdraw),
        any[Seq[SchemeEvaluationResult]]
      )
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(eqTo(applicationId),
        eqTo(ProgressStatuses.SIFT_READY))
    }

    "throw an exception when withdrawing from the last scheme" in new TestFixture {
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
  }

  "current scheme status with failure details" must {
    "return no failure reasons when all schemes are green" in new TestFixture {
      List(phase1EvaluationRepositoryMock, phase2EvaluationRepositoryMock, phase3EvaluationRepositoryMock).foreach { repo =>
        when(repo.getPassMarkEvaluation(any[String]())).thenReturnAsync(
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
        when(repo.getPassMarkEvaluation(any[String]())).thenReturnAsync(
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
        when(repo.getPassMarkEvaluation(any[String]())).thenReturnAsync(
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

  trait TestFixture {

    val appRepositoryMock: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val pdRepositoryMock: PersonalDetailsRepository = mock[PersonalDetailsRepository]
    val cdRepositoryMock: ContactDetailsRepository = mock[ContactDetailsRepository]
    val schemeRepositoryMock: SchemePreferencesRepository = mock[SchemePreferencesRepository]
    val mediaRepoMock: MediaRepository = mock[MediaRepository]
    val evalPhase1ResultMock: EvaluatePhase1ResultService = mock[EvaluatePhase1ResultService]
    val evalPhase2ResultMock: EvaluatePhase2ResultService = mock[EvaluatePhase2ResultService]
    val evalPhase3ResultMock: EvaluatePhase3ResultService = mock[EvaluatePhase3ResultService]
    val mockSiftService: ApplicationSiftService = mock[ApplicationSiftService]
    val phase1TestRepositoryMock: Phase1TestRepository = mock[Phase1TestRepository]
    val phase2TestRepositoryMock: Phase2TestRepository = mock[Phase2TestRepository]
    val phase3TestRepositoryMock: Phase3TestRepository = mock[Phase3TestRepository]
    val siftRepoMock = mock[ApplicationSiftRepository]
    val fsacRepoMock = mock[AssessmentCentreRepository]
    val fsbRepoMock = mock[FsbRepository]
    val phase1EvaluationRepositoryMock = mock[Phase1EvaluationMongoRepository]
    val phase2EvaluationRepositoryMock = mock[Phase2EvaluationMongoRepository]
    val phase3EvaluationRepositoryMock = mock[Phase3EvaluationMongoRepository]
    val candidateAllocationServiceMock = mock[CandidateAllocationService]

    val civilServiceExperienceRepositoryMock = mock[CivilServiceExperienceDetailsRepository]

    val business = "Business"
    val commercial = "Commercial"
    val digitalAndTechnology = "DigitalAndTechnology"
    val diplomaticService = "DiplomaticService"
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

    val mockSchemeRepo = new SchemeRepository {
      override lazy val siftableSchemeIds = Seq(SchemeId(commercial), SchemeId(digitalAndTechnology), SchemeId(diplomaticService))
      override lazy val noSiftEvaluationRequiredSchemeIds = Seq(SchemeId(digitalAndTechnology), SchemeId(edip), SchemeId(generalist),
        SchemeId(governmentCommunicationService), SchemeId(housesOfParliament), SchemeId(humanResources), SchemeId(projectDelivery),
        SchemeId(scienceAndEngineering)
      )
      override lazy val nonSiftableSchemeIds = Seq(SchemeId(generalist), SchemeId(humanResources))
      override lazy val numericTestSiftRequirementSchemeIds = Seq(SchemeId(commercial), SchemeId(finance))
    }

    val underTest = new ApplicationService with StcEventServiceFixture {
      val appRepository = appRepositoryMock
      val pdRepository = pdRepositoryMock
      val cdRepository = cdRepositoryMock
      val eventService = eventServiceMock
      val mediaRepo = mediaRepoMock
      val schemePrefsRepository = schemeRepositoryMock
      val schemesRepo = mockSchemeRepo
      val evaluateP1ResultService = evalPhase1ResultMock
      val evaluateP2ResultService = evalPhase2ResultMock
      val evaluateP3ResultService = evalPhase3ResultMock
      val siftService = mockSiftService
      val phase1TestRepo = phase1TestRepositoryMock
      val phase2TestRepository = phase2TestRepositoryMock
      val phase3TestRepository = phase3TestRepositoryMock
      val appSiftRepository = siftRepoMock
      val fsacRepo = fsacRepoMock
      val fsbRepo = fsbRepoMock
      val phase1EvaluationRepository = phase1EvaluationRepositoryMock
      val phase2EvaluationRepository = phase2EvaluationRepositoryMock
      val phase3EvaluationRepository = phase3EvaluationRepositoryMock
      val civilServiceExperienceDetailsRepo = civilServiceExperienceRepositoryMock
      val candidateAllocationService = candidateAllocationServiceMock
    }

    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]

    val userId = "userId"
    val applicationId = "appId"
    val frameworkId = ""

    val candidate1 = Candidate(userId = "user123", applicationId = Some("appId234"), email = Some("test1@localhost"),
      None, None, None, None, None, None, None, None, None)

    val cd1 = ContactDetails(outsideUk = false, Address("line1"), None, None, "email@email.com", "123":PhoneNumber)

    val candidate2 = Candidate(userId = "user456", applicationId = Some("appId4567"), email = Some("test2@localhost"),
      None, None, None, None, None, None, None, None, None)

    val candidate3 = Candidate(userId = "user569", applicationId = Some("appId84512"), email = Some("test3@localhost"),
      None, None, None, None, None, None, None, None, None)

    val generalException = new RuntimeException("something went wrong")
    val failure = Future.failed(generalException)

    val getApplicationsToFixSuccess2: Future[List[Candidate]] = Future.successful(candidate1 :: candidate2 :: Nil)
    val getApplicationsToFixSuccess1: Future[List[Candidate]] = Future.successful(candidate3 :: Nil)
    val getApplicationsToFixFailure: Future[List[Candidate]] = Future.failed(generalException)
    val getApplicationsToFixEmpty: Future[List[Candidate]] = Future.successful(Nil)
    val success = Future.successful(())
  }
}
