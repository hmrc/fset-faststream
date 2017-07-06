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

package model.persisted

import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class PassmarkEvaluation(
  passmarkVersion: String,
  previousPhasePassMarkVersion: Option[String],
  result: List[SchemeEvaluationResult],
  resultVersion: String,
  previousPhaseResultVersion: Option[String]
)

object PassmarkEvaluation {
  implicit val passmarkEvaluationFormat = Json.format[PassmarkEvaluation]
  implicit val passmarkEvaluationHandler = Macros.handler[PassmarkEvaluation]
}
