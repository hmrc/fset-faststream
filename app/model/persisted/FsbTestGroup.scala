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

package model.persisted

import play.api.libs.json.{Json, OFormat}

case class FsbEvaluation(result: List[SchemeEvaluationResult])

object FsbEvaluation {
  implicit val jsonFormat: OFormat[FsbEvaluation] = Json.format[FsbEvaluation]
}

case class FsbTestGroup(evaluation: FsbEvaluation)

object FsbTestGroup {
  implicit val jsonFormat: OFormat[FsbTestGroup] = Json.format[FsbTestGroup]

  def apply(results: List[SchemeEvaluationResult]): FsbTestGroup = new FsbTestGroup(FsbEvaluation(results))
}
