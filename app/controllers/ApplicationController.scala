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

import java.nio.file.Files

import akka.stream.scaladsl.Source
import model.Exceptions.{ ApplicationNotFound, CannotUpdatePreview, NotFoundException, PassMarkEvaluationNotFound }
import model.{ CreateApplicationRequest, OverrideSubmissionDeadlineRequest, PreviewRequest, ProgressStatuses }
import model.command.WithdrawApplication
import play.api.libs.json.Json
import play.api.libs.streams.Streams
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.fileupload.FileUploadMongoRepository
import services.AuditService
import services.application.ApplicationService
import services.assessmentcentre.AssessmentCentreService
import services.assessmentcentre.AssessmentCentreService.{ CandidateAlreadyHasAnAnalysisExerciseException, CandidateHasNoAnalysisExerciseException }
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ApplicationController extends ApplicationController {
  val appRepository = applicationRepository
  val auditService = AuditService
  val applicationService = ApplicationService
  val passmarkService = EvaluatePhase3ResultService
  val assessmentCentreService = AssessmentCentreService
  val uploadRepository = fileUploadRepository
}

trait ApplicationController extends BaseController {
  val appRepository: GeneralApplicationRepository
  val auditService: AuditService
  val applicationService: ApplicationService
  val passmarkService: EvaluatePhase3ResultService
  val assessmentCentreService: AssessmentCentreService
  val uploadRepository: FileUploadMongoRepository

  def createApplication = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateApplicationRequest] { applicationRequest =>
      appRepository.create(applicationRequest.userId, applicationRequest.frameworkId, applicationRequest.applicationRoute).map { result =>
        auditService.logEvent("ApplicationCreated")
        Ok(Json.toJson(result))
      }
    }
  }

  def applicationProgress(applicationId: String) = Action.async { implicit request =>
    appRepository.findProgress(applicationId).map { result =>
      Ok(Json.toJson(result))
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application for user with id: ${e.id}")
    }
  }

  def findApplicationStatusDetails(applicationId: String) = Action.async { implicit request =>
    appRepository.findStatus(applicationId).map { result =>
      Ok(Json.toJson(result))
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot retrieve applications status details for application: ${e.id}")
    }
  }

  def findApplication(userId: String, frameworkId: String) = Action.async { implicit request =>
    appRepository.findByUserId(userId, frameworkId).map(result =>
      Ok(Json.toJson(result))).recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application for user with id: ${e.id}")
    }
  }


  def preview(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[PreviewRequest] { _ =>
      appRepository.preview(applicationId).map { _ =>
        auditService.logEvent("ApplicationPreviewed")
        Ok
      }.recover {
        case e: CannotUpdatePreview => NotFound(s"cannot update application with id: ${e.applicationId}")
      }
    }
  }

  def getPhase3Results(applicationId: String) = Action.async { implicit request =>
    passmarkService.getPassmarkEvaluation(applicationId).map { passmarks =>
      Ok(Json.toJson(passmarks.result))
    } recover {
      case _: PassMarkEvaluationNotFound => NotFound(s"No evaluation results found for applicationId: $applicationId")
    }
  }

  def getCurrentSchemeStatus(applicationId: String) = Action.async { implicit request =>
    appRepository.getCurrentSchemeStatus(applicationId).map { schemeStatus =>
      Ok(Json.toJson(schemeStatus))
    } recover {
      case _: PassMarkEvaluationNotFound => NotFound(s"No evaluation results found for applicationId: $applicationId")
    }
  }

  def considerForSdip(applicationId: String) = Action.async { implicit request =>
    applicationService.considerForSdip(applicationId).map { _ => Ok
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application with id: ${e.id}")
    }
  }

  def continueAsSdip(userId: String, userIdToArchiveWith: String) = Action.async { implicit request =>
    applicationService.cloneFastStreamAsSdip(userId, userIdToArchiveWith).map { _ =>
      Ok
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application for userId: ${e.id}")
    }
  }

  def overrideSubmissionDeadline(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[OverrideSubmissionDeadlineRequest] { overrideRequest =>
      applicationService.overrideSubmissionDeadline(applicationId, overrideRequest.submissionDeadline).map(_ => Ok)
        .recover {
          case e: NotFoundException => NotFound(s"cannot find application with id $applicationId")
        }
    }
  }

  def uploadAnalysisExercise(applicationId: String, contentType: String) = Action.async(parse.temporaryFile) {
    implicit request =>
      (for {
        fileId <- uploadRepository.add(contentType, Files.readAllBytes(request.body.file.toPath))
        _ <- assessmentCentreService.updateAnalysisTest(applicationId, fileId)
      } yield {
        Ok
      }).recover {
        case x: CandidateAlreadyHasAnAnalysisExerciseException => Conflict("An analysis exercise has already been added for this user")
      }
  }

  def downloadAnalysisExercise(applicationId: String) = Action.async {
    implicit request =>
      for {
        assessmentCentreTests <- assessmentCentreService.getTests(applicationId)
        analysis = assessmentCentreTests.analysisExercise.getOrElse(throw CandidateHasNoAnalysisExerciseException(applicationId))
        file <- uploadRepository.retrieve(analysis.fileId)
      } yield {
        val source = Source.fromPublisher(Streams.enumeratorToPublisher(file.fileContents))

        Ok.chunked(source).as(file.contentType)
      }
  }

  def hasAnalysisExercise(applicationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      for {
        assessmentCentreTests <- assessmentCentreService.getTests(applicationId)
        analysis = assessmentCentreTests.analysisExercise
      } yield {
        Ok(Json.toJson(analysis.nonEmpty))
      }
  }

  case class ApplicationStatus(applicationId: String, progressStatus: String)
  object ApplicationStatus {
    implicit val applicationStatusFormat = play.api.libs.json.Json.format[ApplicationStatus]
  }

  case class ApplicationStatuses(applications: List[ApplicationStatus])
  object ApplicationStatuses {
    implicit val applicationStatusesFormat = play.api.libs.json.Json.format[ApplicationStatuses]
  }

  def updateStatus() = Action.async(parse.json) { implicit request =>
    withJsonBody[ApplicationStatuses] { applicationStatuses =>
      val updateFutures = applicationStatuses.applications.map { application =>
        val progressStatus = ProgressStatuses.nameToProgressStatus(application.progressStatus)
        appRepository.addProgressStatusAndUpdateAppStatus(application.applicationId, progressStatus)
      }
      Future.sequence(updateFutures).map(_ => Ok)
    }
  }
}
