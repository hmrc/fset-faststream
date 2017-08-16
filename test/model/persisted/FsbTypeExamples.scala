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

package persisted

import model.FsbType

object FsbTypeExamples {
  val YamlFsbTypes = List(
    FsbType("SAC", "GovernmentStatisticalService", "GSS"),
    FsbType("SRAC", "GovernmentSocialResearchService", "GSR"),
    FsbType("ORAC", "GovernmentOperationalResearchService", "GORS"),
    FsbType("EAC", "GovernmentEconomicsService", "GES"),
    FsbType("EAC_DS", "GovernmentEconomicsServiceDiplomaticService", "GES_DS"),
    FsbType("GOV COMS", "GovernmentCommunicationService", "GCFS"),
    FsbType("DAT", "DigitalAndTechnology", "DAT"),
    FsbType("SEFS", "ScienceAndEngineering", "SEFS"),
    FsbType("FCO", "DiplomaticService", "DS"),
    FsbType("P&D", "ProjectDelivery" ,"PDFS"),
    FsbType("FIFS", "Finance", "FIFS"),
    FsbType("COMMERCIAL", "Commercial", "CFS"),
    FsbType("HOP", "HousesOfParliament", "HOP")
  )
}
