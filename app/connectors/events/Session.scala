/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors.events

import models.UniqueIdentifier
import org.joda.time.LocalTime
import play.api.libs.json.{ Json, OFormat }

case class Session(
  id: UniqueIdentifier,
  description: String,
  capacity: Int,
  minViableAttendees: Int,
  attendeeSafetyMargin: Int,
  startTime: LocalTime,
  endTime: LocalTime) {

  def startTimeAsString = startTime.toString("h:mm a")
  def endTimeAsString = endTime.toString("h:mm a")
}

object Session {
  import models.FaststreamImplicits._
  implicit val eventFormat: OFormat[Session] = Json.format[Session]
}
