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

import java.nio.file.Files
import java.io.ByteArrayInputStream
import akka.stream.scaladsl.StreamConverters
import connectors.exchange.UserIdResponse
import controllers.ApplicationController.CandidateNotFound

import javax.inject.{Inject, Singleton}
import model.Exceptions._
import model.{CreateApplicationRequest, OverrideSubmissionDeadlineRequest, PreviewRequest, ProgressStatuses}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.assessmentcentre.AssessmentCentreService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import repositories.application.GeneralApplicationRepository
import repositories.fileupload.FileUploadRepository
import services.AuditService
import services.application.ApplicationService
import services.assessmentcentre.AssessmentCentreService.CandidateAlreadyHasAnAnalysisExerciseException
import services.assessmentcentre.AssessmentCentreService.CandidateHasNoAnalysisExerciseException
import services.personaldetails.PersonalDetailsService
import services.sift.ApplicationSiftService

import scala.concurrent.Future

object ApplicationController {
  case class CandidateNotFound(msg: String) extends Exception(msg)
}

@Singleton
class ApplicationController @Inject() (cc: ControllerComponents,
                                       appRepository: GeneralApplicationRepository,
                                       auditService: AuditService,
                                       applicationService: ApplicationService,
                                       passmarkService: EvaluatePhase3ResultService,
                                       siftService: ApplicationSiftService,
                                       assessmentCentreService: AssessmentCentreService,
                                       uploadRepository: FileUploadRepository,
                                       personalDetailsService: PersonalDetailsService
                                      ) extends BackendController(cc) {

  implicit val ec = cc.executionContext

  def createApplication = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateApplicationRequest] { applicationRequest =>
      appRepository.create(applicationRequest.userId, applicationRequest.frameworkId,
        applicationRequest.applicationRoute, applicationRequest.sdipDiversity).map { result =>
        auditService.logEvent("ApplicationCreated")
        Ok(Json.toJson(result))
      }
    }
  }

  def findByApplicationId(applicationId: String) = Action.async {
    appRepository.find(applicationId).map { app =>
      Ok(Json.toJson(UserIdResponse(app.map(_.userId).getOrElse(throw CandidateNotFound(applicationId)))))
    }. recover {
      case _: CandidateNotFound => NotFound(s"No application found for $applicationId")
    }
  }

  def applicationProgress(applicationId: String) = Action.async {
    appRepository.findProgress(applicationId).map { result =>
      Ok(Json.toJson(result))
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application for user with id: ${e.id}")
    }
  }

  def findApplicationStatusDetails(applicationId: String) = Action.async {
    appRepository.findStatus(applicationId).map { result =>
      Ok(Json.toJson(result.toExchange))
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot retrieve applications status details for application: ${e.id}")
    }
  }

  def findApplication(userId: String, frameworkId: String) = Action.async {
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

  def getPhase3Results(applicationId: String): Action[AnyContent] = Action.async {
    passmarkService.getPassmarkEvaluation(applicationId).map { passmarks =>
      Ok(Json.toJson(passmarks.result))
    } recover {
      case _: PassMarkEvaluationNotFound => NotFound(s"No phase3 evaluation results found for applicationId: $applicationId")
    }
  }

  def getSiftResults(applicationId: String): Action[AnyContent] = Action.async {
    siftService.getSiftEvaluations(applicationId).map { passmarks =>
      Ok(Json.toJson(passmarks))
    } recover {
      case _: PassMarkEvaluationNotFound => NotFound(s"No sift evaluation results found for applicationId: $applicationId")
    }
  }

  def getCurrentSchemeStatus(applicationId: String) = Action.async {
      applicationService.currentSchemeStatusWithFailureDetails(applicationId).map { currentSchemeStatus =>
        Ok(Json.toJson(currentSchemeStatus))
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
          case _: NotFoundException => NotFound(s"cannot find application with id $applicationId")
        }
    }
  }

  def uploadAnalysisExercise(applicationId: String, contentType: String) = Action.async(parse.temporaryFile) {
    implicit request =>
      (for {
        fileId <- uploadRepository.add(contentType, Files.readAllBytes(request.body.path))
        _ <- assessmentCentreService.updateAnalysisTest(applicationId, fileId)
      } yield {
        Ok
      }).recover {
        case _: CandidateAlreadyHasAnAnalysisExerciseException => Conflict("An analysis exercise has already been added for this user")
      }
  }

  def updateAnalysisExercise(applicationId: String,
                             contentType: String,
                             updatedBy: String) = Action.async(parse.temporaryFile) {
    implicit request =>
      for {
        assessmentCentreTests <- assessmentCentreService.getTests(applicationId)
        oldFileId = assessmentCentreTests.analysisExercise.map(_.fileId).getOrElse("NONE")
        newFileId <- uploadRepository.add(contentType, Files.readAllBytes(request.body.path))
        _ <- assessmentCentreService.updateAnalysisTest(applicationId, newFileId, isAdminUpdate = true)
        auditDetails = Map("applicationId" -> applicationId, "oldFileId" -> oldFileId, "newFileId" -> newFileId, "updatedBy" -> updatedBy)
        _ = auditService.logEvent("Analysis exercise updated", auditDetails)
      } yield {
        Ok
      }
  }

  def downloadAnalysisExercise(applicationId: String) = Action.async {
    for {
      assessmentCentreTests <- assessmentCentreService.getTests(applicationId)
      analysis = assessmentCentreTests.analysisExercise.getOrElse(throw CandidateHasNoAnalysisExerciseException(applicationId))
      file <- uploadRepository.retrieve(analysis.fileId)
    } yield {
      val inputStream = new ByteArrayInputStream(file.fileContents)
      val source = StreamConverters.fromInputStream(() => inputStream)

        Ok.chunked(source).as(file.contentType)
    }
  }

  def analysisExerciseStatistics: Action[AnyContent] = Action.async {
    val result = for {
      allFileInfo <- uploadRepository.retrieveAllIdsAndSizes
      allApplicationFiles <- appRepository.findAllFileInfo
    } yield {
      allApplicationFiles.map { applicationFile =>
        val matchingFileInfo = allFileInfo.find(_.id == applicationFile.analysisExerciseId)
          .map(fileInfo => Json.toJson(fileInfo).as[JsObject]).getOrElse(Json.obj("notFoundMatch" -> true))

          Json.toJson(applicationFile).as[JsObject].deepMerge(matchingFileInfo)
      }
    }

    result.map(_.foldLeft(Json.arr())((a,b) => a.:+(b))).map(Ok(_))
  }

  def hasAnalysisExercise(applicationId: String): Action[AnyContent] = Action.async {
    for {
      assessmentCentreTests <- assessmentCentreService.getTests(applicationId)
      analysis = assessmentCentreTests.analysisExercise
    } yield {
      Ok(Json.toJson(analysis.nonEmpty))
    }
  }

  def retrieveAnalysisExerciseInfo(applicationId: String): Action[AnyContent] = Action.async {
    for {
      assessmentCentreTests <- assessmentCentreService.getTests(applicationId)
      analysis = assessmentCentreTests.analysisExercise
    } yield {
      Ok(Json.toJson(analysis))
    }
  }

  def analysisExerciseFileMetadata(fileId: String): Action[AnyContent] = Action.async {
    uploadRepository.retrieveMetaData(fileId).map(f => Ok(Json.toJson(f)))
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

  def findCandidatesByApplicationIds(applicationIds: List[String]) = Action.async {
    appRepository.find(applicationIds).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }

  def getFsacEvaluationResultAverages(applicationId: String) = Action.async {
    for {
      averagesOpt <- assessmentCentreService.getFsacEvaluationResultAverages(applicationId)
    } yield {
      averagesOpt match {
        case Some(averages) => Ok(Json.toJson(averages))
        case None => NotFound(s"Cannot find evaluation averages for applicationId: $applicationId")
      }
    }
  }

  def getFsacExerciseResultAverages(applicationId: String) = Action.async {
    for {
      scoresOpt <- assessmentCentreService.getFsacExerciseResultAverages(applicationId)
    } yield {
      scoresOpt match {
        case Some(averages) => Ok(Json.toJson(averages))
        case None => NotFound(s"Cannot find fsac exercise averages for applicationId: $applicationId")
      }
    }
  }

  def updateFsacIndicator(userId: String, applicationId: String, fsacAssessmentCentre: String) = Action.async {
    personalDetailsService.updateFsacIndicator(applicationId, userId, fsacAssessmentCentre) map { _ =>
      Ok
    } recover {
      case _: IllegalArgumentException =>
        BadRequest(s"Invalid FSAC assessment centre supplied when trying to update the FSAC indicator - $fsacAssessmentCentre")
      case _: CannotUpdateFSACIndicator =>
        BadRequest(s"Failed to update FSAC indicator userId = $userId, applicationId = $applicationId, are the ids correct?")
    }
  }
}
