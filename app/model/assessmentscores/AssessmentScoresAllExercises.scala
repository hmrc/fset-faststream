/*
 * Copyright 2019 HM Revenue & Customs
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
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

// finalFeedback should be None in case of Reviewer Assessment scores
case class AssessmentScoresAllExercises(
                                         applicationId: UniqueIdentifier,
                                         analysisExercise: Option[AssessmentScoresExercise] = None,
                                         groupExercise: Option[AssessmentScoresExercise] = None,
                                         leadershipExercise: Option[AssessmentScoresExercise] = None,
                                         finalFeedback: Option[AssessmentScoresFinalFeedback] = None
                                       ) {

  def strategicApproachToObjectivesAvg: Double = {
    average(List(analysisExercise, leadershipExercise).flatMap(_.flatMap(_.seeingTheBigPictureAverage)), 2)
  }

  def buildingProductiveRelationshipsAvg: Double = {
    average(List(groupExercise, leadershipExercise).flatMap(_.flatMap(_.buildingProductiveRelationshipsAverage)), 2)
  }

   def analysisAndDecisionMakingAvg: Double = {
    average(List(analysisExercise, groupExercise).flatMap(_.flatMap(_.makingEffectiveDecisionsAverage)), 2)
  }

   def leadingAndCommunicatingAvg: Double = {
    average(List(analysisExercise, groupExercise, leadershipExercise).flatMap(_.flatMap(_.communicatingAndInfluencingAverage)), 3)
  }

  private def average(list: List[Double], mandatoryNumberOfElements: Int): Double = {
    (list.map(BigDecimal(_)).sum / mandatoryNumberOfElements).toDouble
  }
}

object AssessmentScoresAllExercises {
  implicit val jsonFormat = Json.format[AssessmentScoresAllExercises]
  implicit val bsonHandler: BSONHandler[BSONDocument, AssessmentScoresAllExercises] =
    Macros.handler[AssessmentScoresAllExercises]
}
