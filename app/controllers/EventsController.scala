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

  def allocateAssessor(eventId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.AssessorAllocations] { assessorAllocations =>
      val newAllocations = command.AssessorAllocations.fromExchange(eventId, assessorAllocations)
      assessorAllocationService.allocate(newAllocations).map( _ => Ok)
    }
  }

  def allocateCandidates(eventId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.CandidateAllocations] { candidateAllocations =>
      val newAllocations = command.CandidateAllocations.fromExchange(eventId, candidateAllocations)
      assessorAllocationService.allocateCandidates(newAllocations).map( _ => Ok)
    }
  }

  def candidateAllocations(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorAllocationService.getCandidateAllocations(eventId).map { allocations =>
      if (allocations.allocations.isEmpty) {
        NotFound
      } else {
        Ok(Json.toJson(allocations))
      }
    }
  }
}
