/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers.report

import config.TestFixtureBase
import connectors.AuthProviderClient
import controllers.ReportingController
import model.persisted.fsb.ScoresAndFeedback
import model.report.onlinetestpassmark.{ ApplicationForOnlineTestPassMarkReportItemExamples, TestResultsForOnlineTestPassMarkReportItemExamples }
import model.report.{ OnlineTestPassMarkReportItem, _ }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.PreviousYearCandidatesDetailsRepository
import repositories.{ MediaRepository, QuestionnaireRepository, contactdetails }
import repositories.application.{ GeneralApplicationRepository, ReportingRepository }
import repositories.csv.FSACIndicatorCSVRepository
import repositories.events.EventsRepository
import repositories.sift.ApplicationSiftRepository
import repositories._
import repositories.fsb.FsbRepository
import testkit.MockitoImplicits.OngoingStubbingExtension
import testkit.UnitWithAppSpec

import scala.language.postfixOps

class OnlineTestPassMarkReportingControllerSpec extends UnitWithAppSpec {

  "Online test pass mark report" should {
    "return nothing if no application exists" in new TestFixture {
      when(mockReportRepository.onlineTestPassMarkReportFsOnly).thenReturnAsync(Nil)
      when(mockQuestionRepository.findForOnlineTestPassMarkReport(any[List[String]]())).thenReturnAsync(Map.empty)
      when(mockFsbRepo.findScoresAndFeedback(any[List[String]]())).thenReturnAsync(Map.empty)

      val response = controller.onlineTestPassMarkReportFsOnly(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return nothing if applications exist, but no questionnaires" in new TestFixture {
      when(mockReportRepository.onlineTestPassMarkReportFsOnly).thenReturnAsync(applications)
      when(mockQuestionRepository.findForOnlineTestPassMarkReport(any[List[String]]())).thenReturnAsync(Map.empty)
      when(mockFsbRepo.findScoresAndFeedback(any[List[String]]())).thenReturnAsync(Map.empty)

      val response = controller.onlineTestPassMarkReportFsOnly(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return applications and questionnaires if applications and questionnaires exist, but no test results" in new TestFixture {
      when(mockReportRepository.onlineTestPassMarkReportFsOnly).thenReturnAsync(applicationsWithNoTestResults)

      when(mockQuestionRepository.findForOnlineTestPassMarkReport(any[List[String]]())).thenReturnAsync(questionnairesForNoTestResults)

      when(mockFsbRepo.findScoresAndFeedback(any[List[String]])).thenReturnAsync(
        Map(
          ApplicationForOnlineTestPassMarkReportExamples.applicationWithNoTestResult1.applicationId -> None,
          ApplicationForOnlineTestPassMarkReportExamples.applicationWithNoTestResult2.applicationId -> None
        )
      )

      val response = controller.onlineTestPassMarkReportFsOnly(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK
      result must have size 2
      result must contain(OnlineTestPassMarkReportItem(
        ApplicationForOnlineTestPassMarkReportItemExamples.applicationWithNoTestResult1,
        QuestionnaireReportItemExamples.questionnaire1))
      result must contain(OnlineTestPassMarkReportItem(
        ApplicationForOnlineTestPassMarkReportItemExamples.applicationWithNoTestResult2,
        QuestionnaireReportItemExamples.questionnaire2))
    }

    "return applications with questionnaire and test results" in new TestFixture {
      when(mockReportRepository.onlineTestPassMarkReportFsOnly).thenReturnAsync(applications)

      when(mockQuestionRepository.findForOnlineTestPassMarkReport(any[List[String]]())).thenReturnAsync(questionnaires)

      when(mockFsbRepo.findScoresAndFeedback(any[List[String]])).thenReturnAsync(
        Map(
          ApplicationForOnlineTestPassMarkReportExamples.application1.applicationId -> None,
          ApplicationForOnlineTestPassMarkReportExamples.application2.applicationId -> None
        )
      )

      val response = controller.onlineTestPassMarkReportFsOnly(frameworkId)(request).run
      val result = contentAsJson(response).as[List[OnlineTestPassMarkReportItem]]

      status(response) mustBe OK

      result must contain theSameElementsAs List(
        OnlineTestPassMarkReportItem(ApplicationForOnlineTestPassMarkReportItemExamples.application1,
          QuestionnaireReportItemExamples.questionnaire1),
        OnlineTestPassMarkReportItem(ApplicationForOnlineTestPassMarkReportItemExamples.application2,
          QuestionnaireReportItemExamples.questionnaire2)
      )
    }
  }

  trait TestFixture extends TestFixtureBase {
    val frameworkId = "FastStream-2016"

    val mockReportRepository = mock[ReportingRepository]
    val mockQuestionRepository = mock[QuestionnaireRepository]
    val mockMediaRepository = mock[MediaRepository]
    val mockAssessorAllocationRepository = mock[AssessorAllocationRepository]
    val mockEventsRepository = mock[EventsRepository]
    val mockAssessorRepository = mock[AssessorRepository]
    val mockSchemeYamlRepository = mock[SchemeRepository]
    val mockAssessmentScoresRepository = mock[AssessmentScoresRepository]
    val mockCandidateAllocationRepo = mock[CandidateAllocationRepository]
    val mockApplicationSiftRepo = mock[ApplicationSiftRepository]
    val mockFsbRepo = mock[FsbRepository]
    val mockAppRepo = mock[GeneralApplicationRepository]

    val controller = new ReportingController {
      val reportingRepository = mockReportRepository
      val contactDetailsRepository = mock[contactdetails.ContactDetailsRepository]
      val questionnaireRepository = mockQuestionRepository
      val assessmentScoresRepository = mockAssessmentScoresRepository
      val mediaRepository: MediaRepository = mockMediaRepository
      val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository = mock[FSACIndicatorCSVRepository]
      val prevYearCandidatesDetailsRepository = mock[PreviousYearCandidatesDetailsRepository]
      val authProviderClient = mock[AuthProviderClient]
      val eventsRepository = mockEventsRepository
      val assessorRepository = mockAssessorRepository
      val assessorAllocationRepository = mockAssessorAllocationRepository
      val schemeRepo = mockSchemeYamlRepository
      val candidateAllocationRepo = mockCandidateAllocationRepo
      val applicationSiftRepository = mockApplicationSiftRepo
      val fsbRepository: FsbRepository = mockFsbRepo
      val applicationRepository = mockAppRepo
    }

    lazy val testResults = Map(
      ApplicationForOnlineTestPassMarkReportExamples.application1.applicationId ->
        TestResultsForOnlineTestPassMarkReportItemExamples.testResults1,
      ApplicationForOnlineTestPassMarkReportExamples.application2.applicationId ->
        TestResultsForOnlineTestPassMarkReportItemExamples.testResults2)

    lazy val applications = List(
      ApplicationForOnlineTestPassMarkReportExamples.application1,
      ApplicationForOnlineTestPassMarkReportExamples.application2)
    lazy val applicationsWithNoTestResults = List(
      ApplicationForOnlineTestPassMarkReportExamples.applicationWithNoTestResult1,
      ApplicationForOnlineTestPassMarkReportExamples.applicationWithNoTestResult2)

    lazy val questionnaires = Map(
      ApplicationForOnlineTestPassMarkReportExamples.application1.applicationId ->
        QuestionnaireReportItemExamples.questionnaire1,
      ApplicationForOnlineTestPassMarkReportExamples.application2.applicationId ->
        QuestionnaireReportItemExamples.questionnaire2)
    lazy val questionnairesForNoTestResults = Map(
      ApplicationForOnlineTestPassMarkReportExamples.applicationWithNoTestResult1.applicationId ->
        QuestionnaireReportItemExamples.questionnaire1,
      ApplicationForOnlineTestPassMarkReportExamples.applicationWithNoTestResult2.applicationId ->
        QuestionnaireReportItemExamples.questionnaire2)

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.onlineTestPassMarkReportFsOnly(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    when(mockAssessmentScoresRepository.findAll).thenReturnAsync(Nil)
    when(mockApplicationSiftRepo.findAllResults).thenReturnAsync(Seq.empty[SiftPhaseReportItem])
  }
}
