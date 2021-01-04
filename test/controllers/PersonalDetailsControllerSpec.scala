/*
 * Copyright 2021 HM Revenue & Customs
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
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors._
import connectors.exchange.{CivilServiceExperienceDetailsExamples, GeneralDetailsExamples, SelectedSchemes}
import forms.PersonalDetailsForm
import forms.PersonalDetailsFormExamples._
import models.ApplicationData.ApplicationStatus
import models.ApplicationRoute._
import models._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.test.Helpers._
import testkit.MockitoImplicits._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class PersonalDetailsControllerSpec extends BaseControllerSpec {
  "present" should {
    "load personal details page for the new user and generate return to dashboard link" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller.presentAndContinue()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content must include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      // The only scheme where civilServantEligible = false and a degree is specified
      content must include("""<li>Project Delivery</li>""")
      // The page should not include any schemes that do not have a degree requirement
      ReferenceDataExamples.Schemes.schemesWithNoDegree.foreach{ scheme =>
        content must not include s"<li>${scheme.name}</li>"
      }
      content must include(routes.PersonalDetailsController.submitPersonalDetails().url)
    }

    "load edip personal details page for the new user and generate return to dashboard link" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller(currentCandidateWithEdipApp).presentAndContinue()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetails().url)
    }

    "load sdip personal details page for the new user and generate return to dashboard link" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

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
    "load personal details page for the new user and generate submit link to continue the journey" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller.presentAndContinue()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content must include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetailsAndContinue().url)
    }

    "load personal details page for the already created personal details" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller.present()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${GeneralDetailsExamples.FullDetails.preferredName}"""")
      content must include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
    }

    "load edip personal details page for the new user and generate submit link for continue the journey" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller(currentCandidateWithEdipApp).presentAndContinue()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetailsAndContinue().url)
    }

    "load edip personal details page for the already created personal details" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller(currentCandidateWithEdipApp).present()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${GeneralDetailsExamples.FullDetails.preferredName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
    }

    "load sdip personal details page for the new user and generate submit link for continue the journey" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new PersonalDetailsNotFound))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller(currentCandidateWithSdipApp).presentAndContinue()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${currentCandidate.user.firstName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include ("""<input name="edipCompleted" type="radio"""")
      content must include(routes.PersonalDetailsController.submitPersonalDetailsAndContinue().url)
    }

    "load sdip personal details page for the already created personal details" in new TestFixture {
      when(mockApplicationClient.getPersonalDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(GeneralDetailsExamples.FullDetails))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)

      val result = controller(currentCandidateWithSdipApp).present()(fakeRequest)

      assertPageTitle(result, "Personal details")
      val content = contentAsString(result)
      content must include(s"""name="preferredName" value="${GeneralDetailsExamples.FullDetails.preferredName}"""")
      content mustNot include("""<input name="civilServiceExperienceDetails.applicable" type="radio"""")
      content must include ("""<input name="edipCompleted" type="radio"""")
    }
  }

  "submit personal details" should {
    "update candidate's details and return to scheme preferences" in new TestFixture {
      configureCommonSubmitPersonalDetailsMocks()
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS,
          civilServiceExperienceDetails = Some(CivilServiceExperienceDetailsExamples.CivilServantExperience))
      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(true))))(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller.submitPersonalDetailsAndContinue()(Request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SchemePreferencesController.present().url)
    }

    "update edip candidate's details and return to assistance details" in new TestFixture {
      configureCommonSubmitPersonalDetailsMocks()
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithEdipApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS,
          civilServiceExperienceDetails = None)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(mockSchemeClient.updateSchemePreferences(eqTo(SelectedSchemes(List(Edip), orderAgreed = true, eligible = true))
      )(eqTo(Application.applicationId))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier],
        any[Request[_]])).thenReturn(Future.successful(UpdatedCandidate))
      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(true))))(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller(currentCandidateWithEdipApp).submitPersonalDetailsAndContinue()(Request)

      status(result) mustBe  SEE_OTHER
      redirectLocation(result) mustBe Some(routes.AssistanceDetailsController.present().url)
    }

    "update sdip candidate's details and return to assistance details" in new TestFixture {
      configureCommonSubmitPersonalDetailsMocks()
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InProgress))
      val Application = currentCandidateWithSdipApp.application
        .copy(progress = ProgressResponseExamples.InProgress, applicationStatus = ApplicationStatus.IN_PROGRESS,
          civilServiceExperienceDetails = None)
      val UpdatedCandidate = currentCandidate.copy(application = Some(Application))
      when(mockSchemeClient.updateSchemePreferences(eqTo(SelectedSchemes(List(Edip), orderAgreed = true, eligible = true))
      )(eqTo(Application.applicationId))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier],
        any[Request[_]])).thenReturn(Future.successful(UpdatedCandidate))
      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressForm.toExchange(currentEmail, Some(true))))(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller(currentCandidateWithEdipApp).submitPersonalDetailsAndContinue()(Request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.AssistanceDetailsController.present().url)
    }

    "update candidate's details and return to dashboard page" in new TestFixture {
      configureCommonSubmitPersonalDetailsMocks()
      val Application = currentCandidateWithApp.application
        .copy(progress = ProgressResponseExamples.Submitted, applicationStatus = ApplicationStatus.SUBMITTED)
      when(mockUserManagementClient.updateDetails(eqTo(currentUserId), eqTo(currentUser.firstName), eqTo(currentUser.lastName),
        eqTo(currentUser.preferredName))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.Submitted))
      when(mockApplicationClient.updatePersonalDetails(eqTo(currentApplicationId), eqTo(currentUserId),
        eqTo(ValidUKAddressWithoutCivilServiceDetailsForm.toExchange(currentEmail, Some(false)))
      )(any[HeaderCarrier])).thenReturn(Future.successful(()))

      val Request = fakeRequest.withFormUrlEncodedBody(ValidFormUrlEncodedBody: _*)
      val result = controller.submitPersonalDetails()(Request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present().url)
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    // scalastyle:off method.name
    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp) = {
      val formWrapper = new PersonalDetailsForm
      new PersonalDetailsController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent, mockNotificationTypeHelper,
      mockApplicationClient, mockSchemeClient, mockUserManagementClient, mockReferenceDataClient, formWrapper) with TestableSecureActions {
        override val candidateWithApp: CachedDataWithApp = candWithApp
      }
    }

    def configureCommonSubmitPersonalDetailsMocks(): Unit = {
      when(mockUserManagementClient.updateDetails(eqTo(currentUserId), eqTo(currentUser.firstName), eqTo(currentUser.lastName),
        eqTo(currentUser.preferredName))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)
    }
  }
}
