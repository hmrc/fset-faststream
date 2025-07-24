/*
 * Copyright 2025 HM Revenue & Customs
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

import model.EvaluationResults.Green
import model.SelectedSchemesExamples.twoSchemes
import model.command.ApplicationStatusDetails
import model.command.PersonalDetailsExamples.personalDetails
import model.persisted.AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments
import model.persisted.ContactDetailsExamples.ContactDetailsUK
import model.persisted.SchemeEvaluationResult
import model.{ApplicationRoute, ApplicationStatus, PostSubmissionCheckData, ProgressStatuses, Schemes}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import play.api.test.Helpers.*
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import repositories.{FrameworkPreferenceRepository, FrameworkRepository, QuestionnaireRepository}
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits.*
import testkit.UnitWithAppSpec

import scala.concurrent.ExecutionContext

class SubmitApplicationControllerSpec extends UnitWithAppSpec {

  "Submit application" should {
    "process a Faststream candidate with a SUBMITTED_CHECK_PASSED progress status" in new TestFixture {
      val applicationStatusDetails = ApplicationStatusDetails(
        status = ApplicationStatus.SUBMITTED,
        applicationRoute = ApplicationRoute.Faststream,
        latestProgressStatus = None,
        statusDate = None,
        overrideSubmissionDeadline = None
      )

      when(mockApplicationRepository.findStatus(any[String], any[Boolean])).thenReturnAsync(applicationStatusDetails)

      when(mockApplicationRepository.submit(any[String])).thenReturnAsync()
      when(mockApplicationRepository.updateCurrentSchemeStatus(any[String], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()

      val socioEconomicScore = "SE-4"
      // Save socio economic score
      when(mockQuestionnaireRepository.calculateSocioEconomicScore(any[String])).thenReturnAsync(socioEconomicScore)
      when(mockApplicationRepository.saveSocioEconomicScore(any[String], any[String])).thenReturnAsync()

      // Post submission check
      val postSubmissionCheckData = PostSubmissionCheckData(ApplicationRoute.Faststream, socioEconomicScore)
      when(mockApplicationRepository.findPostSubmissionCheckData(any[String])).thenReturnAsync(postSubmissionCheckData)
      when(mockApplicationRepository.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      val result = controller.submitApplication(UserId, AppId)(fakeRequest)
      status(result) mustBe OK

      val expectedCss = Seq(SchemeEvaluationResult(Digital, Green.toString), SchemeEvaluationResult(Commercial, Green.toString))
      verify(mockApplicationRepository).updateCurrentSchemeStatus(eqTo(AppId), eqTo(expectedCss))
      verify(mockApplicationRepository).saveSocioEconomicScore(eqTo(AppId), eqTo(socioEconomicScore))
      verify(mockApplicationRepository).addProgressStatusAndUpdateAppStatus(eqTo(AppId), eqTo(ProgressStatuses.SUBMITTED_CHECK_PASSED))

      // We expect the submitted email to be sent and no notification progress status was added
//      verifyEmailEvent("ApplicationSubmitted")
      verify(mockApplicationRepository, never())
        .addProgressStatusAndUpdateAppStatus(eqTo(AppId), eqTo(ProgressStatuses.SUBMITTED_CHECK_FAILED_NOTIFIED))
    }

    "process a Sdip candidate with a SUBMITTED_CHECK_PASSED progress status if they have a lower SEB score (SE-4 or SE-5)" in new TestFixture {
      val applicationStatusDetails = ApplicationStatusDetails(
        status = ApplicationStatus.SUBMITTED,
        applicationRoute = ApplicationRoute.Sdip,
        latestProgressStatus = None,
        statusDate = None,
        overrideSubmissionDeadline = None
      )

      when(mockApplicationRepository.findStatus(any[String], any[Boolean])).thenReturnAsync(applicationStatusDetails)

      when(mockApplicationRepository.submit(any[String])).thenReturnAsync()
      when(mockApplicationRepository.updateCurrentSchemeStatus(any[String], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()

      val socioEconomicScore = "SE-4"
      // Save socio economic score
      when(mockQuestionnaireRepository.calculateSocioEconomicScore(any[String])).thenReturnAsync(socioEconomicScore)
      when(mockApplicationRepository.saveSocioEconomicScore(any[String], any[String])).thenReturnAsync()

      // Post submission check
      val postSubmissionCheckData = PostSubmissionCheckData(ApplicationRoute.Sdip, socioEconomicScore)
      when(mockApplicationRepository.findPostSubmissionCheckData(any[String])).thenReturnAsync(postSubmissionCheckData)
      when(mockApplicationRepository.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      val result = controller.submitApplication(UserId, AppId)(fakeRequest)
      status(result) mustBe OK

      val expectedCss = Seq(SchemeEvaluationResult(Digital, Green.toString), SchemeEvaluationResult(Commercial, Green.toString))
      verify(mockApplicationRepository).updateCurrentSchemeStatus(eqTo(AppId), eqTo(expectedCss))
      verify(mockApplicationRepository).saveSocioEconomicScore(eqTo(AppId), eqTo(socioEconomicScore))
      verify(mockApplicationRepository).addProgressStatusAndUpdateAppStatus(eqTo(AppId), eqTo(ProgressStatuses.SUBMITTED_CHECK_PASSED))

      // We expect the submitted check passed email to be sent and no notification progress status was added
//      verifyEmailEvent("ApplicationPostSubmittedCheckPassed")
      verify(mockApplicationRepository, never())
        .addProgressStatusAndUpdateAppStatus(eqTo(AppId), eqTo(ProgressStatuses.SUBMITTED_CHECK_FAILED_NOTIFIED))
    }

    "process a Sdip candidate with a SUBMITTED_CHECK_FAILED progress status if they have a higher SEB score" in new TestFixture {
      val applicationStatusDetails = ApplicationStatusDetails(
        status = ApplicationStatus.SUBMITTED,
        applicationRoute = ApplicationRoute.Sdip,
        latestProgressStatus = None,
        statusDate = None,
        overrideSubmissionDeadline = None
      )

      when(mockApplicationRepository.findStatus(any[String], any[Boolean])).thenReturnAsync(applicationStatusDetails)

      when(mockApplicationRepository.submit(any[String])).thenReturnAsync()
      when(mockApplicationRepository.updateCurrentSchemeStatus(any[String], any[Seq[SchemeEvaluationResult]])).thenReturnAsync()

      val socioEconomicScore = "SE-1"
      // Save socio economic score
      when(mockQuestionnaireRepository.calculateSocioEconomicScore(any[String])).thenReturnAsync(socioEconomicScore)
      when(mockApplicationRepository.saveSocioEconomicScore(any[String], any[String])).thenReturnAsync()

      // Post submission check
      val postSubmissionCheckData = PostSubmissionCheckData(ApplicationRoute.Sdip, socioEconomicScore)
      when(mockApplicationRepository.findPostSubmissionCheckData(any[String])).thenReturnAsync(postSubmissionCheckData)
      when(mockApplicationRepository.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      val result = controller.submitApplication(UserId, AppId)(fakeRequest)
      status(result) mustBe OK

      val expectedCss = Seq(SchemeEvaluationResult(Digital, Green.toString), SchemeEvaluationResult(Commercial, Green.toString))
      verify(mockApplicationRepository).updateCurrentSchemeStatus(eqTo(AppId), eqTo(expectedCss))
      verify(mockApplicationRepository).saveSocioEconomicScore(eqTo(AppId), eqTo(socioEconomicScore))
      verify(mockApplicationRepository).addProgressStatusAndUpdateAppStatus(eqTo(AppId), eqTo(ProgressStatuses.SUBMITTED_CHECK_FAILED))

      // We expect the submitted check failed email to be sent and the failed notification progress status to be added
      //      verifyEmailEvent("ApplicationPostSubmittedCheckFailed")
//      verify(mockApplicationRepository).addProgressStatusAndUpdateAppStatus(eqTo(AppId), eqTo(ProgressStatuses.SUBMITTED_CHECK_FAILED_NOTIFIED))
    }
  }

  trait TestFixture extends StcEventServiceFixture with Schemes {
    val mockPersonalDetailsRepository = mock[PersonalDetailsRepository]
    val mockAssistanceDetailsRepository = mock[AssistanceDetailsRepository]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]
    val mockSchemePreferencesRepository = mock[SchemePreferencesRepository]
    val mockFrameworkPreferenceRepository = mock[FrameworkPreferenceRepository]
    val mockFrameworkRegionsRepository = mock[FrameworkRepository]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockQuestionnaireRepository = mock[QuestionnaireRepository]

    when(mockPersonalDetailsRepository.find(any[String])).thenReturnAsync(personalDetails)
    when(mockAssistanceDetailsRepository.find(any[String])).thenReturnAsync(OnlyDisabilityNoGisNoAdjustments)
    when(mockContactDetailsRepository.find(any[String])).thenReturnAsync(ContactDetailsUK)
    when(mockSchemePreferencesRepository.find(any[String])).thenReturnAsync(twoSchemes)
    when(mockFrameworkPreferenceRepository.tryGetPreferences(any[String])).thenReturnAsync(None)

    when(mockFrameworkRegionsRepository.getFrameworksByRegionFilteredByQualification(any[CandidateHighestQualification])(any[ExecutionContext]))
      .thenReturnAsync(Nil)

    val controller = new SubmitApplicationController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockPersonalDetailsRepository,
      mockAssistanceDetailsRepository,
      mockContactDetailsRepository,
      mockSchemePreferencesRepository,
      mockFrameworkPreferenceRepository,
      mockFrameworkRegionsRepository,
      mockApplicationRepository,
      mockQuestionnaireRepository,
      stcEventServiceMock
    )
  }
}
