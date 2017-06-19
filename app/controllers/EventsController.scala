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

import model.persisted.eventschedules.{ EventType, VenueType }
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.events.EventsRepository
import services.assessmentcentre._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object EventsController extends EventsController {
  override val assessmentEventsRepository: EventsRepository = repositories.eventsRepository
  override val assessmentCenterParsingService: AssessmentCentreParsingService = AssessmentCentreParsingService
}

trait EventsController extends BaseController {
  val assessmentEventsRepository: EventsRepository
  val assessmentCenterParsingService: AssessmentCentreParsingService

  def saveAssessmentEvents() = Action.async { implicit request =>
    assessmentCenterParsingService.processCentres().flatMap{ events =>
      Logger.debug("Events have been processed!")
      assessmentEventsRepository.save(events)
    }.map(_ => Created).recover { case _ => UnprocessableEntity }
  }

  def fetchEvents(eventTypeParam: String, venueParam: String) = Action.async { implicit request =>
    // convert params to native enum type
    val eventType = EventType.withName(eventTypeParam.toUpperCase)
    val venue = VenueType.withName(venueParam.toUpperCase)

    assessmentEventsRepository.fetchEvents(Some(eventType), Some(venue), None, None)
      .map(events => Ok(Json.toJson(events)))
  }
}
