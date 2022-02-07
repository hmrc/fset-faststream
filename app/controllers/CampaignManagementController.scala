/*
 * Copyright 2022 HM Revenue & Customs
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

import model.Exceptions.NotFoundException

import javax.inject.{Inject, Singleton}
import model.command.SetTScoreRequest
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.campaignmanagement.CampaignManagementService
import services.search.SearchForApplicantService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CampaignManagementController @Inject() (cc: ControllerComponents,
                                              campaignManagementService: CampaignManagementService,
                                              searchForApplicantService: SearchForApplicantService) extends BackendController(cc) {

  def afterDeadlineSignupCodeUnusedAndValid(code: String): Action[AnyContent] = Action.async { implicit request =>
    campaignManagementService.afterDeadlineSignupCodeUnusedAndValid(code).map(response => Ok(Json.toJson(response)))
  }

  def generateAfterDeadlineSignupCode(createdByUserId: String, expiryInHours: Int): Action[AnyContent] = Action.async { implicit request =>
    campaignManagementService.generateAfterDeadlineSignupCode(createdByUserId, expiryInHours).map(code => Ok(Json.toJson(code)))
  }

  def markSignupCodeAsUsed(code: String, applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    campaignManagementService.markSignupCodeAsUsed(code, applicationId).map(_ => Ok)
  }

  def listCollections: Action[AnyContent] = Action.async { implicit request =>
    campaignManagementService.listCollections.map(Ok(_))
  }

  def removeCollection(name: String): Action[AnyContent] = Action.async { implicit request =>
    campaignManagementService.removeCollection(name).map(_ => Ok)
  }

  def setTScore = Action.async(parse.json) { implicit request =>
    withJsonBody[SetTScoreRequest] { tScoreRequest =>
      tScoreRequest.phase.toUpperCase match {
        case "PHASE1" =>
          campaignManagementService.setPhase1TScore(tScoreRequest).map(_ => Ok)
            .recover{
              case e: Exception => Forbidden(e.getMessage)
            }
        case "PHASE2" =>
          campaignManagementService.setPhase2TScore(tScoreRequest).map(_ => Ok)
            .recover{
              case e: Exception => Forbidden(e.getMessage)
            }
        case _ =>
          Future.successful(BadRequest(s"${tScoreRequest.phase} is not a valid value"))
      }
    }
  }

  def findCandidateByUserId(userId: String): Action[AnyContent] = Action.async { implicit request =>
    searchForApplicantService.findCandidateByUserId(userId).map {
      case None => NotFound
      case Some(candidate) => Ok(Json.toJson(candidate))
    }
  }

  def removeCandidate(applicationId: String, userId: String): Action[AnyContent] = Action.async { implicit request =>
    campaignManagementService.removeCandidate(applicationId, userId).map(_ => Ok)
      .recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
      }
  }
}
