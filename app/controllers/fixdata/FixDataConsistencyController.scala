/*
 * Copyright 2019 HM Revenue & Customs
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
import model.Exceptions.{ ApplicationNotFound, NotFoundException }
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_PASSED, _ }
import model.{ ProgressStatuses, SchemeId }
import model.command.FastPassPromotion
import play.api.Logger
import play.api.mvc.{ Action, AnyContent, Result }
import scheduler.assessment.MinimumCompetencyLevelConfig
import services.application.{ ApplicationService, FsbService }
import services.assessmentcentre.{ AssessmentCentreService, ProgressionToFsbOrOfferService }
import services.assessmentcentre.AssessmentCentreService.CandidateHasNoAssessmentScoreEvaluationException
import services.fastpass.FastPassService
import services.onlinetesting.phase2.Phase2TestService2
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FixDataConsistencyController extends FixDataConsistencyController {
  override val applicationService = ApplicationService
  override val fastPassService = FastPassService
  override val siftService = ApplicationSiftService
  override val assessmentCentreService = AssessmentCentreService
  override val progressionToFsbOrOfferService = ProgressionToFsbOrOfferService
  override val fsbService = FsbService
  override val phase2TestService = Phase2TestService2
}

// scalastyle:off number.of.methods
trait FixDataConsistencyController extends BaseController with MinimumCompetencyLevelConfig {
  val applicationService: ApplicationService
  val fastPassService: FastPassService
  val siftService: ApplicationSiftService
  val assessmentCentreService: AssessmentCentreService
  val progressionToFsbOrOfferService: ProgressionToFsbOrOfferService
  val fsbService: FsbService
  val phase2TestService: Phase2TestService2

  def undoFullWithdraw(applicationId: String, newApplicationStatus: ApplicationStatus) = Action.async { implicit request =>
    applicationService.undoFullWithdraw(applicationId, newApplicationStatus).map { _ =>
      Ok
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def removeETray(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingETray(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def removeProgressStatus(appId: String, progressStatus: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingProgressStatus(appId, progressStatus).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def promoteToFastPassAccepted(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FastPassPromotion] { req =>
      fastPassService.promoteToFastPassCandidate(applicationId, req.triggeredBy).map { _ =>
        NoContent
      } recover {
        case _: NotFoundException => NotFound
      }
    }
  }

  def removeVideoInterviewFailed(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingVideoInterviewFailed(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def rollbackToPhase2CompletedFromPhase2Failed(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackCandidateToPhase2CompletedFromPhase2Failed)
  }

  def rollbackToPhase1ResultsReceivedFromPhase1FailedNotified(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackToPhase1ResultsReceivedFromPhase1FailedNotified)
  }

  def rollbackToPhase2ResultsReceivedFromPhase2FailedNotified(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackToPhase2ResultsReceivedFromPhase2FailedNotified)
  }

  def rollbackToSubmittedWithFastPassFromOnlineTestsExpired(applicationId: String, fastPass: Int,
    sdipFaststream: Boolean): Action[AnyContent] = Action.async {
    for {
      _ <- applicationService.convertToFastStreamRouteWithFastpassFromOnlineTestsExpired(applicationId, fastPass, sdipFaststream)
      response <- rollbackApplicationState(applicationId, applicationService.rollbackToSubmittedFromOnlineTestsExpired)
    } yield response
  }

  def rollbackToInProgressFromFastPassAccepted(applicationId: String): Action[AnyContent] = Action.async {
    for {
      response <- rollbackApplicationState(applicationId, applicationService.rollbackToInProgressFromFastPassAccepted)
    } yield response
  }

  def removeSdipSchemeFromFaststreamUser(applicationId: String): Action[AnyContent] = Action.async {
    for {
      _ <- applicationService.removeSdipSchemeFromFaststreamUser(applicationId)
    } yield Ok
  }

  def rollbackApplicationState(applicationId: String, operator: String => Future[Unit]): Future[Result] = {
    operator(applicationId).map { _ =>
      Ok(s"Successfully rolled back $applicationId")
    }.recover { case _ =>
      InternalServerError(s"Unable to rollback $applicationId")
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
      case "FSAC" => {
        for {
          currentSchemeStatus <- applicationService.getCurrentSchemeStatus(applicationId)
          assessmentCentreScoreEvaluation <- assessmentCentreService.getAssessmentScoreEvaluation(applicationId)
          _ = if (assessmentCentreScoreEvaluation.isEmpty) { throw CandidateHasNoAssessmentScoreEvaluationException(applicationId) }
          newEvaluation = assessmentCentreScoreEvaluation.get.copy(passmarkVersion = UUIDFactory.generateUUID())
          _ <- assessmentCentreService.saveAssessmentScoreEvaluation(newEvaluation, currentSchemeStatus)
        } yield Ok(s"Pass marks randomised for application $applicationId in phase $phase")
      }
      case _ => Future.successful(NotImplemented("Phase pass mark randomisation not implemented for phase: " + phase))
    }
  }

  def addProgressStatus(applicationId: String, progressStatus: ProgressStatus): Action[AnyContent] = Action.async {
    applicationService.addProgressStatusAndUpdateAppStatus(applicationId, progressStatus).map(_ => Ok)
  }

  def setPhase3UsedForResults(applicationId: String, newUsedForResults: Boolean, token: String): Action[AnyContent] = Action.async {
    applicationService.setPhase3UsedForResults(applicationId, newUsedForResults, token)
      .map(_ => Ok(s"Successfully updated PHASE3 test for $applicationId"))
  }

  def setPhase2UsedForResults(applicationId: String, inventoryId: String, orderId: String,
                              newUsedForResults: Boolean): Action[AnyContent] = Action.async {
    applicationService.setPhase2UsedForResults(applicationId, inventoryId, orderId, newUsedForResults)
      .map { _ =>
        val msg = s"Successfully updated PHASE2 test usedForResults value to $newUsedForResults for " +
          s"applicationId=$applicationId,inventoryId=$inventoryId,orderId=$orderId"
        Ok(msg)
      }
  }

  def setPhase1UsedForResults(applicationId: String, inventoryId: String, orderId: String,
                              newUsedForResults: Boolean): Action[AnyContent] = Action.async {
    applicationService.setPhase1UsedForResults(applicationId, inventoryId, orderId, newUsedForResults)
      .map { _ =>
        val msg = s"Successfully updated PHASE1 test usedForResults value to $newUsedForResults for " +
          s"applicationId=$applicationId,inventoryId=$inventoryId,orderId=$orderId"
        Ok(msg)
      }
  }

  def findUsersStuckInAssessmentScoresAccepted(): Action[AnyContent] = Action.async {
    assessmentCentreService.findUsersStuckInAssessmentScoresAccepted.map(resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("applicationId,fsac scheme evaluation") ++ resultList.map { user =>
          s"""${user.applicationId},"${user.schemeEvaluation.map(eval => eval.schemeId + " -> " + eval.result).mkString(",")}""""
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
    applicationService.fixUsersEligibleForJobOfferButFsbApplicationStatus().map(affected => Ok(s"$affected Users Fixed"))
  }

  def fixUserStuckInSiftReadyWithFailedPreSiftSiftableSchemes(applicationId: String): Action[AnyContent] = Action.async {
    siftService.fixUserInSiftReadyWhoShouldHaveBeenCompleted(applicationId).map(_ => Ok)
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
          InternalServerError(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixUserStuckInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(applicationId).map(_ => Ok)
    }

  def fixUserSiftedWithAFailByMistake(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserSiftedWithAFailByMistake(applicationId).map(_ =>
        Ok(s"Successfully fixed $applicationId. Remember to update the CurrentSchemeStatus")
      ).recover {
        case ex: Throwable =>
          InternalServerError(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixUserSiftedWithAFailToSiftCompleted(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserSiftedWithAFailToSiftCompleted(applicationId).map(_ =>
        Ok(s"Successfully fixed $applicationId. Remember to update the CurrentSchemeStatus and sift evaluation")
      ).recover {
        case ex: Throwable =>
          InternalServerError(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixSdipFaststreamCandidateWhoExpiredInOnlineTests(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    applicationService.fixSdipFaststreamCandidateWhoExpiredInOnlineTests(applicationId).map(_ => Ok(s"Successfully fixed $applicationId"))
  }

  def markSiftSchemeAsRed(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markSiftSchemeAsRed(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as red for $applicationId")
    )
  }

  def markSiftSchemeAsGreen(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markSiftSchemeAsGreen(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as green for $applicationId")
    )
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
    )
  }

  def markFsbSchemeAsGreen(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markFsbSchemeAsGreen(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as green for $applicationId")
    )
  }

  def rollbackToSiftReadyFromAssessmentCentreAwaitingAllocation(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.rollbackToSiftReadyFromAssessmentCentreAwaitingAllocation(applicationId).map(_ =>
      Ok(s"Successfully rolled $applicationId back to sift ready")
    )
  }

  def updateCurrentSchemeStatusScheme(applicationId: String, schemeId: SchemeId,
                                      result: model.EvaluationResults.Result): Action[AnyContent] = Action.async {
    applicationService.updateCurrentSchemeStatusScheme(applicationId, schemeId, result).map(_ =>
      Ok(s"Successfully updated CSS for schemeId ${schemeId.toString} to ${result.toString} for $applicationId ")
    )
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
      )
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
    )
  }

  def rollbackFastPassFromFsacToSubmitted(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    applicationService.rollbackFastPassFromFsacToSubmitted(applicationId)
      .map(_ => Ok(s"Successfully rolled $applicationId back to Submitted from FSAC"))
  }

  def rollbackToSubmittedFromOnlineTestsAndAddFastpassNumber(applicationId: String, certificateNumber: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.rollbackToSubmittedFromOnlineTestsAndAddFastpassNumber(applicationId, certificateNumber)
        .map(_ => Ok(s"Successfully rolled $applicationId back to Submitted and added Fastpass($certificateNumber"))
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
    )
  }

  def rollbackToFsacAwaitingAllocationFromFsacFailed(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.rollbackToFsacAwaitingAllocationFromFsacFailed(applicationId).map(_ =>
      Ok(s"Successfully rolled $applicationId back to FSAC AWAITING ALLOCATION. Remember to update the CurrentSchemeStatus")
    )
  }

  def rollbackToFsacAllocationConfirmedFromFsb(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.rollbackToFsacAllocationConfirmedFromFsb(applicationId).map(_ =>
      Ok(s"Successfully rolled $applicationId back to FSAC AWAITING ALLOCATION. Remember to update the CurrentSchemeStatus")
    )
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
      )
    }

  def rollbackToPhase2TestExpiredFromSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.rollbackToPhase2ExpiredFromSift(applicationId).map(_ =>
        Ok(s"Successfully rolled back to phase2 expired $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixPhase2PartialCallbackCandidate(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.fixPhase2PartialCallbackCandidate(applicationId).map(_ =>
        Ok(s"Successfully fixed partial callback candidate $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix partial callback candidate $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixPhase3ExpiredCandidate(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.fixPhase3ExpiredCandidate(applicationId).map(_ =>
        Ok(s"Successfully fixed p3 expired candidate $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix p3 expired candidate $applicationId - message: ${ex.getMessage}")
      }
    }

  def rollbackToPhase3TestExpiredFromSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.rollbackToPhase3ExpiredFromSift(applicationId).map(_ =>
        Ok(s"Successfully rolled back to phase3 expired $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def rollbackToPhase1TestsPassedFromSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.rollbackToPhase1TestsPassedFromSift(applicationId).map(_ =>
        Ok(s"Successfully rolled back to phase1 tests passed $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def enablePhase3CandidateToBeEvaluated(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.enablePhase3CandidateToBeEvaluated(applicationId).map(_ =>
        Ok(s"Successfully updated phase3 state so candidate $applicationId can be evaluated ")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removePhase3TestAndSetOtherToActive(removeTestToken: String, markTestAsActiveToken: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.removePhase3TestAndSetOtherToActive(removeTestToken, markTestAsActiveToken).map(_ =>
        Ok(s"Successfully removed phase3 test for token $removeTestToken and set other test to active for token $markTestAsActiveToken ")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix candidate - message: ${ex.getMessage}")
      }
    }

  def rollbackToRetakePhase3FromSift(applicationId: String, token: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.rollbackToRetakePhase3FromSift(applicationId, token).map(_ =>
        Ok(s"Successfully rolled back candidate $applicationId from sift so can retake video interview")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removePhase1TestEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.removePhase1TestEvaluation(applicationId).map(_ =>
        Ok(s"Successfully removed P1 test evaluation and css for candidate $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removePhase3TestEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.removePhase3TestEvaluation(applicationId).map(_ =>
        Ok(s"Successfully removed P3 test evaluation and updated css for candidate $applicationId")
      ).recover {
        case ex: Throwable =>
          BadRequest(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def removeSiftTestGroup(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    applicationService.removeSiftTestGroup(applicationId).map(_ => Ok(s"Successfully removed SIFT test group for $applicationId"))
  }

  def removeFsbTestGroup(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    applicationService.removeFsbTestGroup(applicationId).map { _ =>
      Ok(s"Successfully removed FSB test group for $applicationId")
    } recover {
      case _: ApplicationNotFound => NotFound
    }
  }

  def updateApplicationStatus(applicationId: String, newApplicationStatus: ApplicationStatus) = Action.async { implicit request =>
    applicationService.updateApplicationStatus(applicationId, newApplicationStatus).map { _ =>
      Ok(s"Successfully updated $applicationId application status to $newApplicationStatus")
    } recover {
      case _: ApplicationNotFound => NotFound
    }
  }

  def fsacResetFastPassCandidate(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.fsacResetFastPassCandidate(applicationId).map { _ =>
        Ok(s"Successfully reset fsac fast pass candidate $applicationId. Remember to update the current scheme status")
      } recover {
        case _: NotFoundException => NotFound
      }
    }

  def fsacRollbackWithdraw(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.fsacRollbackWithdraw(applicationId).map { _ =>
        Ok(s"Successfully rolled back withdraw for fsac candidate $applicationId")
      } recover {
        case _: NotFoundException => NotFound
      }
    }

  def fsacRemoveEvaluation(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.fsacRemoveEvaluation(applicationId).map { _ =>
        Ok(s"Successfully removed evaluation for fsac candidate $applicationId")
      } recover {
        case _: NotFoundException => NotFound
      }
    }

  def fsacEvaluateCandidate(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      assessmentCentreService.nextSpecificCandidateReadyForEvaluation(applicationId).flatMap { candidateResults =>
        if (candidateResults.isEmpty) {
          Future.successful(BadRequest("No candidate found to evaluate at FSAC. Please check the candidate's state in the diagnostic report"))
        } else {
          val candidateFutures = candidateResults.map { candidateResult =>

            if (candidateResult.schemes.isEmpty) {
              val msg = s"FSAC candidate $applicationId has no eligible schemes so will not evaluate"
              Logger.warn(msg)
              Future.failed(new Exception(msg))
            } else {
              assessmentCentreService.evaluateAssessmentCandidate(candidateResult, minimumCompetencyLevelConfig)
            }
          }
          Future.sequence(candidateFutures).map(_ => Ok(s"Successfully evaluated candidate $applicationId at FSAC"))
            .recover {
              case ex: Throwable => BadRequest(ex.getMessage)
            }
        }
      }
    }

  def progressCandidateToFsbOrOfferJob(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc = HeaderCarrier()
      progressionToFsbOrOfferService.nextApplicationForFsbOrJobOffer(applicationId).flatMap {
        case Nil =>
          val msg = "No candidate found to progress to fsb or offer job. Please check the candidate's state in the diagnostic report"
          Future.successful(BadRequest(msg))
        case application =>
          progressionToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(application).map { result =>
            val msg = s"Progress to fsb or job offer complete - ${result.successes.size} processed successfully " +
              s"and ${result.failures.size} failed to update"
            Ok(msg)
          }
      }
    }

  def progressCandidateFailedAtFsb(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      fsbService.processApplicationFailedAtFsb(applicationId).map { result =>
        val successfulAppIds = result.successes.map( _.applicationId )
        val failedAppIds = result.failures.map( _.applicationId )
        val msg = s"Progress candidate failed at FSB complete - ${result.successes.size} updated, appIds: ${successfulAppIds.mkString(",")} " +
          s"and ${result.failures.size} failed to update, appIds: ${failedAppIds.mkString(",")}"
        Logger.warn(msg)
        if (result.failures.nonEmpty) {
          BadRequest(s"Failed to update candidate, appId: ${failedAppIds.mkString(",")}")
        } else if (result.successes.isEmpty) {
          BadRequest(s"No candidate found to update, appId: $applicationId. Please check the candidate's status")
        } else {
          Ok(msg)
        }
      }
    }

  def inviteP2CandidateToMissingTest(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc = HeaderCarrier()
      phase2TestService.inviteP2CandidateToMissingTest(applicationId).map { _ =>
        Ok(s"Successfully added missing P2 test for candidate $applicationId")
      }.recover {
        case e: Exception =>
          val msg = s"Failed to add missing P2 test to candidate $applicationId because ${e.getMessage}"
          Logger.warn(msg)
          BadRequest(msg)
      }
    }
}
// scalastyle:on
