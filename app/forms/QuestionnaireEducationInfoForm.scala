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

object QuestionnaireEducationInfoForm {

  val form = Form(
    mapping(
      "schoolName" -> of(Mappings.fieldWithCheckBox(256)),
      "preferNotSay_schoolName" -> optional(checked(Messages("error.required.schoolName"))),
      "sixthForm" -> of(Mappings.fieldWithCheckBox(256)),
      "preferNotSay_sixthForm" -> optional(checked(Messages("error.required.sixthForm"))),
      "postcodeQ" -> of(Mappings.fieldWithCheckBox(256)),
      "preferNotSay_postcodeQ" -> optional(checked(Messages("error.required.postcodeQ"))),
      "freeSchoolMeals" -> Mappings.nonEmptyTrimmedText("error.required.freeSchoolMeals", 256),
      "university" -> Mappings.nonEmptyTrimmedText("error.required.university", 256)
    )(Data.apply)(Data.unapply)
  )

  case class Data(
    schoolName: Option[String],
    preferNotSaySchoolName: Option[Boolean],
    sixthForm: Option[String],
    preferNotSaySixthForm: Option[Boolean],
    postcode: Option[String],
    preferNotSayPostcode: Option[Boolean],
    freeSchoolMeals: String,
    university: String
  ) {
    def toQuestionnaire: Questionnaire = {
      val freeSchoolMealAnswer = freeSchoolMeals match {
        case "Unknown/prefer not to say" => Answer(None, None, Some(true))
        case _ => Answer(Some(freeSchoolMeals).sanitize, None, None)
      }

      val universityAnswer = university match {
        case "Unknown/prefer not to say" => Answer(None, None, Some(true))
        case _ => Answer(Some(university).sanitize, None, None)
      }
      Questionnaire(List(
        Question(Messages("schoolName.question"), Answer(schoolName, None, preferNotSaySchoolName)),
        Question(Messages("sixthForm.question"), Answer(sixthForm, None, preferNotSaySixthForm)),
        Question(Messages("postcode.question"), Answer(postcode, None, preferNotSayPostcode)),
        Question(Messages("freeSchoolMeals.question"), freeSchoolMealAnswer),
        Question(Messages("university.question"), universityAnswer)
      ))
    }
  }

}
