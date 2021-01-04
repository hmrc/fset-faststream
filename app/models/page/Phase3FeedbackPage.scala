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

package models.page

case class Phase3CompetencyFeedback(feedbackOnPerformance: String, suggestionsForFurtherDevelopment: String)

case class Phase3FeedbackPage(
  capabilityFeedback: Phase3CompetencyFeedback,
  engagementFeedback: Phase3CompetencyFeedback
)

case object Phase3FeedbackPage {
  def apply(feedback: (String, String)): Phase3FeedbackPage = {
    val competencyFeedback = Phase3CompetencyFeedback(
      s"phase3.feedback.capability.feedbackOnPerformance.${feedback._1}",
      s"phase3.feedback.capability.suggestionsForFurtherDevelopment.${feedback._1}")
    val engagementFeedback = Phase3CompetencyFeedback(
      s"phase3.feedback.engagement.feedbackOnPerformance.${feedback._2}",
      s"phase3.feedback.engagement.suggestionsForFurtherDevelopment.${feedback._2}")
    Phase3FeedbackPage(competencyFeedback, engagementFeedback)
  }
}
