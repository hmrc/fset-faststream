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

import play.api.libs.json.JsValue
import play.api.mvc.Action
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.stc.StcEventService
import services.onlinetesting.OnlineTestExtensionService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1TestGroupController extends Phase1TestGroupController {
  override val phase1Repository = phase1TestRepository
  override val phase1TestExtensionService = OnlineTestExtensionService
  val eventService: StcEventService = StcEventService
}

trait Phase1TestGroupController extends BaseController {

  val phase1Repository: Phase1TestRepository
  val phase1TestExtensionService: OnlineTestExtensionService
  val eventService: StcEventService

  def extend(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestExtension] { extension =>
      phase1TestExtensionService.extendTestGroupExpiryTime(applicationId, extension.extraDays,
        extension.actionTriggeredBy
      ).map( _ => Ok )
    }
  }
}
