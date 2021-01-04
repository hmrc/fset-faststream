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

package forms.sift

import forms.sift.GeneralQuestionsForm.{postGradDegreeInfoFormFormatter, undergradDegreeInfoFormFormatter}
import mappings.Mappings._
import mappings.SeqMapping._
import mappings.Year
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import play.api.i18n.Messages

class UndergradDegreeInfoForm(classifications: Seq[String]) {

  def form(implicit messages: Messages) = Form(
    mapping("undergradDegree.name" -> nonEmptyTrimmedText("undergradDegree.error.required", 300),
      "undergradDegree.classification" -> of(requiredSetFormatter(classifications)),
      "undergradDegree.graduationYear" -> of(Year.yearFormatter),
      "undergradDegree.moduleDetails" -> optional(text)
    )(UndergradDegreeInfoForm.Data.apply)(UndergradDegreeInfoForm.Data.unapply))
}

object UndergradDegreeInfoForm {
  def apply() =
    new UndergradDegreeInfoForm(Classifications)

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
   def form() = {
     Form(
       mapping("postgradDegree.name" -> nonEmptyTrimmedText("postgradDegree.error.required", 400),
         "postgradDegree.graduationYear" -> of(Year.yearFormatter),
         "postgradDegree.otherDetails" -> optional(text),
         "postgradDegree.projectDetails" -> optional(text)
       )(Data.apply)(Data.unapply))
   }

  case class Data(
    name: String,
    graduationYear: Option[String],
    otherDetails: Option[String],
    projectDetails: Option[String]
  )
}

class GeneralQuestionsForm(validNationalities: Seq[String]) {
  def form(implicit messages: Messages) = Form(
    mapping("multipleNationalities" -> of(BooleanMapping.booleanFormatter("generalquestions.error.multiplenationalities")),
      "secondNationality" -> of(conditionalRequiredSetFormatter(
        data => data.getOrElse("multipleNationalities", "") == "true", validNationalities)),
      "nationality" -> of(requiredSetFormatter(validNationalities)),
      "hasUndergradDegree" -> of(BooleanMapping.booleanFormatter("generalquestions.error.undergraduatedegree")),
      "undergradDegree" -> of(undergradDegreeInfoFormFormatter("hasUndergradDegree")),
      "hasPostgradDegree" -> of(BooleanMapping.booleanFormatter("generalquestions.error.postgraduatedegree")),
      "postgradDegree" -> of(postGradDegreeInfoFormFormatter("hasPostgradDegree"))
    )(GeneralQuestionsForm.Data.apply)(GeneralQuestionsForm.Data.unapply))
}

object GeneralQuestionsForm {
  def apply() = new GeneralQuestionsForm(Nationalities)

  case class Data(
    multipleNationalities: Option[Boolean],
    secondNationality: Option[String],
    nationality: Option[String],
    hasUndergradDegree: Option[Boolean],
    undergradDegree: Option[UndergradDegreeInfoForm.Data],
    hasPostgradDegree: Option[Boolean],
    postgradDegree: Option[PostGradDegreeInfoForm.Data]
  )

  def undergradDegreeInfoFormFormatter(yesNo: String)(implicit messages: Messages) = new Formatter[Option[UndergradDegreeInfoForm.Data]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[UndergradDegreeInfoForm.Data]] = {
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

   val Nationalities = Seq(
    "Afghan",
    "Albanian",
    "Algerian",
    "American",
    "Andorran",
    "Angolan",
    "Antiguans",
    "Argentinean",
    "Armenian",
    "Australian",
    "Austrian",
    "Azerbaijani",
    "Bahamian",
    "Bahraini",
    "Bangladeshi",
    "Barbadian",
    "Barbudans",
    "Batswana",
    "Belarusian",
    "Belgian",
    "Belizean",
    "Beninese",
    "Bhutanese",
    "Bolivian",
    "Bosnian",
    "Brazilian",
    "British",
    "Bruneian",
    "Bulgarian",
    "Burkinabe",
    "Burmese",
    "Burundian",
    "Cambodian",
    "Cameroonian",
    "Canadian",
    "Cape Verdean",
    "Central African",
    "Chadian",
    "Chilean",
    "Chinese",
    "Colombian",
    "Comoran",
    "Congolese",
    "Costa Rican",
    "Croatian",
    "Cuban",
    "Cypriot",
    "Czech",
    "Danish",
    "Djibouti",
    "Dominican",
    "Dutch",
    "East Timorese",
    "Ecuadorean",
    "Egyptian",
    "Emirian",
    "Equatorial Guinean",
    "Eritrean",
    "Estonian",
    "Ethiopian",
    "Fijian",
    "Filipino",
    "Finnish",
    "French",
    "Gabonese",
    "Gambian",
    "Georgian",
    "German",
    "Ghanaian",
    "Greek",
    "Grenadian",
    "Guatemalan",
    "Guinea-Bissauan",
    "Guinean",
    "Guyanese",
    "Haitian",
    "Herzegovinian",
    "Honduran",
    "Hungarian",
    "I-Kiribati",
    "Icelander",
    "Indian",
    "Indonesian",
    "Iranian",
    "Iraqi",
    "Irish",
    "Israeli",
    "Italian",
    "Ivorian",
    "Jamaican",
    "Japanese",
    "Jordanian",
    "Kazakhstani",
    "Kenyan",
    "Kittian and Nevisian",
    "Kuwaiti",
    "Kyrgyz",
    "Laotian",
    "Latvian",
    "Lebanese",
    "Liberian",
    "Libyan",
    "Liechtensteiner",
    "Lithuanian",
    "Luxembourger",
    "Macedonian",
    "Malagasy",
    "Malawian",
    "Malaysian",
    "Maldivian",
    "Malian",
    "Maltese",
    "Marshallese",
    "Mauritanian",
    "Mauritian",
    "Mexican",
    "Micronesian",
    "Moldovan",
    "Monacan",
    "Mongolian",
    "Moroccan",
    "Mosotho",
    "Motswana",
    "Mozambican",
    "Namibian",
    "Nauruan",
    "Nepalese",
    "New Zealander",
    "Ni-Vanuatu",
    "Nicaraguan",
    "Nigerian",
    "Nigerien",
    "North Korean",
    "Northern Irish",
    "Norwegian",
    "Omani",
    "Pakistani",
    "Palauan",
    "Panamanian",
    "Papua New Guinean",
    "Paraguayan",
    "Peruvian",
    "Polish",
    "Portuguese",
    "Qatari",
    "Romanian",
    "Russian",
    "Rwandan",
    "Saint Lucian",
    "Salvadoran",
    "Samoan",
    "San Marinese",
    "Sao Tomean",
    "Saudi",
    "Scottish",
    "Senegalese",
    "Serbian",
    "Seychellois",
    "Sierra Leonean",
    "Singaporean",
    "Slovakian",
    "Slovenian",
    "Solomon Islander",
    "Somali",
    "South African",
    "South Korean",
    "Spanish",
    "Sri Lankan",
    "Sudanese",
    "Surinamer",
    "Swazi",
    "Swedish",
    "Swiss",
    "Syrian",
    "Taiwanese",
    "Tajik",
    "Tanzanian",
    "Thai",
    "Togolese",
    "Tongan",
    "Trinidadian or Tobagonian",
    "Tunisian",
    "Turkish",
    "Tuvaluan",
    "Ugandan",
    "Ukrainian",
    "Uruguayan",
    "Uzbekistani",
    "Venezuelan",
    "Vietnamese",
    "Welsh",
    "Yemenite",
    "Zambian",
    "Zimbabwean"
  )
}
