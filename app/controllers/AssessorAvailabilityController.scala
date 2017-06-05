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

import model.Exceptions.AssessorAvailabilityNotFoundException
import model.exchange.AssessorAvailability
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import services.assessoravailability.AssessorAvailabilityService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssessorAvailabilityController extends AssessorAvailabilityController {
  val assessorAvailabilityService = AssessorAvailabilityService
}

trait AssessorAvailabilityController extends BaseController {

  val assessorAvailabilityService: AssessorAvailabilityService

  def save(userId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[AssessorAvailability] { availability => assessorAvailabilityService.save(userId, availability).map ( _ =>
        Ok(s"I was called with userId: $userId")
    )}
  }

  def find(userId: String) = Action.async { implicit request =>
    assessorAvailabilityService.find(userId) map { availability =>
      Ok(Json.toJson(availability))
    } recover {
      case e: AssessorAvailabilityNotFoundException => NotFound(s"Cannot find assessor availability userId: ${e.userId}")
    }
  }

  def countSubmitted(): Action[AnyContent] = Action.async { implicit request =>
    assessorAvailabilityService.countSubmitted().map { count =>
      Ok(Json.obj("size" -> count))
    } recover {
      case ex: Throwable => InternalServerError("Could not retrieve a count of submitted assessor availabilities")
    }
  }
}
