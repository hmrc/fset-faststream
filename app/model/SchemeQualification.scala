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
import model.Scheme.Scheme
import play.api.libs.json.Json

case class SchemeQualification(scheme: Scheme, qualification: Qualification, specificRequirement: Boolean)

object SchemeQualification {
  implicit val schemeQualificationFormat = Json.format[SchemeQualification]

  import Scheme._
  import Qualification._

  val AllSchemesWithQualification = List(
    SchemeQualification(CentralDepartments, Degree_22, specificRequirement = false),
    SchemeQualification(Commercial, Degree_22, specificRequirement = false),
    SchemeQualification(DigitalAndTechnology, Degree_21, specificRequirement = false),
    SchemeQualification(DiplomaticService, Degree_22, specificRequirement = false),
    SchemeQualification(European, Degree_22, specificRequirement = false),
    SchemeQualification(Finance, Degree_21, specificRequirement = false),
    SchemeQualification(GovernmentCommunicationService, Degree_21, specificRequirement = false),
    SchemeQualification(GovernmentEconomicService, Degree_Economics, specificRequirement = false),
    SchemeQualification(GovernmentOperationalResearchService, Degree_Numerate, specificRequirement = false),
    SchemeQualification(GovernmentSocialResearchService, Degree_SocialScience, specificRequirement = false),
    SchemeQualification(GovernmentStatisticalService, Degree_Numerate, specificRequirement = false),
    SchemeQualification(HousesOfParliament, Degree_22, specificRequirement = false),
    SchemeQualification(HumanResources, Degree_22, specificRequirement = false),
    SchemeQualification(ProjectDelivery, Degree_22, specificRequirement = false),
    SchemeQualification(ScienceAndEngineering, Degree_CharteredEngineer, specificRequirement = false),
    SchemeQualification(Tax, Degree_22, specificRequirement = false)
  )
}
