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

package controllers.fixdata

import factories.UUIDFactory
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions._
import model.ProgressStatuses._
import model.command.FastPassPromotion
import model.persisted.sift.SiftAnswersStatus
import model.{SchemeId, UniqueIdentifier}
import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import repositories.SchemeRepository
import services.application.ApplicationService.{InvalidSchemeException, NoChangeInCurrentSchemeStatusException}
import services.application.{ApplicationService, FsbService}
import services.assessmentcentre.AssessmentCentreService.CandidateHasNoAssessmentScoreEvaluationException
import services.assessmentcentre.{AssessmentCentreService, ProgressionToFsbOrOfferService}
import services.fastpass.FastPassService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService
import services.sift.{ApplicationSiftService, SiftAnswersService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off number.of.methods file.size.limit
@Singleton
class FixDataConsistencyController @Inject()(cc: ControllerComponents,
                                             applicationService: ApplicationService,
                                             fastPassService: FastPassService,
                                             siftService: ApplicationSiftService,
                                             siftAnswersService: SiftAnswersService,
                                             assessmentCentreService: AssessmentCentreService,
                                             progressionToFsbOrOfferService: ProgressionToFsbOrOfferService,
                                             fsbService: FsbService,
                                             phase2TestService: Phase2TestService,
                                             phase3TestService: Phase3TestService,
                                             uuidFactory: UUIDFactory,
                                             schemeRepository: SchemeRepository
                                            ) extends BackendController(cc) with Logging {

  implicit val ec: ExecutionContext = cc.executionContext

  def undoFullWithdraw(applicationId: String, newApplicationStatus: ApplicationStatus) = Action.async { implicit request =>
    applicationService.undoFullWithdraw(applicationId, newApplicationStatus).map { _ =>
      Ok
    } recover {
      case e: ApplicationNotFound => NotFound(s"Did not find candidate ${e.getMessage}")
      case e: CandidateInIncorrectState => BadRequest(s"Cannot rollback withdraw for candidate because ${e.getMessage}")
    }
  }

  def removeETray(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingETray(appId).map { _ =>
      NoContent
    } recover {
      case _: ApplicationNotFound => NotFound
    }
  }

  def removeProgressStatus(appId: String, progressStatus: ProgressStatus) = Action.async { implicit request =>
    applicationService.fixDataByRemovingProgressStatus(appId, progressStatus.key).map { _ =>
      NoContent
    } recover {
      case _: ApplicationNotFound => NotFound
    }
  }

  def promoteToFastPassAccepted(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FastPassPromotion] { req =>
      fastPassService.promoteToFastPassCandidate(applicationId, req.triggeredBy).map { _ =>
        NoContent
      } recover {
        case e: CannotUpdateCivilServiceExperienceDetails => BadRequest(e.getMessage)
        case _: NotFoundException => NotFound
      }
    }
  }

  def removeVideoInterviewFailed(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingVideoInterviewFailed(appId).map { _ =>
      NoContent
    } recover {
      case _: ApplicationNotFound => NotFound
      case _: CannotUpdateRecord => BadRequest("Candidate does not have PHASE3_TESTS_FAILED_NOTIFIED progressStatus")
    }
  }

  def rollbackToPhase1ResultsReceivedFromPhase1FailedNotified(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackToPhase1ResultsReceivedFromPhase1FailedNotified)
  }

  def rollbackToPhase2ResultsReceivedFromPhase2FailedNotified(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackToPhase2ResultsReceivedFromPhase2FailedNotified)
  }

  def rollbackToSubmittedWithFastPassFromOnlineTestsExpired(applicationId: String,
                                                            fastPass: Int,
                                                            sdipFaststream: Boolean): Action[AnyContent] = Action.async {
    (for {
      _ <- applicationService.convertToFastStreamRouteWithFastpassFromOnlineTestsExpired(applicationId, fastPass, sdipFaststream)
      _ <- applicationService.rollbackToSubmittedFromOnlineTestsExpired(applicationId)
    } yield Ok(s"Successfully rolled back $applicationId"))
    .recover {
      case e: NotFoundException =>
        NotFound(s"Unable to rollback $applicationId because ${e.getMessage}")
    }
  }

  def rollbackToInProgressFromFastPassAccepted(applicationId: String): Action[AnyContent] = Action.async {
    for {
      response <- rollbackApplicationState(applicationId, applicationService.rollbackToInProgressFromFastPassAccepted)
    } yield response
  }

  def removeSdipSchemeFromFaststreamUser(applicationId: String): Action[AnyContent] = Action.async {
    (for {
      _ <- applicationService.removeSdipSchemeFromFaststreamUser(applicationId)
    } yield Ok)
      .recover {
        case e: ApplicationNotFound => NotFound(e.getMessage)
        case e: Throwable => BadRequest(e.getMessage)
      }
  }

  def addSdipSchemePreference(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.addSdipSchemePreference(applicationId).map(_ =>
      Ok(s"Successfully added Sdip for $applicationId")
    ).recover {
      case e: ApplicationNotFound => NotFound(s"Application not found: ${e.getMessage}")
      case e: Throwable => BadRequest(e.getMessage)
    }
  }

  def removeSchemePreference(applicationId: String, schemeToRemove: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.removeSchemePreference(applicationId, schemeToRemove).map(_ =>
      Ok(s"Successfully removed $schemeToRemove for $applicationId")
    ).recover {
      case e: ApplicationNotFound => NotFound(s"Application not found: ${e.getMessage}")
      case e: SchemeNotFoundException => NotFound(e.getMessage)
      case e: Throwable => BadRequest(e.getMessage)
    }
  }

  def rollbackApplicationState(applicationId: String, operator: String => Future[Unit]): Future[Result] = {
    operator(applicationId).map { _ =>
      Ok(s"Successfully rolled back $applicationId")
    }.recover {
      case _ => InternalServerError(s"Unable to rollback $applicationId")
    }
  }

  def findUsersStuckInSiftReadyWithFailedPreSiftSiftableSchemes(): Action[AnyContent] = Action.async {
    siftService.findUsersInSiftReadyWhoShouldHaveBeenCompleted.map(resultList =>
      Ok((Seq("applicationId, timeEnteredSift, shouldBeCompleted") ++ resultList.map { case(user, result) =>
        s"${user.applicationId},${user.timeEnteredSift},$result"
      }).mkString("\n"))
    )
  }

  def randomisePhasePassmarkVersion(applicationId: String, phase: String): Action[AnyContent] = Action.async {
    phase match {
      case "FSAC" =>
        (for {
          currentSchemeStatus <- applicationService.getCurrentSchemeStatus(applicationId)
          assessmentCentreScoreEvaluation <- assessmentCentreService.getAssessmentScoreEvaluation(applicationId)
          _ = if (assessmentCentreScoreEvaluation.isEmpty) {
            throw CandidateHasNoAssessmentScoreEvaluationException(s"No assessment score evaluation found for $applicationId")
          }
          newEvaluation = assessmentCentreScoreEvaluation.get.copy(passmarkVersion = uuidFactory.generateUUID())
          _ <- assessmentCentreService.saveAssessmentScoreEvaluation(newEvaluation, currentSchemeStatus)
        } yield Ok(s"Pass marks randomised for application $applicationId in phase $phase"))
          .recover {
            case e: CandidateHasNoAssessmentScoreEvaluationException => BadRequest(e.getMessage)
          }
      case _ => Future.successful(NotImplemented("Phase pass mark randomisation not implemented for phase: " + phase))
    }
  }

  def addProgressStatus(applicationId: String, progressStatus: ProgressStatus): Action[AnyContent] = Action.async {
    applicationService.addProgressStatusAndUpdateAppStatus(applicationId, progressStatus).map(_ => Ok)
      .recover {
        case ex: NotFoundException =>
          val msg = s"Failed to update candidate applicationId=$applicationId because ${ex.getMessage}"
          NotFound(msg)
      }
  }

  def setPhase3UsedForResults(applicationId: String, newUsedForResults: Boolean, token: String): Action[AnyContent] = Action.async {
    applicationService.setPhase3UsedForResults(applicationId, newUsedForResults, token)
      .map(_ => Ok(s"Successfully updated PHASE3 test for $applicationId"))
      .recover {
        case ex @ (_: NoResultsReturned | _: NotFoundException) => NotFound(ex.getMessage)
      }
  }

  def setPhase2UsedForResults(applicationId: String, inventoryId: String, orderId: String,
                              newUsedForResults: Boolean): Action[AnyContent] = Action.async {
    applicationService.setPhase2UsedForResults(applicationId, inventoryId, orderId, newUsedForResults)
      .map { _ =>
        val msg = s"Successfully updated PHASE2 test usedForResults value to $newUsedForResults for " +
          s"applicationId=$applicationId,inventoryId=$inventoryId,orderId=$orderId"
        Ok(msg)
      }.recover {
      case ex: Throwable =>
        val msg = s"Could not update PHASE2 test usedForResults value to $newUsedForResults for " +
          s"applicationId=$applicationId,inventoryId=$inventoryId,orderId=$orderId because ${ex.getMessage}"
        BadRequest(msg)
    }
  }

  def setPhase1UsedForResults(applicationId: String, inventoryId: String, orderId: String,
                              newUsedForResults: Boolean): Action[AnyContent] = Action.async {
    applicationService.setPhase1UsedForResults(applicationId, inventoryId, orderId, newUsedForResults)
      .map { _ =>
        val msg = s"Successfully updated PHASE1 test usedForResults value to $newUsedForResults for " +
          s"applicationId=$applicationId,inventoryId=$inventoryId,orderId=$orderId"
        Ok(msg)
      }.recover {
      case ex: Throwable =>
        val msg = s"Could not update PHASE1 test usedForResults value to $newUsedForResults for " +
          s"applicationId=$applicationId,inventoryId=$inventoryId,orderId=$orderId because ${ex.getMessage}"
        BadRequest(msg)
      }
  }

  def findUsersStuckInAssessmentScoresAccepted(): Action[AnyContent] = Action.async {
    assessmentCentreService.findUsersStuckInAssessmentScoresAccepted.map(resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("applicationId,fsac scheme evaluation") ++ resultList.map { user =>
          s"""${user.applicationId},"${user.schemeEvaluation.map(eval => eval.schemeId.toString + " -> " + eval.result).mkString(",")}""""
        }).mkString("\n"))
      }
    )
  }

  def findUsersEligibleForJobOfferButFsbApplicationStatus(): Action[AnyContent] = Action.async {
    applicationService.findUsersEligibleForJobOfferButFsbApplicationStatus().map(resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("applicationId") ++ resultList.map { applicationId =>
          s"""$applicationId"""
        }).mkString("\n"))
      }
    )
  }

  def fixUsersEligibleForJobOfferButFsbApplicationStatus(): Action[AnyContent] = Action.async {
    applicationService.fixUsersEligibleForJobOfferButFsbApplicationStatus().map(applicationIds =>
      Ok(s"Successfully fixed ${applicationIds.length} user(s) - appIds: ${applicationIds.mkString(",")}"))
  }

  def fixUserStuckInSiftReadyWithFailedPreSiftSiftableSchemes(applicationId: String): Action[AnyContent] = Action.async {
    siftService.fixUserInSiftReadyWhoShouldHaveBeenCompleted(applicationId).map(_ => Ok)
      .recover {
        case ex: ApplicationNotFound => NotFound(ex.getMessage)
      }
  }

  def findUsersStuckInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase(): Action[AnyContent] = Action.async {
    siftService.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase.map(resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("applicationId,currentSchemeStatus") ++ resultList.map { user =>
          s"${user.applicationId},${user.currentSchemeStatus}"
        }).mkString("\n"))
      }
    )
  }

  def fixUserStuckInSiftEnteredWhoShouldBeInSiftReadyWhoHasFailedFormBasedSchemesInVideoPhase(applicationId: String): Action[AnyContent] =
    Action.async {
    siftService.fixUserInSiftEnteredWhoShouldBeInSiftReadyWhoHasFailedFormBasedSchemesInVideoPhase(applicationId).map(_ => Ok)
      .recover {
        case ex: NoResultsReturned => NotFound(ex.getMessage)
      }
  }

  def findUsersStuckInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(): Action[AnyContent] = Action.async {
    siftService.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes.map(resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("applicationId,currentSchemeStatus") ++ resultList.map { user =>
          s"${user.applicationId},${user.currentSchemeStatus}"
        }).mkString("\n"))
      }
    )
  }

  def findSdipFaststreamFailedFaststreamInvitedToVideoInterview(): Action[AnyContent] = Action.async {
    applicationService.findSdipFaststreamFailedFaststreamInvitedToVideoInterview.map { resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("Email,Preferred Name (or first name if no preferred),Application ID,Failed at stage," +
          "Latest Progress Status,online test results,e-tray results") ++
          resultList.map { case (user, contactDetails, failedAtStage, latestProgressStatus, onlineTestPassMarks, etrayPassMarks) =>
            val onlineTestResultsAsString = "\"[" + onlineTestPassMarks.result.map(schemeResult =>
              s"${schemeResult.schemeId.toString} -> ${schemeResult.result}"
            ).mkString(", ") + "]\""

            val eTrayResultsAsString = "\"[" + etrayPassMarks.result.map(schemeResult =>
              s"${schemeResult.schemeId.toString} -> ${schemeResult.result}"
            ).mkString(", ") + "]\""

          s"${contactDetails.email},${user.preferredName.getOrElse(user.firstName)},${user.applicationId.get}," +
            s"$failedAtStage,${latestProgressStatus.toString},$onlineTestResultsAsString,$eTrayResultsAsString"
        }).mkString("\n"))
      }
    }
  }

  def moveSdipFaststreamFailedFaststreamInvitedToVideoInterviewToSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.moveSdipFaststreamFailedFaststreamInvitedToVideoInterviewToSift(applicationId).map(_ =>
        Ok(s"Successfully fixed $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixUserStuckInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(applicationId).map(_ => Ok)
        .recover {
          case ex: ApplicationNotFound => NotFound(ex.getMessage)
        }
    }

  def fixUserSiftedWithAFailByMistake(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserSiftedWithAFailByMistake(applicationId).map(_ =>
        Ok(s"Successfully fixed $applicationId. Remember to update the CurrentSchemeStatus")
      ).recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
        case ex: Throwable =>
          InternalServerError(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixUserSiftedWithAFailToSiftCompleted(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserSiftedWithAFailToSiftCompleted(applicationId).map(_ =>
        Ok(s"Successfully fixed $applicationId. Remember to update the CurrentSchemeStatus and sift evaluation")
      ).recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
        case ex: Throwable =>
          InternalServerError(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixSdipFaststreamCandidateWhoExpiredInOnlineTests(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    applicationService.fixSdipFaststreamCandidateWhoExpiredInOnlineTests(applicationId).map(_ => Ok(s"Successfully fixed $applicationId"))
      .recover {
        case e: ApplicationNotFound =>
          NotFound(s"Application not found: ${e.getMessage}")
        case e: UnexpectedException =>
          BadRequest(s"Error correcting candidate $applicationId because ${e.getMessage}")
      }
  }

  def markSiftSchemeAsRed(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markSiftSchemeAsRed(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as red for $applicationId")
    ).recover {
      case e: ApplicationNotFound => NotFound(s"Application not found: ${e.getMessage}")
    }
  }

  def markSiftSchemeAsGreen(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markSiftSchemeAsGreen(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as green for $applicationId")
    ).recover {
      case e: ApplicationNotFound => NotFound(s"Application not found: ${e.getMessage}")
    }
  }

  def createSiftStructure(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.findStatus(applicationId).flatMap { applicationStatus =>
      val statuses = Seq(SIFT_ENTERED, SIFT_READY, WITHDRAWN)
      val canProceed = statuses.exists( s => applicationStatus.latestProgressStatus.contains(s) )

      if (canProceed) {
        for {
          _ <- siftService.saveSiftExpiryDate(applicationId)
        } yield {
          Ok(s"Successfully created sift structure for $applicationId")
        }
      } else {
          Future.successful {
            BadRequest(s"Cannot create sift structure for $applicationId because the latest progress status is " +
              s"${applicationStatus.latestProgressStatus.getOrElse("NO-STATUS")}")
          }
      }
    }.recover {
      case e: ApplicationNotFound => NotFound(s"Cannot retrieve application status details for application: $applicationId")
    }
  }

  def extendSiftCandidate(applicationId: String, extraDays: Int): Action[AnyContent] = Action.async { implicit _ =>
      siftService.extendSiftCandidateFailedByMistake(applicationId, extraDays).map { _ =>
        Ok
      }.recover {
        case e: ApplicationNotFound => NotFound(s"Cannot extend sift candidate $applicationId because no candidate found")
      }
  }

  def removeSiftEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      siftService.removeEvaluation(applicationId).map { _ =>
        Ok(s"Successfully removed evaluation for sift candidate $applicationId")
      } recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
      }
    }

  def setSiftAnswersStatusToDraft(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      commonSetSiftAnswersStatus(applicationId, SiftAnswersStatus.DRAFT)
    }

  def setSiftAnswersStatusToSubmitted(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      commonSetSiftAnswersStatus(applicationId, SiftAnswersStatus.SUBMITTED)
    }

  private def commonSetSiftAnswersStatus(applicationId: String, status: SiftAnswersStatus.SiftAnswersStatus) = {
    siftAnswersService.setSiftAnswersStatus(applicationId, status).map { _ =>
      Ok(s"Successfully set sift answers status to $status for $applicationId")
    } recover {
      case nfe: NotFoundException => NotFound(nfe.getMessage)
    }
  }

  def findSdipFaststreamFailedFaststreamInPhase1ExpiredPhase2InvitedToSift: Action[AnyContent] = Action.async {
    applicationService.findSdipFaststreamFailedFaststreamInPhase1ExpiredPhase2InvitedToSift.map { resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("Email,Preferred Name (or first name if no preferred),Application ID," +
          "Latest Progress Status,online test results") ++
          resultList.map { case (user, contactDetails, latestProgressStatus, onlineTestResults) =>
            val onlineTestResultsAsString = "\"[" + onlineTestResults.result.map(schemeResult =>
              s"${schemeResult.schemeId.toString} -> ${schemeResult.result}"
            ).mkString(", ") + "]\""

            s"${contactDetails.email},${user.preferredName.getOrElse(user.firstName)},${user.applicationId.get}," +
              s"${latestProgressStatus.toString},$onlineTestResultsAsString"
          }).mkString("\n"))
      }
    }
  }

  def findSdipFaststreamFailedFaststreamInPhase2ExpiredPhase3InvitedToSift: Action[AnyContent] = Action.async {
    applicationService.findSdipFaststreamFailedFaststreamInPhase2ExpiredPhase3InvitedToSift.map { resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("Email,Preferred Name (or first name if no preferred),Application ID," +
          "Latest Progress Status,online test results") ++
          resultList.map { case (user, contactDetails, latestProgressStatus, onlineTestResults) =>
            val onlineTestResultsAsString = "\"[" + onlineTestResults.result.map(schemeResult =>
              s"${schemeResult.schemeId.toString} -> ${schemeResult.result}"
            ).mkString(", ") + "]\""

            s"${contactDetails.email},${user.preferredName.getOrElse(user.firstName)},${user.applicationId.get}," +
              s"${latestProgressStatus.toString},$onlineTestResultsAsString"
          }).mkString("\n"))
      }
    }
  }

  def markFsbSchemeAsRed(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markFsbSchemeAsRed(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as red for $applicationId")
    ).recover {
      case ex: ApplicationNotFound => NotFound(s"No document found for ${ex.getMessage} and scheme $schemeId")
    }
  }

  def markFsbSchemeAsGreen(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markFsbSchemeAsGreen(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as green for $applicationId")
    ).recover {
      case ex: ApplicationNotFound => NotFound(s"No document found for ${ex.getMessage} and scheme $schemeId")
    }
  }

  def rollbackToSiftReadyFromAssessmentCentreAwaitingAllocation(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.rollbackToSiftReadyFromAssessmentCentreAwaitingAllocation(applicationId).map(_ =>
      Ok(s"Successfully rolled $applicationId back to sift ready")
    ). recover {
      case ex: NotFoundException => NotFound(ex.getMessage)
    }
  }

  def updateCurrentSchemeStatusScheme(applicationId: String, schemeId: SchemeId,
                                      result: model.EvaluationResults.Result): Action[AnyContent] = Action.async {
    applicationService.updateCurrentSchemeStatusScheme(applicationId, schemeId, result).map(_ =>
      Ok(s"Successfully updated CSS for schemeId ${schemeId.toString} to ${result.toString} for $applicationId ")
    ). recover {
      case ex: NoChangeInCurrentSchemeStatusException =>
        BadRequest(s"Error updating CSS: ${ex.getMessage}")
    }
  }

  def removeCurrentSchemeStatusScheme(applicationId: String, schemeId: SchemeId): Action[AnyContent] = Action.async {
    applicationService.removeCurrentSchemeStatusScheme(applicationId, schemeId).map(_ =>
      Ok(s"Successfully removed schemeId ${schemeId.toString} from CSS for $applicationId ")
    ). recover {
      case ex: NoChangeInCurrentSchemeStatusException =>
        BadRequest(s"Error updating CSS: ${ex.getMessage}")
    }
  }

  def rollbackToAssessmentCentreConfirmedFromAssessmentCentreFailedNotified(applicationId: String): Action[AnyContent] =
    Action.async {
      val statusesToRemove = List(
        ASSESSMENT_CENTRE_SCORES_ENTERED,
        ASSESSMENT_CENTRE_SCORES_ACCEPTED,
        ASSESSMENT_CENTRE_FAILED,
        ASSESSMENT_CENTRE_FAILED_NOTIFIED
      )
      applicationService.rollbackToAssessmentCentreConfirmed(applicationId, statusesToRemove).map(_ =>
        Ok(s"Successfully rolled $applicationId back to assessment centre confirmed")
      ).recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
        case ex: NoResultsReturned => BadRequest(ex.getMessage)
      }
    }

  def rollbackToFsacAllocatedFromAwaitingFsb(applicationId: String): Action[AnyContent] = Action.async {
    val statusesToRemove = List(
      ASSESSMENT_CENTRE_SCORES_ENTERED,
      ASSESSMENT_CENTRE_SCORES_ACCEPTED,
      ASSESSMENT_CENTRE_PASSED,
      FSB_AWAITING_ALLOCATION
    )
    applicationService.rollbackToAssessmentCentreConfirmed(applicationId, statusesToRemove).map(_ =>
      Ok(s"Successfully rolled $applicationId back to assessment centre confirmed")
    ).recover {
      case e: NotFoundException => NotFound(s"Failed to rollback FSB candidate because ${e.getMessage}")
      case e: NoResultsReturned => BadRequest(s"Failed to rollback FSB candidate because ${e.getMessage}")
    }
  }

  def rollbackFastPassFromFsacToSubmitted(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    applicationService.rollbackFastPassFromFsacToSubmitted(applicationId)
      .map(_ => Ok(s"Successfully rolled $applicationId back to Submitted from FSAC"))
      .recover {
        case ex: NotFoundException => NotFound(s"Failed to rollback FastPass candidate because ${ex.getMessage}")
      }
  }

  def rollbackToSubmittedFromOnlineTestsAndAddFastpassNumber(applicationId: String, certificateNumber: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.rollbackToSubmittedFromOnlineTestsAndAddFastpassNumber(applicationId, certificateNumber)
        .map(_ => Ok(s"Successfully rolled $applicationId back to Submitted and added FastPass($certificateNumber"))
  }

  def rollbackToSubmittedFromPhase1AfterFastpassRejectedByMistake(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.rollbackToSubmittedFromPhase1AfterFastpassRejectedByMistake(applicationId)
        .map(_ => Ok(s"Successfully rolled $applicationId back to SUBMITTED and removed fast pass rejection"))
  }

  def rollbackToAssessmentCentreConfirmedFromEligibleForJobOfferNotified(applicationId: String): Action[AnyContent] = Action.async {
    val statusesToRemove = List(
      ASSESSMENT_CENTRE_SCORES_ENTERED,
      ASSESSMENT_CENTRE_SCORES_ACCEPTED,
      ASSESSMENT_CENTRE_PASSED,
      ELIGIBLE_FOR_JOB_OFFER,
      ELIGIBLE_FOR_JOB_OFFER_NOTIFIED
    )
    applicationService.rollbackToAssessmentCentreConfirmed(applicationId, statusesToRemove).map(_ =>
      Ok(s"Successfully rolled $applicationId back to assessment centre confirmed")
    ).recover {
      case ex: NotFoundException => NotFound(ex.getMessage)
      case ex: NoResultsReturned => BadRequest(ex.getMessage)
    }
  }

  def rollbackToFsacAwaitingAllocationFromFsacFailed(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.rollbackToFsacAwaitingAllocationFromFsacFailed(applicationId).map(_ =>
      Ok(s"Successfully rolled $applicationId back to FSAC AWAITING ALLOCATION. Remember to update the CurrentSchemeStatus")
    ).recover {
      case ex: NotFoundException => NotFound(ex.getMessage)
    }
  }

  def rollbackToFsacAllocationConfirmedFromFsb(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.rollbackToFsacAllocationConfirmedFromFsb(applicationId).map(_ =>
      Ok(s"Successfully rolled $applicationId back to FSAC AWAITING ALLOCATION. Remember to update the CurrentSchemeStatus")
    ).recover {
      case ex: NotFoundException => NotFound(ex.getMessage)
    }
  }

  def rollbackToFsbAwaitingAllocationFromEligibleForJobOfferNotified(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>

      val statusesToRemove = List(
        FSB_FAILED,
        FSB_PASSED,
        FSB_FAILED_TO_ATTEND,
        FSB_RESULT_ENTERED,
        FSB_ALLOCATION_CONFIRMED,
        FSB_ALLOCATION_UNCONFIRMED,
        ELIGIBLE_FOR_JOB_OFFER,
        ELIGIBLE_FOR_JOB_OFFER_NOTIFIED,
        ALL_FSBS_AND_FSACS_FAILED,
        ALL_FSBS_AND_FSACS_FAILED_NOTIFIED
      )

      applicationService.rollbackToFsbAwaitingAllocation(applicationId, statusesToRemove).map(_ =>
        Ok(s"Successfully rolled $applicationId back to assessment centre confirmed")
      ).recover {
        case ex @ (_: NoResultsReturned | _: NotFoundException) => NotFound(ex.getMessage)
      }
    }

  def rollbackToPhase2TestExpiredFromSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.rollbackToPhase2ExpiredFromSift(applicationId).map(_ =>
        Ok(s"Successfully rolled back to phase2 expired $applicationId")
      ).recover {
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixPhase3ExpiredCandidate(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.fixPhase3ExpiredCandidate(applicationId).map(_ =>
        Ok(s"Successfully fixed p3 expired candidate $applicationId")
      ).recover {
        case ex: ApplicationNotFound => NotFound(ex.getMessage)
        case ex: Throwable => BadRequest(s"Could not fix p3 expired candidate $applicationId - message: ${ex.getMessage}")
      }
    }

  def rollbackToPhase3TestExpiredFromSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.rollbackToPhase3ExpiredFromSift(applicationId).map(_ =>
        Ok(s"Successfully rolled back to phase3 expired $applicationId")
      ).recover {
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def rollbackToPhase1TestsPassedFromSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.rollbackToPhase1TestsPassedFromSift(applicationId).map(_ =>
        Ok(s"Successfully rolled back to phase1 tests passed $applicationId")
      ).recover {
        case ex: NotFoundException => NotFound(s"Could not fix $applicationId - message: ${ex.getMessage}")
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def enablePhase3ExpiredCandidateToBeEvaluated(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.enablePhase3ExpiredCandidateToBeEvaluated(applicationId).map(_ =>
        Ok(s"Successfully updated phase3 state so candidate $applicationId can be evaluated ")
      ).recover {
        case ex: ApplicationNotFound => NotFound(s"Could not fix $applicationId - message: ${ex.getMessage}")
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removePhase3TestAndSetOtherToActive(removeTestToken: String, markTestAsActiveToken: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.removePhase3TestAndSetOtherToActive(removeTestToken, markTestAsActiveToken).map(_ =>
        Ok(s"Successfully removed phase3 test for token $removeTestToken and set other test to active for token $markTestAsActiveToken ")
      ).recover {
        case ex: TokenNotFound => NotFound(ex.getMessage)
        case ex: Throwable => BadRequest(s"Could not fix candidate - message: ${ex.getMessage}")
      }
    }

  def rollbackToRetakePhase3FromSift(applicationId: String, token: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.rollbackToRetakePhase3FromSift(applicationId, token).map(_ =>
        Ok(s"Successfully rolled back candidate $applicationId from sift so can retake video interview")
      ).recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
        case ex: TokenNotFound => NotFound(s"No reviewed callbacks found for token ${ex.getMessage}")
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removePhase1TestEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.removePhase1TestEvaluation(applicationId).map(_ =>
        Ok(s"Successfully removed P1 test evaluation and css for candidate $applicationId")
      ).recover {
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removePhase2TestEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.removePhase2TestEvaluation(applicationId).map(_ =>
        Ok(s"Successfully removed P2 test evaluation and updated css for candidate $applicationId")
      ).recover {
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removePhase3TestEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.removePhase3TestEvaluation(applicationId).map(_ =>
        Ok(s"Successfully removed P3 test evaluation and updated css for candidate $applicationId")
      ).recover {
        case ex: Throwable => BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def extendPhase3TestGroup(applicationId: String, extraDays: Int): Action[AnyContent] =
    Action.async { implicit request =>
      phase3TestService.extendTestGroupExpiryTime(applicationId, extraDays).map( _ =>
        Ok(s"Successfully extended P3 test group by $extraDays day(s) for candidate $applicationId")
      ).recover {
        case ex: IllegalStateException =>
          NotFound(s"Could not extend P3 test group by $extraDays for $applicationId - message: ${ex.getMessage}")
        case ex: Throwable =>
          BadRequest(s"Could not extend P3 test group by $extraDays for $applicationId - message: ${ex.getMessage}")
      }
  }

  def removePhase3TestGroup(applicationId: String): Action[AnyContent] = Action.async { implicit _ =>
    applicationService.removePhase3TestGroup(applicationId).map { _ =>
      Ok(s"Successfully removed phase 3 test group for $applicationId")
    } recover {
      case ex: NotFoundException =>
        NotFound(s"Could not remove phase 3 test group for $applicationId - message: ${ex.getMessage}")
    }
  }

  def markPhase3SchemeAsRed(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markPhase3SchemeAsRed(applicationId, schemeId).map { _ =>
      Ok(s"Successfully marked ${schemeId.value} as red for $applicationId")
    } recover {
      case _: ApplicationNotFound => NotFound(s"No candidate found for $applicationId, scheme:$schemeId")
      case ex: Throwable =>
        BadRequest(s"Could not update phase 3 test group for $applicationId - message: ${ex.getMessage}")
    }
  }

  def markPhase3SchemeAsGreen(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markPhase3SchemeAsGreen(applicationId, schemeId).map { _ =>
      Ok(s"Successfully marked ${schemeId.value} as green for $applicationId")
    } recover {
      case _: ApplicationNotFound => NotFound(s"No candidate found for $applicationId, scheme:$schemeId")
      case ex: Throwable =>
        BadRequest(s"Could not update phase 3 test group for $applicationId - message: ${ex.getMessage}")
    }
  }

  def addPhase3SchemeAsGreen(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    (for {
      isValid <- Future.successful(schemeRepository.isValidSchemeId(schemeId))
      _ = if (!isValid) { throw new Exception(s"Scheme $schemeId is not a valid name") }
      _ <- applicationService.addPhase3SchemeAsGreen(applicationId, schemeId)
    } yield {
      Ok(s"Successfully added ${schemeId.value} as green for $applicationId")
    }) recover {
      case _: ApplicationNotFound => NotFound(s"No candidate found or bad scheme provided for $applicationId, scheme:$schemeId")
      case ex: Throwable =>
        BadRequest(s"Could not add ${schemeId.value} to phase 3 test group for $applicationId - message: ${ex.getMessage}")
    }
  }

  def removeSiftTestGroup(applicationId: String): Action[AnyContent] = Action.async { implicit _ =>
    applicationService.removeSiftTestGroup(applicationId).map(_ => Ok(s"Successfully removed SIFT test group for $applicationId"))
      .recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
      }
  }

  def removeFsbTestGroup(applicationId: String): Action[AnyContent] = Action.async { implicit _ =>
    applicationService.removeFsbTestGroup(applicationId).map { _ =>
      Ok(s"Successfully removed FSB test group for $applicationId")
    } recover {
      case ex: ApplicationNotFound => NotFound(ex.getMessage)
    }
  }

  def updateApplicationStatus(applicationId: String, newApplicationStatus: ApplicationStatus) = Action.async { implicit _ =>
    applicationService.updateApplicationStatus(applicationId, newApplicationStatus).map { _ =>
      Ok(s"Successfully updated $applicationId application status to $newApplicationStatus")
    } recover {
      case _: ApplicationNotFound => NotFound
    }
  }

  def fsacResetFastPassCandidate(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.fsacResetFastPassCandidate(applicationId).map { _ =>
        Ok(s"Successfully reset fsac fast pass candidate $applicationId. Remember to update the current scheme status")
      } recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
      }
    }

  def fsacRollbackWithdraw(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.fsacRollbackWithdraw(applicationId).map { _ =>
        Ok(s"Successfully rolled back withdraw for fsac candidate $applicationId")
      } recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
      }
    }

  def fsacRemoveEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      applicationService.fsacRemoveEvaluation(applicationId).map { _ =>
        Ok(s"Successfully removed evaluation for fsac candidate $applicationId")
      } recover {
        case ex: NotFoundException => NotFound(ex.getMessage)
      }
    }

  def fsacEvaluateCandidate(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      assessmentCentreService.nextSpecificCandidateReadyForEvaluation(applicationId).flatMap { candidateResults =>
        if (candidateResults.isEmpty) {
          Future.successful(BadRequest("No candidate found to evaluate at FSAC. Please check the candidate's state in the diagnostic report"))
        } else {
          val candidateFutures = candidateResults.map { candidateResult =>

            if (candidateResult.schemes.isEmpty) {
              val msg = s"FSAC candidate $applicationId has no eligible schemes so will not evaluate"
              logger.warn(msg)
              Future.failed(new Exception(msg))
            } else {
              assessmentCentreService.evaluateAssessmentCandidate(candidateResult)
            }
          }
          Future.sequence(candidateFutures).map(_ => Ok(s"Successfully evaluated candidate $applicationId at FSAC"))
            .recover {
              case ex: Throwable => BadRequest(ex.getMessage)
            }
        }
      }
    }

  def fsacSetAverageScore(applicationId: String, version: String, averageScoreName: String, averageScore: Double): Action[AnyContent] =
    Action.async { implicit _ =>
      assessmentCentreService.setFsacAverageScore(UniqueIdentifier(applicationId), version, averageScoreName, averageScore).map { ss =>
        Ok(s"Successfully updated applicationId:$applicationId averageScoreName:$averageScoreName in exercise whose version=$version " +
          s"to averageScore:$averageScore")
      }.recover {
        case e: NoResultsReturned => NotFound(e.getMessage)
        case e: Throwable =>
          logger.warn(s"Error occurred whilst trying to set Fsac average score: " +
            s"applicationId=$applicationId,version=$version,averageScoreName=$averageScoreName,averageScore=$averageScore,error=${e.getMessage}")
          BadRequest(e.getMessage)
      }
    }

  def progressCandidateToFsbOrOfferJob(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      implicit val hc = HeaderCarrier()
      progressionToFsbOrOfferService.nextApplicationForFsbOrJobOffer(applicationId).flatMap {
        case Nil =>
          val msg = "No candidate found to progress to fsb or offer job. Please check the candidate's state in the diagnostic report"
          Future.successful(NotFound(msg))
        case application =>
          progressionToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(application).map { result =>
            val msg = s"Progress to fsb or job offer complete - ${result.successes.size} processed successfully " +
              s"and ${result.failures.size} failed to update"
            Ok(msg)
          }
      }
    }

  def progressCandidateFailedAtFsb(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      fsbService.processApplicationFailedAtFsb(applicationId).map { result =>
        val successfulAppIds = result.successes.map( _.applicationId )
        val failedAppIds = result.failures.map( _.applicationId )
        val msg = s"Progress candidate failed at FSB complete - ${result.successes.size} updated, appIds: ${successfulAppIds.mkString(",")} " +
          s"and ${result.failures.size} failed to update, appIds: ${failedAppIds.mkString(",")}"
        logger.warn(msg)
        if (result.failures.nonEmpty) {
          BadRequest(s"Failed to update candidate, appId: ${failedAppIds.mkString(",")}")
        } else if (result.successes.isEmpty) {
          NotFound(s"No candidate found to update, appId: $applicationId. Please check the candidate's status")
        } else {
          Ok(msg)
        }
      }
    }

  def inviteP2CandidateToMissingTest(applicationId: String): Action[AnyContent] =
    Action.async { implicit _ =>
      phase2TestService.inviteP2CandidateToMissingTest(applicationId).map { _ =>
        Ok(s"Successfully added missing P2 test for candidate $applicationId")
      }.recover {
        case e: Exception =>
          val msg = s"Failed to add missing P2 test to candidate $applicationId because ${e.getMessage}"
          logger.warn(msg)
          BadRequest(msg)
      }
    }

  def setGis(applicationId: String, newGis: Boolean): Action[AnyContent] = Action.async {
    applicationService.setGis(applicationId, newGis)
      .map(_ => Ok(s"Successfully updated candidate $applicationId to GIS $newGis"))
      .recover {
        case e: Exception =>
          val msg = s"Failed to update candidate $applicationId to GIS $newGis because ${e.getMessage}"
          logger.warn(msg)
          BadRequest(msg)
      }
  }
}
// scalastyle:on number.of.methods file.size.limit
