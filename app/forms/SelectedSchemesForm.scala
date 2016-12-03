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
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages
import scala.language.implicitConversions

object SelectedSchemesForm {

  val NoDegree = "None"
  val Degree_21 = "Degree_21"
  val Degree_22 = "Degree_22"
  val Degree_21_PostGrad = "Degree_21_PostGrad"
  val Degree_DipServEconomics = "Degree_DipServEconomics"
  val Degree_Economics = "Degree_Economics"
  val Degree_Numerate = "Degree_Numerate"
  val Degree_GORS = "Degree_GORS"
  val Degree_SocialScience = "Degree_SocialScience"
  val Degree_CharteredEngineer = "Degree_CharteredEngineer"

  val VisibleSchemes = Seq(
    Scheme("Commercial", Degree_22, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("DigitalAndTechnology", Degree_21_PostGrad, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("DiplomaticService", Degree_22, specificRequirement = true, eligibilityForCivilServant = true),
    Scheme("DiplomaticServiceEconomics", Degree_DipServEconomics, specificRequirement = true, eligibilityForCivilServant = false),
    Scheme("DiplomaticServiceEuropean", Degree_22, specificRequirement = true, eligibilityForCivilServant = true),
    Scheme("European", Degree_22, specificRequirement = true, eligibilityForCivilServant = true),
    Scheme("Finance", Degree_21, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("Generalist", Degree_22, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("GovernmentCommunicationService", Degree_21, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("GovernmentEconomicsService", Degree_Economics, specificRequirement = true, eligibilityForCivilServant = false),
    Scheme("GovernmentOperationalResearchService", Degree_GORS, specificRequirement = true, eligibilityForCivilServant = false),
    Scheme("GovernmentSocialResearchService", Degree_SocialScience, specificRequirement = true, eligibilityForCivilServant = false),
    Scheme("GovernmentStatisticalService", Degree_Numerate, specificRequirement = true, eligibilityForCivilServant = true),
    Scheme("HousesOfParliament", Degree_22, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("HumanResources", Degree_22, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("ProjectDelivery", Degree_22, specificRequirement = false, eligibilityForCivilServant = true),
    Scheme("ScienceAndEngineering", Degree_CharteredEngineer, specificRequirement = true, eligibilityForCivilServant = true)
  )

  val HiddenSchemes = Seq(
    Scheme("Edip", NoDegree, specificRequirement = false, eligibilityForCivilServant = false)
  )

  val AllSchemes = VisibleSchemes ++ HiddenSchemes

  val AllSchemesMap = AllSchemes groupBy(_.id) mapValues (_.head)

  case class Scheme(id: String, qualification: String, specificRequirement: Boolean, eligibilityForCivilServant: Boolean)

  case class SchemePreferences(schemes: List[String], orderAgreed: Boolean, eligible: Boolean)

  implicit def toSchemePreferences(selectedSchemes: SelectedSchemes): SchemePreferences = SchemePreferences(
    selectedSchemes.schemes,
    selectedSchemes.orderAgreed,
    selectedSchemes.eligible
  )

  implicit def toSelectedSchemes(schemePreferences: SchemePreferences): SelectedSchemes = SelectedSchemes(
    schemePreferences.schemes,
    schemePreferences.orderAgreed,
    schemePreferences.eligible
  )

  def form = {
    Form(
      mapping(
        "schemes" -> of(schemeFormatter("schemes")),
        "orderAgreed" -> checked(Messages("orderAgreed.required")),
        "eligible" -> checked(Messages("eligible.required"))
      )(SchemePreferences.apply)(SchemePreferences.unapply))
  }

  def schemeFormatter(formKey: String) = new Formatter[List[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], List[String]] = {
      getSchemesByPriority(data) match {
        case selectedSchemes if selectedSchemes.isEmpty => Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes if selectedSchemes.size > AllSchemes.size => Left(List(FormError(formKey, Messages("schemes.required"))))
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
