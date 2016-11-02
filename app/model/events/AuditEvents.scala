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

// rename AuditEvent
sealed trait AuditEvent extends EventType {
  val details: Map[String, String]
  override def toString: String = s"${super.toString}, details=$details"
}

sealed trait AuditEventWithAppId extends AuditEvent {
  val applicationId: String
  val details: Map[String, String] = Map("applicationId" -> applicationId)
  override def toString: String = s"${super.toString}, details=$details"
}

sealed trait AuditEventNoRequest extends AuditEvent

object AuditEvents {
  // NOTICE. The name for the case class is important and is used when the event is emitted.
  // In other words: Renaming the case class here, impacts in renaming the event name in Audit service.

  case class ApplicationSubmitted(applicationId: String) extends AuditEventWithAppId
  case class ApplicationWithdrawn(details: Map[String, String]) extends AuditEvent
  case class ExpiredTestsExtended(details: Map[String, String]) extends AuditEventNoRequest
  case class NonExpiredTestsExtended(details: Map[String, String]) extends AuditEventNoRequest
  case class Phase1TestsReset(details: Map[String, String]) extends AuditEventNoRequest
  case class ApplicationExpired(details: Map[String, String]) extends AuditEvent
  case class ApplicationExpiryReminder(details: Map[String, String]) extends AuditEvent
  case class ExpiredTestEmailSent(details: Map[String, String]) extends AuditEventNoRequest
  case class FailedTestEmailSent(details: Map[String, String]) extends AuditEventNoRequest
  case class Phase2TestsReset(details: Map[String, String]) extends AuditEventNoRequest
  case class AdjustmentsConfirmed(details: Map[String, String]) extends AuditEvent
}
