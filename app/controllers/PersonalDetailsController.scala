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

import model.Commands._
import model.Exceptions.{ CannotUpdateContactDetails, CannotUpdateRecord, ContactDetailsNotFound, PersonalDetailsNotFound }
import model.PersistedObjects.{ ContactDetails, PersonalDetails }
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories._
import repositories.application.PersonalDetailsRepository
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object PersonalDetailsController extends PersonalDetailsController {
  val psRepository = personalDetailsRepository
  val cdRepository = contactDetailsRepository
  val auditService = AuditService
}

trait PersonalDetailsController extends BaseController {

  import model.Commands.Implicits._

  val psRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val auditService: AuditService

  def personalDetails(userId: String, applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[UpdateGeneralDetails] { req =>
      val personalDetails = PersonalDetails(req.firstName, req.lastName, req.preferredName, req.dateOfBirth, req.aLevel, req.stemLevel)
      val contactDetails = ContactDetails(req.address, req.postCode, req.email, req.phone)

      (for {
        _ <- psRepository.update(applicationId, userId, personalDetails)
        _ <- cdRepository.update(userId, contactDetails)
      } yield {
        auditService.logEvent("PersonalDetailsSaved")
        Created
      }).recover {
        case e: CannotUpdateContactDetails => BadRequest(s"cannot update contact details for user: ${e.userId}")
        case e: CannotUpdateRecord => BadRequest(s"cannot update personal details record with applicationId: ${e.applicationId}")
      }
    }
  }

  def find(userId: String, applicationId: String) = Action.async { implicit request =>
    (for {
      pd <- psRepository.find(applicationId)
      cd <- cdRepository.find(userId)
    } yield {
      Ok(Json.toJson(UpdateGeneralDetails(
        pd.firstName, pd.lastName, pd.preferredName, cd.email, pd.dateOfBirth,
        cd.address, cd.postCode, cd.phone,
        pd.aLevel, pd.stemLevel
      )))
    }) recover {
      case e: ContactDetailsNotFound => NotFound(s"cannot find contact details for userId: ${e.userId}")
      case e: PersonalDetailsNotFound =>
        NotFound(s"cannot find personal details for applicationId: ${e.applicationId}")
    }
  }
}
