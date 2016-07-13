/*
 * Copyright 2016 HM Revenue & Customs
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

import connectors.ExchangeObjects.{ Answer, Question, Questionnaire }
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages

object QuestionnaireDiversityInfoForm {

  val form = Form(
    mapping(
      "gender" -> of(Mappings.fieldWithCheckBox(256)),
      "other_gender" -> optional(Mappings.nonEmptyTrimmedText("error.required.gender", 256)),
      "preferNotSay_gender" -> optional(checked(Messages("error.required.gender"))),

      "sexOrientation" -> of(Mappings.fieldWithCheckBox(256)),
      "other_sexOrientation" -> optional(Mappings.nonEmptyTrimmedText("error.required.sexOrientation", 256)),
      "preferNotSay_sexOrientation" -> optional(checked(Messages("error.required.sexOrientation"))),

      "ethnicity" -> of(Mappings.fieldWithCheckBox(256)),
      "other_ethnicity" -> optional(Mappings.nonEmptyTrimmedText("error.required.ethnicity", 256)),
      "preferNotSay_ethnicity" -> optional(checked(Messages("error.required.ethnicity")))
    )(Data.apply)(Data.unapply)
  )

  case class Data(
    gender: Option[String],
    otherGender: Option[String],
    preferNotSayGender: Option[Boolean],
    sexOrientation: Option[String],
    otherSexOrientation: Option[String],
    preferNotSaySexOrientation: Option[Boolean],
    ethnicity: Option[String],
    otherEthnicity: Option[String],
    preferNotSayEthnicity: Option[Boolean]
  ) {
    def toQuestionnaire: Questionnaire = Questionnaire(List(
      Question(Messages("gender.question"), Answer(gender, otherGender, preferNotSayGender)),
      Question(Messages("sexOrientation.question"), Answer(sexOrientation, otherSexOrientation, preferNotSaySexOrientation)),
      Question(Messages("ethnicity.question"), Answer(ethnicity, otherEthnicity, preferNotSayEthnicity))
    ))
  }

}
