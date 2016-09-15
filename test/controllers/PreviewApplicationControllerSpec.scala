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

import com.github.tomakehurst.wiremock.client.WireMock.{ any => _ }
import config.CSRHttp
import connectors.{ ApplicationClient, SchemeClient }
import connectors.ApplicationClient.{ AssistanceDetailsNotFound, CannotUpdateRecord, PartnerGraduateProgrammesNotFound, PersonalDetailsNotFound }
import connectors.SchemeClient.SchemePreferencesNotFound
import connectors.exchange.{ AssistanceDetailsExamples, GeneralDetailsExamples, PartnerGraduateProgrammesExamples, SchemePreferencesExamples }
import controllers.forms.AssistanceDetailsFormExamples
import models.ApplicationData.ApplicationStatus
import models.SecurityUserExamples._
import models._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.UserService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class PreviewApplicationControllerSpec extends BaseControllerSpec {

  // This is the implicit user
  override def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CachedDataExample.InQuestionnaireApplication.copy(userId = ActiveCandidate.user.userID))

  "present" should {
    "load preview page for existing application" in new TestFixture {
      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("<title>Check your application")
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
      content must include(s"""<p id="fastPassApplicable">No</p>""")
    }

    "redirect to home page with error when personal details cannot be found" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))
      val result = controller.present()(fakeRequest)
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }

    "redirect to home page with error when scheme preferences cannot be found" in new TestFixture {
      when(mockSchemeClient.getSchemePreferences(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new SchemePreferencesNotFound))
      val result = controller.present()(fakeRequest)
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }

    "redirect to home page with error when partner graduate programmes cannot be found" in new TestFixture {
      when(mockApplicationClient.getPartnerGraduateProgrammes(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PartnerGraduateProgrammesNotFound))
      val result = controller.present()(fakeRequest)
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }

    "redirect to home page with error when assistance details cannot be found" in new TestFixture {
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new AssistanceDetailsNotFound))
      val result = controller.present()(fakeRequest)
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }
  }

  "submit preview" should {
    "redirect to submit application page" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody(AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsFormUrlEncodedBody: _*)
      when(mockApplicationClient.updatePreview(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InPreview))

      val Application = currentCandidateWithApp.application.copy(progress = ProgressResponseExamples.InPreview)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(mockUserService.save(eqTo(UpdatedCandidate))(any[HeaderCarrier])).thenReturn(Future.successful(UpdatedCandidate))

      val result = controller.submit()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.SubmitApplicationController.present().url))
    }
  }


  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockSchemeClient = mock[SchemeClient]
    val mockSecurityEnvironment = mock[security.SecurityEnvironment]
    val mockUserService = mock[UserService]

    when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
      .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))
    when(mockSchemeClient.getSchemePreferences(eqTo(currentApplicationId))(any[HeaderCarrier]))
      .thenReturn(Future.successful(SchemePreferencesExamples.DefaultSelectedSchemes))
    when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
      .thenReturn(Future.successful(AssistanceDetailsExamples.DisabilityGisAndAdjustments))
    when(mockApplicationClient.getPartnerGraduateProgrammes(eqTo(currentApplicationId))(any[HeaderCarrier]))
      .thenReturn(Future.successful(PartnerGraduateProgrammesExamples.InterestedNotAll))

    class TestablePreviewApplicationController extends PreviewApplicationController(mockApplicationClient,
      mockSchemeClient)
      with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override protected def env = mockSecurityEnvironment
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
    }

    def controller = new TestablePreviewApplicationController

  }
}
