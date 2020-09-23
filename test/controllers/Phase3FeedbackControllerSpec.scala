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
import models._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.test.Helpers._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class Phase3FeedbackControllerSpec extends BaseControllerSpec {

  "present" should {
    val CapabilityPerformanceHigh = "phase3.feedback.capability.feedbackOnPerformance.High"
    val CapabilityPerformanceMedium = "phase3.feedback.capability.feedbackOnPerformance.Medium"
    val CapabilityPerformanceLow = "phase3.feedback.capability.feedbackOnPerformance.Low"
    val CapabilitySuggestionsHigh = "phase3.feedback.capability.suggestionsForFurtherDevelopment.High"
    val CapabilitySuggestionsMedium = "phase3.feedback.capability.suggestionsForFurtherDevelopment.Medium"
    val CapabilitySuggestionsLow = "phase3.feedback.capability.suggestionsForFurtherDevelopment.Low"
    val EngagementPerformanceHigh = "phase3.feedback.engagement.feedbackOnPerformance.High"
    val EngagementPerformanceMedium = "phase3.feedback.engagement.feedbackOnPerformance.Medium"
    val EngagementPerformanceLow = "phase3.feedback.engagement.feedbackOnPerformance.Low"
    val EngagementSuggestionsHigh = "phase3.feedback.engagement.suggestionsForFurtherDevelopment.High"
    val EngagementSuggestionsMedium = "phase3.feedback.engagement.suggestionsForFurtherDevelopment.Medium"
    val EngagementSuggestionsLow = "phase3.feedback.engagement.suggestionsForFurtherDevelopment.Low"

    "show report for high scores" in new TestFixture {
      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("High", "High"))))

      val result = controller.present()(fakeRequest)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must include(CapabilityPerformanceHigh)
      content must not include CapabilityPerformanceMedium
      content must not include CapabilityPerformanceLow

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

    "show report for medium scores" in new TestFixture {
      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("Medium", "Medium"))))

      val result = controller.present()(fakeRequest)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must not include CapabilityPerformanceHigh
      content must include(CapabilityPerformanceMedium)
      content must not include CapabilityPerformanceLow

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

    "show report for low scores" in new TestFixture {
      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("Low", "Low"))))

      val result = controller.present()(fakeRequest)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must not include CapabilityPerformanceHigh
      content must not include(CapabilityPerformanceMedium)
      content must include (CapabilityPerformanceLow)

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

    "show report for mixed scores: capability is high and engagement is low" in new TestFixture {
      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("High", "Low"))))

      val result = controller.present()(fakeRequest)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must include (CapabilityPerformanceHigh)
      content must not include(CapabilityPerformanceMedium)
      content must not include (CapabilityPerformanceLow)

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

    "show report for mixed scores: capability is medium and engagement is high" in new TestFixture {
      when(mockPhase3FeedbackService.getFeedback(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(("Medium", "High"))))

      val result = controller.present()(fakeRequest)

      status(result) must be(OK)
      val content = contentAsString(result)
      content must not include (CapabilityPerformanceHigh)
      content must include(CapabilityPerformanceMedium)
      content must not include (CapabilityPerformanceLow)

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

  trait TestFixture extends BaseControllerTestFixture {
    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp) = {
      new Phase3FeedbackController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent,
        mockNotificationTypeHelper, mockPhase3FeedbackService) with TestableSecureActions {
        override val candidateWithApp: CachedDataWithApp = candWithApp
      }
    }
  }
}
