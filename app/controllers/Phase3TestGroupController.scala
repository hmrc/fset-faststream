/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.onlinetesting.{ Phase1TestRepository, Phase3TestRepository }
import services.events.EventService
import services.onlinetesting.{ OnlineTestExtensionService, Phase3TestService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object Phase3TestGroupController extends Phase3TestGroupController {
  override val phase3Repository = phase3TestRepository
  override val phase3TestService = Phase3TestService
  val eventService: EventService = EventService
}

trait Phase3TestGroupController extends BaseController {

  val phase3Repository: Phase3TestRepository
  val phase3TestService: Phase3TestService
  val eventService: EventService

  def get(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
      phase3Repository.getTestGroup(applicationId).map {
        case Some(testGroup) =>
          Ok(Json.toJson(testGroup))
        case None =>
          NotFound
    }
  }


}
