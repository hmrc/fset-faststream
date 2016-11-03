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
import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import connectors.ApplicationClient.PartnerGraduateProgrammesNotFound
import connectors.exchange.PartnerGraduateProgrammesExamples
import forms.PartnerGraduateProgrammesFormExamples
import models.SecurityUserExamples._
import models._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.UserService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class PartnerGraduateProgrammesControllerSpec extends BaseControllerSpec {

  // This is the implicit user
  override def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CachedDataExample.InProgressInPartnerGraduateProgrammesApplication.copy(userId = ActiveCandidate.user.userID))

  "present" should {
    "load partner graduate programmes page for the new user" in new TestFixture {
      when(mockApplicationClient.getPartnerGraduateProgrammes(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PartnerGraduateProgrammesNotFound))

      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("<title>Do you want to defer your place?")
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
    }

    "load partner graduate programmes page for the already created partner graduate programmes" in new TestFixture {
      when(mockApplicationClient.getPartnerGraduateProgrammes(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(PartnerGraduateProgrammesExamples.InterestedNotAll))

      val result = controller.present()(fakeRequest)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("<title>Do you want to defer your place?")
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
      content must include("Are you interested in a partner programme?")
    }
  }

  "submit partner graduate programmes" should {
    "update partner graduate programmes and redirect to assistance details" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody(PartnerGraduateProgrammesFormExamples.InterestedNotAllFormUrlEncodedBody: _*)
      when(mockApplicationClient.updatePartnerGraduateProgrammes(eqTo(currentApplicationId),
        eqTo(PartnerGraduateProgrammesExamples.InterestedNotAll))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InPartnerGraduateProgrammes))
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.InPartnerGraduateProgrammes)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(mockUserService.save(eqTo(UpdatedCandidate))(any[HeaderCarrier])).thenReturn(Future.successful(UpdatedCandidate))

      val result = controller.submit()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.AssistanceDetailsController.present().url))
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockSecurityEnvironment = mock[security.SecurityEnvironment]
    val mockUserService = mock[UserService]

    class TestablePartnerGraduateProgrammesController extends PartnerGraduateProgrammesController(mockApplicationClient, mockCacheClient)
      with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override protected def env = mockSecurityEnvironment
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
    }

    def controller = new TestablePartnerGraduateProgrammesController
  }
}
