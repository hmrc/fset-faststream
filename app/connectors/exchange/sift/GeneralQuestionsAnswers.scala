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

package connectors.exchange.sift

import play.api.libs.json.Json

case class DegreeInfoAnswers(
  name: String,
  classification: String,
  graduationYear: String,
  moduleDetails: String
)

object DegreeInfoAnswers {
  implicit val degreeInfoFormat= Json.format[DegreeInfoAnswers]
}

case class GeneralQuestionsAnswers(
  multiplePassports: Boolean,
  passportCountry: String,
  hasUndergradDegree: Option[DegreeInfoAnswers],
  hasPostgradDegree: Option[DegreeInfoAnswers]
)

object GeneralQuestionsAnswers {
  implicit val generalQuestionsAnswersFormat = Json.format[GeneralQuestionsAnswers]
}
