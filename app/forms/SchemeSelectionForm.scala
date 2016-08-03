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

  val Degree_21 = "Degree_21"
  val Degree_22 = "Degree_22"
  val Degree_Economics = "Degree_Economics"
  val Degree_Numerate = "Degree_Numerate"
  val Degree_SocialScience = "Degree_SocialScience"
  val Degree_CharteredEngineer = "Degree_CharteredEngineer"

  val AllSchemes = Seq(
    Scheme("CentralDepartments", "Central Departments",Degree_22, specific=false),
    Scheme("Commercial", "Commercial",Degree_22, specific=false),
    Scheme("DigitalAndTechnology", "Digital and Technology",Degree_21, specific=false),
    Scheme("DiplomaticService", "Diplomatic Service",Degree_22, specific=false),
    Scheme("European", "European",Degree_22, specific=false),
    Scheme("Finance", "Finance",Degree_21, specific=false),
    Scheme("GovernmentCommunicationService", "Government Communication Service",Degree_21, specific=false),
    Scheme("GovernmentEconomicService", "Government Economic Service",Degree_Economics, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-economic-service#eligibility"),
    Scheme("GovernmentOperationalResearchService", "Government Operational Research Service",Degree_Numerate, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-operational-research-service#eligibility"),
    Scheme("GovernmentSocialResearchService", "Government Social Research Service",Degree_SocialScience, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-social-research-service#eligibility"),
    Scheme("GovernmentStatisticalService", "Government Statistical Service", Degree_Numerate, specific=true,
      "civil-service-analytical-fast-streams/civil-service-fast-stream-government-statistical-service#eligibility"),
    Scheme("HousesOfParliament", "Houses of Parliament", Degree_22, specific=false),
    Scheme("HumanResources", "Human Resources", Degree_22, specific=false),
    Scheme("ProjectDelivery", "Project Delivery",Degree_22, specific=false),
    Scheme("ScienceAndEngineering", "Science and Engineering",Degree_CharteredEngineer, specific=true,
      "civil-service-generalist-fast-stream/fast-stream-science-and-engineering#eligibility"),
    Scheme("Tax" ,"Tax", Degree_22, specific=false)
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
      value.map(key => key -> AllSchemes.find(_.id == key).map(_.description).getOrElse("")).toMap
    }
  }

}
