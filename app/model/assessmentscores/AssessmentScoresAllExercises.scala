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
import play.api.libs.json.Json

// finalFeedback should be None in case of Reviewer Assessment scores
case class AssessmentScoresAllExercises(
                                         applicationId: UniqueIdentifier,
                                         writtenExercise: Option[AssessmentScoresExercise] = None,
                                         teamExercise: Option[AssessmentScoresExercise] = None,
                                         leadershipExercise: Option[AssessmentScoresExercise] = None,
                                         finalFeedback: Option[AssessmentScoresFinalFeedback] = None
                                       ) {

  def seeingTheBigPictureAvg: Double = {
    average(List(writtenExercise, leadershipExercise).flatMap(_.flatMap(_.seeingTheBigPictureAverage)), 2)
  }

  def workingTogetherDevelopingSelfAndOthersAvg: Double = {
    average(List(teamExercise, leadershipExercise).flatMap(_.flatMap(_.workingTogetherDevelopingSelfAndOthersAverage)), 2)
  }

  def makingEffectiveDecisionsAvg: Double = {
    average(List(writtenExercise, teamExercise).flatMap(_.flatMap(_.makingEffectiveDecisionsAverage)), 2)
  }

  def communicatingAndInfluencingAvg: Double = {
    average(List(writtenExercise, teamExercise, leadershipExercise).flatMap(_.flatMap(_.communicatingAndInfluencingAverage)), 3)
  }

  private def average(list: List[Double], mandatoryNumberOfElements: Int): Double = {
    (list.map(BigDecimal(_)).sum / mandatoryNumberOfElements).toDouble
  }

  def toExchange = AssessmentScoresAllExercisesExchange(
    applicationId: UniqueIdentifier,
    writtenExercise.map(_.toExchange),
    teamExercise.map(_.toExchange),
    leadershipExercise.map(_.toExchange),
    finalFeedback.map(_.toExchange)
  )
}

object AssessmentScoresAllExercises {
  implicit val jsonFormat = Json.format[AssessmentScoresAllExercises]
}

case class AssessmentScoresAllExercisesExchange(
                                         applicationId: UniqueIdentifier,
                                         writtenExercise: Option[AssessmentScoresExerciseExchange] = None,
                                         teamExercise: Option[AssessmentScoresExerciseExchange] = None,
                                         leadershipExercise: Option[AssessmentScoresExerciseExchange] = None,
                                         finalFeedback: Option[AssessmentScoresFinalFeedbackExchange] = None
                                       )

object AssessmentScoresAllExercisesExchange {
  implicit val jsonFormat = Json.format[AssessmentScoresAllExercisesExchange]
}
