/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.Exceptions.{EventNotFoundException, NotFoundException, OptimisticLockException, TooManyEntries}
import model.exchange.{AssessorAllocations, Event => ExchangeEvent}
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{Event, EventType, UpdateEvent}
import model.{command, exchange}
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.application.GeneralApplicationRepository
import repositories.events.{LocationsWithVenuesRepository, UnknownVenueException}
import services.allocation.AssessorAllocationService
import services.events.EventsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class EventsController @Inject() (cc: ControllerComponents,
                                  eventsService: EventsService,
                                  locationsAndVenuesRepository: LocationsWithVenuesRepository,
                                  assessorAllocationService: AssessorAllocationService,
                                  applicationRepository: GeneralApplicationRepository
                                 ) extends BackendController(cc) with Logging {

  implicit val ec = cc.executionContext

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

  def updateEvent(eventId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[UpdateEvent] { eventUpdate =>
      eventsService.update(eventUpdate).map { _ => Ok }
    }
  }

  def getEvent(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    eventsService.getEvent(eventId).map { event =>
      Ok(Json.toJson(event))
    }.recover {
      case _: EventNotFoundException => NotFound(s"No event found with id $eventId")
    }
  }

  def getEvents(eventTypeParam: String, venueParam: String, description: Option[String] = None) = Action.async { implicit request =>
    locationsAndVenuesRepository.venue(venueParam).flatMap { venue =>
      eventsService.getEvents(EventType.withName(eventTypeParam.toUpperCase), venue, description).map { events =>
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

  def delete(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    eventsService.delete(eventId).map(_ => Ok)
  }

  def getAssessorAllocation(eventId: String, userId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorAllocationService.getAllocation(eventId, userId).map {
      case Some(allocation) => Ok(Json.toJson(allocation))
      case None => NotFound
    }
  }

  def allocateAssessor(eventId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.AssessorAllocations] { assessorAllocations =>
      val newAllocations = command.AssessorAllocations.fromExchange(eventId, assessorAllocations)
      assessorAllocationService.allocate(newAllocations).map(_ => Ok)
          .recover {
            case e: OptimisticLockException =>
              logger.warn(s"Error occurred when allocating assessor for event:$eventId: ${e.getMessage} - " +
                s"will return http $CONFLICT}")
              Conflict(e.getMessage)
          }
    }
  }

  def getEventsWithAllocationsSummary(
    venueName: String,
    eventType: EventType,
    description: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.venue(venueName).flatMap { venue =>
      eventsService.getEventsWithAllocationsSummary(venue, eventType, description).map { eventsWithAllocations =>
        Ok(Json.toJson(eventsWithAllocations))
      }
    }
  }

  def getEventsWithAllocationsSummaryWithDescription(venueName: String, eventType: EventType, description: String) =
    getEventsWithAllocationsSummary(venueName, eventType, Some(description))

  def addNewAttributes() = Action.async { implicit request =>
    eventsService.updateStructure().map(_ => Ok)
  }

  def deleteOneAllocation(eventId: String, assessorId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorAllocationService.deleteOneAllocation(eventId, assessorId).map(_ => Ok)
   .recover {
     case ex @ (_: NotFoundException | _: TooManyEntries) =>
       val message = s"Error occurred: ${ex.getMessage}"
       logger.warn(s"Error occurred: $message")
       BadRequest(message)
    }
  }
}
