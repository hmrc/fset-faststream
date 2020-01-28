/*
 * Copyright 2020 HM Revenue & Customs
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
import mappings.PostCodeMapping._
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

object EducationQuestionnaireForm {

  def form(universityQuestionKey: String) = Form(
    mapping(
      "liveInUKBetween14and18" -> Mappings.nonEmptyTrimmedText("error.liveInUKBetween14and18.required", 31),
      "postcodeQ" -> of(requiredFormatterWithValidationCheckAndSeparatePreferNotToSay("liveInUKBetween14and18",
        "postcodeQ", "preferNotSay_postcodeQ", Some(256))
      (postCode => !postcodePattern.pattern.matcher(postCode).matches(), "error.postcodeQ.invalid")),
      "preferNotSay_postcodeQ" -> optional(checked(Messages("error.required.postcodeQ"))),
      "schoolName14to16" -> of(requiredFormatterWithValidationCheckAndSeparatePreferNotToSay("liveInUKBetween14and18",
        "schoolName14to16", "preferNotSay_schoolName14to16", Some(256))),
      "schoolId14to16" -> of(schoolIdFormatter("schoolName14to16")),
      "preferNotSay_schoolName14to16" -> optional(checked(Messages("error.required.schoolName14to16"))),
      "schoolType14to16" -> of(requiredFormatterWithMaxLengthCheck(
         "liveInUKBetween14and18", "schoolType14to16", Some(256)
      )),
      "schoolName16to18" -> of(requiredFormatterWithValidationCheckAndSeparatePreferNotToSay("liveInUKBetween14and18",
        "schoolName16to18", "preferNotSay_schoolName16to18", Some(256))),
      "schoolId16to18" -> of(schoolIdFormatter("schoolName16to18")),
      "preferNotSay_schoolName16to18" -> optional(checked(Messages("error.required.schoolName16to18"))),
      "freeSchoolMeals" -> of(requiredFormatterWithMaxLengthCheck("liveInUKBetween14and18", "freeSchoolMeals", Some(256))),
      "isCandidateCivilServant" -> Mappings.nonEmptyTrimmedText("error.isCandidateCivilServant.required", 31),
      "haveDegree" -> of(requiredFormatterWithMaxLengthCheck("isCandidateCivilServant", "haveDegree", Some(31))),
      "university" -> of(requiredFormatterWithValidationCheckAndSeparatePreferNotToSay("haveDegree",
        "universityQuestionKey", "preferNotSay_university", Some(256), Some(Messages(s"error.$universityQuestionKey.required")))),
      "preferNotSay_university" -> optional(checked(Messages(s"error.$universityQuestionKey.required"))),
      "universityDegreeCategory" -> of(requiredFormatterWithValidationCheckAndSeparatePreferNotToSay("haveDegree",
        "universityDegreeCategory", "preferNotSay_universityDegreeCategory", Some(256))),
      "preferNotSay_universityDegreeCategory" -> optional(checked(Messages("error.universityDegreeCategory.required")))
    )(Data.apply)(Data.unapply)
  )

  def schoolIdFormatter(schoolNameKey: String) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val schoolId = request.getOrElse(key, "")
      val schoolName = request.getOrElse(schoolNameKey, "")
      (schoolName.trim.nonEmpty, schoolId.trim.nonEmpty) match {
        case (true, true) => Right(Some(schoolId))
        case _ => Right(None)
      }
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  case class Data(
    liveInUKBetween14and18: String,
    postcode: Option[String],
    preferNotSayPostcode: Option[Boolean],
    schoolName14to16: Option[String],
    schoolId14to16: Option[String],
    preferNotSaySchoolName14to16: Option[Boolean],
    schoolType14to16: Option[String],
    schoolName16to18: Option[String],
    schoolId16to18: Option[String],
    preferNotSaySchoolName16to18: Option[Boolean],
    freeSchoolMeals: Option[String],
    isCandidateCivilServant: String,
    haveDegree: Option[String],
    university: Option[String],
    preferNotSayUniversity: Option[Boolean],
    universityDegreeCategory: Option[String],
    preferNotSayUniversityDegreeCategory: Option[Boolean]
  ) {


    def exchange(): Questionnaire = {
      def getAnswer(field: Option[String], preferNotToSayField: Option[Boolean], otherDetails: Option[String] = None) = {
        preferNotToSayField match {
          case Some(true) => Answer(None, otherDetails, Some(true))
          case _ => Answer(field, otherDetails, None)
        }
      }

      val freeSchoolMealAnswer = freeSchoolMeals match {
        case None | Some("I don't know/prefer not to say") => Answer(None, None, Some(true))
        case _ => Answer(freeSchoolMeals, None, None)
      }

      def getOptionalSchoolList = {
        if (liveInUKBetween14and18 == "Yes") {
          List(Question(Messages("postcode.question"), getAnswer(postcode, preferNotSayPostcode)),
            Question(Messages("schoolName14to16.question"), getAnswer(schoolName14to16, preferNotSaySchoolName14to16, schoolId14to16)),
            Question(Messages("schoolType14to16.question"), Answer(schoolType14to16, None, None)),
            Question(Messages("schoolName16to18.question"), getAnswer(schoolName16to18, preferNotSaySchoolName16to18, schoolId16to18)),
            Question(Messages("freeSchoolMeals.question"), freeSchoolMealAnswer))
        } else {
          List.empty
        }
      }

      def getOptionalUniversityList: List[Question] = {
        haveDegree match {
          case Some("Yes") => List(
            Question(Messages("university.question"), getAnswer(university, preferNotSayUniversity)),
            Question(Messages("universityDegreeCategory.question"), getAnswer(universityDegreeCategory,
              preferNotSayUniversityDegreeCategory))
          )
          case _ => List.empty
        }
      }

      Questionnaire(
        List(Question(Messages("liveInUKBetween14and18.question"), Answer(Some(liveInUKBetween14and18), None, None))) ++
          getOptionalSchoolList ++
          List(Question(Messages("haveDegree.question"), getAnswer(haveDegree, None))) ++
          getOptionalUniversityList
      )
    }

    /** It makes sure that when you select "No" as an answer to "live in the UK between 14 and 18" question, the dependent
      * questions are reset to None.
      *
      * This is a kind of backend partial clearing form functionality.
      */
    def sanitizeData = {
      sanitizeLiveInUK.sanitizeUniversity
    }

    private def sanitizeLiveInUK = {
      if (liveInUKBetween14and18 == "Yes") {
        this.copy(
          postcode = sanitizeValueWithPreferNotToSay(postcode, preferNotSayPostcode),
          schoolName14to16 = sanitizeValueWithPreferNotToSay(schoolName14to16, preferNotSaySchoolName14to16),
          schoolType14to16 = schoolType14to16,
          schoolName16to18 = sanitizeValueWithPreferNotToSay(schoolName16to18, preferNotSaySchoolName16to18)
        )
      } else {
        this.copy(
          postcode = None,
          preferNotSayPostcode = None,
          schoolName14to16 = None,
          schoolType14to16 = None,
          schoolName16to18 = None,
          preferNotSaySchoolName16to18 = None,
          freeSchoolMeals = None)
      }
    }

    private def sanitizeUniversity = {
      if (haveDegree.contains("Yes")) {
        this.copy(
          university = sanitizeValueWithPreferNotToSay(university, preferNotSayUniversity),
          universityDegreeCategory = sanitizeValueWithPreferNotToSay(universityDegreeCategory, preferNotSayUniversityDegreeCategory)
        )
      } else {
        this.copy(
          university = None,
          preferNotSayUniversity = None,
          universityDegreeCategory = None,
          preferNotSayUniversityDegreeCategory = None)
      }
    }

    private def sanitizeValueWithPreferNotToSay(value: Option[String], preferNotToSayValue: Option[Boolean]): Option[String] = {
      preferNotToSayValue match {
        case Some(true) => None
        case _ => value
      }
    }
  }
}
