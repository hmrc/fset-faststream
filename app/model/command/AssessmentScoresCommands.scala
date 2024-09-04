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

package model.command

import model.UniqueIdentifier
import model.assessmentscores.{AssessmentScoresAllExercisesExchange, AssessmentScoresExerciseExchange, AssessmentScoresFinalFeedbackExchange}
import org.mongodb.scala.bson.BsonValue
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.LocalDate

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

  object AssessmentScoresSectionType extends Enumeration {
    type AssessmentScoresSectionType = Value

    val exercise1, exercise2, exercise3, finalFeedback = Value

    implicit val assessmentExerciseFormat: Format[AssessmentScoresSectionType] = new Format[AssessmentScoresSectionType] {
      def reads(json: JsValue): JsSuccess[Value] = JsSuccess(AssessmentScoresSectionType.withName(json.as[String]))
      def writes(scheme: AssessmentScoresSectionType): JsString = JsString(scheme.toString)
    }

    implicit class BsonOps(val sectionType: AssessmentScoresSectionType) extends AnyVal {
      def toBson: BsonValue = Codecs.toBson(sectionType)
    }

    def asListOfStrings = List(
      exercise1.toString, exercise2.toString, exercise3.toString, finalFeedback.toString
    )
  }

  case class AssessmentScoresSubmitExerciseRequest(
    applicationId: UniqueIdentifier,
    exercise: String,
    scoresExercise: AssessmentScoresExerciseExchange
  )
  object AssessmentScoresSubmitExerciseRequest {
    implicit val jsonFormat: Format[AssessmentScoresSubmitExerciseRequest] = Json.format[AssessmentScoresSubmitExerciseRequest]
  }
  type AssessmentScoresSaveExerciseRequest = AssessmentScoresSubmitExerciseRequest

  case class AssessmentScoresFinalFeedbackSubmitRequest(
                                            applicationId: UniqueIdentifier,
                                            finalFeedback: AssessmentScoresFinalFeedbackExchange
                                          )
  object AssessmentScoresFinalFeedbackSubmitRequest {
    implicit val jsonFormat: Format[AssessmentScoresFinalFeedbackSubmitRequest] = Json.format[AssessmentScoresFinalFeedbackSubmitRequest]
  }

  case class AssessmentScoresFindResponse(
                                           candidate: AssessmentScoresCandidateSummary,
                                           scoresAllExercises: Option[AssessmentScoresAllExercisesExchange]
                                         )
  object AssessmentScoresFindResponse {
    implicit val jsonFormat: Format[AssessmentScoresFindResponse] = Json.format[AssessmentScoresFindResponse]
  }

  case class ResetExercisesRequest(
                                    applicationId: UniqueIdentifier,
                                    exercise1: Boolean,
                                    exercise2: Boolean,
                                    exercise3: Boolean,
                                    finalFeedback: Boolean
  )
  object ResetExercisesRequest {
    implicit val jsonFormat: Format[ResetExercisesRequest] = Json.format[ResetExercisesRequest]
  }
}
