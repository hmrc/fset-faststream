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
import model.persisted.MediaExamples
import model.report.{ DiversityReportItem, DiversityReportItemExamples, QuestionnaireReportItemExamples }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import persisted.ApplicationForDiversityReportExamples
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.GeneralApplicationRepository
import repositories.{ ApplicationAssessmentScoresRepository, ContactDetailsRepository, MediaRepository, QuestionnaireRepository, TestReportRepository }
import testkit.MockitoImplicits.OngoingStubbingExtension
import testkit.UnitWithAppSpec

import scala.language.postfixOps

class DiversityReportingControllerSpec extends UnitWithAppSpec {

  "Diversity report" should {
    "return empty if no applications exist" in new DiversityReportTestFixture {
      val response = controller.diversityReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[DiversityReportItem]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return applications with no questionnaries and no media when no questionnaires and no media" in new DiversityReportTestFixture {
      when(mockAppRepository.diversityReport(any())).thenReturnAsync(applications)

      val response = controller.diversityReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[DiversityReportItem]]

      status(response) mustBe OK
      result mustBe List(DiversityReportItemExamples.OnlyApplication1, DiversityReportItemExamples.OnlyApplication2)
    }

    "return applications with questionnaires and no media when there are questionnaires but no media" in new DiversityReportTestFixture {
      when(mockAppRepository.diversityReport(any())).thenReturnAsync(applications)
      when(mockQuestionRepository.findAllForDiversityReport).thenReturnAsync(questionnaires)

      val response = controller.diversityReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[DiversityReportItem]]

      status(response) mustBe OK
      result mustBe List(
        DiversityReportItemExamples.OnlyApplicationAndQuestionnaire1,
        DiversityReportItemExamples.OnlyApplicationAndQuestionnaire2)
    }

    "return applications with no questionnaires or no media when passing questionnaires" +
      " that dont belong to applications and no media" in new DiversityReportTestFixture {
      when(mockAppRepository.diversityReport(any())).thenReturnAsync(applications)
      when(mockQuestionRepository.findAllForDiversityReport).thenReturnAsync(notFoundQuestionnaires)

      val response = controller.diversityReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[DiversityReportItem]]

      status(response) mustBe OK
      result mustBe List(DiversityReportItemExamples.OnlyApplication1, DiversityReportItemExamples.OnlyApplication2)
    }

    "return applications with questionnaires and no media when passing questionnaires" +
      " and media that dont belong to the applications"  in new DiversityReportTestFixture {
      when(mockAppRepository.diversityReport(any())).thenReturnAsync(applications)
      when(mockQuestionRepository.findAllForDiversityReport).thenReturnAsync(questionnaires)
      when(mockMediaRepository.findAll()).thenReturnAsync(notFoundMedias)

      val response = controller.diversityReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[DiversityReportItem]]

      status(response) mustBe OK
      result mustBe List(
        DiversityReportItemExamples.OnlyApplicationAndQuestionnaire1,
        DiversityReportItemExamples.OnlyApplicationAndQuestionnaire2)
    }

    "return applications with no questionnaires and with media when passing media but no questionnaires" in new DiversityReportTestFixture {
      when(mockAppRepository.diversityReport(any())).thenReturnAsync(applications)
      when(mockMediaRepository.findAll()).thenReturnAsync(medias)

      val response = controller.diversityReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[DiversityReportItem]]

      status(response) mustBe OK
      result mustBe List(DiversityReportItemExamples.OnlyApplicationAndMedia1, DiversityReportItemExamples.OnlyApplicationAndMedia2)
    }

    "return applications with questionnaires and media when passing media and questionnaires" in new DiversityReportTestFixture {
      when(mockAppRepository.diversityReport(any())).thenReturnAsync(applications)
      when(mockQuestionRepository.findAllForDiversityReport).thenReturnAsync(questionnaires)
      when(mockMediaRepository.findAll()).thenReturnAsync(medias)

      val response = controller.diversityReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[DiversityReportItem]]

      status(response) mustBe OK
      result mustBe List(DiversityReportItemExamples.AllFields1, DiversityReportItemExamples.AllFields2)
    }
  }

  trait DiversityReportTestFixture extends TestFixtureBase {
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

    val applications = List(ApplicationForDiversityReportExamples.Example1,
      ApplicationForDiversityReportExamples.Example2)

    val questionnaires = Map(
      ApplicationForDiversityReportExamples.Example1.applicationId ->
        QuestionnaireReportItemExamples.NoParentOccupation1,
      ApplicationForDiversityReportExamples.Example2.applicationId ->
        QuestionnaireReportItemExamples.NoParentOccupation2)

    val notFoundQuestionnaires = Map(
      "NotFoundApplicationId1" -> QuestionnaireReportItemExamples.NoParentOccupation1,
      "NotFoundApplicationId2" -> QuestionnaireReportItemExamples.NoParentOccupation2)

    val medias = Map(
      ApplicationForDiversityReportExamples.Example1.userId -> MediaExamples.Example1,
      ApplicationForDiversityReportExamples.Example2.userId -> MediaExamples.Example2)

    val notFoundMedias = Map(
      "NotFoundUserId1" -> MediaExamples.Example1,
      "NotFoundUserId2" -> MediaExamples.Example2)

    when(mockAppRepository.diversityReport(any())).thenReturnAsync(List.empty)
    when(mockQuestionRepository.findAllForDiversityReport).thenReturnAsync(Map.empty)
    when(mockMediaRepository.findAll()).thenReturnAsync(Map.empty)

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.diversityReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
