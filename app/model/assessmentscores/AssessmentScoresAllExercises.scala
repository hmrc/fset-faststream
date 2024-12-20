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
import play.api.libs.json.{Json, OFormat}

import scala.math.BigDecimal.RoundingMode

// finalFeedback should be None in case of Reviewer Assessment scores
case class AssessmentScoresAllExercises(
                                         applicationId: UniqueIdentifier,
                                         exercise1: Option[AssessmentScoresExercise] = None,
                                         exercise2: Option[AssessmentScoresExercise] = None,
                                         exercise3: Option[AssessmentScoresExercise] = None,
                                         finalFeedback: Option[AssessmentScoresFinalFeedback] = None
                                       ) {

  def exercise1OverallAvg(appId: String): Double = {
    exercise1OverallAvgOpt.getOrElse(throw new Exception(s"Error fetching fsac exercise1 overall average for appId: $appId"))
  }

  def exercise1OverallAvgOpt: Option[Double] = {
    exercise1.flatMap(ex =>
      for {
        average <- ex.overallAverage
      } yield average
    )
  }

  def exercise2OverallAvg(appId: String): Double = {
    exercise2OverallAvgOpt.getOrElse(throw new Exception(s"Error fetching fsac exercise2 overall average for appId: $appId"))
  }

  def exercise2OverallAvgOpt: Option[Double] = {
    exercise2.flatMap(ex =>
      for {
        average <- ex.overallAverage
      } yield average
    )
  }

  def exercise3OverallAvg(appId: String): Double = {
    exercise3OverallAvgOpt.getOrElse(throw new Exception(s"Error fetching fsac exercise3 overall average for appId: $appId"))
  }

  def exercise3OverallAvgOpt: Option[Double] = {
    exercise3.flatMap(ex =>
      for {
        average <- ex.overallAverage
      } yield average
    )
  }

  def toExchange: AssessmentScoresAllExercisesExchange = AssessmentScoresAllExercisesExchange(
    applicationId: UniqueIdentifier,
    exercise1.map(_.toExchange),
    exercise2.map(_.toExchange),
    exercise3.map(_.toExchange),
    finalFeedback.map(_.toExchange)
  )
}

object AssessmentScoresAllExercises {
  implicit val jsonFormat: OFormat[AssessmentScoresAllExercises] = Json.format[AssessmentScoresAllExercises]
}

case class AssessmentScoresAllExercisesExchange(
                                                 applicationId: UniqueIdentifier,
                                                 exercise1: Option[AssessmentScoresExerciseExchange] = None,
                                                 exercise2: Option[AssessmentScoresExerciseExchange] = None,
                                                 exercise3: Option[AssessmentScoresExerciseExchange] = None,
                                                 finalFeedback: Option[AssessmentScoresFinalFeedbackExchange] = None
                                       )

object AssessmentScoresAllExercisesExchange {
  implicit val jsonFormat: OFormat[AssessmentScoresAllExercisesExchange] = Json.format[AssessmentScoresAllExercisesExchange]
}
