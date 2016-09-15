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
import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController
import repositories._
import repositories.application.OnlineTestRepository
import services.onlinetesting.OnlineTestService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase1TestGroupController extends Phase1TestGroupController {
  override val phase1TestRepository = onlineTestRepository
  override val phase1TestService = OnlineTestService
}

trait Phase1TestGroupController extends BaseController {

  val phase1TestRepository: OnlineTestRepository
  val phase1TestService: OnlineTestService

  def getStatus(applicationId: String) = Action.async { implicit request =>
    for {
      phase1TestGroupOpt <- phase1TestRepository.getPhase1TestProfile(applicationId)
    } yield {
      phase1TestGroupOpt.map { phase1TestGroup => Ok(Json.toJson(phase1TestGroup)) }.getOrElse(NotFound)
    }
  }

  def extend(applicationId: String) = Action.async(parse.json) { implicit request =>
      //withJsonBody[OnlineTestExtension] { extension =>
        // phase1TestService.extendTestGroup("PHASE1", applicationId, extension.extraDays)
      //}
    Future.successful(Ok)
  }
}
