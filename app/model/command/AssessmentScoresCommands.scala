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

import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise }
import model.UniqueIdentifier
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object AssessmentScoresCommands {

  case class RecordCandidateScores(firstName: String, lastName: String, venueName: String, assessmentDate: LocalDate)
  object RecordCandidateScores {
    implicit val RecordCandidateScoresFormats: Format[RecordCandidateScores] = Json.format[RecordCandidateScores]
  }

  // TODO MIGUEL: See if we will use this
  object AssessmentExercise extends Enumeration {
    type AssessmentExercise = Value

    val analysisExercise, groupExercise, leadershipExercise = Value

    implicit val assessmentExerciseFormat = new Format[AssessmentExercise] {
      def reads(json: JsValue) = JsSuccess(AssessmentExercise.withName(json.as[String]))

      def writes(scheme: AssessmentExercise) = JsString(scheme.toString)
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, AssessmentExercise] {
      def read(doc: BSONString) = AssessmentExercise.withName(doc.value)

      def write(scheme: AssessmentExercise) = BSON.write(scheme.toString)
    }
  }

  case class AssessmentScoresSubmitRequest(
    applicationId: UniqueIdentifier,
    exercise: String,
    scoresAndFeedback: AssessmentScoresExercise
  )
  object AssessmentScoresSubmitRequest {
    implicit val exerciseScoresAndFeedbackFormats: Format[AssessmentScoresSubmitRequest] = Json.format[AssessmentScoresSubmitRequest]
  }

  case class AssessmentScoresFindResponse(candidate: RecordCandidateScores, scoresAndFeedback: Option[AssessmentScoresAllExercises])
  object AssessmentScoresFindResponse {
    implicit val ApplicationScoresFormats: Format[AssessmentScoresFindResponse] = Json.format[AssessmentScoresFindResponse]
  }
}
