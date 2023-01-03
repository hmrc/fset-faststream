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

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, ControllerComponents }
import services.onlinetesting.phase2.Phase2TestService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class Phase2TestGroupController @Inject() (cc: ControllerComponents,
                                           phase2TestService: Phase2TestService) extends BackendController(cc) {

  def extend(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestExtension] { extension =>
      phase2TestService.extendTestGroupExpiryTime(applicationId, extension.extraDays,
        extension.actionTriggeredBy) map ( _ => Ok )
    }
  }
}
