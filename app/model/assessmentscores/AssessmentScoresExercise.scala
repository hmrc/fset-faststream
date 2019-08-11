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
import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }
import repositories._

case class AssessmentScoresExercise(
                                     attended: Boolean,

                                     seeingTheBigPictureScores: Option[SeeingTheBigPictureScores] = None,
                                     seeingTheBigPictureAverage: Option[Double] = None,
                                     seeingTheBigPictureFeedback: Option[String] = None,

                                     makingEffectiveDecisionsScores: Option[MakingEffectiveDecisionsScores] = None,
                                     makingEffectiveDecisionsAverage: Option[Double] = None,
                                     makingEffectiveDecisionsFeedback: Option[String] = None,

                                     leadingAndCommunicatingScores: Option[LeadingAndCommunicatingScores] = None,
                                     leadingAndCommunicatingAverage: Option[Double] = None,
                                     leadingAndCommunicatingFeedback: Option[String] = None,

                                     buildingProductiveRelationshipsScores: Option[BuildingProductiveRelationshipsScores] = None,
                                     buildingProductiveRelationshipsAverage: Option[Double] = None,
                                     buildingProductiveRelationshipsFeedback: Option[String] = None,

                                     updatedBy: UniqueIdentifier,
                                     savedDate: Option[DateTime] = None,
                                     submittedDate: Option[DateTime] = None,
                                     version: Option[String] = None
) extends AssessmentScoresSection {
  def isSubmitted = submittedDate.isDefined
}

object AssessmentScoresExercise {
  implicit val jsonFormat = Json.format[AssessmentScoresExercise]
  implicit val bsonHandler: BSONHandler[BSONDocument, AssessmentScoresExercise] =
    Macros.handler[AssessmentScoresExercise]
}
