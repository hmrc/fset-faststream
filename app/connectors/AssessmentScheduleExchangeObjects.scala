/*
 * Copyright 2017 HM Revenue & Customs
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

package connectors

import org.joda.time.LocalDate
import play.api.libs.json.Json

object AssessmentScheduleExchangeObjects {
  case class UsedCapacity(usedCapacity: Int, hasUnconfirmedAttendees: Boolean)
  case class UsedCapacityDate(date: LocalDate, amUsedCapacity: UsedCapacity, pmUsedCapacity: UsedCapacity)
  case class Venue(name: String, usedCapacityDates: List[UsedCapacityDate])
  case class Location(name: String, venues: List[Venue])
  case class Schedule(locations: List[Location])
  case class VenueAllocation(date: LocalDate, amSlotsBooked: List[Int], pmSlotsBooked: List[Int])
  case class VenueAllocations(allocations: List[VenueAllocation])

  case class VenueDayDetails(amSession: List[Option[VenueDaySessionCandidate]], pmSession: List[Option[VenueDaySessionCandidate]])

  case class VenueDaySessionCandidate(userId: String, applicationId: String, firstName: String,
    lastName: String, confirmed: Boolean)

  case class ScheduledCandidateDetail(firstName: String, lastName: String, preferredName: Option[String], email: Option[String],
    phone: Option[String], venue: String, session: String, date: LocalDate, status: String)

  case class LocationName(name: String)

  object Implicits {
    implicit val usedCapacityFormat = Json.format[UsedCapacity]
    implicit val usedCapacityDateFormat = Json.format[UsedCapacityDate]
    implicit val venueFormat = Json.format[Venue]
    implicit val locationFormat = Json.format[Location]
    implicit val scheduleFormat = Json.format[Schedule]
    implicit val venueAllocationFormat = Json.format[VenueAllocation]
    implicit val venueAllocationsFormat = Json.format[VenueAllocations]

    implicit val venueDaySessionCandidateFormat = Json.format[VenueDaySessionCandidate]
    implicit val venueDayDetailsFormat = Json.format[VenueDayDetails]

    implicit val scheduledCandidateDetailFormat = Json.format[ScheduledCandidateDetail]

    implicit val locationNameFormat = Json.format[LocationName]
  }
}
