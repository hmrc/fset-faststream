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

import com.github.tomakehurst.wiremock.client.WireMock.{any => _}
import config.CSRHttp
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.exchange.ProgressResponseExamples
import connectors.{ApplicationClient, UserManagementClient}
import _root_.forms.GeneralDetailsFormExamples._
import models.ApplicationData.ApplicationStatus
import models.GeneralDetailsExchangeExamples
import models.services.UserService
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class PersonalDetailsControllerSpec extends BaseControllerSpec {
  val applicationClient = mock[ApplicationClient]
  val userManagementClient = mock[UserManagementClient]
  val securityEnvironment = mock[security.SecurityEnvironment]
  val userService = mock[UserService]

  class TestablePersonalDetailsController extends PersonalDetailsController(applicationClient, userManagementClient)
    with TestableSecureActions {
    val http: CSRHttp = CSRHttp
    override protected def env = securityEnvironment

    when(securityEnvironment.userService).thenReturn(userService)
  }

  def controller = new TestablePersonalDetailsController

  "present" should {
    "load general details page for the new user" in {
      when(applicationClient.findPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("<title>Personal details")
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
    }

    "load general details page for the already created personal details" in {
      when(applicationClient.findPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExchangeExamples.FullDetails))

      val result = controller.present()(fakeRequest)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("<title>Personal details")
      content must include(s"""name="preferredName" value="${GeneralDetailsExchangeExamples.FullDetails.preferredName}"""")
    }
  }

  "submit general details" should {
    "update candidate's details" in {
      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      when(applicationClient.updateGeneralDetails(eqTo(currentApplicationId), eqTo(currentUserId), eqTo(ValidForm),
        eqTo(currentEmail))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(userManagementClient.updateDetails(eqTo(currentUserId), eqTo(currentUser.firstName), eqTo(currentUser.lastName),
        eqTo(currentUser.preferredName))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(applicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(userService.save(eqTo(UpdatedCandidate))(any[HeaderCarrier])).thenReturn(Future.successful(UpdatedCandidate))

      val result = controller.submitGeneralDetails()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.SchemeController.entryPoint().url))
    }

    "fail updating the candidate when person cannot be found" in {
      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      when(applicationClient.updateGeneralDetails(eqTo(currentApplicationId), eqTo(currentUserId), eqTo(ValidForm),
        eqTo(currentEmail))(any[HeaderCarrier])).thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller.submitGeneralDetails()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
      flash(result).data must be (Map("danger" -> "account.error"))
    }
  }
}
