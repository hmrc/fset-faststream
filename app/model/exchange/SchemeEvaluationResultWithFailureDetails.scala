/*
 * Copyright 2018 HM Revenue & Customs
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

package model.exchange

import model.SchemeId
import model.persisted.SchemeEvaluationResult
import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class SchemeEvaluationResultWithFailureDetails(schemeId: SchemeId, result: String, failedAt: Option[String])

object SchemeEvaluationResultWithFailureDetails {
  implicit val format = Json.format[SchemeEvaluationResultWithFailureDetails]
  implicit val bsonHandler = Macros.handler[SchemeEvaluationResultWithFailureDetails]

  def apply(schemeEvaluation: SchemeEvaluationResult, failedAt: String): SchemeEvaluationResultWithFailureDetails =
    SchemeEvaluationResultWithFailureDetails(schemeEvaluation.schemeId, schemeEvaluation.result, Some(failedAt))

  def apply(schemeEvaluation: SchemeEvaluationResult): SchemeEvaluationResultWithFailureDetails =
    SchemeEvaluationResultWithFailureDetails(schemeEvaluation.schemeId, schemeEvaluation.result, None)
}
