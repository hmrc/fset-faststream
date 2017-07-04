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

package controllers

import model.exchange.{ EventAssessorAllocationsSummaryPerSkill, EventWithAllocationsSummary }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.VenueType.VenueType
import model.persisted.eventschedules._
import org.joda.time.{ LocalDate, LocalTime }
import model.Exceptions.EventNotFoundException
import model.exchange
import model.command
import model.exchange.AssessorAllocations
import model.persisted.eventschedules.EventType
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import repositories.events.{ LocationsWithVenuesInMemoryRepository, LocationsWithVenuesRepository, UnknownVenueException }
import services.allocation.AssessorAllocationService

import scala.concurrent.Future
import scala.util.Try
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object EventsController extends EventsController {
  val eventsService: EventsService = EventsService
  val locationsAndVenuesRepository: LocationsWithVenuesRepository = LocationsWithVenuesInMemoryRepository
  val assessorAllocationService: AssessorAllocationService = AssessorAllocationService
}

trait EventsController extends BaseController {
  def eventsService: EventsService
  def locationsAndVenuesRepository: LocationsWithVenuesRepository
  def assessorAllocationService: AssessorAllocationService

  def venuesForEvents: Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.venues.map(x => Ok(Json.toJson(x)))
  }

  def locationsForEvents: Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.locations.map(x => Ok(Json.toJson(x)))
  }

  def saveAssessmentEvents(): Action[AnyContent] = Action.async { implicit request =>
    eventsService.saveAssessmentEvents().map(_ => Created("Events saved")).recover { case _ => UnprocessableEntity }
  }

  def getEvent(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    eventsService.getEvent(eventId).map { event =>
      Ok(Json.toJson(event))
    }.recover {
      case _: EventNotFoundException => NotFound(s"No event found with id $eventId")
    }
  }

  def getEvents(eventTypeParam: String, venueParam: String): Action[AnyContent] = Action.async { implicit request =>
    val events =  Try {
        val eventType = EventType.withName(eventTypeParam.toUpperCase)
        locationsAndVenuesRepository.venue(venueParam).flatMap { venue =>
          eventsService.getEvents(eventType, venue).map { events =>
            if (events.isEmpty) {
              NotFound
            } else {
              Ok(Json.toJson(events))
            }
          }
        }
    }

    play.api.Logger.debug(s"$events")

    Future.fromTry(events) flatMap identity recover {
      case _: NoSuchElementException => BadRequest(s"$eventTypeParam is not a valid event type")
      case _: UnknownVenueException => BadRequest(s"$venueParam is not a valid venue")
    }
  }

  def getAssessorAllocations(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorAllocationService.getAllocations(eventId).map { allocations =>
      if (allocations.allocations.isEmpty) {
        Ok(Json.toJson(AssessorAllocations(version = None, allocations = Nil)))
      } else {
        Ok(Json.toJson(allocations))
      }
    }
  }

  // TODO MIGUEL: Decide if this should go here or a separate eventsAllocations controller
  // scalastyle:off method.length
  def getEventsWithAllocationsSummary(venue: Venue, eventType: EventType): Action[AnyContent] = Action.async { implicit request =>
    assessorAllocationService.getEventsWithAllocationsSummary(venue, eventType).map { eventsWithAllocations =>
      Ok(Json.toJson(eventsWithAllocations))
    }
    // 1st get events
    // 2nd get allocations for every event

    // Example: 8 events in different event types, locations and venues on the same day.
/*    val londonFsacVenue = Venue("LONDON_FSAC", "London FSAC")
    val newcastleFsacVenue = Venue("NEWCASTLE_FSAC", "Newcastle FSAC")
    val newcastleLongbentonVenue = Venue("NEWCASTLE_FSAC", "Newcastle FSAC")

    val event1LondonFSAC = Event("1", EventType.FSAC, "Description", Location("London"), londonFsacVenue,
      new LocalDate("2017-07-02"), 24, 10, 1, LocalTime.now(), LocalTime.now().plusHours(1),
      Map(
        SkillType.ASSESSOR.toString -> 6,
        SkillType.QUALITY_ASSURANCE_COORDINATOR.toString -> 3,
        SkillType.CHAIR.toString -> 2))

    val event2LondonFSAC = event1LondonFSAC.copy(id = "2")
    val event1NewcastleFSAC = event2LondonFSAC.copy(id = "3", location = Location("Newcastle"), venue = newcastleFsacVenue)
    val event2NewcastleFSAC = event1NewcastleFSAC.copy(id = "4", venue = newcastleLongbentonVenue)
    val event1LondonSkype = event1LondonFSAC.copy(id = "5", eventType = EventType.SKYPE_INTERVIEW)
    val event2LondonSkype = event2LondonFSAC.copy(id = "6", eventType = EventType.SKYPE_INTERVIEW)
    val event1NewcastleSkype = event2NewcastleFSAC.copy(id = "7", eventType = EventType.SKYPE_INTERVIEW)
    val event2NewcastleSkype = event1NewcastleFSAC.copy(id = "8", eventType = EventType.SKYPE_INTERVIEW)

    val allocationsSameAsRequirements = List(
      EventAssessorAllocationsSummaryPerSkill(SkillType.ASSESSOR, 6, 6),
      EventAssessorAllocationsSummaryPerSkill(SkillType.QUALITY_ASSURANCE_COORDINATOR, 3, 3),
      EventAssessorAllocationsSummaryPerSkill(SkillType.CHAIR, 2, 2)
    )
    val allocationsLessThanRequirementsButEqualToAllocations = List(
      EventAssessorAllocationsSummaryPerSkill(SkillType.ASSESSOR, 17, 5),
      EventAssessorAllocationsSummaryPerSkill(SkillType.QUALITY_ASSURANCE_COORDINATOR, 4, 3),
      EventAssessorAllocationsSummaryPerSkill(SkillType.CHAIR, 5, 2)
    )
    val allocationsLessThanRequirementsAndLessThanAllocations = List(
      EventAssessorAllocationsSummaryPerSkill(SkillType.ASSESSOR, 5, 4),
      EventAssessorAllocationsSummaryPerSkill(SkillType.QUALITY_ASSURANCE_COORDINATOR, 2, 1),
      EventAssessorAllocationsSummaryPerSkill(SkillType.CHAIR, 1, 0)
    )

    val event1LondonFSACAllocations = EventWithAllocationsSummary(event1LondonFSAC, 24,
      allocationsSameAsRequirements)
    val event2LondonFSACAllocations = EventWithAllocationsSummary(event2LondonFSAC, 24,
      allocationsSameAsRequirements)
    val event1NewcastleFSACAllocations = EventWithAllocationsSummary(event1NewcastleFSAC, 10,
      allocationsLessThanRequirementsButEqualToAllocations)
    val event2NewcastleFSACAllocations = EventWithAllocationsSummary(event1NewcastleFSAC, 8,
      allocationsLessThanRequirementsAndLessThanAllocations)

    val event1LondonSkypeAllocations = EventWithAllocationsSummary(event1LondonFSAC, 24,
      allocationsSameAsRequirements)
    val event2LondonSkypeAllocations = EventWithAllocationsSummary(event2LondonFSAC, 24,
      allocationsSameAsRequirements)
    val event1NewcastleSkypeAllocations = EventWithAllocationsSummary(event1NewcastleFSAC, 10,
      allocationsLessThanRequirementsButEqualToAllocations)
    val event2NewcastleSkypeAllocations = EventWithAllocationsSummary(event1NewcastleFSAC, 8,
      allocationsLessThanRequirementsAndLessThanAllocations)

    val eventsWithAllocationSummary = List(
      event1LondonFSACAllocations,
      event2LondonFSACAllocations,
      event1NewcastleFSACAllocations,
      event2NewcastleFSACAllocations,
      event1LondonSkypeAllocations,
      event2LondonSkypeAllocations,
      event1NewcastleSkypeAllocations,
      event2NewcastleSkypeAllocations
    )

    Future.successful(Ok(Json.toJson(eventsWithAllocationSummary)))*/
  }
  // scalastyle:on method.length
  def allocateAssessor(eventId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.AssessorAllocations] { assessorAllocations =>
      val newAllocations = command.AssessorAllocations.fromExchange(eventId, assessorAllocations)
      assessorAllocationService.allocate(newAllocations).map( _ => Ok)
    }
  }

}
