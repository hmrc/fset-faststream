/*
 * Copyright 2023 HM Revenue & Customs
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

import connectors.exchange.{ AssessorAllocationRequest, FindAssessorsByIdsRequest }
import javax.inject.{ Inject, Singleton }
import model.AllocationStatuses.AllocationStatus
import model.Exceptions._
import model.exchange._
import model.persisted.eventschedules.SkillType.SkillType
import model.{ AllocationStatuses, UniqueIdentifier }
import org.joda.time.LocalDate
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import services.AuditService
import services.assessor.AssessorService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future

@Singleton
class AssessorController @Inject() (cc: ControllerComponents,
                                    assessorService: AssessorService,
                                    auditService: AuditService
                                   ) extends BackendController(cc) {

  implicit val ec = cc.executionContext

  def saveAssessor(userId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Assessor] { assessor =>
      assessorService.saveAssessor(userId, assessor).map { _ =>
        auditService.logEvent("AssessorSaved", Map("assessor" -> assessor.toString))
        Ok
      }.recover {
        case e: OptimisticLockException => Conflict(e.getMessage)
        case e: CannotUpdateAssessorWhenSkillsAreRemovedAndFutureAllocationExistsException => FailedDependency(e.getMessage)
      }
    }
  }

  def saveAvailability(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[AssessorAvailabilities] { availability =>
      assessorService.saveAvailability(availability).map(_ => Ok).recover {
        case e: OptimisticLockException => Conflict(e.getMessage)
      }
    }
  }

  def findAssessor(userId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorService.findAssessor(userId) map { assessor =>
      Ok(Json.toJson(assessor))
    } recover {
      case e: AssessorNotFoundException => NotFound(s"Cannot find assessor userId: ${e.userId}")
    }
  }

  def findAssessorsByIds(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FindAssessorsByIdsRequest] { req =>
      assessorService.findAssessorsByIds(req.userIds).map { assessors =>
        Ok(Json.toJson(assessors))
      }
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

  def findAssessorAllocations(assessorId: String, status: Option[AllocationStatus]): Action[AnyContent] = Action.async { implicit request =>
    assessorService.findAssessorAllocations(assessorId, status).map(allocations => Ok(Json.toJson(allocations)))
  }

  def findAllocations(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[AssessorAllocationRequest] { reqBody =>
      val res = assessorService.findAllocations(reqBody.assessorIds, reqBody.status.map(AllocationStatuses.withName))
      res.map(r => Ok(Json.toJson(r)))
    }
  }

  def updateAllocationStatuses(assessorId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Seq[UpdateAllocationStatusRequest]] { statusUpdate =>
      if (!statusUpdate.forall(_.assessorId == assessorId)) {
        Future(BadRequest("Assessor allocation update requests must be for the same assessor"))
      } else {
        assessorService.updateAssessorAllocationStatuses(statusUpdate).map { updateResult =>
          Ok(Json.toJson(UpdateAllocationStatusResponse(updateResult.successes, updateResult.failures)))
        }
      }
    }
  }

  def removeAssessor(userId: UniqueIdentifier): Action[AnyContent] = Action.async { implicit request =>
    assessorService.remove(userId).map {
      _ => {
        auditService.logEvent("AssessorRemoved", Map("userId" -> userId.toString()))
        Ok
      }
    }.recover {
      case e: CannotRemoveAssessorWhenFutureAllocationExistsException => Conflict(e.getMessage)
      case e: AssessorNotFoundException => NotFound(e.getMessage)
    }
  }
}
