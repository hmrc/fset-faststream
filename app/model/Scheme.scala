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

import play.api.libs.json.{Format, JsString, JsSuccess, JsValue}
import reactivemongo.bson.{BSON, BSONHandler, BSONString}

object Scheme extends Enumeration {
  type Scheme = Value

  val CentralDepartments, Commercial, DigitalAndTechnology, DiplomaticService, European, Finance,
  GovernmentCommunicationService, GovernmentEconomicService, GovernmentOperationalResearchService,
  GovernmentSocialResearchService, GovernmentStatisticalService, HousesOfParliament, HumanResources,
  ProjectDelivery, ScienceAndEngineering, Tax = Value

  implicit val schemeFormat = new Format[Scheme] {
    def reads(json: JsValue) = JsSuccess(Scheme.withName(json.as[String]))

    def writes(myEnum: Scheme) = JsString(myEnum.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, Scheme] {
    def read(doc: BSONString) = Scheme.withName(doc.value)

    def write(stats: Scheme) = BSON.write(stats.toString)
  }
}
