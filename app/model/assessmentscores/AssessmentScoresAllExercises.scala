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
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

// finalFeedback should be None in case of Reviewer Assessment scores
case class AssessmentScoresAllExercises(
                                         applicationId: UniqueIdentifier,
                                         analysisExercise: Option[AssessmentScoresExercise] = None,
                                         groupExercise: Option[AssessmentScoresExercise] = None,
                                         leadershipExercise: Option[AssessmentScoresExercise] = None,
                                         finalFeedback: Option[AssessmentScoresFinalFeedback] = None
                                            )

object AssessmentScoresAllExercises {
  implicit val jsonFormat = Json.format[AssessmentScoresAllExercises]
  implicit val bsonHandler: BSONHandler[BSONDocument, AssessmentScoresAllExercises] =
    Macros.handler[AssessmentScoresAllExercises]
}
