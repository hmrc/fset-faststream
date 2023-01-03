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

package model.exchange

import model.SchemeId
import play.api.libs.json.{ Format, Json }

case class ApplicationResult(applicationId: String, result: String)

object ApplicationResult {
  implicit val format = Json.format[ApplicationResult]
}

case class FsbEvaluationResults(schemeId: SchemeId, applicationResults: List[ApplicationResult])

object FsbEvaluationResults {
  implicit val format = Json.format[FsbEvaluationResults]
}

case class FsbScoresAndFeedback(
  overallScore: Double,
  feedback: String
)

object FsbScoresAndFeedback {
  implicit val jsonFormat: Format[FsbScoresAndFeedback] = Json.format[FsbScoresAndFeedback]
}
