/*
 * Copyright 2019 HM Revenue & Customs
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

package models.events

import play.api.libs.json._

object EventType extends Enumeration {
  type EventType = Value

  val ALL_EVENTS, FSAC, FSB = Value

  val options = Map(
    FSAC -> "Fast Stream Assessment Centre",
    FSB -> "Final Selection Board"
  )

  val allOption = Map(ALL_EVENTS -> "All Events")

  val displayText = allOption ++ options

  implicit val EventTypeFormat = new Format[EventType] {
    override def reads(json: JsValue): JsResult[EventType] = JsSuccess(EventType.withName(json.as[String].toUpperCase))

    override def writes(eventType: EventType): JsValue = JsString(eventType.toString)
  }

  implicit class RichEventType(eventType: EventType) {
    def displayValue: String = displayText(eventType)
  }
}
