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

import model.Commands.{ AssistanceDetailsExchange, Implicits }
import model.Exceptions.AssistanceDetailsNotFound
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories._
import repositories.application.AssistanceDetailsRepository
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssistanceController extends AssistanceController {
  val asRepository = assistanceRepository
  val auditService = AuditService
}

trait AssistanceController extends BaseController {
  import Implicits._

  val asRepository: AssistanceDetailsRepository
  val auditService: AuditService

  def assistanceDetails(userId: String, applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[AssistanceDetailsExchange] { req =>
      (for {
        _ <- asRepository.update(applicationId, userId, req)
      } yield {
        auditService.logEvent("AssistanceDetailsSaved")
        Created
      }).recover {
        case e: AssistanceDetailsNotFound => BadRequest(s"cannot update assistance details for user: ${e.id}")
      }
    }
  }

  def findAssistanceDetails(userId: String, applicationId: String) = Action.async { implicit request =>
    (for {
      ad <- asRepository.find(applicationId)
    } yield {
      Ok(Json.toJson(ad))
    }).recover {
      case e: AssistanceDetailsNotFound => NotFound(s"cannot find assistance details for user: ${e.id}")
    }
  }
}
