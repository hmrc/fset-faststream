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


object SchemeSelectionForm {

  val qua_degree2_1 = "2:1 degree in any subject "
  val qua_degree2_2 = "2:2 degree in any subject "
  val qua_economics = "2:1 degree in economics or a 2:2 with a postgraduate qualification in economics "
  val qua_numerical = "2:1 degree in a numerate subject or a 2:2 with a relevant postgraduate qualification "
  val qua_socialScience = "2:1 degree in social science or a 2:2 with a postgraduate qualification in social research "
  val qua_chartaredEng = "2:1 degree, plus either chartered engineer status or a postgraduate degree in a relevant subject "

  val AllSchemes = Seq(
    Scheme("CentralDepartments", "Central Departments",qua_degree2_2, specific=false),
    Scheme("Commercial", "Commercial",qua_degree2_2, specific=false),
    Scheme("DigitalAndTechnology", "Digital and Technology",qua_degree2_1, specific=false),
    Scheme("DiplomaticService", "Diplomatic Service",qua_degree2_2, specific=false),
    Scheme("European", "European",qua_degree2_2, specific=false),
    Scheme("Finance", "Finance",qua_degree2_1, specific=false),
    Scheme("GovernmentCommunicationService", "Government Communication Service",qua_degree2_1, specific=false),
    Scheme("GovernmentEconomicService", "Government Economic Service",qua_economics, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-economic-service#eligibility"),
    Scheme("GovernmentOperationalResearchService", "Government Operational Research Service",qua_numerical, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-operational-research-service#eligibility"),
    Scheme("GovernmentSocialResearchService", "Government Social Research Service",qua_socialScience, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-social-research-service#eligibility"),
    Scheme("GovernmentStatisticalService", "Government Statistical Service", qua_numerical, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-statistical-service#eligibility"),
    Scheme("HousesOfParliament", "Houses of Parliament", qua_degree2_2, specific=false),
    Scheme("HumanResources", "Human Resources", qua_degree2_2, specific=false),
    Scheme("ProjectDelivery", "Project Delivery",qua_degree2_2, specific=false),
    Scheme("ScienceAndEngineering", "Science and Engineering",qua_chartaredEng, specific=true,
      "civil-service-generalist-fast-stream/fast-stream-science-and-engineering#eligibility"),
    Scheme("Tax" ,"Tax", qua_degree2_2, specific=false)
  )

  case class Scheme(id:String, description:String, qualification:String, specific:Boolean, link:String = "")

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
      value.map(key => key -> AllSchemes.find(_.id == key).map(_.description).getOrElse("")).toMap
    }
  }

}
