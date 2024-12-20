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

package services.evaluation

import model.EvaluationResults.ExerciseAverageResult
import model.assessmentscores.AssessmentScoresAllExercises

object AssessmentScoreCalculator extends AssessmentScoreCalculator

trait AssessmentScoreCalculator {

  def fetchExerciseAverages(scores: AssessmentScoresAllExercises, appId: String): ExerciseAverageResult = {
    // The overall score is the individual exercise averages summed
    val overallScore = List(
      scores.exercise1OverallAvg(appId),
      scores.exercise2OverallAvg(appId),
      scores.exercise3OverallAvg(appId)
    ).map{ avg =>
      val bd = BigDecimal.apply(avg)
      val decimalPlaces = 4
      bd.setScale(decimalPlaces, BigDecimal.RoundingMode.HALF_UP)
    }.sum

    ExerciseAverageResult(
      scores.exercise1OverallAvg(appId),
      scores.exercise2OverallAvg(appId),
      scores.exercise3OverallAvg(appId),
      overallScore.toDouble)
  }

  def fetchExerciseAveragesOpt(scores: AssessmentScoresAllExercises): Option[ExerciseAverageResult] = {
    // The overall score is the individual exercise averages summed
    val exerciseScores = List(
      scores.exercise1OverallAvgOpt,
      scores.exercise2OverallAvgOpt,
      scores.exercise3OverallAvgOpt
    )

    val decimalPlaces = 4
    val overallScoreOpt = exerciseScores.foldLeft(
      Option(BigDecimal.apply(0d).setScale(decimalPlaces, BigDecimal.RoundingMode.HALF_UP)))((acc, cur) =>
        for ( a <- acc; c <- cur) yield {
          val bd = BigDecimal.apply(c)
          a + bd
        } )

    overallScoreOpt match {
      case Some(overallScore) =>
        // If the overallScore is populated then all the data is populated
        for {
          ex1Avg <- scores.exercise1OverallAvgOpt
          ex2Avg <- scores.exercise2OverallAvgOpt
          ex3Avg <- scores.exercise3OverallAvgOpt
        } yield {
          ExerciseAverageResult(
            ex1Avg,
            ex2Avg,
            ex3Avg,
            overallScore.toDouble
          )
        }
      case None => None
    }
  }
}
