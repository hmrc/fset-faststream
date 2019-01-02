/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import services.stc.StcEventService
import services.onlinetesting.phase3.Phase3TestService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object Phase3TestGroupController extends Phase3TestGroupController {
  val phase3TestService = Phase3TestService
  val eventService: StcEventService = StcEventService
}

trait Phase3TestGroupController extends BaseController {
  val phase3TestService: Phase3TestService
  val eventService: StcEventService

  def getTestGroup(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    phase3TestService.getTestGroup(applicationId).map {
        case Some(testGroup) =>
          Ok(Json.toJson(testGroup))
        case None =>
          NotFound
    }
  }

  def extend(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestExtension] { extension =>
      phase3TestService.extendTestGroupExpiryTime(applicationId, extension.extraDays,
        extension.actionTriggeredBy) map ( _ => Ok )
    }
  }

  def unexpireCompleted(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    phase3TestService.removeExpiredStatus(applicationId).map(_ => Ok)
  }
}
