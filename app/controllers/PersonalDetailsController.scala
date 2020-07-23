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

import javax.inject.{ Inject, Singleton }
import model.Exceptions._
import model.command.GeneralDetails
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.AuditService
import services.personaldetails.PersonalDetailsService
import uk.gov.hmrc.play.bootstrap.controller.BaseController
//import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

//object PersonalDetailsController extends PersonalDetailsController {
//  val personalDetailsService = PersonalDetailsService
//  val auditService = AuditService
//}

@Singleton
class PersonalDetailsController @Inject() (personalDetailsService: PersonalDetailsService,
                                           auditService: AuditService
                                          ) extends BaseController {
  val PersonalDetailsSavedEvent = "PersonalDetailsSaved"

  def update(userId: String, applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[GeneralDetails] { req =>
      personalDetailsService.update(applicationId, userId, req) map { _ =>
        auditService.logEvent(PersonalDetailsSavedEvent)
        Created
      } recover {
        case e: CannotUpdateContactDetails => BadRequest(s"cannot update contact details for user: ${e.userId}")
        case e: CannotUpdateRecord => BadRequest(s"cannot update personal details record with applicationId: ${e.applicationId}")
        case e: CannotUpdateCivilServiceExperienceDetails =>
          BadRequest(s"cannot update fast pass details record with applicationId: ${e.applicationId}")
      }
    }
  }

  def find(userId: String, applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    personalDetailsService.find(applicationId, userId) map { candidateDetails =>
      Ok(Json.toJson(candidateDetails))
    } recover {
      case e: ContactDetailsNotFound => NotFound(s"Cannot find contact details for userId: ${e.userId}")
      case e: PersonalDetailsNotFound =>
        NotFound(s"Cannot find personal details for applicationId: ${e.applicationId}")
      case e: CivilServiceExperienceDetailsNotFound =>
        NotFound(s"Cannot find fast pass details for applicationId: ${e.applicationId}")
    }
  }

  def findByApplicationId(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    personalDetailsService.find(applicationId) map { candidateDetails =>
      Ok(Json.toJson(candidateDetails))
    } recover {
      case e: PersonalDetailsNotFound =>
        NotFound(s"Cannot find personal details for applicationId: ${e.applicationId}")
    }
  }
}
