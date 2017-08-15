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

package model.questionnaire

import model.persisted.{ QuestionnaireAnswer, QuestionnaireQuestion }
import play.api.libs.json.{ Json, OFormat }

case class Question(question: String, answer: Answer)

object Question {
  implicit val questionFormat: OFormat[Question] = Json.format[Question]
  def fromCommandToPersistedQuestion(q: Question): QuestionnaireQuestion =
    QuestionnaireQuestion(q.question, QuestionnaireAnswer(q.answer.answer, q.answer.otherDetails, q.answer.unknown))
}
