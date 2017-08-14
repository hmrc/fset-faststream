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

package services

import model.Commands.PhoneNumber
import model.Exceptions.{ LastSchemeWithdrawException, PassMarkEvaluationNotFound }
import model.command.{ ProgressResponse, WithdrawApplication, WithdrawScheme }
import model.{ ApplicationResponse, Candidate }
import model.stc.AuditEvents
import org.joda.time.DateTime
import model.persisted.{ ContactDetails, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ Address, ApplicationRoute, SchemeId, SelectedSchemes }
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.MediaRepository
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{ PassToPhase2, ResetPhase1TestInvitedSubmitted }
import services.application.ApplicationService
import testkit.{ ExtendedTimeout, UnitSpec }
import testkit.MockitoImplicits._
import uk.gov.hmrc.play.http.HeaderCarrier
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import services.stc.StcEventServiceFixture

import scala.concurrent.Future


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

    "don't fix anything if no issues is detected" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixEmpty)

      underTest.fix(FixBatch(PassToPhase2, 1) :: Nil)(hc, rh).futureValue

      verify(appRepositoryMock, never).fix(any[Candidate], any[FixBatch])
      verifyZeroInteractions(underTest.auditEventHandlerMock)
    }

    "proceeds with the others searches if one of them fails" in new TestFixture {
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
        List(SchemeEvaluationResult(SchemeId("Commercial"), "Green"),
          SchemeEvaluationResult(SchemeId("GovernmentOperationalResearchService"), "Red")),
        "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId("Commercial"))
    }

    "retrieve passed schemes for Faststream application with fast pass approved" in new TestFixture {
      val faststreamApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Faststream,
        userId,ProgressResponse(applicationId, fastPassAccepted = true), None, None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(schemeRepositoryMock.find(eqTo(applicationId))).thenReturn(Future.successful(SelectedSchemes(List(SchemeId("Commercial")),
        orderAgreed = true, eligible = true)))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId("Commercial"))
    }

    "retrieve passed schemes for Edip application" in new TestFixture {
      val edipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Edip, userId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId("Edip"), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(edipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId("Edip"))
    }

    "retrieve passed schemes for Sdip application" in new TestFixture {
      val sdipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Sdip, userId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId("Sdip"), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(sdipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId("Sdip"))
    }

    "retrieve passed schemes for SdipFaststream application" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId,
        ProgressResponse(applicationId), None, None
      )
      val phase1PassmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId("Sdip"), "Green"),
        SchemeEvaluationResult(SchemeId("Finance"), "Green")), "", None)

      val phase3PassmarkEvaluation = PassmarkEvaluation("", None,
        List(SchemeEvaluationResult(SchemeId("Commercial"), "Green"),
          SchemeEvaluationResult(SchemeId("GovernmentOperationalResearchService"), "Red"),
          SchemeEvaluationResult(SchemeId("Finance"), "Red")
        ), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(application))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(phase3PassmarkEvaluation))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId("Sdip"))
    }

    "retrieve schemes for SdipFaststream when the applicant has failed Faststream prior to Phase 3 tests" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId,
        ProgressResponse(applicationId), None, None
      )
      val phase1PassmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(SchemeId("Sdip"), "Green")), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(application))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.failed(PassMarkEvaluationNotFound(applicationId)))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))).thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(SchemeId("Sdip"))
    }
  }

  "Override submission deadline" must {
    "update the submission deadline in the repository" in new TestFixture {
      val newDeadline = new DateTime(2016, 5, 21, 23, 59, 59)
      val appId = "appId"

      when(appRepositoryMock.updateSubmissionDeadline(appId, newDeadline)).thenReturn(Future.successful(()))

      underTest.overrideSubmissionDeadline("appId", newDeadline)

      verify(appRepositoryMock, times(1)).updateSubmissionDeadline(eqTo(appId), eqTo(newDeadline))
    }
  }

  "withdraw" must {
    "withdraw an application" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), "Green")
      ))
      when(appRepositoryMock.withdraw(any[String], any[WithdrawApplication])).thenReturnAsync()
      val withdraw = WithdrawApplication("reason", None, "Candidate")

      underTest.withdraw("appId", withdraw).futureValue

      verify(appRepositoryMock).withdraw("appId", withdraw)

    }

    "withdraw from a scheme" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), "Green"),
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), "Green")
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any(classOf[(WithdrawScheme) => Seq[SchemeEvaluationResult]])
      )).thenReturnAsync()
      val withdraw = WithdrawScheme(SchemeId("Commercial"), "reason", "Candidate")

      underTest.withdraw("appId", withdraw).futureValue

      verify(appRepositoryMock).withdrawScheme(eqTo("appId"), eqTo(withdraw),
        any(classOf[(WithdrawScheme) => Seq[SchemeEvaluationResult]])
      )
    }

    "throw an exception when withdrawing from the last scheme" in new TestFixture {
      when(appRepositoryMock.find(any[String])).thenReturnAsync(Some(candidate1))
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), "Green")
      ))
      when(cdRepositoryMock.find(candidate1.userId)).thenReturnAsync(cd1)
      when(appRepositoryMock.withdrawScheme(any[String], any[WithdrawScheme],
          any(classOf[(WithdrawScheme) => Seq[SchemeEvaluationResult]])
      )).thenReturnAsync()

      val withdraw = WithdrawScheme(SchemeId("Commercial"), "reason", "Candidate")

      whenReady(underTest.withdraw("appId", withdraw).failed) { r =>
        r mustBe a[LastSchemeWithdrawException]
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
    val evalPhase3ResultMock: EvaluatePhase3ResultService = mock[EvaluatePhase3ResultService]

    val underTest = new ApplicationService with StcEventServiceFixture {
      val appRepository = appRepositoryMock
      val pdRepository = pdRepositoryMock
      val cdRepository = cdRepositoryMock
      val eventService = eventServiceMock
      val mediaRepo = mediaRepoMock
      val schemeRepository = schemeRepositoryMock
      val evaluateP1ResultService = evalPhase1ResultMock
      val evaluateP3ResultService = evalPhase3ResultMock
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
