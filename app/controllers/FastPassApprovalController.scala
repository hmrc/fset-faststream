/*
 * Copyright 2021 HM Revenue & Customs
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
import model.command.{ FastPassEvaluation, ProcessedFastPassCandidate }
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{ Action, ControllerComponents }
import services.fastpass.FastPassService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class FastPassApprovalController @Inject() (cc: ControllerComponents,
                                            fastPassService: FastPassService) extends BackendController(cc) {

  def processFastPassCandidate(userId: String, applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FastPassEvaluation] { req =>
      fastPassService.processFastPassCandidate(userId, applicationId, req.accepted, req.triggeredBy).map { fullName =>
        val (firstName, lastName) = fullName
        Ok(Json.toJson(ProcessedFastPassCandidate(firstName, lastName)))
      }.recover {
        case ex : IllegalStateException =>
          Logger.warn(ex.getMessage)
          Forbidden(ex.getMessage)
      }
    }
  }
}
