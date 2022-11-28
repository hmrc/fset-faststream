/*
 * Copyright 2022 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{any => _}
import connectors.ApplicationClient.AssistanceDetailsNotFound
import connectors.exchange.AssistanceDetailsExamples
import forms.{AssistanceDetailsForm, AssistanceDetailsFormExamples}
import models.SecurityUserExamples._
import models._
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.test.Helpers._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AssistanceDetailsControllerSpec extends BaseControllerSpec {

  // This is the implicit user
  override def currentCandidateWithApp: CachedDataWithApp = {
    CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.InProgressInSchemePreferencesApplication.copy(userId = ActiveCandidate.user.userID))
  }

  "present" should {
    "load assistance details page for the new user" in new TestFixture {
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new AssistanceDetailsNotFound))

      val result = controller.present()(fakeRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("<title>Disability and health conditions")
      content must include(onlineTestText)
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
    }

    "load assistance details page for the already created assistance details" in new TestFixture {
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.DisabilityGisAndAdjustments))

      val result = controller.present()(fakeRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("<title>Disability and health conditions")
      content must include(onlineTestText)
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
      content must include("Some adjustment")
    }

    "load edip assistance details page for the new user" in new TestFixture {
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new AssistanceDetailsNotFound))

      val result = controller(currentCandidateWithEdipApp).present()(fakeRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("<title>Disability and health conditions")
      content must include(phoneText)
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
    }

    "load edip assistance details page for the already created assistance details" in new TestFixture {
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.EdipAdjustments))

      val result = controller(currentCandidateWithEdipApp).present()(fakeRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("<title>Disability and health conditions")
      content must include(phoneText)
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
      content must include("Some adjustment")
    }
  }

  "submit assistance details" should {
    "update assistance details and redirect to questionnaire if questionnaire is not completed" in new TestFixture {
      val Request = fakeRequest.withMethod("POST")
        .withFormUrlEncodedBody(AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsFormUrlEncodedBody: _*)
      when(mockApplicationClient.updateAssistanceDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(AssistanceDetailsExamples.DisabilityGisAndAdjustments))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InAssistanceDetails))

      val result = controller.submit()(Request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.QuestionnaireController.presentStartOrContinue.url))
    }

    "update assistance details and redirect to preview if questionnaire is completed" in new TestFixture {
      val Request = fakeRequest.withMethod("POST")
        .withFormUrlEncodedBody(AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsFormUrlEncodedBody: _*)
      when(mockApplicationClient.updateAssistanceDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(AssistanceDetailsExamples.DisabilityGisAndAdjustments))(any[HeaderCarrier])).thenReturn(Future.successful(()))

      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InQuestionnaire))

      val candidate = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.InProgressInQuestionnaireApplication.copy(userId = ActiveCandidate.user.userID))
      val result = controller(candidate).submit()(Request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.PreviewApplicationController.present.url))
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val phoneText = "Will you need any support for your phone interview?"
    val onlineTestText = "Will you need any support for your work based scenarios or numerical test?"

    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp) = {
      val formWrapper = new AssistanceDetailsForm
      new AssistanceDetailsController(mockConfig, stubMcc,mockSecurityEnv, mockSilhouetteComponent,
      mockNotificationTypeHelper, mockApplicationClient, formWrapper) with TestableSecureActions {
        override val candidateWithApp: CachedDataWithApp = candWithApp
      }
    }
  }
}
