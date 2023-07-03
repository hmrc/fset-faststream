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

import com.google.inject.name.Named

import javax.inject.{Inject, Singleton}
import model.Exceptions.{CandidateAllocationNotFoundException, CannotUpdateRecord, EventNotFoundException, OptimisticLockException}
import model.UniqueIdentifier
import model.assessmentscores._
import model.command.AssessmentScoresCommands._
import play.api.Logging
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import repositories.AssessmentScoresRepository
import services.AuditService
import services.assessmentscores.AssessmentScoresService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

abstract class AssessmentScoresController(cc: ControllerComponents) extends BackendController(cc) with Logging {
  val service: AssessmentScoresService
  val repository: AssessmentScoresRepository
  val auditService: AuditService

  val AssessmentScoresOneExerciseSaved: String
  val AssessmentScoresOneExerciseSubmitted: String
  val AssessmentScoresAllExercisesSubmitted: String
  val UserIdForAudit: String

  implicit val ec = cc.executionContext

  def submitExercise() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresSubmitExerciseRequest] { submitRequest =>
        val assessmentExerciseType = AssessmentScoresSectionType.withName(submitRequest.exercise)
        service.submitExercise(
          submitRequest.applicationId,
          assessmentExerciseType,
          submitRequest.scoresExercise.toPersistence
        ).map { _ =>
          val auditDetails = Map(
            "applicationId" -> submitRequest.applicationId.toString(),
            "exercise" -> assessmentExerciseType.toString,
            UserIdForAudit -> submitRequest.scoresExercise.updatedBy.toString())
          auditService.logEvent(AssessmentScoresOneExerciseSubmitted, auditDetails)
        }.map (_ => Ok).recover {
          case e @ (_: OptimisticLockException | _: CannotUpdateRecord) =>
            logger.warn(s"FSAC error occurred while submitting exercise: ${e.getMessage} - data = $submitRequest")
            Conflict(e.getMessage)
        }
      }
  }

  def saveExercise() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresSaveExerciseRequest] { submitRequest =>
        val assessmentExerciseType = AssessmentScoresSectionType.withName(submitRequest.exercise)
        service.saveExercise(
          submitRequest.applicationId,
          assessmentExerciseType,
          submitRequest.scoresExercise.toPersistence
        ).map { _ =>
          val auditDetails = Map(
            "applicationId" -> submitRequest.applicationId.toString(),
            "exercise" -> assessmentExerciseType.toString,
            UserIdForAudit -> submitRequest.scoresExercise.updatedBy.toString())
          auditService.logEvent(AssessmentScoresOneExerciseSaved, auditDetails)
        }.map (_ => Ok).recover {
          case e @ (_: OptimisticLockException | _: CannotUpdateRecord) =>
            logger.warn(s"FSAC error occurred while saving exercise: ${e.getMessage} - data = $submitRequest")
            Conflict(e.getMessage)
        }
      }
  }

  def submitFinalFeedback() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresFinalFeedbackSubmitRequest] { submitRequest =>
        service.submitFinalFeedback(
          submitRequest.applicationId,
          submitRequest.finalFeedback.toPersistence
        ).map { _ =>
          val oneExerciseAuditDetails = Map(
            "applicationId" -> submitRequest.applicationId.toString(),
            "exercise" -> "finalFeedback",
            UserIdForAudit -> submitRequest.finalFeedback.updatedBy.toString())
          auditService.logEvent(AssessmentScoresOneExerciseSubmitted, oneExerciseAuditDetails)
          val allExercisesAuditDetails = Map(
            "applicationId" -> submitRequest.applicationId.toString(),
            UserIdForAudit -> submitRequest.finalFeedback.updatedBy.toString())
          auditService.logEvent(AssessmentScoresAllExercisesSubmitted, allExercisesAuditDetails)
        }.map (_ => Ok).recover {
          case e @ (_: OptimisticLockException | _: CannotUpdateRecord) =>
            logger.warn(s"FSAC error occurred while submitting final feedback: ${e.getMessage} - data = $submitRequest")
            Conflict(e.getMessage)
        }
      }
  }

  def save() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresAllExercises] { scores =>
        service.save(scores).map { _ =>
          val auditDetails = Map(
            "applicationId" -> scores.applicationId.toString(),
            "assessorId" -> scores.writtenExercise.map(_.updatedBy.toString).getOrElse("Unknown")
          )
          auditService.logEvent(AssessmentScoresAllExercisesSubmitted, auditDetails)
        }.map(_ => Ok).recover {
          case e @ (_: OptimisticLockException | _: CannotUpdateRecord) =>
            logger.warn(s"FSAC error occurred while saving scores: ${e.getMessage} - data = $scores")
            Conflict(e.getMessage)
        }
      }
  }

  def findAssessmentScoresWithCandidateSummaryByApplicationId(applicationId: UniqueIdentifier) = Action.async {
    service.findAssessmentScoresWithCandidateSummaryByApplicationId(applicationId).map { scores =>
      Ok(Json.toJson(scores))
    }.recover {
      case ex if ex.isInstanceOf[CandidateAllocationNotFoundException] || ex.isInstanceOf[EventNotFoundException] =>
        logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByApplicationId: $ex")
        NotFound
      case other: Throwable =>
        logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByApplicationId: $other")
        InternalServerError(other.getMessage)
    }
  }

  def findAssessmentScoresWithCandidateSummaryByEventId(eventId: UniqueIdentifier) = Action.async {
    service.findAssessmentScoresWithCandidateSummaryByEventId(eventId).map { scores =>
      Ok(Json.toJson(scores))
    }.recover {
      case ex: EventNotFoundException =>
        logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByEventId: $ex")
        NotFound
      case other: Throwable =>
        logger.error(s"Exception when calling findAssessmentScoresWithCandidateSummaryByEventId: $other")
        InternalServerError(other.getMessage)
    }
  }

  def find(applicationId: UniqueIdentifier) = Action.async {
    repository.find(applicationId).map { scores =>
      Ok(Json.toJson(scores))
    }
  }

  def findAll = Action.async {
    repository.findAll.map { scores =>
      Ok(Json.toJson(scores))
    }
  }

  def findAcceptedAssessmentScoresByApplicationId(applicationId: UniqueIdentifier) = Action.async {
    service.findAcceptedAssessmentScoresAndFeedbackByApplicationId(applicationId).map {
      case Some(scores) => Ok(Json.toJson(scores))
      case None => NotFound
    }.recover {
      case other: Throwable =>
        logger.error(s"Exception when calling findAssessmentScoresByApplicationId: $other")
        InternalServerError(other.getMessage)
    }
  }

  def resetExercises(applicationId: UniqueIdentifier) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[ResetExercisesRequest] { resetExercisesRequest =>
        val withWritten = if (resetExercisesRequest.written) List("writtenExercise") else List.empty[String]
        val withTeam = if (resetExercisesRequest.team) withWritten ++ List("teamExercise") else withWritten
        val withLeadership = if (resetExercisesRequest.leadership) withTeam ++ List("leadershipExercise") else withTeam
        val exercisesToRemove = if (resetExercisesRequest.finalFeedback) withLeadership ++ List("finalFeedback") else withLeadership

        repository.resetExercise(applicationId, exercisesToRemove).map { _ =>
          val auditDetails = Map(
            "applicationId" -> applicationId.toString(),
            "resetWrittenExercise" -> resetExercisesRequest.written.toString,
            "resetTeamExercise" -> resetExercisesRequest.team.toString,
            "resetLeadershipExercise" -> resetExercisesRequest.leadership.toString,
            "resetFinalFeedback" -> resetExercisesRequest.finalFeedback.toString
          )
          auditService.logEvent("AssessmentScoresReset", auditDetails)
          Ok
        }.recover {
          case other: Throwable =>
            logger.error(s"Exception when calling resetExercises with applicationId $applicationId: $other")
            InternalServerError(other.getMessage)
        }
      }
  }
}

@Singleton
class AssessorAssessmentScoresController @Inject() (@Named("AssessorAssessmentScoresService") val service: AssessmentScoresService,
                                                    @Named("AssessorAssessmentScoresRepo") val repository: AssessmentScoresRepository,
                                                    val auditService: AuditService,
                                                    cc: ControllerComponents
                                                   ) extends AssessmentScoresController(cc) {

  val AssessmentScoresOneExerciseSaved = "AssessorAssessmentScoresOneExerciseSaved"
  val AssessmentScoresOneExerciseSubmitted = "AssessorAssessmentScoresOneExerciseSubmitted"
  val AssessmentScoresAllExercisesSubmitted = "AssessorAssessmentScoresAllExercisesSubmitted"
  val UserIdForAudit = "reviewerId"

  override def submitFinalFeedback() = Action.async(parse.json) {
    implicit request =>
      throw new UnsupportedOperationException("This method is only applicable for a reviewer")
  }

  override def findAcceptedAssessmentScoresByApplicationId(applicationId: UniqueIdentifier) = Action.async {
    implicit request =>
      throw new UnsupportedOperationException("This method is only applicable for a reviewer")
  }
}

@Singleton
class ReviewerAssessmentScoresController @Inject() (@Named("ReviewerAssessmentScoresService") val service: AssessmentScoresService,
                                                    @Named("ReviewerAssessmentScoresRepo") val repository: AssessmentScoresRepository,
                                                    val auditService: AuditService,
                                                    cc: ControllerComponents
                                                   ) extends AssessmentScoresController(cc) {

  val AssessmentScoresOneExerciseSaved = "ReviewerAssessmentScoresOneExerciseSaved"
  val AssessmentScoresOneExerciseSubmitted = "ReviewerAssessmentScoresOneExerciseSubmitted"
  val AssessmentScoresAllExercisesSubmitted = "ReviewerAssessmentScoresAllExercisesSubmitted"
  val UserIdForAudit = "assessorId"
}
