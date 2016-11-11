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
sealed abstract class AuditEvent(val details: Map[String, String]) extends EventType {
  override def toString: String = s"${super.toString}, details=$details"
}

sealed abstract class AuditEventWithAppId(applicationId: String) extends AuditEvent(Map("applicationId" -> applicationId))

sealed abstract class AuditEventNoRequest(details: Map[String, String]) extends AuditEvent(details)

object AuditEvents {
  // NOTICE. The name for the case class is important and is used when the event is emitted.
  // In other words: Renaming the case class here, impacts in renaming the event name in Audit service.

  case class ApplicationSubmitted(applicationId: String) extends AuditEventWithAppId(applicationId)
  case class ApplicationWithdrawn(mapDetails: Map[String, String]) extends AuditEvent(mapDetails)
  case class ExpiredTestsExtended(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)
  case class NonExpiredTestsExtended(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)
  case class Phase1TestsReset(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)
  case class ApplicationExpired(mapDetails: Map[String, String]) extends AuditEvent(mapDetails)
  case class ApplicationExpiryReminder(mapDetails: Map[String, String]) extends AuditEvent(mapDetails)
  case class ExpiredTestEmailSent(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)
  case class FailedTestEmailSent(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)

  case class Phase2TestsReset(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)
  case class Phase2TestInvitationProcessComplete(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)

  case class AdjustmentsConfirmed(mapDetails: Map[String, String]) extends AuditEvent(mapDetails)
  case class AdjustmentsCommentUpdated(mapDetails: Map[String, String]) extends AuditEvent(mapDetails)
  case class AdjustmentsCommentRemoved(mapDetails: Map[String, String]) extends AuditEvent(mapDetails)

  case class VideoInterviewCandidateRegistered(seqDetails: (String, String)*) extends AuditEventNoRequest(seqDetails.toMap)
  case class VideoInterviewInvited(seqDetails: (String, String)*) extends AuditEventNoRequest(seqDetails.toMap)
  case class VideoInterviewInvitationEmailSent(seqDetails: (String, String)*) extends AuditEventNoRequest(seqDetails.toMap)
  case class VideoInterviewRegistrationAndInviteComplete(seqDetails: (String, String)*) extends AuditEventNoRequest(seqDetails.toMap)
  case class VideoInterviewExtended(seqDetails: (String, String)*) extends AuditEventNoRequest(seqDetails.toMap)
  case class VideoInterviewReset(seqDetails: (String, String)*) extends AuditEventNoRequest(seqDetails.toMap)
  case class VideoInterviewStarted(applicationId: String) extends AuditEventWithAppId(applicationId)
  case class VideoInterviewCompleted(applicationId: String) extends AuditEventWithAppId(applicationId)
  case class VideoInterviewTestExpiryReminder(mapDetails: Map[String, String]) extends AuditEvent(mapDetails)

  case class FixedProdData(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)
  case class FailedFixedProdData(mapDetails: Map[String, String]) extends AuditEventNoRequest(mapDetails)
}
