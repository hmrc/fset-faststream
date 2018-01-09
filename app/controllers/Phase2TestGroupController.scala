/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.mvc.Action
import services.onlinetesting.phase2.Phase2TestService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object Phase2TestGroupController extends Phase2TestGroupController {
  val phase2TestService = Phase2TestService
}

trait Phase2TestGroupController extends BaseController {
  val phase2TestService: Phase2TestService

  def extend(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestExtension] { extension =>
      phase2TestService.extendTestGroupExpiryTime(applicationId, extension.extraDays,
        extension.actionTriggeredBy) map ( _ => Ok )
    }
  }
}
