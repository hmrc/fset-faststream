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

package model.stc

import model.stc.StcEventTypes.StcEventType
import model.persisted.StcEvent
import org.joda.time.DateTime

sealed trait DataStoreEvent extends StcEventType {
  final val eventCreated: DateTime = DateTime.now()
  lazy val applicationId: Option[String] = None
  lazy val userId: Option[String] = None
  lazy val createdBy: Option[String] = None

  require(applicationId.isDefined || userId.isDefined)

  override def toString: String = s"${super.toString}, applicationId=$applicationId, userId=$userId," +
    s"eventCreated=$eventCreated, createdBy=$createdBy"
}

object DataStoreEvent {
  import scala.language.implicitConversions

  implicit def toDataStoreEvent(dataStoreEvent: DataStoreEvent): model.persisted.StcEvent =
    StcEvent(
      dataStoreEvent.eventName,
      dataStoreEvent.eventCreated,
      dataStoreEvent.applicationId,
      dataStoreEvent.userId,
      dataStoreEvent.createdBy
    )
}

sealed trait DataStoreEventWithAppId extends DataStoreEvent {
  val appId: String
  override lazy val applicationId = Some(appId)
}

sealed trait DataStoreEventWithCreatedBy extends DataStoreEvent {
  val appId: String
  val createdByUser: String
  override lazy val applicationId = Some(appId)
  override lazy val createdBy = Some(createdByUser)
}

// scalastyle:off number.of.methods
object DataStoreEvents {
  // NOTICE. The name for the case class is important and is used when the event is emitted.
  // In other words: Renaming the case class here, impacts in renaming the event name in database.

  // scalastyle:off
  case class ApplicationSubmitted(appId: String) extends DataStoreEventWithAppId
  case class ApplicationExpired(appId: String) extends DataStoreEventWithAppId
  case class ApplicationReadyForExport(appId: String) extends DataStoreEventWithAppId
  case class ApplicationExported(appId: String) extends DataStoreEventWithAppId
  case class ApplicationExportUpdated(appId: String) extends DataStoreEventWithAppId
  case class ApplicationExpiryReminder(appId: String) extends DataStoreEventWithAppId
  case class ApplicationWithdrawn(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class SchemeWithdrawn(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy

  case class OnlineExerciseStarted(appId: String) extends DataStoreEventWithAppId
  case class OnlineExercisesCompleted(appId: String) extends DataStoreEventWithAppId
  case class AllOnlineExercisesCompleted(appId: String) extends DataStoreEventWithAppId
  case class OnlineExerciseExtended(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class OnlineExerciseReset(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class OnlineExerciseResultSent(appId: String) extends DataStoreEventWithAppId
  case class FailedTestEmailSent(appId: String) extends DataStoreEventWithAppId
  case class SuccessTestEmailSent(appId: String) extends DataStoreEventWithAppId

  case class ETrayStarted(appId: String) extends DataStoreEventWithAppId
  case class ETrayCompleted(appId: String) extends DataStoreEventWithAppId
  case class ETrayExtended(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class ETrayReset(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class ETrayResultSent(appId: String) extends DataStoreEventWithAppId

  case class VideoInterviewInvited(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewCandidateRegistered(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewRegistrationAndInviteComplete(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewInvitationEmailSent(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewStarted(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewCompleted(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewExtended(appId: String, createdByUser: String) extends DataStoreEventWithAppId
  case class VideoInterviewReset(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewResultsReceived(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewExpiryReminder(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewRemoved(appId: String) extends DataStoreEventWithAppId
  case class VideoInterviewRescheduled(appId: String, createdByUser: String) extends DataStoreEventWithAppId

  case class ManageAdjustmentsUpdated(appId: String) extends DataStoreEventWithAppId
  case class FastPassApproved(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class FastPassRejected(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class AdjustmentsCommentUpdated(appId: String) extends DataStoreEventWithAppId
  case class AdjustmentsCommentRemoved(appId: String) extends DataStoreEventWithAppId

  case class SiftNumericExerciseExtended(appId: String, createdByUser: String) extends DataStoreEventWithCreatedBy
  case class SiftTestResultsReceived(appId: String) extends DataStoreEventWithAppId
  // scalastyle:on


  // scalastyle:on number.of.methods
}
