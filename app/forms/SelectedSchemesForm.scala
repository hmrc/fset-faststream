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

import connectors.exchange.SelectedSchemes
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import play.api.i18n.Messages

object SelectedSchemesForm {

  val Degree_21 = "Degree_21"
  val Degree_22 = "Degree_22"
  val Degree_Economics = "Degree_Economics"
  val Degree_Numerate = "Degree_Numerate"
  val Degree_SocialScience = "Degree_SocialScience"
  val Degree_CharteredEngineer = "Degree_CharteredEngineer"

  val AllSchemes = Seq(
    Scheme("CentralDepartments", Degree_22, specificRequirement = false),
    Scheme("Commercial", Degree_22, specificRequirement = false),
    Scheme("DigitalAndTechnology", Degree_21, specificRequirement = false),
    Scheme("DiplomaticService", Degree_22, specificRequirement = false),
    Scheme("European", Degree_22, specificRequirement = false),
    Scheme("Finance", Degree_21, specificRequirement = false),
    Scheme("GovernmentCommunicationService", Degree_21, specificRequirement = false),
    Scheme("GovernmentEconomicService", Degree_Economics, specificRequirement = true),
    Scheme("GovernmentOperationalResearchService", Degree_Numerate, specificRequirement = true),
    Scheme("GovernmentSocialResearchService", Degree_SocialScience, specificRequirement = true),
    Scheme("GovernmentStatisticalService", Degree_Numerate, specificRequirement = true),
    Scheme("HousesOfParliament", Degree_22, specificRequirement = false),
    Scheme("HumanResources", Degree_22, specificRequirement = false),
    Scheme("ProjectDelivery", Degree_22, specificRequirement = false),
    Scheme("ScienceAndEngineering", Degree_CharteredEngineer, specificRequirement = true),
    Scheme("Tax", Degree_22, specificRequirement = false)
  )

  val schemesMaxLimit = 5

  case class Scheme(id: String, qualification: String, specificRequirement: Boolean)

  case class SchemePreferences(schemes: List[String], orderAgreed: Boolean, eligible: Boolean, alternatives: String)

  implicit def toSchemePreferences(selectedSchemes: SelectedSchemes): SchemePreferences = SchemePreferences(
    selectedSchemes.schemes,
    selectedSchemes.orderAgreed,
    selectedSchemes.eligible,
    selectedSchemes.alternatives.toString
  )

  implicit def toSelectedSchemes(schemePreferences: SchemePreferences): SelectedSchemes = SelectedSchemes(
    schemePreferences.schemes,
    schemePreferences.orderAgreed,
    schemePreferences.eligible,
    schemePreferences.alternatives.toBoolean
  )

  def form = {
    Form(
      mapping(
        "schemes" -> of(schemeFormatter("schemes")),
        "orderAgreed" -> checked(Messages("orderAgreed.required")),
        "eligible" -> checked(Messages("eligible.required")),
        "alternatives" -> Mappings.nonEmptyTrimmedText("alternatives.required", 5)
          .verifying(Messages("alternatives.required"), boolValue => boolValue == true.toString || boolValue == false.toString)
      )(SchemePreferences.apply)(SchemePreferences.unapply))
  }

  def schemeFormatter(formKey: String) = new Formatter[List[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], List[String]] = {
      getSchemesByPriority(data) match {
        case selectedSchemes if selectedSchemes.isEmpty => Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes if selectedSchemes.size > schemesMaxLimit => Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes if getInvalidSchemes(selectedSchemes).nonEmpty => Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes => Right(selectedSchemes)
      }
    }

    def unbind(key: String, value: List[String]): Map[String, String] = {
      value.map(key => key -> Messages("scheme." + key + ".description")).toMap
    }
  }

  def getValidSchemesByPriority(formData: Map[String, String]) = {
    val selectedSchemes = getSchemesByPriority(formData)
    val invalidSchemes = getInvalidSchemes(selectedSchemes)
    selectedSchemes.filterNot(schemeId => invalidSchemes.contains(schemeId))
  }

  private val getInvalidSchemes = (selectedSchemes: List[String]) => selectedSchemes.diff(AllSchemes.map(_.id))

  private def getSchemesByPriority(formData: Map[String, String]) = {
    val validSchemeParams = (name: String, value: String) => name.startsWith("scheme_") && value.nonEmpty
    val priority: String => Int = _.split("_").last.toInt
    formData.filter(pair => validSchemeParams(pair._1, pair._2))
      .collect { case (name, value) => priority(name) -> value }
      .toList
      .sortBy {
        _._1
      }
      .map {
        _._2
      }
      .distinct
  }

}
