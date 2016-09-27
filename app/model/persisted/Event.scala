package model.persisted

import org.joda.time.DateTime
import reactivemongo.bson.Macros

case class Event(name: String, created: DateTime, applicationId: Option[String], userId: Option[String])

object Event {
  import repositories.BSONDateTimeHandler
  implicit val eventHandler = Macros.handler[Event]
}

