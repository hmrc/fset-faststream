package model.report

import model.persisted.eventschedules.{ Event, Location, Venue }
import model.persisted.eventschedules.EventType.EventType
import org.joda.time.LocalDate

case class CandidateAcceptanceReportItem(
  emailAddress: String,
  invitedAt: LocalDate,
  eventType: EventType,
  eventDescription: String,
  eventLocation: String,
  eventVenue: String
)
