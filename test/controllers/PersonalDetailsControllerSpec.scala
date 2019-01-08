/*
 * Copyright 2019 HM Revenue & Customs
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
import config.{ CSRHttp, SecurityEnvironmentImpl }
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.exchange.{ CivilServiceExperienceDetailsExamples, GeneralDetailsExamples, SelectedSchemes }
import connectors.{ ApplicationClient, SchemeClient, UserManagementClient }
import forms.PersonalDetailsFormExamples._
import models.ApplicationData.ApplicationStatus
import models.ApplicationRoute._
import models._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.test.Helpers._
import security.{ SilhouetteComponent, UserCacheService, UserService }
import testkit.{ BaseControllerSpec, TestableSecureActions }

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class PersonalDetailsControllerSpec extends BaseControllerSpec {
  val mockApplicationClient = mock[ApplicationClient]
  val mockSchemeClient = mock[SchemeClient]
  val mockUserManagementClient = mock[UserManagementClient]
  val userService = mock[UserCacheService]
  val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]

  class TestablePersonalDetailsController extends PersonalDetailsController(mockApplicationClient, mockSchemeClient,
    mockUserManagementClient)
    with TestableSecureActions {
    val http: CSRHttp = CSRHttp
    override val env = mockSecurityEnvironment
    override lazy val silhouette = SilhouetteComponent.silhouette

    when(mockSecurityEnvironment.userService).thenReturn(userService)
  }

  // scalastyle:off method.name
  def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp) = new TestablePersonalDetailsController {
    override val candidateWithApp: CachedDataWithApp = candWithApp
  }

  "present" should {
    "load personal details page for the new user and generate return to dashboard link" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller.presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content must include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetails().url)
    }

    "load edip personal details page for the new user and generate return to dashboard link" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller(currentCandidateWithEdipApp).presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetails().url)
    }

    "load sdip personal details page for the new user and generate return to dashboard link" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller(currentCandidateWithSdipApp).presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content must include ("""<input name="edipCompleted" type="radio"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetails().url)
    }
  }

  "present and continue" should {
    "load personal details page for the new user and generate submit link for continue the journey" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller.presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content must include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetailsAndContinue().url)
    }

    "load personal details page for the already created personal details" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))

      val result = controller.present()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${GeneralDetailsExamples.FullDetails.preferredName}"""")
      content must include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
    }

    "load edip personal details page for the new user and generate submit link for continue the journey" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller(currentCandidateWithEdipApp).presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetailsAndContinue().url)
    }

    "load edip personal details page for the already created personal details" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))

      val result = controller(currentCandidateWithEdipApp).present()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${GeneralDetailsExamples.FullDetails.preferredName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
    }

    "load sdip personal details page for the new user and generate submit link for continue the journey" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))

      val result = controller(currentCandidateWithSdipApp).presentAndContinue()(fakeRequest)
      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include ("""<input name="edipCompleted" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetailsAndContinue().url)
    }

    "load sdip personal details page for the already created personal details" in {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))

      val result = controller(currentCandidateWithSdipApp).present()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${GeneralDetailsExamples.FullDetails.preferredName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include ("""<input name="edipCompleted" type="radio"""")
    }
  }

  "submit personal details" should {
    when(mockUserManagementClient.updateDetails(eqTo(currentUserId), eqTo(currentUser.firstName), eqTo(currentUser.lastName),
      eqTo(currentUser.preferredName))(any[HeaderCarrier])).thenReturn(Future.successful(()))

    "update candidate's details and return to scheme preferences" in {
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS,
          civilServiceExperienceDetails = Some(CivilServiceExperienceDetailsExamples.CivilServantExperience))
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(true))))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller.submitPersonalDetailsAndContinue()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.SchemePreferencesController.present().url))
    }

    "update edip candidate's details and return to assistance details" in {
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithEdipApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS,
          civilServiceExperienceDetails = None)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(mockSchemeClient.updateSchemePreferences(eqTo(SelectedSchemes(List(Edip), orderAgreed = true, eligible = true))
      )(eqTo(Application.applicationId))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(userService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier],
        any[Request[_]])).thenReturn(Future.successful(UpdatedCandidate))
      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(true))))(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller(currentCandidateWithEdipApp).submitPersonalDetailsAndContinue()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.AssistanceDetailsController.present().url))
    }

    "update sdip candidate's details and return to assistance details" in {
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithSdipApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS,
          civilServiceExperienceDetails = None)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(mockSchemeClient.updateSchemePreferences(eqTo(SelectedSchemes(List(Edip), orderAgreed = true, eligible = true))
      )(eqTo(Application.applicationId))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(userService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier],
        any[Request[_]])).thenReturn(Future.successful(UpdatedCandidate))
      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(true))))(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller(currentCandidateWithEdipApp).submitPersonalDetailsAndContinue()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.AssistanceDetailsController.present().url))
    }

    "update candidate's details and return to dashboard page" in {
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.Submitted, applicationStatus = ApplicationStatus.SUBMITTED)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))

      when(mockUserManagementClient.updateDetails(eqTo(currentUserId), eqTo(currentUser.firstName), eqTo(currentUser.lastName),
        eqTo(currentUser.preferredName))(any[HeaderCarrier])).thenReturn(Future.successful(()))

      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.Submitted))

      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressWithoutCivilServiceDetailsForm.toExchange(currentEmail, Some(false)))
      )(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller.submitPersonalDetails()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }
  }
}
