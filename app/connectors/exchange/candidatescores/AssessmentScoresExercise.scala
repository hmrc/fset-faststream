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

package connectors.exchange.candidatescores

import models.UniqueIdentifier
import org.joda.time.DateTime
import play.api.libs.json.Json

case class AssessmentScoresExercise(
  attended: Boolean,

  strategicApproachToObjectivesScores: Option[StrategicApproachToObjectivesScores] = None,
  analysisAndDecisionMakingScores: Option[AnalysisAndDecisionMakingScores] = None,
  leadingAndCommunicatingScores: Option[LeadingAndCommunicatingScores] = None,
  buildingProductiveRelationshipsScores: Option[BuildingProductiveRelationshipsScores] = None,

  strategicApproachToObjectivesFeedback: Option[String] = None,
  analysisAndDecisionMakingFeedback: Option[String] = None,
  leadingAndCommunicatingFeedback: Option[String] = None,
  buildingProductiveRelationshipsFeedback: Option[String] = None,

  strategicApproachToObjectivesAverage: Option[Double] = None,
  analysisAndDecisionMakingAverage: Option[Double] = None,
  leadingAndCommunicatingAverage: Option[Double] = None,
  buildingProductiveRelationshipsAverage: Option[Double] = None,
  updatedBy: UniqueIdentifier,
  savedDate: Option[DateTime] = None,
  submittedDate: Option[DateTime] = None,
  version: Option[String] = None
)

object AssessmentScoresExercise {
  implicit val scoresAndFeedbackFormat = Json.format[AssessmentScoresExercise]
}
