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

import connectors.exchange.{ Answer, Question, Questionnaire }
import models.view.questionnaire.{ Ethnicities, Genders, SexOrientations }
import play.api.data.{ Form, FormError }
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.api.data.format.Formatter

object DiversityQuestionnaireForm {

  val OtherMaxSize = 256
  val form = Form(
    mapping(
      "gender" -> of(genderFormatter),
      "other_gender" -> optional(Mappings.nonEmptyTrimmedText("error.required.gender", OtherMaxSize)),

      "sexOrientation" -> of(sexOrientationFormatter),
      "other_sexOrientation" -> optional(Mappings.nonEmptyTrimmedText("error.required.sexOrientation", OtherMaxSize)),

      "ethnicity" -> of(ethnicityFormatter),
      "other_ethnicity" -> optional(Mappings.nonEmptyTrimmedText("error.required.ethnicity", OtherMaxSize)),
      "preferNotSay_ethnicity" -> optional(checked(Messages("error.required.ethnicity"))),

      "isEnglishFirstLanguage" -> of(englishLanguageFormatter)
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
                   preferNotSayEthnicity: Option[Boolean],
                   isEnglishFirstLanguage: String
                 ) {
    def exchange: Questionnaire = Questionnaire(List(
      Question(Messages("gender.question"), Answer(Some(gender), otherGender, unknown = None)),
      Question(Messages("sexOrientation.question"), Answer(Some(sexOrientation), otherSexOrientation, unknown = None)),
      Question(Messages("ethnicity.question"), Answer(ethnicity, otherEthnicity, preferNotSayEthnicity)),
      Question(Messages("language.question"), Answer(Some(isEnglishFirstLanguage), otherDetails = None, unknown = None))
    ))
  }

  case class AcceptanceTerms(acceptTerms: Boolean) {
    def toQuestionnaire: Questionnaire = {
      val answer = if (acceptTerms) Some("Yes") else Some("No")
      Questionnaire(List(
        Question(Messages("accept-terms.question"), Answer(answer, otherDetails = None, unknown = None))
      ))
    }
  }

  private def bindParam[T](validityCheck: Boolean, errMsg: String, key: String, value: => T): Either[Seq[FormError], T] =
    if (validityCheck) {
      Right(value)
    } else {
      Left(List(FormError(key, errMsg)))
    }

  private def genderFormatter = new Formatter[String] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], String] =
      bindParam(request.isGenderValid, "error.required.gender", key, request.genderParam)

    def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  private def sexOrientationFormatter = new Formatter[String] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], String] =
      bindParam(request.isSexOrientationValid, "error.required.sexOrientation", key, request.sexOrientationParam)

    def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  private def ethnicityFormatter = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val preferNotToSayField = request.preferNotSayEthnicityParam.sanitize
      val isEthnicityValid = request.isEthnicityValid

      (preferNotToSayField, isEthnicityValid) match {
        case (Some(_), _) => Right(None)
        case (_, true) => Right(Some(request.ethnicityParam))
        case (_, false) => Left(List(FormError(key, Messages("error.required.ethnicity"))))
        case _ => Right(Some(request.ethnicityParam))
      }
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  private def englishLanguageFormatter = new Formatter[String] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], String] =
      bindParam(request.isEnglishLanguageValid, "error.required.englishLanguage", key, request.englishLanguageParam)

    def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  implicit class RequestValidation(request: Map[String, String]) {
    def param(name: String) = request.collectFirst { case (key, value) if key == name => value }

    def genderParam = param("gender").getOrElse("")
    val validGenderOptions = Genders.list.map{ case (_, value, _) => value }
    def isGenderValid = validGenderOptions.contains(genderParam)

    def sexOrientationParam = param("sexOrientation").getOrElse("")
    val validSexOrientationOptions = SexOrientations.list.map{ case (_, value, _) => value }
    def isSexOrientationValid = validSexOrientationOptions.contains(sexOrientationParam)

    def ethnicityParam = param("ethnicity").getOrElse("")

    val validEthnicityOptions =
      Ethnicities.map.values.flatMap{ list: List[(String, Boolean)] =>
        list.map { case (ethnicity, _) => ethnicity }
      }.toList

    def isEthnicityValid = validEthnicityOptions.contains(ethnicityParam)

    def preferNotSayEthnicityParam = param("preferNotSay_ethnicity")

    def englishLanguageParam = param("isEnglishFirstLanguage").getOrElse("")
    val validEnglishLanguageOptions = Seq("Yes", "No", "I don't know/prefer not to say")
    def isEnglishLanguageValid = validEnglishLanguageOptions.contains(englishLanguageParam)
  }
}
