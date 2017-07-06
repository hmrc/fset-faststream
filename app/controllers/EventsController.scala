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
import model.persisted.eventschedules.EventType
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import repositories.events.{ LocationsWithVenuesRepository, LocationsWithVenuesInMemoryRepository, UnknownVenueException }

import scala.concurrent.Future
import scala.util.Try
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object EventsController extends EventsController {
  val eventsService: EventsService = EventsService
  val locationsAndVenues: LocationsWithVenuesRepository = LocationsWithVenuesInMemoryRepository
}

trait EventsController extends BaseController {
  def eventsService: EventsService
  def locationsAndVenues: LocationsWithVenuesRepository

  def venuesForEvents: Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenues.venues.map(x => Ok(Json.toJson(x)))
  }

  def locationsForEvents: Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenues.locations.map(x => Ok(Json.toJson(x)))
  }

  def saveAssessmentEvents(): Action[AnyContent] = Action.async { implicit request =>
    eventsService.saveAssessmentEvents().map(_ => Created("Events saved"))
      .recover { case e: Exception => UnprocessableEntity(e.getMessage) }
  }

  def getEvent(eventId: String): Action[AnyContent] = Action.async { implicit request =>
    eventsService.getEvent(eventId).map { event =>
      Ok(Json.toJson(event))
    }.recover {
      case _: EventNotFoundException => NotFound(s"No event found with id $eventId")
    }
  }

  def getEvents(eventTypeParam: String, venueParam: String): Action[AnyContent] = Action.async { implicit request =>
    val events = Try {
      val eventType = EventType.withName(eventTypeParam.toUpperCase)
      locationsAndVenues.venue(venueParam).flatMap { venue =>
        eventsService.getEvents(eventType, venue).map { events =>
          Ok(Json.toJson(events))
        }
      }
    }

    play.api.Logger.debug(s"$events")

    Future.fromTry(events) flatMap identity recover {
      case _: NoSuchElementException => BadRequest(s"$eventTypeParam is not a valid event type")
      case _: UnknownVenueException => BadRequest(s"$venueParam is not a valid venue")
    }
  }
}
