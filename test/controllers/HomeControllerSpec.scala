/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.LocalDateTime

import com.github.tomakehurst.wiremock.client.WireMock.{ any => _ }
import config.{ CSRCache, CSRHttp, SecurityEnvironmentImpl }
import connectors.ApplicationClient
import connectors.ApplicationClient.{ CannotWithdraw, OnlineTestNotFound }
import connectors.exchange.{ AssistanceDetailsExamples, SchemeEvaluationResult, WithdrawApplicationExamples }
import forms.WithdrawApplicationFormExamples
import models.ApplicationData.ApplicationStatus
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.{ SilhouetteComponent, UserCacheService, UserService }
import testkit.{ BaseControllerSpec, TestableSecureActions }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class HomeControllerSpec extends BaseControllerSpec {

  // The current candidate needed by other methods than withdraw may be different, in that case, we might need
  // to split to tests of this file.
  // This is the implicit user
  override def currentCandidateWithApp: CachedDataWithApp = {
    CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.SubmittedApplication.copy(userId = ActiveCandidate.user.userID))
  }

  "present" should {
    "display home page" in new TestFixture {
      val previewApp = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))
      val result = controller(previewApp).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content mustNot include("Fast Stream applications are now closed")
      content must include("""<ol class="step-by-step-coloured " id="sixSteps">""")
    }

    "display home page with submit disabled" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None }
      val previewApp = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))
      val result = controller(previewApp, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("Applications are now closed")
      content must include("""<ol class="step-by-step-coloured disabled" id="sixSteps">""")
    }

    "display faststream final scheme results page" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      val phase3TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.Phase3TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockApplicationClient.getFinalSchemeResults(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(List(SchemeEvaluationResult(SchemeType.DiplomaticService, "Green")))))

      val result = controller(phase3TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content must include("Congratulations, you've been successful for at least one of your")
      content mustNot include("Your application has been withdrawn.")
    }

    "display faststream final results page for withdrawn application" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      val withdrawnPhase3TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.WithdrawnPhase3TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockApplicationClient.getFinalSchemeResults(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(List(SchemeEvaluationResult(SchemeType.DiplomaticService, "Green")))))

      val result = controller(withdrawnPhase3TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content must include("Your application has been withdrawn.")
      content must include("Congratulations, you've been successful for at least one of your")
    }

    "display edip final results page" in new EdipAndSdipTestFixture {
      val result = controller(edipPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content must include("Congratulations, you&#x27;ve been succcessful for the Early Diversity Internship Programme (EDIP).")
      content mustNot include("Your application has been withdrawn.")
    }

    "display sdip final results page" in new EdipAndSdipTestFixture {
      val result = controller(sdipPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content must include("Congratulations, you&#x27;ve been succcessful for the Summer Diversity Internship Programme (SDIP).")
      content mustNot include("Your application has been withdrawn.")
    }

    "display edip final results page for withdrawn application" in new EdipAndSdipTestFixture {
      val result = controller(edipWithdrawnPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content must include("Your application has been withdrawn.")
      content must include("Congratulations, you&#x27;ve been succcessful for the Early Diversity Internship Programme (EDIP).")
    }

    "display sdip final results page for withdrawn application" in new EdipAndSdipTestFixture {
      val result = controller(sdipWithdrawnPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content must include("Your application has been withdrawn.")
      content must include("Congratulations, you&#x27;ve been succcessful for the Summer Diversity Internship Programme (SDIP).")
    }

    "display fast pass rejected message" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      val fastPassRejectedInvitedToPhase1Application = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.fastPassRejectedInvitedToPhase1Application.copy(userId = ActiveCandidate.user.userID))

      when(mockApplicationClient.getPhase1TestProfile(eqTo(fastPassRejectedInvitedToPhase1Application.application.applicationId)
      )(any[HeaderCarrier])).thenReturn(Future.failed(new OnlineTestNotFound))

      val result = controller(fastPassRejectedInvitedToPhase1Application, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content must include("Unfortunately we've not been able to confirm that your Fast Pass is valid.")
    }

    "not display fast pass rejected message when phase1 tests are started" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      val fastPassRejectedPhase1StartedApplication = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.fastPassRejectedPhase1StartedApplication.copy(userId = ActiveCandidate.user.userID))

      when(mockApplicationClient.getPhase1TestProfile(eqTo(fastPassRejectedPhase1StartedApplication.application.applicationId)
      )(any[HeaderCarrier])).thenReturn(Future.failed(new OnlineTestNotFound))

      val result = controller(fastPassRejectedPhase1StartedApplication, applicationRouteState).present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)

      content mustNot include("Unfortunately we've not been able to confirm that your Fast Pass is valid.")
    }
  }

  "present with sdip eligibility info" should {
    "display eligibility information when faststream application is withdrawn" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val withdrawnApplication = currentCandidateWithApp.copy(application = CachedDataExample.WithdrawApplication)
      val result = controller(withdrawnApplication, applicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content must include("Unfortunately, you withdrew your Civil Service Fast Stream application.")
    }

    "display eligibility information when faststream application is not submitted" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val inProgressApp = currentCandidateWithApp.copy(application = CachedDataExample.InProgressInAssistanceDetailsApplication)
      val result = controller(inProgressApp, applicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content must include("submit your application for the Civil Service Fast Stream before the deadline")
    }

    "display eligibility information when faststream application is phase1 tests expired" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val phase1TestsExpiredCandidate = currentCandidateWithApp.copy(application = CachedDataExample.Phase1TestsExpiredApplication)
      val result = controller(phase1TestsExpiredCandidate, applicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content must include("complete your online exercises for the Civil Service Fast Stream before the deadline")
    }

    "not display eligibility information when application route is not faststream" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val result = controller(currentCandidateWithEdipApp, applicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content mustNot include("Continue as SDIP")
    }

    "not display eligibility information when faststream application is submitted" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None }

      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val submittedCandidate = currentCandidateWithApp.copy(application = CachedDataExample.SubmittedApplication)
      val result = controller(submittedCandidate, applicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content mustNot include("Continue as SDIP")
    }
  }

  "presentWithdrawApplication" should {
    "display withdraw page" in new TestFixture {
      val result = controller.presentWithdrawApplication()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("<title>Withdraw your application")
      content must include(s"""<span class="your-name" id="bannerUserName">${currentCandidate.user.preferredName.get}</span>""")
    }
  }

  "withdrawApplication" should {
    "display withdraw form when the form was submitted invalid" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody(WithdrawApplicationFormExamples.OtherReasonInvalidNoReasonFormUrlEncodedBody: _*)

      val result = controller.withdrawApplication()(Request)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must include(routes.HomeController.withdrawApplication().url)
      content must include ("Select a reason for withdrawing your application")
    }

    "display dashboard with error message when form is valid but cannot withdraw" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody(WithdrawApplicationFormExamples.ValidFormUrlEncodedBody: _*)
      when(mockApplicationClient.withdrawApplication(eqTo(currentApplicationId),
        eqTo(WithdrawApplicationExamples.Valid))(any[HeaderCarrier])).thenReturn(Future.failed(new CannotWithdraw))

      val result = controller.withdrawApplication()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
      flash(result).data must be (Map("danger" -> "We can't find an application to withdraw"))
    }

    "display dashboard with withdrawn success message when withdraw is successful" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody(WithdrawApplicationFormExamples.ValidFormUrlEncodedBody: _*)
      when(mockApplicationClient.withdrawApplication(eqTo(currentApplicationId),
        eqTo(WithdrawApplicationExamples.Valid))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.Submitted))

      val Candidate = CachedData(currentCandidateWithApp.user, Some(currentCandidateWithApp.application))
      val UpdatedApplication = currentCandidateWithApp.application
        .copy(applicationStatus= ApplicationStatus.WITHDRAWN,  progress = ProgressResponseExamples.WithdrawnAfterSubmitted)
      val UpdatedCandidate = currentCandidate.copy(application = Some(UpdatedApplication))
      when(mockUserService.save(eqTo(UpdatedCandidate))(any[HeaderCarrier])).thenReturn(Future.successful(UpdatedCandidate))

      val result = controller.withdrawApplication()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
      //scalastyle:off line.length
      flash(result).data must be (Map("success" ->"You've successfully withdrawn your application. <a href=\"https://www.gov.uk/done/apply-civil-service-fast-stream\" target=\"_blank\" rel=\"external\">Give feedback?</a> (30 second survey)"))
      //scalastyle:on line.length
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockUserService = mock[UserCacheService]
    val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]

    class TestableHomeController extends HomeController(mockApplicationClient, mockCacheClient)
      with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override val env = mockSecurityEnvironment
      override lazy val silhouette = SilhouetteComponent.silhouette
      val appRouteConfigMap = Map.empty[ApplicationRoute, ApplicationRouteState]
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
    }

    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp,
                   appRouteState: ApplicationRouteState = defaultApplicationRouteState) = new TestableHomeController {
      override val candidate: CachedData = CachedData(candWithApp.user, Some(candWithApp.application))
      override val candidateWithApp: CachedDataWithApp = candWithApp
      override val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
    }

    def defaultApplicationRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = Some(LocalDateTime.now)
    }
  }

  trait EdipAndSdipTestFixture extends TestFixture {
    val applicationRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = None }

    when(mockApplicationClient.getFinalSchemeResults(eqTo(currentApplicationId))(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(List(SchemeEvaluationResult(SchemeType.DiplomaticService, "Green")))))

    val edipPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.EdipPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
    val sdipPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.SdipPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
    val edipWithdrawnPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.EdipWithdrawnPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
    val sdipWithdrawnPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.SdipWithdrawnPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))

  }
}
