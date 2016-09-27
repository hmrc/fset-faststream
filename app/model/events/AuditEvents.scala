package model.events

import model.events.EventTypes.AuditEvent

sealed trait AuditEventWithDetailsMap extends AuditEvent {
  val details: Map[String, String]
  override lazy val detailsMap: Map[String, String] = details
}

object AuditEvents {
  case class ApplicationWithdrawn(details: Map[String, String]) extends AuditEvent
}
