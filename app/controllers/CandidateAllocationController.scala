/*
 * Copyright 2018 HM Revenue & Customs
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

import model.Exceptions.OptimisticLockException
import model.persisted.CandidateAllocation
import model.persisted.eventschedules.EventType.EventType
import model.{ command, exchange }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.allocation.CandidateAllocationService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object CandidateAllocationController extends CandidateAllocationController {
  val candidateAllocationService: CandidateAllocationService = CandidateAllocationService
}

trait CandidateAllocationController extends BaseController {

  def candidateAllocationService: CandidateAllocationService

  def confirmAllocation(eventId: String, sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.CandidateAllocations] { candidateAllocations =>
      val newAllocations = command.CandidateAllocations.fromExchange(eventId, sessionId, candidateAllocations)
      candidateAllocationService.confirmCandidateAllocation(newAllocations).map {
        _ => Ok
      }.recover {
        case e: OptimisticLockException => Conflict(e.getMessage)
      }
    }
  }

  def allocateCandidates(eventId: String, sessionId: String, append: Boolean): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.CandidateAllocations] { candidateAllocations =>
      val newAllocations = command.CandidateAllocations.fromExchange(eventId, sessionId, candidateAllocations)
      candidateAllocationService.allocateCandidates(newAllocations, append).map {
        _ => Ok
      }.recover {
        case e: OptimisticLockException => Conflict(e.getMessage)
      }
    }
  }

  def getCandidateAllocations(eventId: String, sessionId: String): Action[AnyContent] = Action.async { implicit request =>
    candidateAllocationService.getCandidateAllocations(eventId, sessionId).map { allocations =>
      if (allocations.allocations.isEmpty) {
        NotFound
      } else {
        Ok(Json.toJson(allocations))
      }
    }
  }

  def removeCandidateAllocations(eventId: String, sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[exchange.CandidateAllocations] { candidateAllocs =>
      val allocations = CandidateAllocation.fromExchange(candidateAllocs, eventId, sessionId).toList
      candidateAllocationService.unAllocateCandidates(allocations).map(_ => Ok)
        .recover {
          case e: OptimisticLockException => Conflict(e.getMessage)
        }
    }
  }

  def findCandidatesEligibleForEventAllocation(
    assessmentCentreLocation: String,
    eventType: EventType,
    eventDescription: String): Action[AnyContent] = Action.async {
    implicit request =>
      candidateAllocationService.findCandidatesEligibleForEventAllocation(
        assessmentCentreLocation, eventType, eventDescription
      ) map { apps =>
        Ok(Json.toJson(apps))
      }
  }

  def findAllocatedApplications(): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[List[String]] { appIds =>
        candidateAllocationService.findAllocatedApplications(appIds).map { apps =>
          Ok(Json.toJson(apps))
        }
      }
  }

  def candidateAllocationsSummary(applicationId: String) = Action.async { implicit request =>
    candidateAllocationService.getCandidateAllocationsSummary(Seq(applicationId)) map {
      res => Ok(Json.toJson(res))
    }
  }

  def findSessionsForApplication(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    candidateAllocationService.getSessionsForApplication(applicationId).map { data =>
      Ok(Json.toJson(data))
    }
  }

  def removeCandidateRemovalReason(applicationId: String, eventType: EventType) = Action.async { implicit request =>
    candidateAllocationService.removeCandidateRemovalReason(applicationId, eventType).map { _ =>
      NoContent
    }
  }

  def addNewAttributes() = Action.async { implicit request =>
    candidateAllocationService.updateStructure().map(_ => Ok)
  }
}
