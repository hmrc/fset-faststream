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

import model.Exceptions.{ AssessorNotFoundException, NilUpdatesException }
import model.SuccessfulUpdateResult
import model.exchange.{ Assessor, AssessorAllocation, AssessorAvailability, UpdateAssessorAllocationStatus }
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import repositories.AllocationRepository
import services.assessoravailability.AssessorService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessorController extends AssessorController {
  val assessorService = AssessorService
}

trait AssessorController extends BaseController {

  val assessorService: AssessorService

  def saveAssessor(userId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Assessor] { assessor =>
      assessorService.saveAssessor(userId, assessor).map(_ => Ok)
    }
  }

  def addAvailability(userId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[List[AssessorAvailability]] { availability =>
      assessorService.addAvailability(userId, availability).map(_ => Ok)
    }
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
      case e: Throwable => InternalServerError(s"Could not retrieve a count of submitted assessor availabilities: ${e.getMessage}")
    }
  }

  def findAvailableAssessorsForLocationAndDate(locationName: String, date: LocalDate,
    skills: Seq[SkillType]
  ): Action[AnyContent] = Action.async { implicit request =>
    assessorService.findAvailabilitiesForLocationAndDate(locationName, date, skills).map { a => Ok(Json.toJson(a)) }
  }

  def findAllocations(assessorId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorService.findAllocations(assessorId).map(allocations => Ok(Json.toJson(allocations)))
  }

  def updateAllocationsStatuses(assessorId: String): Action[AnyContent] = Action.async { implicit request =>
    withJsonBody[Seq[UpdateAssessorAllocationStatus]] { statusUpates =>
      assessorService.updateAssessorAllocationStatuses(statusUpates).map {
        case SuccessfulUpdateResult(_) => Ok()
      }
    }

  }

}
