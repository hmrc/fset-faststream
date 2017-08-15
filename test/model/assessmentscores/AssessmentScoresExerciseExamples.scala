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

package model.assessmentscores

import model.UniqueIdentifier
import org.joda.time.{ DateTime, DateTimeZone }

object AssessmentScoresExerciseExamples {
  val Example1 = getExample(1)
  val Example2 = getExample(1.5)
  val Example3 = getExample(2)
  val Example4 = getExample(2.5)
  lazy val dateTimeNow = DateTime.now(DateTimeZone.UTC)
  lazy val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  private def getExample(baseValue: Double): AssessmentScoresExercise = {
    val strategicScore = Some(baseValue + 0.1)
    val analysisScore = Some(baseValue + 0.1)
    val leadingScore = Some(baseValue + 0.2)
    val buildingScore = Some(baseValue + 0.3)
    val otherScore = Some(baseValue + 0.4)

    AssessmentScoresExercise(
      attended = true,
      Some(StrategicApproachToObjectivesScores(strategicScore, strategicScore, strategicScore, strategicScore, strategicScore)),
      Some(AnalysisAndDecisionMakingScores(analysisScore, analysisScore, analysisScore, analysisScore, analysisScore)),
      Some(LeadingAndCommunicatingScores(leadingScore, leadingScore, leadingScore, leadingScore, leadingScore)),
      Some(BuildingProductiveRelationshipsScores(buildingScore, buildingScore,
        buildingScore,buildingScore,buildingScore,buildingScore,buildingScore)),
      Some("feedback1"), Some("feedback2"), Some("feedback3"), Some("feedback4"),
      otherScore, otherScore, otherScore, otherScore,
      updatedBy,
      Some(dateTimeNow),
      Some(dateTimeNow),
      Some("version1")
    )
  }
}
