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

package model.command

import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise, AssessmentScoresFinalFeedback }
import model.UniqueIdentifier
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object AssessmentScoresCommands {

  case class AssessmentScoresCandidateSummary(applicationId: UniqueIdentifier,
                                              firstName: String,
                                              lastName: String,
                                              venueName: String,
                                              assessmentDate: LocalDate,
                                              sessionId: UniqueIdentifier)
  object AssessmentScoresCandidateSummary {
    implicit val jsonFormat: Format[AssessmentScoresCandidateSummary] = Json.format[AssessmentScoresCandidateSummary]
  }

  object AssessmentExerciseType extends Enumeration {
    type AssessmentExerciseType = Value

    val analysisExercise, groupExercise, leadershipExercise = Value

    implicit val assessmentExerciseFormat = new Format[AssessmentExerciseType] {
      def reads(json: JsValue) = JsSuccess(AssessmentExerciseType.withName(json.as[String]))

      def writes(scheme: AssessmentExerciseType) = JsString(scheme.toString)
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, AssessmentExerciseType] {
      def read(doc: BSONString) = AssessmentExerciseType.withName(doc.value)

      def write(scheme: AssessmentExerciseType) = BSON.write(scheme.toString)
    }
  }

  case class AssessmentScoresSubmitExerciseRequest(
    applicationId: UniqueIdentifier,
    exercise: String,
    scoresAndFeedback: AssessmentScoresExercise
  )
  object AssessmentScoresSubmitExerciseRequest {
    implicit val jsonFormat: Format[AssessmentScoresSubmitExerciseRequest] = Json.format[AssessmentScoresSubmitExerciseRequest]
  }
  type AssessmentScoresSaveExerciseRequest = AssessmentScoresSubmitExerciseRequest

  case class AssessmentScoresFinalFeedbackSubmitRequest(
                                            applicationId: UniqueIdentifier,
                                            finalFeedback: AssessmentScoresFinalFeedback
                                          )
  object AssessmentScoresFinalFeedbackSubmitRequest {
    implicit val jsonFormat: Format[AssessmentScoresFinalFeedbackSubmitRequest] = Json.format[AssessmentScoresFinalFeedbackSubmitRequest]
  }

  case class AssessmentScoresFindResponse(candidate: AssessmentScoresCandidateSummary, scoresAndFeedback: Option[AssessmentScoresAllExercises])
  object AssessmentScoresFindResponse {
    implicit val jsonFormat: Format[AssessmentScoresFindResponse] = Json.format[AssessmentScoresFindResponse]
  }
}
