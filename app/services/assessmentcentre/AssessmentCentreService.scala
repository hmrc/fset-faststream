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

package services.assessmentcentre

import common.FutureEx
import config.AssessmentEvaluationMinimumCompetencyLevel
import model._
import model.command.ApplicationForFsac
import model.exchange.passmarksettings.AssessmentCentrePassMarkSettings
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.{ AnalysisExercise, AssessmentCentreTests }
import play.api.Logger
import repositories.AssessmentScoresRepository
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
}

trait AssessmentCentreService {
  def applicationRepo: GeneralApplicationRepository
  def assessmentCentreRepo: AssessmentCentreRepository
  def passmarkService: PassMarkSettingsService[AssessmentCentrePassMarkSettings]
  def assessmentScoresRepo: AssessmentScoresRepository
  def evaluationEngine: AssessmentCentreEvaluationEngine

  def nextApplicationsForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]] = {
    assessmentCentreRepo.nextApplicationForAssessmentCentre(batchSize)
  }

  def progressApplicationsToAssessmentCentre(applications: Seq[ApplicationForFsac]): Future[SerialUpdateResult[ApplicationForFsac]] = {
    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        assessmentCentreRepo.progressToAssessmentCentre(application,
          ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }

  def nextAssessmentCandidateReadyForEvaluation: Future[Option[AssessmentPassMarksSchemesAndScores]] = {
    passmarkService.getLatestPassMarkSettings.flatMap {
      case Some(passmark) =>
        Logger.debug(s"Assessment evaluation found pass marks - $passmark")
        assessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation(passmark.version).flatMap {
          case Some(appId) =>
            Logger.debug(s"Assessment evaluation found candidate to process - applicationId = $appId")
            tryToFindEvaluationData(appId, passmark)
          case None =>
            Logger.debug(s"Assessment evaluation completed - no candidates found")
            Future.successful(None)
        }
      case None =>
        Logger.debug("Assessment centre pass marks have not been set")
        Future.successful(None)
    }
  }

  // Find existing evaluation data: 1. assessment centre pass marks, 2. the schemes to evaluate and 3. the scores awarded by the reviewer
  def tryToFindEvaluationData(appId: UniqueIdentifier,
    passmark: AssessmentCentrePassMarkSettings): Future[Option[AssessmentPassMarksSchemesAndScores]] = {

    def filterSchemesToEvaluate(schemeList: Seq[SchemeEvaluationResult]) = {
      schemeList.filterNot( schemeEvaluationResult => schemeEvaluationResult.result == model.EvaluationResults.Red.toString)
        .map(_.schemeId)
    }

    for {
      // get the reviewer entered scores for the candidate
      assessmentCentreScoresOpt <- assessmentScoresRepo.find(appId)
      // get the list of schemes with their current results from the current scheme status
      currentSchemeStatusList <- applicationRepo.getCurrentSchemeStatus(appId.toString())
    } yield {
      assessmentCentreScoresOpt.map { scores =>
        Logger.debug(s"AssessmentCentreService - tryToFindEvaluationData - scores = $scores")
        val schemesToEvaluate = filterSchemesToEvaluate(currentSchemeStatusList)

        Logger.debug(s"AssessmentCentreService - tryToFindEvaluationData - current scheme status excluding RED = $schemesToEvaluate")
        AssessmentPassMarksSchemesAndScores(passmark, schemesToEvaluate, scores)
      }
    }
  }

  def evaluateAssessmentCandidate(assessmentPassMarksSchemesAndScores: AssessmentPassMarksSchemesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel): Future[Unit] = {

    Logger.debug(s"evaluateAssessmentCandidate - running")

    val evaluationResult = evaluationEngine.evaluate(assessmentPassMarksSchemesAndScores, config)
    Logger.debug(s"evaluation result = $evaluationResult")

    Logger.debug(s"now writing to DB... applicationId = ${assessmentPassMarksSchemesAndScores.scores.applicationId}" +
      s"")
    val evaluation = AssessmentPassMarkEvaluation(assessmentPassMarksSchemesAndScores.scores.applicationId,
      assessmentPassMarksSchemesAndScores.passmark.version, evaluationResult)
    for {
      currentSchemeStatus <- calculateCurrentSchemeStatus(assessmentPassMarksSchemesAndScores.scores.applicationId,
        evaluationResult.schemesEvaluation)
      _ <- assessmentCentreRepo.saveAssessmentScoreEvaluation(evaluation, currentSchemeStatus)
    } yield {
      Logger.debug(s"written to DB... applicationId = ${assessmentPassMarksSchemesAndScores.scores.applicationId}")
    }
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

  def calculateCurrentSchemeStatus(existingEvaluations: Seq[SchemeEvaluationResult],
    newEvaluations: Seq[SchemeEvaluationResult]): Seq[SchemeEvaluationResult] = {
    val notUpdated = existingEvaluations.filterNot( existingEvaluation => newEvaluations.exists(_.schemeId == existingEvaluation.schemeId))
    newEvaluations ++ notUpdated
  }

  def getTests(applicationId: String): Future[AssessmentCentreTests] = {
    assessmentCentreRepo.getTests(applicationId)
  }

  def updateAnalysisTest(applicationId: String, fileId: String): Future[Unit] = {
    for {
      tests <- getTests(applicationId)
      hasSubmissions = tests.analysisExercise.isDefined
      _ <- if (!hasSubmissions) {
                assessmentCentreRepo.updateTests(applicationId, tests.copy(analysisExercise = Some(AnalysisExercise(fileId))))
            } else { throw CandidateAlreadyHasAnAnalysisExerciseException(s"App Id: $applicationId, File Id: $fileId") }
    } yield ()
  }
}
