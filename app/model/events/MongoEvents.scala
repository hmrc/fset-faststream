package model.events

import model.events.EventTypes.MongoEvent

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
  case class OnlineExerciseCompleted(appId: String) extends MongoEventWithAppId
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
  case class CandidateLockedAccount(appId: String) extends MongoEventWithAppId
  case class CandidateUnlockedAccount(appId: String) extends MongoEventWithAppId
}
