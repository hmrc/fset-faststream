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

package model.persisted.eventschedules

import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object EventType extends Enumeration {
  type EventType = Value

  val ALL_EVENTS, FSAC, FSB, TELEPHONE_INTERVIEW, SKYPE_INTERVIEW = Value

  implicit val EventTypeFormat = new Format[EventType] {
    override def reads(json: JsValue): JsResult[EventType] = JsSuccess(EventType.withName(json.as[String].toUpperCase))

    override def writes(eventType: EventType): JsValue = JsString(eventType.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, EventType] {
    override def write(eventType: EventType): BSONString = BSON.write(eventType.toString)

    override def read(bson: BSONString): EventType = EventType.withName(bson.value.toUpperCase)
  }
}
