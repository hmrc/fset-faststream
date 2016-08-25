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

import model.Exceptions._
import model.command.GeneralDetailsExchange
import play.api.libs.json.Json
import play.api.mvc.Action
import services.AuditService
import services.generaldetails.CandidateDetailsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object CandidateDetailsController extends CandidateDetailsController {
  val candidateDetailsService = CandidateDetailsService
  val auditService = AuditService
}

trait CandidateDetailsController extends BaseController {
  val PersonalDetailsSavedEvent = "PersonalDetailsSaved"

  val candidateDetailsService: CandidateDetailsService
  val auditService: AuditService

  def updateDetails(userId: String, applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[GeneralDetailsExchange] { req =>
      candidateDetailsService.update(applicationId, userId, req) map { _ =>
        auditService.logEvent(PersonalDetailsSavedEvent)
        Created
      } recover {
        case e: CannotUpdateContactDetails => BadRequest(s"cannot update contact details for user: ${e.userId}")
        case e: CannotUpdateRecord => BadRequest(s"cannot update personal details record with applicationId: ${e.applicationId}")
        case e: CannotUpdateFastPassDetails => BadRequest(s"cannot update fast pass details record with applicationId: ${e.applicationId}")
      }
    }
  }

  def find(userId: String, applicationId: String) = Action.async { implicit request =>
    candidateDetailsService.find(applicationId, userId) map { candidateDetails =>
      Ok(Json.toJson(candidateDetails))
    } recover {
      case e: ContactDetailsNotFound => NotFound(s"Cannot find contact details for userId: ${e.userId}")
      case e: PersonalDetailsNotFound =>
        NotFound(s"Cannot find personal details for applicationId: ${e.applicationId}")
      case e: FastPassDetailsNotFound =>
        NotFound(s"Cannot find fast pass details for applicationId: ${e.applicationId}")
    }
  }
}
