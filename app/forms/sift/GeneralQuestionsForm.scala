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

package forms.sift

import connectors.exchange.sift.{ GeneralQuestionsAnswers, PostGradDegreeInfoAnswers, UndergradDegreeInfoAnswers }
import forms.Mappings._
import forms.sift.GeneralQuestionsForm.{ Countries, postGradDegreeInfoFormFormatter, undergradDegreeInfoFormFormatter }
import mappings.{ SeqMapping, Year }
import play.api.data.Forms._
import play.api.data.{ Form, FormError }
import play.api.data.format.Formatter
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

class UndergradDegreeInfoForm(classifications: Seq[String]) {
  val form = Form(
    mapping("undergradDegree.name" -> nonEmptyTrimmedText("undergradDegree.error.required", 300),
      "undergradDegree.classification" -> of(SeqMapping.requiredSetFormatter(classifications)),
      "undergradDegree.graduationYear" -> of(Year.yearFormatter),
      "undergradDegree.moduleDetails" -> optional(text)
    )(UndergradDegreeInfoForm.Data.apply)(UndergradDegreeInfoForm.Data.unapply))
}

object UndergradDegreeInfoForm {
  def apply() = new UndergradDegreeInfoForm(Classifications)

  val Classifications = Seq(
    "First-class honours",
    "Upper second-class honours",
    "Lower second-class honours",
    "Third-class honours",
    "Ordinary degree"
  )

  case class Data(
    name: String,
    classification: Option[String],
    graduationYear: Option[String],
    moduleDetails: Option[String]
  )
}

object PostGradDegreeInfoForm {
   val form = Form(
    mapping("postgradDegree.name" -> nonEmptyTrimmedText("postgradDegree.error.required", 400),
      "postgradDegree.graduationYear" -> of(Year.yearFormatter),
      "postgradDegree.otherDetails" -> optional(text),
      "postgradDegree.projectDetails" -> optional(text)
  )(Data.apply)(Data.unapply))

  case class Data(
    name: String,
    graduationYear: Option[String],
    otherDetails: Option[String],
    projectDetails: Option[String]
  )
}


class GeneralQuestionsForm(validCountries: Seq[String]) {
  val form = Form(
    mapping("multiplePassports" -> of(BooleanMapping.booleanFormatter("generalquestions.error.multiplepassports")),
      "secondPassportCountry" -> of(SeqMapping.conditionalRequiredSetFormatter(
        data => data.get("multiplePassports").getOrElse("") == "true", validCountries)),
      "passportCountry" -> of(SeqMapping.requiredSetFormatter(validCountries)),
      "hasUndergradDegree" -> of(BooleanMapping.booleanFormatter("generalquestions.error.undergraduatedegree")),
      "undergradDegree" -> of(undergradDegreeInfoFormFormatter("hasUndergradDegree")),
      "hasPostgradDegree" -> of(BooleanMapping.booleanFormatter("generalquestions.error.postgraduatedegree")),
      "postgradDegree" -> of(postGradDegreeInfoFormFormatter("hasPostgradDegree"))
    )(GeneralQuestionsForm.Data.apply)(GeneralQuestionsForm.Data.unapply))
}

object GeneralQuestionsForm {
  def apply() = new GeneralQuestionsForm(Countries)

  case class Data(
    multiplePassports: Option[Boolean],
    secondPassportCountry: Option[String],
    passportCountry: Option[String],
    hasUndergradDegree: Option[Boolean],
    undergradDegree: Option[UndergradDegreeInfoForm.Data],
    hasPostgradDegree: Option[Boolean],
    postgradDegree: Option[PostGradDegreeInfoForm.Data]
  )

  def undergradDegreeInfoFormFormatter(yesNo: String) = new Formatter[Option[UndergradDegreeInfoForm.Data]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[UndergradDegreeInfoForm.Data]] = {
      play.api.Logger.error(s"\n\n DATA \n $data")
      val requiredField: Option[String] = if (data.isEmpty) None else data.get(yesNo)

      requiredField match {
        case Some("true") =>
          UndergradDegreeInfoForm().form.mapping.bind(data) match {
            case Right(success) => Right(Some(success))
            case Left(error) => Left(error)
          }
        case _ => Right(None)
      }
    }

    override def unbind(key: String, fastPassData: Option[UndergradDegreeInfoForm.Data]): Map[String, String] =
      fastPassData.map(fpd => UndergradDegreeInfoForm().form.fill(fpd).data).getOrElse(Map(key -> ""))
  }

  def postGradDegreeInfoFormFormatter(yesNo: String) = new Formatter[Option[PostGradDegreeInfoForm.Data]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[PostGradDegreeInfoForm.Data]] = {
      val requiredField: Option[String] = if (data.isEmpty) None else data.get(yesNo)

      requiredField match {
        case Some("true") =>
          PostGradDegreeInfoForm.form.mapping.bind(data) match {
            case Right(success) => Right(Some(success))
            case Left(error) => Left(error)
          }
        case _ => Right(None)
      }
    }

    override def unbind(key: String, fastPassData: Option[PostGradDegreeInfoForm.Data]): Map[String, String] =
      fastPassData.map(fpd => PostGradDegreeInfoForm.form.fill(fpd).data).getOrElse(Map(key -> ""))
  }

   val Countries = Seq(
   "Afghanistan", "Albania", "Algeria", "Andorra", "Angola", "Antigua & Deps", "Argentina", "Armenia", "Australia", "Austria", "Azerbaijan",

   "Bahamas", "Bahrain", "Bangladesh", "Barbados", "Belarus", "Belgium", "Belize", "Benin", "Bhutan", "Bolivia", "Bosnia Herzegovina",
   "Botswana", "Brazil", "Brunei", "Bulgaria", "Burkina", "Burundi",

   "Cambodia", "Cameroon", "Canada", "Cape Verde", "Central African Rep", "Chad", "Chile", "China", "Colombia", "Comoros", "Congo",
   "Congo (Democratic Rep)", "Costa Rica", "Croatia", "Cuba", "Cyprus", "Czech Republic",

   "Denmark", "Djibouti", "Dominica", "Dominican Republic",

   "East Timor", "Ecuador", "Egypt", "El Salvador", "Equatorial Guinea", "Eritrea", "Estonia", "Ethiopia",

   "Fiji", "Finland", "France",

   "Gabon", "Gambia", "Georgia", "Germany", "Ghana", "Greece", "Grenada", "Guatemala", "Guinea", "Guinea-Bissau", "Guyana",

   "Haiti", "Honduras", "Hungary",

   "Iceland", "India", "Indonesia", "Iran", "Iraq", "Ireland (Republic)", "Israel", "Italy", "Ivory Coast",

   "Jamaica", "Japan", "Jordan",

   "Kazakhstan", "Kenya", "Kiribati", "Korea North", "Korea South", "Kosovo", "Kuwait", "Kyrgyzstan",

   "Laos", "Latvia", "Lebanon", "Lesotho", "Liberia", "Libya", "Liechtenstein", "Lithuania", "Luxembourg",

   "Macedonia", "Madagascar", "Malawi", "Malaysia", "Maldives", "Mali", "Malta", "Marshall Islands", "Mauritania", "Mauritius", "Mexico",
   "Micronesia", "Moldova", "Monaco", "Mongolia", "Montenegro", "Morocco", "Mozambique", "Myanmar, (Burma)",

   "Namibia", "Nauru", "Nepal", "Netherlands", "New Zealand", "Nicaragua", "Niger", "Nigeria", "Norway",

   "Oman",

   "Pakistan", "Palau", "Panama", "Papua New Guinea", "Paraguay", "Peru", "Philippines", "Poland", "Portugal",

   "Qatar",

   "Romania", "Russian Federation", "Rwanda",

   "St Kitts & Nevis", "St Lucia", "Saint Vincent & the Grenad", "Samoa", "San Marino", "Sao Tome & Principe", "Saudi Arabia", "Senegal",
   "Serbia", "Seychelles", "Sierra Leone", "Singapore", "Slovakia", "Slovenia", "Solomon Islands", "Somalia", "South Africa", "South Sudan",
   "Spain", "Sri Lanka", "Sudan", "Suriname", "Swaziland", "Sweden", "Switzerland", "Syria",

   "Taiwan", "Tajikistan", "Tanzania", "Thailand", "Togo", "Tonga", "Trinidad & Tobago", "Tunisia", "Turkey", "Turkmenistan", "Tuvalu",

   "Uganda", "Ukraine", "United Arab Emirates", "United Kingdom", "United States", "Uruguay", "Uzbekistan",

   "Vanuatu", "Vatican City", "Venezuela", "Vietnam",

   "Yemen",

   "Zambia", "Zimbabwe"
  )
}
