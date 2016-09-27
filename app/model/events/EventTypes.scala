package model.events

import model.persisted.Event
import org.joda.time.DateTime

object EventTypes {
  type Events = List[Event]

  sealed trait Event {
    val eventName: String = getClass.getSimpleName
    val eventCreated: DateTime = DateTime.now()
  }

  trait MongoEvent extends Event {
    lazy val applicationId: Option[String] = None
    lazy val userId: Option[String] = None

    require(applicationId.isDefined || userId.isDefined)
  }
  object MongoEvent {
    implicit def toMongoEventData(mongoEvent: MongoEvent): model.persisted.Event =
      Event(mongoEvent.eventName, mongoEvent.eventCreated, mongoEvent.applicationId, mongoEvent.userId)
  }

  trait AuditEvent extends Event {
    lazy val detailsMap: Map[String, String] = Map()
  }

}


