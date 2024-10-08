/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneId}

object AssessmentScoresExerciseExamples {
  val Example1 = getExample(1)
  val Example2 = getExample(1.5)
  val Example3 = getExample(2)
  val Example4 = getExample(2.5)
  val ExampleNoFractions = getExampleNoFractions
  // Create the date truncated to milliseconds as that is the precision of the date stored in mongo
  // and the comparison will work when we fetch the date back from the db and check it
  lazy val dateTimeNow = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS)

  lazy val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  private def getExample(baseValue: Double): AssessmentScoresExercise = {
    val strategicScore = Some(baseValue + 0.1)
    val analysisScore = Some(baseValue + 0.1)
    val leadingScore = Some(baseValue + 0.2)
    val buildingScore = Some(baseValue + 0.3)
    val averageScore = Some(baseValue + 0.4)

    AssessmentScoresExercise(
      attended = true,
      Some(RelatesScores(strategicScore, strategicScore, strategicScore, strategicScore)),
      Some("feedback1"),
      Some(ThinksScores(analysisScore, analysisScore, analysisScore, analysisScore)),
      Some("feedback2"),
      Some(StrivesScores(leadingScore, leadingScore, leadingScore, leadingScore, leadingScore)),
      Some("feedback3"),
      Some(AdaptsScores(buildingScore, buildingScore, buildingScore,
        buildingScore, buildingScore, buildingScore)),
      Some("feedback4"),
      updatedBy,
      Some(dateTimeNow),
      Some(dateTimeNow),
      averageScore,
      Some(UniqueIdentifier.randomUniqueIdentifier.toString())
    )
  }

  private def getExampleNoFractions: AssessmentScoresExercise = {
    val baseValue = 2.0
    val strategicScore = Some(baseValue)
    val analysisScore = Some(baseValue)
    val leadingScore = Some(baseValue)
    val buildingScore = Some(baseValue)
    val averageScore = Some(baseValue)

    AssessmentScoresExercise(
      attended = true,
      Some(RelatesScores(strategicScore, strategicScore, strategicScore, strategicScore)),
      Some("feedback1"),
      Some(ThinksScores(analysisScore, analysisScore, analysisScore, analysisScore)),
      Some("feedback2"),
      Some(StrivesScores(leadingScore, leadingScore, leadingScore, leadingScore, leadingScore)),
      Some("feedback3"),
      Some(AdaptsScores(buildingScore, buildingScore, buildingScore,
        buildingScore, buildingScore, buildingScore)),
      Some("feedback4"),
      updatedBy,
      Some(dateTimeNow),
      Some(dateTimeNow),
      averageScore,
      Some(UniqueIdentifier.randomUniqueIdentifier.toString())
    )
  }
}
