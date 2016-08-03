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

import play.api.data.{Form, FormError}
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.i18n.Messages
import play.api.libs.json._

object SelectedSchemesForm {

  val Degree_21 = "Degree_21"
  val Degree_22 = "Degree_22"
  val Degree_Economics = "Degree_Economics"
  val Degree_Numerate = "Degree_Numerate"
  val Degree_SocialScience = "Degree_SocialScience"
  val Degree_CharteredEngineer = "Degree_CharteredEngineer"

  case class Scheme(schemeId: String, qualification: String, specificRequirement: Boolean)

  object Scheme {
    implicit val schemeFormat = Json.format[Scheme]
  }

  case class SelectedSchemes(schemes: List[String] = Nil,
                             orderAgreed: String = "", eligible: String = "", alternatives: String = "")

  object SelectedSchemes {
    implicit val selectedSchemesFormat = Json.format[SelectedSchemes]
  }

  case class SchemePreferences(selectedSchemes: Option[SelectedSchemes])

  object SchemePreferences {
    implicit val schemePreferencesFormat = Json.format[SchemePreferences]
  }

  def form = {
    Form(
      mapping(
        "schemes" -> of(schemeFormatter("schemes")),
        "orderAgreed" -> Mappings.nonEmptyTrimmedText("error.required.orderAgreed", 256),
        "eligible" -> Mappings.nonEmptyTrimmedText("error.required.eligible", 256),
        "alternatives" -> Mappings.nonEmptyTrimmedText("error.required.alternatives", 256)
      )(SelectedSchemes.apply)(SelectedSchemes.unapply))
  }

  val EmptyData = SelectedSchemes()

  val AllSchemes = Seq(
    Scheme("CentralDepartments", Degree_22, specificRequirement=false),
    Scheme("Commercial",Degree_22, specificRequirement=false),
    Scheme("DigitalAndTechnology",Degree_21, specificRequirement=false),
    Scheme("DiplomaticService",Degree_22, specificRequirement=false),
    Scheme("European",Degree_22, specificRequirement=false),
    Scheme("Finance",Degree_21, specificRequirement=false),
    Scheme("GovernmentCommunicationService",Degree_21, specificRequirement=false),
    Scheme("GovernmentEconomicService",Degree_Economics, specificRequirement=true),
    Scheme("GovernmentOperationalResearchService", Degree_Numerate, specificRequirement=true),
    Scheme("GovernmentSocialResearchService", Degree_SocialScience, specificRequirement=true),
    Scheme("GovernmentStatisticalService", Degree_Numerate, specificRequirement=true),
    Scheme("HousesOfParliament", Degree_22, specificRequirement=false),
    Scheme("HumanResources", Degree_22, specificRequirement=false),
    Scheme("ProjectDelivery", Degree_22, specificRequirement=false),
    Scheme("ScienceAndEngineering", Degree_CharteredEngineer, specificRequirement=true),
    Scheme("Tax" ,Degree_22, specificRequirement=false)
  )


  def schemeFormatter(formKey: String) = new Formatter[List[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], List[String]] = {
      val priority: String => Int = _.split("_").last.toInt
      val schemesByPriority = data.filterKeys(_.contains("scheme_"))
        .map{case (name, value) => priority(name) -> value}.toSeq
        .sorted
        .map{_._2}
      schemesByPriority match {
        case selSchemes if selSchemes.isEmpty => Left(List(FormError("schemes", Messages("error.noSchemesSelected"))))
        case _ => Right(schemesByPriority.toList)
      }
    }

    def unbind(key: String, value: List[String]): Map[String, String] = {
      value.map(key => key -> Messages("scheme." + key + ".description")).toMap
    }
  }

}
