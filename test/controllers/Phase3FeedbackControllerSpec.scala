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
import config.{CSRHttp, SecurityEnvironmentImpl}
import models.SecurityUserExamples._
import models._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.{SilhouetteComponent, UserCacheService}
import services.Phase3FeedbackService
import testkit.{BaseControllerSpec, TestableSecureActions}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class Phase3FeedbackControllerSpec extends BaseControllerSpec {

  val mockUserService = mock[UserCacheService]
  val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]
  val mockPhase3FeedbackService = mock[Phase3FeedbackService]

  class TestablePhase3FeedbackController extends Phase3FeedbackController(mockPhase3FeedbackService)
    with TestableSecureActions {
    val http: CSRHttp = CSRHttp

    override val env = mockSecurityEnvironment
    override lazy val silhouette = SilhouetteComponent.silhouette

    when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
  }

  def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp) = new TestablePhase3FeedbackController {
    override val candidateWithApp: CachedDataWithApp = candWithApp
  }

  override def currentCandidateWithApp: CachedDataWithApp = {
    CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.Phase3TestsFailedApplication.copy(userId = ActiveCandidate.user.userID))
  }

  "present" should {
    val CapabilityPerformanceHigh = "shows high capability"
    val CapabilityPerformanceMedium = "shows generally good evidence"
    val CapabiliyPerformanceLow = "shows limited evidence"
    val CapabilitySuggestionsHigh = "To sustain or enhance your performance"
    val CapabilitySuggestionsMedium = "Although you performed well "
    val CapabilitySuggestionsLow = "When in situations where you need to take action"
    val EngagementPerformanceHigh = "Your responses to the Video Interview displayed very clear willingness"
    val EngagementPerformanceMedium = "Your responses to the Video Interview displayed some degree of willingness"
    val EngagementPerformanceLow = "Your responses to the Video Interview displayed limited engagement"
    val EngagementSuggestionsHigh = "Aim to continue your motivation and engagement in situations"
    val EngagementSuggestionsMedium = "Aim to develop your motivation and engagement in situations"
    val EngagementSuggestionsLow = "Aim to enhance your motivation and engagement in situations"
    "show report for high scores" in {

      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("High", "High"))))

      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include(CapabilityPerformanceHigh)
      content must not include CapabilityPerformanceMedium
      content must not include CapabiliyPerformanceLow

      content must include(CapabilitySuggestionsHigh)
      content must not include CapabilitySuggestionsMedium
      content must not include CapabilitySuggestionsLow

      content must include (EngagementPerformanceHigh)
      content must not include EngagementPerformanceMedium
      content must not include EngagementPerformanceLow

      content must include (EngagementSuggestionsHigh)
      content must not include EngagementSuggestionsMedium
      content must not include EngagementSuggestionsLow
    }

    "show report for medium scores" in {

      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("Medium", "Medium"))))

      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must not include CapabilityPerformanceHigh
      content must include(CapabilityPerformanceMedium)
      content must not include CapabiliyPerformanceLow

      content must not include CapabilitySuggestionsHigh
      content must include(CapabilitySuggestionsMedium)
      content must not include CapabilitySuggestionsLow

      content must not include EngagementPerformanceHigh
      content must include (EngagementPerformanceMedium)
      content must not include EngagementPerformanceLow

      content must not include EngagementSuggestionsHigh
      content must include (EngagementSuggestionsMedium)
      content must not include EngagementSuggestionsLow
    }

    "show report for low scores" in {

      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("Low", "Low"))))

      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must not include CapabilityPerformanceHigh
      content must not include(CapabilityPerformanceMedium)
      content must include (CapabiliyPerformanceLow)

      content must not include CapabilitySuggestionsHigh
      content must not include(CapabilitySuggestionsMedium)
      content must include (CapabilitySuggestionsLow)

      content must not include EngagementPerformanceHigh
      content must not include (EngagementPerformanceMedium)
      content must include (EngagementPerformanceLow)

      content must not include EngagementSuggestionsHigh
      content must not include (EngagementSuggestionsMedium)
      content must include (EngagementSuggestionsLow)
    }

    "show report for mixed scores: capability is high and engagement is low" in {

      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("High", "Low"))))

      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include (CapabilityPerformanceHigh)
      content must not include(CapabilityPerformanceMedium)
      content must not include (CapabiliyPerformanceLow)

      content must include (CapabilitySuggestionsHigh)
      content must not include(CapabilitySuggestionsMedium)
      content must not include (CapabilitySuggestionsLow)

      content must not include EngagementPerformanceHigh
      content must not include (EngagementPerformanceMedium)
      content must include (EngagementPerformanceLow)

      content must not include EngagementSuggestionsHigh
      content must not include (EngagementSuggestionsMedium)
      content must include (EngagementSuggestionsLow)
    }

    "show report for mixed scores: capability is medium and engagement is high" in {

      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("Medium", "High"))))

      val result = controller.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must not include (CapabilityPerformanceHigh)
      content must include(CapabilityPerformanceMedium)
      content must not include (CapabiliyPerformanceLow)

      content must not include (CapabilitySuggestionsHigh)
      content must include(CapabilitySuggestionsMedium)
      content must not include (CapabilitySuggestionsLow)

      content must include (EngagementPerformanceHigh)
      content must not include (EngagementPerformanceMedium)
      content must not include (EngagementPerformanceLow)

      content must include (EngagementSuggestionsHigh)
      content must not include (EngagementSuggestionsMedium)
      content must not include (EngagementSuggestionsLow)
    }

  }
}
