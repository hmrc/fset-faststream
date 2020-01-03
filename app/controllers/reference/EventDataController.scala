/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.reference

import model.exchange.candidateevents.CandidateRemoveReason
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

object EventDataController extends EventDataController {
  val eventsService: EventsService = EventsService
}

trait EventDataController extends BaseController {

  def eventsService: EventsService

  def getFsbTypes: Action[AnyContent] = Action { implicit request =>
    Ok(Json.toJson(eventsService.getFsbTypes))
  }

  def candidateRemoveReasons: Action[AnyContent] = Action { implicit request =>
    Ok(Json.toJson(CandidateRemoveReason.Values))
  }
}
