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

package model

import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

@deprecated("Use the config Schemes instead", "June 2017")
object SchemeType extends Enumeration {
  type SchemeType = Value

  val Commercial, DigitalAndTechnology, DiplomaticService, DiplomaticServiceEconomics, DiplomaticServiceEuropean, European = Value
  val Finance, Generalist, GovernmentCommunicationService, GovernmentEconomicsService, GovernmentOperationalResearchService = Value
  val GovernmentSocialResearchService, GovernmentStatisticalService, HousesOfParliament, HumanResources, ProjectDelivery = Value
  val ScienceAndEngineering, Edip, Sdip = Value

  implicit val schemeFormat = new Format[SchemeType] {
    def reads(json: JsValue) = JsSuccess(SchemeType.withName(json.as[String]))

    def writes(myEnum: SchemeType) = JsString(myEnum.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, SchemeType] {
    def read(doc: BSONString) = SchemeType.withName(doc.value)

    def write(stats: SchemeType) = BSON.write(stats.toString)
  }

}
