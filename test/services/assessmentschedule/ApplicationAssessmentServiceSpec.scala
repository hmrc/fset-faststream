/*
 * Copyright 2016 HM Revenue & Customs
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

package services.applicationassessment

import config.AssessmentEvaluationMinimumCompetencyLevel
import connectors.EmailClient
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.CandidateScoresCommands.{CandidateScores, CandidateScoresAndFeedback}
import model.Commands._
import model.ApplicationStatus._
import model.EvaluationResults.AssessmentRuleCategoryResult
import model.Exceptions.{IncorrectStatusInApplicationException, NotFoundException}
import model.PassmarkPersistedObjects.{AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme, PassMarkSchemeThreshold}
import model.PersistedObjects.ContactDetails
import model.persisted.ApplicationForNotification
import model.{Address, EvaluationResults, LocationPreference, Preferences}
import org.joda.time.DateTime
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.PlaySpec
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import repositories.{ApplicationAssessmentScoresRepository, _}
import services.AuditService
import services.evaluation.AssessmentCentrePassmarkRulesEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ApplicationAssessmentServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val applicationAssessmentRepositoryMock = mock[ApplicationAssessmentRepository]
  val onlineTestRepositoryMock = mock[Phase1TestRepository]
  val auditServiceMock = mock[AuditService]
  val emailClientMock = mock[EmailClient]
  val aRepositoryMock = mock[GeneralApplicationRepository]
  val acpmsServiceMock = mock[AssessmentCentrePassMarkSettingsService]
  val aasRepositoryMock = mock[ApplicationAssessmentScoresRepository]
  val fpRepositoryMock = mock[FrameworkPreferenceRepository]
  val cdRepositoryMock = mock[ContactDetailsRepository]
  val passmarkRulesEngineMock = mock[AssessmentCentrePassmarkRulesEngine]

  val ApplicationId = "1111-1111"
  val NotFoundApplicationId = "Not-Found-Id"

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  val auditDetails = Map(
    "applicationId" -> ApplicationId
  )

  /*TODO FS FIX ME
  "delete an Application Assessment" should {
    when(applicationAssessmentRepositoryMock.delete(eqTo(ApplicationId))).thenReturn(Future.successful(()))
    when(applicationAssessmentRepositoryMock.delete(eqTo(NotFoundApplicationId))).thenReturn(
      Future.failed(new NotFoundException("No application assessments were found"))
    )

    when(onlineTestRepositoryMock.removeCandidateAllocationStatus(eqTo(ApplicationId))).thenReturn(Future.successful(()))

    "return a deletion success response when an application id exists" in new ApplicationAssessmentServiceFixture {
      val resultFuture = applicationAssessmentService.removeFromApplicationAssessmentSlot(ApplicationId)
      resultFuture.futureValue mustBe (())
      verify(auditServiceMock).logEventNoRequest("ApplicationAssessmentDeleted", auditDetails)
      verify(auditServiceMock).logEventNoRequest("ApplicationDeallocated", auditDetails)
      verify(onlineTestRepositoryMock).removeCandidateAllocationStatus(eqTo(ApplicationId))

    }
    "return a not found response when an application id does not exist" in new ApplicationAssessmentServiceFixture {
      val result = applicationAssessmentService.removeFromApplicationAssessmentSlot(NotFoundApplicationId)
      result.failed.futureValue mustBe a[NotFoundException]
    }
  }*/

  "next Assessment Candidate and evaluate Assessment" should {
    val Threshhold = PassMarkSchemeThreshold(10.0, 20.0)
    val PassmarkSettings = AssessmentCentrePassMarkSettingsResponse(List(
      AssessmentCentrePassMarkScheme("Business", Some(Threshhold)),
      AssessmentCentrePassMarkScheme("Commercial", Some(Threshhold)),
      AssessmentCentrePassMarkScheme("Digital and technology", Some(Threshhold)),
      AssessmentCentrePassMarkScheme("Finance", Some(Threshhold)),
      AssessmentCentrePassMarkScheme("Project delivery", Some(Threshhold))
    ), Some(AssessmentCentrePassMarkInfo("1", DateTime.now, "user")))
    val CandidateScoresWithFeedback = CandidateScoresAndFeedback("app1", Some(true), assessmentIncomplete = false,
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)))
    val preferences = Preferences(
      LocationPreference("London", "London", "Business", Some("IT")),
      Some(LocationPreference("London", "Reading", "Logistics", None))
    )

    when(acpmsServiceMock.getLatestVersion).thenReturn(Future.successful(PassmarkSettings))
    when(aRepositoryMock.nextApplicationReadyForAssessmentScoreEvaluation(any[String])).thenReturn(Future.successful(Some("app1")))
    when(aasRepositoryMock.tryFind("app1")).thenReturn(Future.successful(Some(CandidateScoresWithFeedback)))
    when(fpRepositoryMock.tryGetPreferences("app1")).thenReturn(Future.successful(Some(preferences)))

    "return an assessment candidate score with application Id" in new ApplicationAssessmentServiceFixture {
      val result = applicationAssessmentService.nextAssessmentCandidateScoreReadyForEvaluation.futureValue

      result must not be empty
      result.get.scores.applicationId must be("app1")
    }

    "return none if there is no passmark settings set" in new ApplicationAssessmentServiceFixture {
      val passmarkSchemesNotSet = PassmarkSettings.schemes.map(p => AssessmentCentrePassMarkScheme(p.schemeName))
      when(acpmsServiceMock.getLatestVersion).thenReturn(Future.successful(PassmarkSettings.copy(schemes = passmarkSchemesNotSet)))

      val result = applicationAssessmentService.nextAssessmentCandidateScoreReadyForEvaluation.futureValue

      result must be(empty)
    }

    "return none if there is no application ready for assessment score evaluation" in new ApplicationAssessmentServiceFixture {
      when(aRepositoryMock.nextApplicationReadyForAssessmentScoreEvaluation(any[String])).thenReturn(Future.successful(None))

      val result = applicationAssessmentService.nextAssessmentCandidateScoreReadyForEvaluation.futureValue

      result must be(empty)
    }

    "save evaluation result and emit and audit event" in new ApplicationAssessmentServiceFixture {
      val scores = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, preferences, CandidateScoresWithFeedback)
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, None, None)
      val result = AssessmentRuleCategoryResult(None, Some(EvaluationResults.Green), None, None, None, None, None, None)
      when(passmarkRulesEngineMock.evaluate(scores, config)).thenReturn(result)
      when(aRepositoryMock.saveAssessmentScoreEvaluation("app1", "1", result, ASSESSMENT_CENTRE_PASSED)).thenReturn(Future.successful(()))

      applicationAssessmentService.evaluateAssessmentCandidateScore(scores, config).futureValue

      verify(aRepositoryMock).saveAssessmentScoreEvaluation("app1", "1", result, ASSESSMENT_CENTRE_PASSED)
      verify(auditServiceMock).logEventNoRequest(
        "ApplicationAssessmentEvaluated",
        Map("applicationId" -> "app1", "applicationStatus" -> "ASSESSMENT_CENTRE_PASSED")
      )
    }
  }

  "process assessment centre passed or failed application" should {
    "return successful if there no applications with assessment centre passed or failed" in new ApplicationAssessmentServiceFixture {
      when(aRepositoryMock.nextAssessmentCentrePassedOrFailedApplication()).thenReturn(Future.successful(None))
      applicationAssessmentService.processNextAssessmentCentrePassedOrFailedApplication.futureValue must be(())
    }

    "return successful if there is one application with assessment centre passed and send notification email and update application status to " +
      "ASSESSMENT_CENTRE_PASSED_NOTIFIED and audit AssessmentCentrePassedEmailed event and audit new status " +
      "with event ApplicationAssessmentPassedNotified" in new ApplicationAssessmentServiceFixture {
        when(aRepositoryMock.nextAssessmentCentrePassedOrFailedApplication()).thenReturn(Future.successful(
          Some(ApplicationForNotification("appId1", "userId1", "preferredName1", ASSESSMENT_CENTRE_PASSED))
        ))
        when(emailClientMock.sendAssessmentCentrePassed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier]))
          .thenReturn(Future.successful(()))
        when(aRepositoryMock.updateStatus(eqTo("appId1"), eqTo(ASSESSMENT_CENTRE_PASSED_NOTIFIED))).thenReturn(Future.successful(()))

        applicationAssessmentService.processNextAssessmentCentrePassedOrFailedApplication.futureValue must be(())

        verify(emailClientMock).sendAssessmentCentrePassed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier])
        verify(aRepositoryMock).updateStatus("appId1", ASSESSMENT_CENTRE_PASSED_NOTIFIED)
        val auditDetails = Map("userId" -> "userId1", "email" -> "email@mailinator.com")
        verify(auditServiceMock).logEventNoRequest(eqTo("AssessmentCentrePassedEmailed"), eqTo(auditDetails))
        val auditDetailsNewStatus = Map("applicationId" -> "appId1", "applicationStatus" -> "ASSESSMENT_CENTRE_PASSED_NOTIFIED")
        verify(auditServiceMock).logEventNoRequest("ApplicationAssessmentPassedNotified", auditDetailsNewStatus)
      }

    "return successful if there is one application with assessment centre failed and send notification email and update application status to " +
      "ASSESSMENT_CENTRE_FAILED_NOTIFIED and audit AssessmentCentreFailedEmailed event and audit new status with event " +
      "ApplicationAssessmentPassedNotified" in new ApplicationAssessmentServiceFixture {
        when(aRepositoryMock.nextAssessmentCentrePassedOrFailedApplication()).thenReturn(Future.successful(
          Some(ApplicationForNotification("appId1", "userId1", "preferredName1", ASSESSMENT_CENTRE_FAILED))
        ))
        when(emailClientMock.sendAssessmentCentreFailed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier]))
          .thenReturn(Future.successful(()))
        when(aRepositoryMock.updateStatus(eqTo("appId1"), eqTo(ASSESSMENT_CENTRE_FAILED_NOTIFIED))).thenReturn(Future.successful(()))

        applicationAssessmentService.processNextAssessmentCentrePassedOrFailedApplication.futureValue must be(())

        verify(emailClientMock).sendAssessmentCentreFailed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier])
        verify(aRepositoryMock).updateStatus("appId1", ASSESSMENT_CENTRE_FAILED_NOTIFIED)
        val auditDetails = Map("userId" -> "userId1", "email" -> "email@mailinator.com")
        verify(auditServiceMock).logEventNoRequest(eqTo("AssessmentCentreFailedEmailed"), eqTo(auditDetails))
        val auditDetailsNewStatus = Map("applicationId" -> "appId1", "applicationStatus" -> "ASSESSMENT_CENTRE_FAILED_NOTIFIED")
        verify(auditServiceMock).logEventNoRequest("ApplicationAssessmentFailedNotified", auditDetailsNewStatus)
      }
  }

  "email candidate" should {
    "throw IncorrectStatusInApplicationException when we pass ONLINE_TEST_COMPLETED" in new ApplicationAssessmentServiceFixture {
      val application = ApplicationForNotification("appId1", "userId1", "preferredName1", ONLINE_TEST_FAILED)
      val result = applicationAssessmentService.emailCandidate(application, "email@mailinator.com")
      result.failed.futureValue mustBe a[IncorrectStatusInApplicationException]
    }
  }

  trait ApplicationAssessmentServiceFixture {
    implicit val hc = HeaderCarrier()

    val contactDetails = ContactDetails(Address("address1"), "postCode1", "email@mailinator.com", Some("11111111"))
    when(cdRepositoryMock.find("userId1")).thenReturn(Future.successful(contactDetails))

    val applicationAssessmentService = new ApplicationAssessmentService {
      val appAssessRepository = applicationAssessmentRepositoryMock
      val otRepository = onlineTestRepositoryMock
      val auditService = auditServiceMock
      val emailClient = emailClientMock
      val passmarkService: AssessmentCentrePassMarkSettingsService = acpmsServiceMock
      val aRepository = aRepositoryMock
      val aasRepository: ApplicationAssessmentScoresRepository = aasRepositoryMock
      val fpRepository: FrameworkPreferenceRepository = fpRepositoryMock
      val cdRepository = cdRepositoryMock
      val passmarkRulesEngine: AssessmentCentrePassmarkRulesEngine = passmarkRulesEngineMock
    }
  }
}
