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

import model.Adjustments
import model.Exceptions._
import play.api.libs.json.Json
import play.api.mvc.Action
import services.adjustmentsmanagement.AdjustmentsManagementService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AdjustmentsManagementController extends AdjustmentsManagementController {
  val adjustmentsManagementService = AdjustmentsManagementService
}

trait AdjustmentsManagementController extends BaseController {
  val adjustmentsManagementService : AdjustmentsManagementService

  def confirmAdjustments(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[Adjustments] { data =>
      adjustmentsManagementService.confirmAdjustment(applicationId, data).map { _ =>
        Ok
      }.recover {
        case e: ApplicationNotFound => NotFound(s"cannot find application for application with id: ${e.id}")
      }
    }
  }

  def findAdjustments(applicationId: String) = Action.async { implicit request =>
    adjustmentsManagementService.find(applicationId).map { adjustments =>
      Ok(Json.toJson(adjustments))
    }
  }
}
