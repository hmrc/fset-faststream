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

package models.page

import testkit.UnitSpec

class Phase3FeedbackPageSpec extends UnitSpec {
  "apply for high" should {
    "create page with 'High' messages for capability and 'High' for engagement" in {
      val page = Phase3FeedbackPage.apply(("High", "High"))
      page.capabilityFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.capability.feedbackOnPerformance.High",
        "phase3.feedback.capability.suggestionsForFurtherDevelopment.High")
      page.engagementFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.engagement.feedbackOnPerformance.High",
        "phase3.feedback.engagement.suggestionsForFurtherDevelopment.High")
    }

    "create page with 'Medium' messages for capability and 'Medium' for engagement" in {
      val page = Phase3FeedbackPage.apply(("Medium", "Medium"))
      page.capabilityFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.capability.feedbackOnPerformance.Medium",
        "phase3.feedback.capability.suggestionsForFurtherDevelopment.Medium")
      page.engagementFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.engagement.feedbackOnPerformance.Medium",
        "phase3.feedback.engagement.suggestionsForFurtherDevelopment.Medium")
    }

    "create page with 'Low' messages for capability and 'Low' for engagement" in {
      val page = Phase3FeedbackPage.apply(("Low", "Low"))
      page.capabilityFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.capability.feedbackOnPerformance.Low",
        "phase3.feedback.capability.suggestionsForFurtherDevelopment.Low")
      page.engagementFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.engagement.feedbackOnPerformance.Low",
        "phase3.feedback.engagement.suggestionsForFurtherDevelopment.Low")
    }

    "create page with 'Medium' messages for capability and 'High' for engagement" in {
      val page = Phase3FeedbackPage.apply(("Medium", "Low"))
      page.capabilityFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.capability.feedbackOnPerformance.Medium",
        "phase3.feedback.capability.suggestionsForFurtherDevelopment.Medium")
      page.engagementFeedback mustBe Phase3CompetencyFeedback("phase3.feedback.engagement.feedbackOnPerformance.Low",
        "phase3.feedback.engagement.suggestionsForFurtherDevelopment.Low")
    }
  }
}
