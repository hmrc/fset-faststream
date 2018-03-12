/*
 * Copyright 2018 HM Revenue & Customs
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

import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime

import com.github.tomakehurst.wiremock.client.WireMock.{ any => _ }
import config.{ CSRHttp, SecurityEnvironmentImpl }
import connectors.ApplicationClient.{ CandidateAlreadyHasAnAnalysisExerciseException, CannotWithdraw, OnlineTestNotFound }
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import connectors.exchange.referencedata.{ Scheme, SchemeId }
import connectors.exchange.sift.SiftAnswersStatus
import connectors.exchange._
import connectors.{ ApplicationClient, ReferenceDataClient, ReferenceDataExamples, SiftClient }
import forms.WithdrawApplicationFormExamples
import models.ApplicationData.ApplicationStatus
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models._
import models.events.{ AllocationStatuses, EventType }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{ AnyContent, MultipartFormData, Request }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest }
import security.{ SilhouetteComponent, UserCacheService }
import testkit.MockitoImplicits._
import testkit.{ BaseControllerSpec, TestableSecureActions }

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class WithdrawControllerSpec extends BaseControllerSpec {

  // The current candidate needed by other methods than withdraw may be different, in that case, we might need
  // to split to tests of this file.
  // This is the implicit user
  override def currentCandidateWithApp: CachedDataWithApp = {
    CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.SubmittedApplication.copy(userId = ActiveCandidate.user.userID))
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
      content must include(routes.WithdrawController.withdrawApplication().url)
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
    val mockRefDataClient = mock[ReferenceDataClient]
    val mockUserService = mock[UserCacheService]
    val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]


    val anyContentMock = mock[AnyContent]

    def mockPostOnlineTestsDashboardCalls(hasAnalysisExerciseAlready: Boolean = false) = {

      val alloc = CandidateAllocationWithEvent("", "", AllocationStatuses.CONFIRMED, EventsExamples.Event1)

      when(mockApplicationClient.candidateAllocationEventWithSession(any[UniqueIdentifier])(any[HeaderCarrier]())).thenReturnAsync(List(alloc))

      when(mockApplicationClient.hasAnalysisExercise(any[UniqueIdentifier]())(any[HeaderCarrier])).thenReturnAsync(hasAnalysisExerciseAlready)
    }


    class TestableHomeController extends WithdrawController(mockApplicationClient, mockRefDataClient)
      with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override val env = mockSecurityEnvironment
      override lazy val silhouette = SilhouetteComponent.silhouette
      val appRouteConfigMap = Map.empty[ApplicationRoute, ApplicationRouteState]
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)
      when(mockApplicationClient.getCurrentSchemeStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(List(SchemeEvaluationResultWithFailureDetails(SchemeId("DiplomaticService"), SchemeStatus.Green)))
      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(CachedData(ActiveCandidate.user, Some(CreatedApplication.copy(userId = ActiveCandidate.user.userID)))))

      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))
      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))

      mockPostOnlineTestsDashboardCalls()
    }

    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp,
                   appRouteState: ApplicationRouteState = defaultApplicationRouteState) = new TestableHomeController {
      override val candidate: CachedData = CachedData(candWithApp.user, Some(candWithApp.application))
      override val candidateWithApp: CachedDataWithApp = candWithApp
      override val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)

      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(candidate))
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

    when(mockApplicationClient.getPhase3Results(eqTo(currentApplicationId))(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(List(SchemeEvaluationResult(SchemeId("DiplomaticService"), SchemeStatus.Green)))))

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
