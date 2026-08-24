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
  val Commercial: SchemeId = SchemeId("Commercial")
  val CyberSecurity: SchemeId = SchemeId("CyberSecurity")
  val Digital: SchemeId = SchemeId("Digital")
  val DiplomaticAndDevelopment: SchemeId = SchemeId("DiplomaticAndDevelopment")
  val DiplomaticAndDevelopmentEconomics: SchemeId = SchemeId("DiplomaticAndDevelopmentEconomics")
  val FastStreamYorkshireAndTheHumber: SchemeId = SchemeId("FastStreamYorkshireAndTheHumber")
  val Finance: SchemeId = SchemeId("Finance")
  val GovernmentEconomicsService: SchemeId = SchemeId("GovernmentEconomicsService")
  val GovernmentOperationalResearchService: SchemeId = SchemeId("GovernmentOperationalResearchService")
  val GovernmentPolicy: SchemeId = SchemeId("GovernmentPolicy")
  val GovernmentSocialResearchService: SchemeId = SchemeId("GovernmentSocialResearchService")
  val GovernmentStatisticalService: SchemeId = SchemeId("GovernmentStatisticalService")
  val HousesOfParliament: SchemeId = SchemeId("HousesOfParliament")
  val HumanResources: SchemeId = SchemeId("HumanResources")
  val OperationalDelivery: SchemeId = SchemeId("OperationalDelivery") // This was previously Generalist
  val ProjectDelivery: SchemeId = SchemeId("ProjectDelivery")
  val Property: SchemeId = SchemeId("Property")
  val RiskManagement: SchemeId = SchemeId("RiskManagement")
  val ScienceAndEngineering: SchemeId = SchemeId("ScienceAndEngineering")
  val Edip: SchemeId = SchemeId("Edip")
  val Sdip: SchemeId = SchemeId("Sdip")
}
