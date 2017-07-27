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

import model.Exceptions.{ EventNotFoundException, OptimisticLockException }
import model.persisted.eventschedules
import model.{ command, exchange }
import model.exchange.{ AssessorAllocations, EventSession, Session, Event => ExchangeEvent }
import model.persisted.CandidateAllocation
import model.persisted.eventschedules.{ Event, EventType }
import model.persisted.eventschedules.EventType.EventType
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import repositories.application.GeneralApplicationRepository
import repositories.events.{ LocationsWithVenuesInMemoryRepository, LocationsWithVenuesRepository, UnknownVenueException }
import services.allocation.{ AssessorAllocationService, CandidateAllocationService }
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object EventsController extends EventsController {
  val eventsService: EventsService = EventsService
  val locationsAndVenuesRepository: LocationsWithVenuesRepository = LocationsWithVenuesInMemoryRepository
  val assessorAllocationService: AssessorAllocationService = AssessorAllocationService
  val candidateAllocationService: CandidateAllocationService = CandidateAllocationService
  val applicationRepository: GeneralApplicationRepository = repositories.applicationRepository
}

trait EventsController extends BaseController {
  def eventsService: EventsService
  def locationsAndVenuesRepository: LocationsWithVenuesRepository
  def assessorAllocationService: AssessorAllocationService
  def applicationRepository: GeneralApplicationRepository
  def candidateAllocationService: CandidateAllocationService

  def saveAssessmentEvents(): Action[AnyContent] = Action.async { implicit request =>
    eventsService.saveAssessmentEvents().map(_ => Created("Events saved"))
      .recover { case e: Exception => UnprocessableEntity(e.getMessage) }
  }

  def createEvent(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ExchangeEvent] { event =>
      val persistedEvent = Event(event)
      eventsService.save(persistedEvent).map { _ =>
        Created
      }.recover { case e: Exception => UnprocessableEntity(e.getMessage) }
    }
  }

  def getEvent(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    eventsService.getEvent(eventId).map { event =>
      Ok(Json.toJson(event))
    }.recover {
      case _: EventNotFoundException => NotFound(s"No event found with id $eventId")
    }
  }

  def getEvents(eventTypeParam: String, venueParam: String): Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.venue(venueParam).flatMap { venue =>
      eventsService.getEvents(EventType.withName(eventTypeParam.toUpperCase), venue).map { events =>
        if (events.isEmpty) {
          NotFound
        } else {
          Ok(Json.toJson(events))
        }
      }
    } recover {
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

  def allocateAssessor(eventId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.AssessorAllocations] { assessorAllocations =>
      val newAllocations = command.AssessorAllocations.fromExchange(eventId, assessorAllocations)
      assessorAllocationService.allocate(newAllocations).map(_ => Ok)
          .recover {
            case e: OptimisticLockException => Conflict(e.getMessage)
          }
    }
  }

  def getEventsWithAllocationsSummary(venueName: String, eventType: EventType): Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.venue(venueName).flatMap { venue =>
      eventsService.getEventsWithAllocationsSummary(venue, eventType).map { eventsWithAllocations =>
        Ok(Json.toJson(eventsWithAllocations))
      }
    }
  }
  def allocateCandidates(eventId: String, sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.CandidateAllocations] { candidateAllocations =>
      val newAllocations = command.CandidateAllocations.fromExchange(eventId, sessionId, candidateAllocations)
      candidateAllocationService.allocateCandidates(newAllocations).map {
        _ => Ok
      }.recover {
        case e: OptimisticLockException => Conflict(e.getMessage)
      }
    }
  }

  def findSessionsForApplication(applicationId: String, sessionEventType: EventType): Action[AnyContent] = Action.async { implicit request =>
    candidateAllocationService.getSessionsForApplication(applicationId, sessionEventType).map { eventSessionMap =>
      val eventSessions: List[eventschedules.Event] = eventSessionMap.map(event => eventschedules.Event(model.exchange.Event(event)))

      Ok(Json.toJson(eventSessions))
    }
  }

  def getCandidateAllocations(eventId: String, sessionId: String): Action[AnyContent] = Action.async { implicit request =>
    candidateAllocationService.getCandidateAllocations(eventId, sessionId).map { allocations =>
      if (allocations.allocations.isEmpty) {
        NotFound
      } else {
        Ok(Json.toJson(allocations))
      }
    }
  }

  def removeCandidateAllocations(eventId: String, sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.CandidateAllocations] { candidateAllocs =>
      val allocations = CandidateAllocation.fromExchange(candidateAllocs, eventId, sessionId).toList
      candidateAllocationService.unAllocateCandidates(allocations).map( _ => Ok)
    }
  }
}
