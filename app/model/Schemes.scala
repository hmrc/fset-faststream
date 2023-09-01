/*
 * Copyright 2023 HM Revenue & Customs
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

trait Schemes {
  val Commercial = SchemeId("Commercial")
  val DigitalDataTechnologyAndCyber = SchemeId("DigitalDataTechnologyAndCyber")
  val DiplomaticAndDevelopment = SchemeId("DiplomaticAndDevelopment")
  val DiplomaticAndDevelopmentEconomics = SchemeId("DiplomaticAndDevelopmentEconomics")
  val Finance = SchemeId("Finance")
  val GovernmentCommunicationService = SchemeId("GovernmentCommunicationService")
  val GovernmentEconomicsService = SchemeId("GovernmentEconomicsService")
  val GovernmentOperationalResearchService = SchemeId("GovernmentOperationalResearchService")
  val GovernmentPolicy = SchemeId("GovernmentPolicy")
  val GovernmentPolicySTEM = SchemeId("GovernmentPolicySTEM")
  val GovernmentSocialResearchService = SchemeId("GovernmentSocialResearchService")
  val GovernmentStatisticalService = SchemeId("GovernmentStatisticalService")
  val HousesOfParliament = SchemeId("HousesOfParliament")
  val HumanResources = SchemeId("HumanResources")
  val OperationalDelivery = SchemeId("OperationalDelivery") // This was previously Generalist
  val OperationalDeliverySTEM = SchemeId("OperationalDeliverySTEM")
  val ProjectDelivery = SchemeId("ProjectDelivery")
  val Property = SchemeId("Property")
  val ScienceAndEngineering = SchemeId("ScienceAndEngineering")
  val Edip = SchemeId("Edip")
  val Sdip = SchemeId("Sdip")
}
