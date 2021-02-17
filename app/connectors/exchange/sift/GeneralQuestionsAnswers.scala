/*
 * Copyright 2021 HM Revenue & Customs
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

import forms.sift.{ GeneralQuestionsForm, PostGradDegreeInfoForm, UndergradDegreeInfoForm }
import play.api.libs.json.Json

case class UndergradDegreeInfoAnswers(
  name: String,
  classification: String,
  graduationYear: String,
  moduleDetails: Option[String]
)

object UndergradDegreeInfoAnswers {
  implicit val degreeInfoFormat = Json.format[UndergradDegreeInfoAnswers]

  def apply(a: UndergradDegreeInfoForm.Data): UndergradDegreeInfoAnswers = {
    UndergradDegreeInfoAnswers(
      a.name,
      a.classification.getOrElse(""),
      a.graduationYear.getOrElse(""),
      a.moduleDetails)
  }
}

case class PostGradDegreeInfoAnswers(
  name: String,
  graduationYear: String,
  otherDetails: Option[String],
  projectDetails: Option[String]
)

object PostGradDegreeInfoAnswers {
  implicit val postGradDegreeInfoAnswersFormat = Json.format[PostGradDegreeInfoAnswers]

  def apply(a: PostGradDegreeInfoForm.Data): PostGradDegreeInfoAnswers = {
    PostGradDegreeInfoAnswers(
      a.name,
      a.graduationYear.getOrElse(""),
      a.otherDetails,
      a.projectDetails
    )
  }
}

case class GeneralQuestionsAnswers(
  multipleNationalities: Boolean,
  secondNationality: Option[String],
  nationality: String,
  undergradDegree: Option[UndergradDegreeInfoAnswers],
  postgradDegree: Option[PostGradDegreeInfoAnswers]
)

object GeneralQuestionsAnswers {
  implicit val generalQuestionsAnswersFormat = Json.format[GeneralQuestionsAnswers]

  def apply(a: GeneralQuestionsForm.Data): GeneralQuestionsAnswers = {
    GeneralQuestionsAnswers(
      a.multipleNationalities.get,
      a.secondNationality,
      a.nationality.getOrElse(""),
      a.undergradDegree map (UndergradDegreeInfoAnswers(_)),
      a.postgradDegree map (PostGradDegreeInfoAnswers(_))
    )
  }
}
