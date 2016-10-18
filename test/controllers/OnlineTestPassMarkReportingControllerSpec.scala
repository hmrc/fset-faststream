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

package controllers

import config.TestFixtureBase
import connectors.AuthProviderClient
import connectors.ExchangeObjects.Candidate
import mocks._
import mocks.application.DocumentRootInMemoryRepository
import model.OnlineTestCommands.TestResult
import model.PersistedObjects.ContactDetailsWithId
import model.report.{OnlineTestPassMarkReportItem, _}
import model.{Address, SchemeType}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsArray
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import repositories.application.GeneralApplicationRepository
import repositories.{ApplicationAssessmentScoresRepository, ContactDetailsRepository, MediaRepository, QuestionnaireRepository, TestReportRepository}
import testkit.MockitoImplicits.OngoingStubbingExtension
import testkit.MockitoSugar

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Random

class OnlineTestPassMarkReportingControllerSpec extends PlaySpec with Results with MockitoSugar {

  "Online test pass mark report" should {
    "return nothing if no application exists" in new PassMarkReportTestFixture {
      when(mockAppRepository.onlineTestPassMarkReport(any())).thenReturnAsync(Nil)
      when(mockQuestionRepository.findForOnlineTestPassMarkReport).thenReturnAsync(Map.empty)
      when(mockTestResultRepository.getOnlineTestReports).thenReturnAsync(Map.empty)

      val response = controller.onlineTestPassMarkReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return nothing if applications exist, but no questionnaires" in new PassMarkReportTestFixture {
      when(mockAppRepository.onlineTestPassMarkReport(any())).thenReturnAsync(applications)
      when(mockQuestionRepository.findForOnlineTestPassMarkReport).thenReturnAsync(Map.empty)
      when(mockTestResultRepository.getOnlineTestReports).thenReturnAsync(Map.empty)

      val response = controller.onlineTestPassMarkReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return applications and questionnaires if applications and questionnaires exist, but no test results" in new PassMarkReportTestFixture {
      when(mockAppRepository.onlineTestPassMarkReport(any())).thenReturnAsync(applicationsWithNoTestResults)

      when(mockQuestionRepository.findForOnlineTestPassMarkReport).thenReturnAsync(questionnairesForNoTestResults)

      val response = controller.onlineTestPassMarkReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK
      result must have size 2
      result must contain(OnlineTestPassMarkReportItem(applicationWithNoTestResult1, questionnaire1))
      result must contain(OnlineTestPassMarkReportItem(applicationWithNoTestResult2, questionnaire2))
    }

    "return applications with questionnaire and test results" in new PassMarkReportTestFixture {
      when(mockAppRepository.onlineTestPassMarkReport(any())).thenReturnAsync(applications)

      when(mockQuestionRepository.findForOnlineTestPassMarkReport).thenReturnAsync(questionnaires)
      when(mockTestResultRepository.getOnlineTestReports).thenReturnAsync(testResults)

      val response = controller.onlineTestPassMarkReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK
      result mustBe List(
        OnlineTestPassMarkReportItem(application1, questionnaire1),
        OnlineTestPassMarkReportItem(application2, questionnaire2)
      )
    }
  }

  trait PassMarkReportTestFixture extends TestFixtureBase {
    val frameworkId = "FastStream-2016"

    val mockAppRepository = mock[GeneralApplicationRepository]
    val mockQuestionRepository = mock[QuestionnaireRepository]
    val mockTestResultRepository = mock[TestReportRepository]
    val mockMediaRepository = mock[MediaRepository]
    val controller = new ReportingController {
      val appRepository = mockAppRepository
      val cdRepository = mock[ContactDetailsRepository]
      val authProviderClient = mock[AuthProviderClient]
      val questionnaireRepository = mockQuestionRepository
      val testReportRepository = mockTestResultRepository
      val assessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
      val medRepository: MediaRepository = mockMediaRepository
    }

    lazy val testResults1 = newTestResults
    lazy val testResults2 = newTestResults
    lazy val testResults = Map(application1.applicationId -> testResults1, application2.applicationId -> testResults2)

    lazy val application1 = newApplicationForOnlineTestPassMarkReportItem(testResults1)
    lazy val application2 = newApplicationForOnlineTestPassMarkReportItem(testResults2)
    lazy val applications = List(application1, application2)

    lazy val applicationWithNoTestResult1 = newApplicationForOnlineTestPassMarkReportItem(
      TestResultsForOnlineTestPassMarkReportItem(None, None, None))
    lazy val applicationWithNoTestResult2 = newApplicationForOnlineTestPassMarkReportItem(
      TestResultsForOnlineTestPassMarkReportItem(None, None, None))
    lazy val applicationsWithNoTestResults = List(applicationWithNoTestResult1, applicationWithNoTestResult2)

    lazy val questionnaire1 = newQuestionnaire
    lazy val questionnaire2 = newQuestionnaire
    lazy val questionnaires = Map(application1.applicationId -> questionnaire1,
      application2.applicationId -> questionnaire2)
    lazy val questionnairesForNoTestResults = Map(applicationWithNoTestResult1.applicationId -> questionnaire1,
      applicationWithNoTestResult2.applicationId -> questionnaire2)

    def newApplicationForOnlineTestPassMarkReportItem(testsResult: TestResultsForOnlineTestPassMarkReportItem) =
      ApplicationForOnlineTestPassMarkReportItem(
        rnd("AppId"),
        "phase1_tests_results_received",
        List(SchemeType.Commercial, SchemeType.DigitalAndTechnology),
        None,
        None,
        None,
        None,
        testsResult)

    def newQuestionnaire =
      QuestionnaireReportItem(someRnd("Gender"), someRnd("Orientation"), someRnd("Ethnicity"),
        someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("(Self)Employed"), someRnd("CompanySize"), rnd("SES"),
        someRnd("university"))

    def newTestResults =
      TestResultsForOnlineTestPassMarkReportItem(maybe(newTestResult), maybe(newTestResult), maybe(newTestResult))

    private def someDouble = Some(Random.nextDouble())

    def newTestResult = TestResult("Completed", "Example Norm", someDouble, someDouble, someDouble, someDouble)

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.onlineTestPassMarkReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
