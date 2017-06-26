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
import play.api.mvc.{Action, AnyContent}
import repositories.events.{ LocationsWithVenuesRepository, LocationsWithVenuesYamlRepository}

import scala.concurrent.Future
import scala.util.Try
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object EventsController extends EventsController {
  val eventsService: EventsService = EventsService
  val locationsAndVenues: LocationsWithVenuesRepository = LocationsWithVenuesYamlRepository
}

trait EventsController extends BaseController {
  def eventsService: EventsService
  def locationsAndVenues: LocationsWithVenuesRepository

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

  def fetchEvents(eventTypeParam: String, venueParam: String): Action[AnyContent] = Action.async { implicit request =>
    val events =  Try {
        val eventType = EventType.withName(eventTypeParam.toUpperCase)
        locationsAndVenues.venue(venueParam).flatMap { venue =>
          eventsService.fetchEvents(eventType, venue).map { events =>
            Ok(Json.toJson(events))
          }
        }
    }

    play.api.Logger.debug(s"$events")

    Future.fromTry(events) flatMap identity recover {
      case e: NoSuchElementException => BadRequest(e.getMessage)
    }
  }
}
