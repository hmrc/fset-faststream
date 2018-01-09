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

package services.assessmentcentre

import common.FutureEx
import config.AssessmentEvaluationMinimumCompetencyLevel
import model.EvaluationResults.{ AssessmentEvaluationResult, CompetencyAverageResult, Green }
import model.ProgressStatuses._
import model._
import model.assessmentscores.FixUserStuckInScoresAccepted
import model.command.ApplicationForProgression
import model.exchange.passmarksettings.AssessmentCentrePassMarkSettings
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.{ AnalysisExercise, AssessmentCentreTests }
import play.api.Logger
import repositories.{ AssessmentScoresRepository, CurrentSchemeStatusHelper }
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import services.assessmentcentre.AssessmentCentreService.CandidateAlreadyHasAnAnalysisExerciseException
import services.evaluation.AssessmentCentreEvaluationEngine
import services.passmarksettings.{ AssessmentCentrePassMarkSettingsService, PassMarkSettingsService }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreService extends AssessmentCentreService {
  val applicationRepo = repositories.applicationRepository
  val assessmentCentreRepo = repositories.assessmentCentreRepository
  val passmarkService = AssessmentCentrePassMarkSettingsService
  val assessmentScoresRepo = repositories.reviewerAssessmentScoresRepository
  val evaluationEngine = AssessmentCentreEvaluationEngine

  case class CandidateAlreadyHasAnAnalysisExerciseException(message: String) extends Exception(message)
  case class CandidateHasNoAnalysisExerciseException(message: String) extends Exception(message)
  case class CandidateHasNoAssessmentScoreEvaluationException(message: String) extends Exception(message)
}

trait AssessmentCentreService extends CurrentSchemeStatusHelper {
  def applicationRepo: GeneralApplicationRepository
  def assessmentCentreRepo: AssessmentCentreRepository
  def passmarkService: PassMarkSettingsService[AssessmentCentrePassMarkSettings]
  def assessmentScoresRepo: AssessmentScoresRepository
  def evaluationEngine: AssessmentCentreEvaluationEngine

  private val logPrefix = "[Assessment Evaluation]"

  def nextApplicationsForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    assessmentCentreRepo.nextApplicationForAssessmentCentre(batchSize)
  }

  def progressApplicationsToAssessmentCentre(applications: Seq[ApplicationForProgression])
  : Future[SerialUpdateResult[ApplicationForProgression]] = {
    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        assessmentCentreRepo.progressToAssessmentCentre(application,
          ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }

  def nextAssessmentCandidatesReadyForEvaluation(batchSize: Int): Future[Seq[AssessmentPassMarksSchemesAndScores]] = {
    passmarkService.getLatestPassMarkSettings.flatMap {
      case Some(passmark) =>
        Logger.debug(s"$logPrefix Assessment evaluation found pass marks - $passmark")
        assessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation(passmark.version, batchSize).flatMap {
          case appIds if appIds.nonEmpty =>
            Logger.debug(
              s"$logPrefix Assessment evaluation found ${appIds.size} candidates to process - applicationIds = [${appIds.mkString(",")}]")
            Future.sequence(appIds.map(appId => tryToFindEvaluationData(appId, passmark))).map(_.flatten)
          case Nil =>
            Logger.debug(s"$logPrefix Assessment evaluation completed - no candidates found")
            Future.successful(Seq.empty)
        }
      case None =>
        Logger.debug(s"$logPrefix Assessment centre pass marks have not been set")
        Future.successful(Seq.empty)
    }
  }

  // Find existing evaluation data: 1. assessment centre pass marks, 2. the schemes to evaluate and 3. the scores awarded by the reviewer
  def tryToFindEvaluationData(appId: UniqueIdentifier,
    passmark: AssessmentCentrePassMarkSettings): Future[Option[AssessmentPassMarksSchemesAndScores]] = {

    def filterSchemesToEvaluate(schemeList: Seq[SchemeEvaluationResult]) = {
      schemeList.filterNot( schemeEvaluationResult =>
        schemeEvaluationResult.result == model.EvaluationResults.Red.toString ||
        schemeEvaluationResult.result == model.EvaluationResults.Withdrawn.toString ||
        schemeEvaluationResult.schemeId == SchemeId(Scheme.Sdip)
      ).map(_.schemeId)
    }

    for {
      // get the reviewer entered scores for the candidate
      assessmentCentreScoresOpt <- assessmentScoresRepo.find(appId)
      // get the list of schemes with their current results from the current scheme status
      currentSchemeStatusList <- applicationRepo.getCurrentSchemeStatus(appId.toString())
    } yield {
      assessmentCentreScoresOpt.map { scores =>
        Logger.debug(s"$logPrefix AssessmentCentreService - tryToFindEvaluationData - scores = $scores")
        val schemesToEvaluate = filterSchemesToEvaluate(currentSchemeStatusList)

        val msg = s"$logPrefix AssessmentCentreService - tryToFindEvaluationData - current scheme status excluding Red, " +
          s"Withdrawn and Sdip = $schemesToEvaluate"
        Logger.debug(msg)
        AssessmentPassMarksSchemesAndScores(passmark, schemesToEvaluate, scores)
      }
    }
  }

  def evaluateAssessmentCandidate(assessmentPassMarksSchemesAndScores: AssessmentPassMarksSchemesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel): Future[Unit] = {

    Logger.debug(s"$logPrefix evaluateAssessmentCandidate - running")

    val evaluationResult = evaluationEngine.evaluate(assessmentPassMarksSchemesAndScores, config)
    Logger.debug(s"$logPrefix evaluation result = $evaluationResult")

    Logger.debug(s"$logPrefix now writing to DB... applicationId = ${assessmentPassMarksSchemesAndScores.scores.applicationId}")

    val applicationId = assessmentPassMarksSchemesAndScores.scores.applicationId
    val evaluation = AssessmentPassMarkEvaluation(applicationId, assessmentPassMarksSchemesAndScores.passmark.version, evaluationResult)
    for {
      currentSchemeStatus <- calculateCurrentSchemeStatus(applicationId, evaluationResult.schemesEvaluation)
      evaluatedSchemes <- assessmentCentreRepo.getFsacEvaluatedSchemes(applicationId.toString())
      mergedEvaluation = mergeSchemes(evaluationResult.schemesEvaluation, evaluatedSchemes, evaluation)
      _ <- assessmentCentreRepo.saveAssessmentScoreEvaluation(mergedEvaluation, currentSchemeStatus)
      applicationStatus <- applicationRepo.findStatus(applicationId.toString())
      _ <- maybeMoveCandidateToPassedOrFailed(applicationId, applicationStatus.latestProgressStatus, currentSchemeStatus,
        applicationStatus.applicationRoute == ApplicationRoute.SdipFaststream)
    } yield {
      Logger.debug(s"$logPrefix written to DB... applicationId = ${assessmentPassMarksSchemesAndScores.scores.applicationId}")
    }
  }

  def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]] = {
    assessmentCentreRepo.getAssessmentScoreEvaluation(applicationId)
  }

  def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation,
    currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = assessmentCentreRepo.saveAssessmentScoreEvaluation(
    evaluation, currentSchemeStatus
  )

  private def mergeSchemes(evaluation: Seq[SchemeEvaluationResult], evaluatedSchemesFromDb: Option[Seq[SchemeEvaluationResult]],
    assessmentPassmarkEvaluation: AssessmentPassMarkEvaluation): AssessmentPassMarkEvaluation = {
      // find any schemes which have been previously evaluated and stored in db and are not in the current evaluated schemes collection
      // these will only be schemes that have been evaluated to red
      val failedSchemes = evaluatedSchemesFromDb.map { evaluatedSchemesSeq =>
        val schemesEvaluatedNow: Seq[SchemeId] = evaluation.groupBy(_.schemeId).keys.toList

        // Any schemes read from db, which have not been evaluated this time will be failed schemes so identify those here
        evaluatedSchemesSeq.filterNot( es => schemesEvaluatedNow.contains(es.schemeId) )
      }.getOrElse(Nil)
      val allSchemes = evaluation ++ failedSchemes
      assessmentPassmarkEvaluation.copy(evaluationResult =
        AssessmentEvaluationResult(
          passedMinimumCompetencyLevel = assessmentPassmarkEvaluation.evaluationResult.passedMinimumCompetencyLevel,
          competencyAverageResult = assessmentPassmarkEvaluation.evaluationResult.competencyAverageResult,
          schemesEvaluation = allSchemes))
  }

  private def maybeMoveCandidateToPassedOrFailed(applicationId: UniqueIdentifier,
    latestProgressStatusOpt: Option[ProgressStatus], results: Seq[SchemeEvaluationResult], isSdipFaststream: Boolean): Future[Unit] = {

    latestProgressStatusOpt.map { latestProgressStatus =>
      if (latestProgressStatus == ASSESSMENT_CENTRE_SCORES_ACCEPTED) {
        firstResidualPreference(results, isSdipFaststream) match {
          // First residual preference is green
          case Some(evaluationResult) if evaluationResult.result == Green.toString =>
            Logger.debug(s"$logPrefix First residual preference (${evaluationResult.schemeId.toString()}) is green, moving candidate to passed")
            applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId.toString(), ASSESSMENT_CENTRE_PASSED)
          // No greens or ambers (i.e. all red or withdrawn)
          case None =>
            if (isSdipFaststream && results.contains(SchemeEvaluationResult(SchemeId(Scheme.Sdip), Green.toString))) {
              val msg = s"$logPrefix Sdip faststream candidate has failed or withdrawn from all faststream schemes, " +
                s"moving candidate to ASSESSMENT_CENTRE_FAILED_SDIP_GREEN, applicationId = $applicationId"
              Logger.debug(msg)
              applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId.toString(), ASSESSMENT_CENTRE_FAILED_SDIP_GREEN)
            } else {
              Logger.debug(s"$logPrefix There is no first non-red/withdrawn residual preference, moving candidate to failed")
              applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId.toString(), ASSESSMENT_CENTRE_FAILED)
            }
          case _ =>
            Logger.debug(s"$logPrefix Residual preferences are amber or red (but not all red), candidate status has not been changed")
            Future.successful(())
        }
      } else {
        // Don't move anyone not in a SCORES_ACCEPTED status
        Logger.debug(s"$logPrefix This was a reevaluation, candidate is not in SCORES_ACCEPTED, candidate status has not been changed")
        Future.successful(())
      }
    }.getOrElse {
      Logger.debug(s"$logPrefix No progress status, candidate status has not been changed")
      Future.successful(())
    }
  }

  def findUsersStuckInAssessmentScoresAccepted: Future[Seq[FixUserStuckInScoresAccepted]] = {
    // Find all with assessment scores accepted and no pass or fail
    // filter to all users where there's an evaluation that has at least one green or all red
    ???
  }

  def calculateCurrentSchemeStatus(applicationId: UniqueIdentifier,
    evaluationResults: Seq[SchemeEvaluationResult]): Future[Seq[SchemeEvaluationResult]] = {
    for {
      currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(applicationId.toString())
    } yield {
      val newSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, evaluationResults)
      Logger.debug(s"After evaluation newSchemeStatus = $newSchemeStatus for applicationId: $applicationId")
      newSchemeStatus
    }
  }

  def getTests(applicationId: String): Future[AssessmentCentreTests] = {
    assessmentCentreRepo.getTests(applicationId)
  }

  def updateAnalysisTest(applicationId: String, fileId: String, isAdminUpdate: Boolean = false): Future[Unit] = {
    for {
      tests <- getTests(applicationId)
      hasSubmissions = tests.analysisExercise.isDefined
      _ <- if (!hasSubmissions || isAdminUpdate) {
                assessmentCentreRepo.updateTests(applicationId, tests.copy(analysisExercise = Some(AnalysisExercise(fileId))))
            } else { throw CandidateAlreadyHasAnAnalysisExerciseException(s"App Id: $applicationId, File Id: $fileId") }
    } yield ()
  }

  def getFsacEvaluationResultAverages(applicationId: String): Future[Option[CompetencyAverageResult]] = {
    for {
      averagesOpt <- assessmentCentreRepo.getFsacEvaluationResultAverages(applicationId)
    } yield {
      averagesOpt
    }
  }
}
