/*
 * Copyright 2020 HM Revenue & Customs
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
import connectors.ApplicationClient.{CannotWithdraw, OnlineTestNotFound}
import connectors.ReferenceDataExamples
import connectors.exchange._
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import connectors.exchange.referencedata.SchemeId
import forms.{SchemeWithdrawForm, WithdrawApplicationForm, WithdrawApplicationFormExamples}
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models._
import models.events.AllocationStatuses
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.test.Helpers._
import testkit.MockitoImplicits._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

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
      content must include ("error.reason.required")
    }

    "display dashboard with error message when form is valid but cannot withdraw" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody(WithdrawApplicationFormExamples.ValidFormUrlEncodedBody: _*)
      when(mockApplicationClient.withdrawApplication(eqTo(currentApplicationId),
        eqTo(WithdrawApplicationExamples.Valid))(any[HeaderCarrier])).thenReturn(Future.failed(new CannotWithdraw))

      val result = controller.withdrawApplication()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
      flash(result).data must be (Map("danger" -> "error.cannot.withdraw"))
    }

    "display dashboard with withdrawn success message when withdraw is successful" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody(WithdrawApplicationFormExamples.ValidFormUrlEncodedBody: _*)
      when(mockApplicationClient.withdrawApplication(eqTo(currentApplicationId),
        eqTo(WithdrawApplicationExamples.Valid))(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.Submitted))

      val result = controller.withdrawApplication()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
      val expectedValue: Map[String, String] = Map("success" -> ("application.withdrawn"))
      flash(result).data mustBe expectedValue
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp,
                   appRouteState: ApplicationRouteState = defaultApplicationRouteState)
    = {
      val CandidateExample = CachedData(candWithApp.user, Some(candWithApp.application))

      val withdrawFormWrapper = new WithdrawApplicationForm
      val schemeWithdrawFormWrapper = new SchemeWithdrawForm

      when(mockReferenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)
      when(mockApplicationClient.getCurrentSchemeStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(List(SchemeEvaluationResultWithFailureDetails(SchemeId("DiplomaticService"), SchemeStatus.Green)))
      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(CachedData(ActiveCandidate.user, Some(CreatedApplication.copy(userId = ActiveCandidate.user.userID)))))

      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))
      when(mockApplicationClient.getPhase1TestProfile(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))

      mockPostOnlineTestsDashboardCalls()

        when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any[Request[_]]))
          .thenReturn(Future.successful(CandidateExample))

      def mockPostOnlineTestsDashboardCalls(hasAnalysisExerciseAlready: Boolean = false) = {
        val alloc = CandidateAllocationWithEvent("", "", AllocationStatuses.CONFIRMED, EventsExamples.Event1)
        when(mockApplicationClient.candidateAllocationEventWithSession(any[UniqueIdentifier])(any[HeaderCarrier]())).thenReturnAsync(List(alloc))
        when(mockApplicationClient.hasAnalysisExercise(any[UniqueIdentifier]())(any[HeaderCarrier])).thenReturnAsync(hasAnalysisExerciseAlready)
      }

      new WithdrawController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent,
        mockNotificationTypeHelper, mockApplicationClient, mockReferenceDataClient, withdrawFormWrapper, schemeWithdrawFormWrapper)
        with TestableSecureActions {
        override val candidate: CachedData = CandidateExample
        override val candidateWithApp: CachedDataWithApp = candWithApp
        override val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
      }
    }
  }
}
