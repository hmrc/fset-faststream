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

import forms.sift.GeneralQuestionsForm
import play.api.libs.json.Json

case class UndergradDegreeInfoAnswers(
  name: String,
  classification: String,
  graduationYear: String,
  moduleDetails: Option[String]
)

object UndergradDegreeInfoAnswers {
  implicit val degreeInfoFormat= Json.format[UndergradDegreeInfoAnswers]
}

case class PostGradDegreeInfoAnswers(
  name: String,
  graduationYear: String,
  otherDetails: Option[String],
  projectDetails: Option[String]
)

object PostGradDegreeInfoAnswers {
  implicit val postGradDegreeInfoAnswersFormat = Json.format[PostGradDegreeInfoAnswers]
}

case class GeneralQuestionsAnswers(
  multiplePassports: Boolean,
  secondPassportCountry: Option[String],
  passportCountry: String,
  undergradDegree: Option[UndergradDegreeInfoAnswers],
  postgradDegree: Option[PostGradDegreeInfoAnswers]
)

object GeneralQuestionsAnswers {
  implicit val generalQuestionsAnswersFormat = Json.format[GeneralQuestionsAnswers]

  def apply(a: GeneralQuestionsForm.Data): GeneralQuestionsAnswers = {
    GeneralQuestionsAnswers(
      a.multiplePassports,
      a.secondPassportCountry,
      a.passportCountry.getOrElse(""),
      a.undergradDegree,
      a.postgradDegree
    )
  }
}
