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
import play.api.libs.json.{ Json, OFormat, Reads, Writes }

object AssessmentScheduleExchangeObjects {
  case class UsedCapacity(usedCapacity: Int, hasUnconfirmedAttendees: Boolean)
  object UsedCapacity { implicit val format: OFormat[UsedCapacity] = Json.format[UsedCapacity] }

  case class UsedCapacityDate(date: LocalDate, amUsedCapacity: UsedCapacity, pmUsedCapacity: UsedCapacity)
  object UsedCapacityDate { implicit val format: OFormat[UsedCapacityDate] = Json.format[UsedCapacityDate] }

  case class Venue(name: String, usedCapacityDates: List[UsedCapacityDate])
  object Venue { implicit val format: OFormat[Venue] = Json.format[Venue] }

  case class Region(name: String, venues: List[Venue])
  object Region { implicit val format: OFormat[Region] = Json.format[Region] }

  case class Schedule(regions: List[Region])
  object Schedule { implicit val format: OFormat[Schedule] = Json.format[Schedule] }

  case class VenueAllocation(date: LocalDate, amSlotsBooked: List[Int], pmSlotsBooked: List[Int])
  object VenueAllocation { implicit val format: OFormat[VenueAllocation] = Json.format[VenueAllocation] }

  case class VenueAllocations(allocations: List[VenueAllocation])
  object VenueAllocations { implicit val format: OFormat[VenueAllocations] = Json.format[VenueAllocations] }

  case class VenueDaySessionCandidate(userId: String, applicationId: String, firstName: String,
    lastName: String, confirmed: Boolean)
  object VenueDaySessionCandidate { implicit val format: OFormat[VenueDaySessionCandidate] = Json.format[VenueDaySessionCandidate] }

  case class VenueDayDetails(amSession: List[Option[VenueDaySessionCandidate]], pmSession: List[Option[VenueDaySessionCandidate]])
  object VenueDayDetails {
    implicit val optionVenueDaySessionCandidateReads = Reads.optionWithNull[VenueDaySessionCandidate]
    implicit val optionVenueDaySessionCandidateWrites = Writes.optionWithNull[VenueDaySessionCandidate]
    implicit val format: OFormat[VenueDayDetails] = Json.format[VenueDayDetails]
  }

  case class ScheduledCandidateDetail(firstName: String, lastName: String, preferredName: Option[String], email: Option[String],
    phone: Option[String], venue: String, session: String, date: LocalDate, status: String)
  object ScheduledCandidateDetail { implicit val format: OFormat[ScheduledCandidateDetail] = Json.format[ScheduledCandidateDetail] }

  case class LocationName(name: String)
  object LocationName { implicit val format: OFormat[LocationName] = Json.format[LocationName] }

}
