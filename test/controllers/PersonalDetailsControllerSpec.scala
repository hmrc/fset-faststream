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
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.{ ApplicationClient, UserManagementClient }
import controllers.forms.GeneralDetailsFormExamples._
import connectors.exchange.{ CivilServiceExperienceDetailsExamples, GeneralDetailsExamples }
import models.ApplicationData.ApplicationStatus
import models.{ CachedData, ProgressResponseExamples }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.UserService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class PersonalDetailsControllerSpec extends BaseControllerSpec {
  val applicationClient = mock[ApplicationClient]
  val userManagementClient = mock[UserManagementClient]
  val userService = mock[UserService]

  class TestablePersonalDetailsController extends PersonalDetailsController(applicationClient, userManagementClient)
    with TestableSecureActions {
    val http: CSRHttp = CSRHttp
    override protected def env = securityEnvironment

    when(securityEnvironment.userService).thenReturn(userService)
  }

  def controller = new TestablePersonalDetailsController

  "present and continue" should {
    "load general details page for the new user and generate submit link for continue the journey" in {
      when(applicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller.presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content must include
      (s"""<input name="civilServiceExperienceDetails.applicable" type="radio" id="civilServiceExperienceDetails_applicable-yes"""")
      content must include(routes.PersonalDetailsController.submitGeneralDetailsAndContinue().url)
    }

    "load general details page for the already created personal details" in {
      when(applicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))

      val result = controller.present()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${GeneralDetailsExamples.FullDetails.preferredName}"""")
      content must include
      (s"""<input name="civilServiceExperienceDetails.applicable" type="radio" id="civilServiceExperienceDetails_applicable-yes"""")
    }
  }

  "present" should {
    "load general details page for the new user and generate return to dashboard link" in {
      when(applicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller.presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content must include(routes.PersonalDetailsController.submitGeneralDetails().url)
    }
  }

  "submit general details" should {
    when(userManagementClient.updateDetails(eqTo(currentUserId), eqTo(currentUser.firstName), eqTo(currentUser.lastName),
      eqTo(currentUser.preferredName))(any[HeaderCarrier])).thenReturn(Future.successful(()))

    "update candidate's details and return to scheme preferences" in {
      when(applicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS,
          civilServiceExperienceDetails = Some(CivilServiceExperienceDetailsExamples.CivilServantExperience))
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(userService.save(any[CachedData])(any[HeaderCarrier])).thenReturn(Future.successful(UpdatedCandidate))
      when(applicationClient.updateGeneralDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(true))))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller.submitGeneralDetailsAndContinue()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.SchemePreferencesController.present().url))
    }

    "update candidate's details and return to dashboard page" in {
      when(userManagementClient.updateDetails(eqTo(currentUserId), eqTo(currentUser.firstName), eqTo(currentUser.lastName),
        eqTo(currentUser.preferredName))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(applicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.Submitted))
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.Submitted, applicationStatus = ApplicationStatus.SUBMITTED)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(userService.save(any[CachedData])(any[HeaderCarrier])).thenReturn(Future.successful(UpdatedCandidate))
      when(applicationClient.updateGeneralDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(false))))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller.submitGeneralDetails()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }
  }
}