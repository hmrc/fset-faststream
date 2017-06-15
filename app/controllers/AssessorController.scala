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

import model.Exceptions.AssessorNotFoundException
import model.exchange.{ Assessor, AssessorAvailability }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.assessoravailability.AssessorService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssessorController extends AssessorController {
  val assessorService = AssessorService
}

trait AssessorController extends BaseController {

  val assessorService: AssessorService


  def saveAssessor(userId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Assessor] { assessor =>
      assessorService.saveAssessor(userId, assessor).map(_ =>
        Ok
    )}
  }

  def addAvailability(userId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[AssessorAvailability] { availability =>
      assessorService.addAvailability(userId, availability).map(_ =>
        Ok
    )}
  }

  def findAssessor(userId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorService.findAssessor(userId) map { assessor =>
      Ok(Json.toJson(assessor))
    } recover {
      case e: AssessorNotFoundException => NotFound(s"Cannot find assessor userId: ${e.userId}")
    }
  }

  def findAvailability(userId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorService.findAvailability(userId) map { assessor =>
      Ok(Json.toJson(assessor))
    } recover {
      case e: AssessorNotFoundException => NotFound(s"Cannot find assessor userId: ${e.userId}")
    }
  }

  def countSubmittedAvailability(): Action[AnyContent] = Action.async { implicit request =>
    assessorService.countSubmittedAvailability().map { count =>
      Ok(Json.obj("size" -> count))
    } recover {
      case ex: Throwable => InternalServerError("Could not retrieve a count of submitted assessor availabilities")
    }
  }
}
