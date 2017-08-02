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

import model.Exceptions.EventNotFoundException
import model.assessmentscores._
import model.UniqueIdentifier
import model.command.AssessmentScoresCommands.{ AssessmentExerciseType, AssessmentScoresSubmitRequest }
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Action
import repositories.AssessmentScoresRepository
import services.AuditService
import services.assessmentscores.{ AssessmentScoresService, AssessorAssessmentScoresService, ReviewerAssessmentScoresService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessmentScoresController extends BaseController {
  val service: AssessmentScoresService
  val auditService: AuditService
  val repository: AssessmentScoresRepository

  val AssessmentScoresAllExercisesSaved: String
  val AssessmentScoresOneExerciseSaved: String
  val UserIdForAudit: String

  def submit() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresSubmitRequest] { submitRequest =>
        val assessmentExerciseType = AssessmentExerciseType.withName(submitRequest.exercise)
        service.saveExercise(
          submitRequest.applicationId,
          assessmentExerciseType,
          submitRequest.scoresAndFeedback
        ).map { _ =>
          val auditDetails = Map(
            "applicationId" -> submitRequest.applicationId.toString(),
            "exercise" -> assessmentExerciseType.toString,
            UserIdForAudit -> submitRequest.scoresAndFeedback.updatedBy.toString())
          auditService.logEvent(AssessmentScoresOneExerciseSaved, auditDetails)
        }.map (_ => Ok)
      }
  }

  def save() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresAllExercises] { scores =>
        service.save(scores).map { _ =>
          val auditDetails = Map(
            "applicationId" -> scores.applicationId.toString(),
            "assessorId" -> scores.analysisExercise.map(_.updatedBy.toString).getOrElse("Unknown")
          )
          auditService.logEvent(AssessmentScoresAllExercisesSaved, auditDetails)
        }.map(_ => Ok)
      }
  }

  def findAssessmentScoresWithCandidateSummaryByApplicationId(applicationId: UniqueIdentifier) = Action.async { implicit request =>
    service.findAssessmentScoresWithCandidateSummaryByApplicationId(applicationId).map { scores =>
      Ok(Json.toJson(scores))
    }.recover {
      case ex: EventNotFoundException => {
        Logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByApplicationId: $ex")
        NotFound
      }
      case other: Throwable =>
        Logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByApplicationId: $other")
        InternalServerError(other.getMessage)
    }
  }

  def findAssessmentScoresWithCandidateSummaryByEventId(eventId: UniqueIdentifier) = Action.async { implicit request =>
    service.findAssessmentScoresWithCandidateSummaryByEventId(eventId).map { scores =>
      Ok(Json.toJson(scores))
    }.recover {
      case ex: EventNotFoundException => {
        Logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByEventId: $ex")
        NotFound
      }
      case other: Throwable =>
        Logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByEventId: $other")
        InternalServerError(other.getMessage)
    }
  }

  def find(applicationId: UniqueIdentifier) = Action.async { implicit request =>
    repository.find(applicationId).map { scores =>
      Ok(Json.toJson(scores))
    }
  }

  def findAll = Action.async { implicit request =>
    repository.findAll.map { scores =>
      Ok(Json.toJson(scores))
    }
  }
}

object AssessorAssessmentScoresController extends AssessmentScoresController {
  val service: AssessmentScoresService = AssessorAssessmentScoresService
  val auditService: AuditService = AuditService
  val repository: AssessmentScoresRepository = repositories.assessorAssessmentScoresRepository
  val AssessmentScoresAllExercisesSaved = "AssessorAssessmentScoresAllExercisesSaved"
  val AssessmentScoresOneExerciseSaved = "AssessorAssessmentScoresOneExerciseSaved"
  val UserIdForAudit = "reviewerId"
}

object ReviewerAssessmentScoresController extends AssessmentScoresController {
  val service: AssessmentScoresService = ReviewerAssessmentScoresService
  val auditService: AuditService = AuditService
  val repository: AssessmentScoresRepository = repositories.reviewerAssessmentScoresRepository
  val AssessmentScoresAllExercisesSaved = "ReviewerAssessmentScoresAllExercisesSaved"
  val AssessmentScoresOneExerciseSaved = "ReviewerAssessmentScoresOneExerciseSaved"
  val UserIdForAudit = "assessorId"
}
