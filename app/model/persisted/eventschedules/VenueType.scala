/*
 * Copyright 2022 HM Revenue & Customs
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

package model.persisted.eventschedules

import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object VenueType extends Enumeration {
  type VenueType = Value

  val ALL_VENUES, LONDON_FSAC, NEWCASTLE_FSAC, LONDON_FSB, VIRTUAL = Value

  implicit val VenueTypeFormat = new Format[VenueType] {
    override def reads(json: JsValue): JsResult[VenueType] = JsSuccess(VenueType.withName(json.as[String].toUpperCase))

    override def writes(venueType: VenueType): JsValue = JsString(venueType.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, VenueType] {
    override def write(venueType: VenueType): BSONString = BSON.write(venueType.toString)

    override def read(bson: BSONString): VenueType = VenueType.withName(bson.value.toUpperCase)
  }
}

