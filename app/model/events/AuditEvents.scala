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

package model.events

import model.events.EventTypes.EventType

sealed trait AuditEvent extends EventType {
  val details: Map[String, String] = Map()
  override def toString: String = s"${super.toString}, details=$details"
}

sealed trait AuditEventWithMap extends AuditEvent {
  private[events] val detailsMap: Map[String, String]
  override val details = detailsMap
}

sealed trait AuditEventNoRequest extends AuditEventWithMap

object AuditEvents {
  case class ApplicationSubmitted() extends AuditEvent
  case class ApplicationWithdrawn(detailsMap: Map[String, String]) extends AuditEventWithMap
  case class ExpiredTestsExtended(detailsMap: Map[String, String]) extends AuditEventNoRequest
  case class NonExpiredTestsExtended(detailsMap: Map[String, String]) extends AuditEventNoRequest
  case class Phase1TestsReset(detailsMap: Map[String, String]) extends AuditEventNoRequest
}
