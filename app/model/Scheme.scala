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

package model

import model.Qualification.Qualification
import model.SchemeType.SchemeType
import play.api.libs.json.Json

case class Scheme(schemeId: SchemeType, qualification: Qualification, specificRequirement: Boolean)

object Scheme {
  implicit val schemeQualificationFormat = Json.format[Scheme]

  import SchemeType._
  import Qualification._

  val AllSchemesWithQualification = List(
    Scheme(CentralDepartments, Degree_22, specificRequirement = false),
    Scheme(Commercial, Degree_22, specificRequirement = false),
    Scheme(DigitalAndTechnology, Degree_21, specificRequirement = false),
    Scheme(DiplomaticService, Degree_22, specificRequirement = false),
    Scheme(European, Degree_22, specificRequirement = false),
    Scheme(Finance, Degree_21, specificRequirement = false),
    Scheme(GovernmentCommunicationService, Degree_21, specificRequirement = false),
    Scheme(GovernmentEconomicService, Degree_Economics, specificRequirement = true),
    Scheme(GovernmentOperationalResearchService, Degree_Numerate, specificRequirement = true),
    Scheme(GovernmentSocialResearchService, Degree_SocialScience, specificRequirement = true),
    Scheme(GovernmentStatisticalService, Degree_Numerate, specificRequirement = true),
    Scheme(HousesOfParliament, Degree_22, specificRequirement = false),
    Scheme(HumanResources, Degree_22, specificRequirement = false),
    Scheme(ProjectDelivery, Degree_22, specificRequirement = false),
    Scheme(ScienceAndEngineering, Degree_CharteredEngineer, specificRequirement = true),
    Scheme(Tax, Degree_22, specificRequirement = false)
  )
}
