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

package controllers

import model.Exceptions._
import model.exchange.AssistanceDetailsExchange
import play.api.libs.json.Json
import play.api.mvc.Action
import services.AuditService
import services.assistancedetails.AssistanceDetailsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssistanceDetailsController extends AssistanceDetailsController {
  val assistanceDetailsService = AssistanceDetailsService
  val auditService = AuditService
}

trait AssistanceDetailsController extends BaseController {
  val AssistanceDetailsSavedEvent = "AssistanceDetailsSaved"

  val assistanceDetailsService: AssistanceDetailsService
  val auditService: AuditService

  def update(userId: String, applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[AssistanceDetailsExchange] { req =>
      assistanceDetailsService.update(applicationId, userId, req) map { _ =>
        auditService.logEvent(AssistanceDetailsSavedEvent)
        Created
      } recover {
        case e: CannotUpdateAssistanceDetails => BadRequest(s"cannot update assistance details for userId: ${e.userId}")
      }
    }
  }

  def find(userId: String, applicationId: String) = Action.async { implicit request =>
    assistanceDetailsService.find(applicationId, userId) map { assistanceDetails =>
      Ok(Json.toJson(assistanceDetails))
    } recover {
      case e: AssistanceDetailsNotFound => NotFound(s"Cannot find assistance details for applicationId: ${e.id}")
    }
  }
}
