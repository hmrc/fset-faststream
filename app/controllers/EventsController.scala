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
import model.persisted.eventschedules.{ Event, EventType, SkillType, VenueType }
import org.joda.time.{ LocalDate, LocalTime }
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import repositories.events.EventsRepository
import services.events.EventsParsingService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object EventsController extends EventsController {
  val assessmentEventsRepository: EventsRepository = repositories.eventsRepository
  val assessmentCenterParsingService: EventsParsingService = EventsParsingService
}

trait EventsController extends BaseController {
  val assessmentEventsRepository: EventsRepository
  val assessmentCenterParsingService: EventsParsingService

  def saveAssessmentEvents(): Action[AnyContent] = Action.async { implicit request =>
    assessmentCenterParsingService.processCentres().flatMap{ events =>
      Logger.debug("Events have been processed!")
      assessmentEventsRepository.save(events)
    }.map(_ => Created).recover { case _ => UnprocessableEntity }
  }

  def getEvent(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(""))
  }

  def fetchEvents(eventTypeParam: String, venueParam: String): Action[AnyContent] = Action.async { implicit request =>
    // convert params to native enum type
    val eventType = EventType.withName(eventTypeParam.toUpperCase)
    val venue = VenueType.withName(venueParam.toUpperCase)

    assessmentEventsRepository.fetchEvents(Some(eventType), Some(venue), None, None).map(events => Ok(Json.toJson(events)))
  }

  // TODO MIGUEL: Decide if this should go here or a separate eventsAllocations controller
  // scalastyle:off method.length
  def getEventsWithAllocationsSummary(venueType: VenueType, eventType: EventType): Action[AnyContent] = Action.async { implicit request =>

    // Example: 8 events in different event types, locations and venues on the same day.
    val event1LondonFSAC = Event("1", EventType.FSAC, "Description", "London", VenueType.LONDON_FSAC,
      new LocalDate("2017-07-02"), 24, 10, 1, LocalTime.now(), LocalTime.now().plusHours(1),
      Map(
        SkillType.ASSESSOR.toString -> 6,
        SkillType.QUALITY_ASSURANCE_COORDINATOR.toString -> 3,
        SkillType.CHAIR.toString -> 2))

    val event2LondonFSAC = event1LondonFSAC.copy(id = "2")
    val event1NewcastleFSAC = event2LondonFSAC.copy(id = "3", location = "Newcastle", venue = VenueType.NEWCASTLE_FSAC)
    val event2NewcastleFSAC = event1NewcastleFSAC.copy(id = "4", venue = VenueType.NEWCASTLE_LONGBENTON)
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

    Future.successful(Ok(Json.toJson(eventsWithAllocationSummary)))
  }
  // scalastyle:on method.length
}
