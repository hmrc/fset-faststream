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

package forms

import connectors.exchange.{ Answer, Question, Questionnaire }
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages

object DiversityQuestionnaireForm {

  val form = Form(
    mapping(
      "gender" -> Mappings.nonEmptyTrimmedText("error.required.gender", 256),
      "other_gender" -> optional(Mappings.nonEmptyTrimmedText("error.required.gender", 256)),

      "sexOrientation" -> Mappings.nonEmptyTrimmedText("error.required.sexOrientation", 256),
      "other_sexOrientation" -> optional(Mappings.nonEmptyTrimmedText("error.required.sexOrientation", 256)),

      "ethnicity" -> of(Mappings.fieldWithCheckBox(256)),
      "other_ethnicity" -> optional(Mappings.nonEmptyTrimmedText("error.required.ethnicity", 256)),
      "preferNotSay_ethnicity" -> optional(checked(Messages("error.required.ethnicity")))
    )(Data.apply)(Data.unapply)
  )

  val acceptanceForm = Form(
    mapping(
      "accept-terms" -> checked(Messages("error.required.acceptance"))
    )(AcceptanceTerms.apply)(AcceptanceTerms.unapply)
  )

  case class Data(
                   gender: String,
                   otherGender: Option[String],
                   sexOrientation: String,
                   otherSexOrientation: Option[String],
                   ethnicity: Option[String],
                   otherEthnicity: Option[String],
                   preferNotSayEthnicity: Option[Boolean]
                 ) {
    def exchange: Questionnaire = Questionnaire(List(
      Question(Messages("gender.question"), Answer(Some(gender), otherGender, None)),
      Question(Messages("sexOrientation.question"), Answer(Some(sexOrientation), otherSexOrientation, None)),
      Question(Messages("ethnicity.question"), Answer(ethnicity, otherEthnicity, preferNotSayEthnicity))
    ))
  }

  case class AcceptanceTerms(acceptTerms: Boolean) {
    def toQuestionnaire: Questionnaire = {
      val answer = if (acceptTerms) Some("Yes") else Some("No")
      Questionnaire(List(
        Question(Messages("accept-terms.question"), Answer(answer, None, None)
        )))
    }
  }
}
