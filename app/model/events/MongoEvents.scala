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
import model.persisted.Event

sealed trait MongoEvent extends EventType {
  lazy val applicationId: Option[String] = None
  lazy val userId: Option[String] = None

  require(applicationId.isDefined || userId.isDefined)

  // TODO equals & hashcode
  override def toString: String = s"${super.toString}, applicationId=$applicationId, userId=$userId"
}

object MongoEvent {
  import scala.language.implicitConversions
  
  implicit def toMongoEventData(mongoEvent: MongoEvent): model.persisted.Event =
    Event(mongoEvent.eventName, mongoEvent.eventCreated, mongoEvent.applicationId, mongoEvent.userId)
}

sealed trait MongoEventWithAppId extends MongoEvent {
  val appId: String
  override lazy val applicationId = Some(appId)
}

// format: OFF
object MongoEvents {
  // TODO appId: implicit?
  case class ApplicationSubmitted(appId: String) extends MongoEventWithAppId
  case class ApplicationWithdrawn(appId: String) extends MongoEventWithAppId

  case class OnlineExerciseStarted(appId: String) extends MongoEventWithAppId
  case class AllOnlineExercisesCompleted(appId: String) extends MongoEventWithAppId
  case class OnlineExerciseExtended(appId: String) extends MongoEventWithAppId
  case class OnlineExerciseReset(appId: String) extends MongoEventWithAppId
  case class OnlineExerciseResultSent(appId: String) extends MongoEventWithAppId

  case class ETrayStarted(appId: String) extends MongoEventWithAppId
  case class ETrayCompleted(appId: String) extends MongoEventWithAppId
  case class ETrayExtended(appId: String) extends MongoEventWithAppId
  case class ETrayReset(appId: String) extends MongoEventWithAppId
  case class ETrayResultSent(appId: String) extends MongoEventWithAppId

  case class VideoInterviewStarted(appId: String) extends MongoEventWithAppId
  case class VideoInterviewCompleted(appId: String) extends MongoEventWithAppId
  case class VideoInterviewExtended(appId: String) extends MongoEventWithAppId
  case class VideoInterviewReset(appId: String) extends MongoEventWithAppId
  case class VideoInterviewResultSnet(appId: String) extends MongoEventWithAppId

  case class ManageAdjustmentsUpdated(appId: String) extends MongoEventWithAppId
  case class FastPassApproved(appId: String) extends MongoEventWithAppId
  case class FastPassRejected(appId: String) extends MongoEventWithAppId
}
