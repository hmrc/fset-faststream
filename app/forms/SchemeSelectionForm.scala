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


//scalastyle:off
object SchemeSelectionForm {
  val AllSchemes = Seq(
    "CentralDepartments" -> "Central Departments",
    "Commercial" -> "Commercial",
    "DigitalAndTechnology" -> "Digital and Technology",
    "DiplomaticService" -> "Diplomatic Service",
    "European" -> "European",
    "GovernmentCommunicationService" -> "Government Communication Service",
    "GovernmentEconomicService" -> "Government Economic Service",
    "GovernmentOperationalResearchService" -> "Government Operational Research Service",
    "GovernmentSocialResearchService" -> "Government Social Research Service",
    "GovernmentStatisticalService" -> "Government Statistical Service",
    "HousesOfParliament" -> "Houses of Parliament",
    "HumanResources" -> "Human Resources",
    "ProjectDelivery" -> "Project Delivery",
    "ScienceAndEngineering" -> "Science and Engineering",
    "Tax" -> "Tax"
  )

  def form = {
    Form(
      mapping(
        "schemes" -> of(schemeFormatter("schemes")),
        "happy" -> Mappings.nonEmptyTrimmedText("error.required.happy", 256),
        "eligible" -> Mappings.nonEmptyTrimmedText("error.required.eligible", 256),
        "alternatives" -> Mappings.nonEmptyTrimmedText("error.required.alternatives", 256)
      )(SchemePreference.apply)(SchemePreference.unapply))
  }

  case class SchemePreference(selectedSchemes: List[String] = Nil, happy:String="", eligible:String = "", alternatives:String = "")

  val EmptyData = SchemePreference()

  def schemeFormatter(formKey: String) = new Formatter[List[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], List[String]] = {
      val selectedSchemes = data.filterKeys(_.contains("schemes"))
      selectedSchemes match {
        case selSchemes if selSchemes.isEmpty => Left(List(FormError("schemes", Messages("error.noSchemesSelected"))))
        case _ => Right(selectedSchemes.values.toList)
      }
    }

    def unbind(key: String, value: List[String]): Map[String, String] = {
      value.map(key => key -> AllSchemes.toMap.getOrElse(key,"")).toMap
    }
  }

}
