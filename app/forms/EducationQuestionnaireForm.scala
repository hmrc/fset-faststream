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

import connectors.exchange._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages

object EducationQuestionnaireForm {

  val form = Form(
    mapping(
      "liveInUKBetween14and18" -> Mappings.nonEmptyTrimmedText("error.liveInUKBetween14and18.required", 31),
      "postcodeQ" -> of(requiredFormatterWithMaxLengthCheckAndSeparatePreferNotToSay("liveInUKBetween14and18",
        "postcodeQ", "preferNotSay_postcodeQ", Some(256))),
      "preferNotSay_postcodeQ" -> optional(checked(Messages("error.required.postcodeQ"))),
      "schoolName14to16" -> of(requiredFormatterWithMaxLengthCheckAndSeparatePreferNotToSay("liveInUKBetween14and18",
        "schoolName14to16", "preferNotSay_schoolName14to16", Some(256))),
      "preferNotSay_schoolName14to16" -> optional(checked(Messages("error.required.schoolName14to16"))),
      "schoolName16to18" -> of(requiredFormatterWithMaxLengthCheckAndSeparatePreferNotToSay("liveInUKBetween14and18",
        "schoolName16to18", "preferNotSay_schoolName14to16", Some(256))),
      "preferNotSay_schoolName16to18" -> optional(checked(Messages("error.required.schoolName16to18"))),
      "freeSchoolMeals" -> of(requiredFormatterWithMaxLengthCheck("liveInUKBetween14and18", "freeSchoolMeals", Some(256))),

      "university" -> of(Mappings.fieldWithCheckBox(256)),
      "preferNotSay_university" -> optional(checked(Messages("error.required.university"))),
      "universityDegreeCategory" -> of(Mappings.fieldWithCheckBox(256)),
      "preferNotSay_universityDegreeCategory" -> optional(checked(Messages("error.required.university")))
    )(Data.apply)(Data.unapply)
  )

  case class Data(
                   liveInUKBetween14and18: String,
                   postcode: Option[String],
                   preferNotSayPostcode: Option[Boolean],
                   schoolName14to16: Option[String],
                   preferNotSaySchoolName14to16: Option[Boolean],
                   schoolName16to18: Option[String],
                   preferNotSaySchoolName16to18: Option[Boolean],
                   freeSchoolMeals: Option[String],

                   university: Option[String],
                   preferNotSayUniversity: Option[Boolean],
                   universityDegreeCategory: Option[String],
                   preferNotSayUniversityDegreeCategory: Option[Boolean]
                 ) {


    def exchange: Questionnaire = {
      def getAnswer(field: Option[String], preferNotToSayField: Option[Boolean]) = {
        preferNotToSayField match {
          case Some(true) => Answer(None, None, Some(true))
          case _ => Answer(field, None, None)
        }
      }

      val freeSchoolMealAnswer = freeSchoolMeals match {
        case None | Some("I don't know/prefer not to say") => Answer(None, None, Some(true))
        case _ => Answer(freeSchoolMeals, None, None)
      }

      def getOptionalList() = {
        (if (liveInUKBetween14and18 == "Yes") {
          List(Question(Messages("postcode.question"), getAnswer(postcode, preferNotSayPostcode)),
            Question(Messages("schoolName14to16.question"), getAnswer(schoolName14to16, preferNotSaySchoolName14to16)),
            Question(Messages("schoolName16to18.question"), getAnswer(schoolName16to18, preferNotSaySchoolName16to18)),
            Question(Messages("freeSchoolMeals.question"), freeSchoolMealAnswer))
        } else {
          List.empty
        })
      }

      Questionnaire(
        List(Question(Messages("liveInUKBetween14and18.question"), Answer(Some(liveInUKBetween14and18), None, None))) ++
          getOptionalList() ++
          List(
            Question(Messages("university.question"), getAnswer(university, preferNotSayUniversity)),
            Question(Messages("universityDegreeCategory.question"), getAnswer(universityDegreeCategory, preferNotSayUniversityDegreeCategory))
          )
      )
    }

    /** It makes sure that when you select "No" as an answer to "live in uk between 14 and 18" question, the dependent
      * questions are resetted to None.
      *
      * This is a kind of backend partial clearing form functionality.
      */
    def sanitizeData = {
      if (liveInUKBetween14and18 == "Yes") {
        this
      } else {
        this.copy(
          postcode = None,
          preferNotSayPostcode = None,
          schoolName14to16 = None,
          preferNotSaySchoolName14to16 = None,
          schoolName16to18 = None,
          preferNotSaySchoolName16to18 = None,
          freeSchoolMeals = None)
      }
    }
  }

}
