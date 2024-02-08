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

package services.assessmentcentre

import com.google.inject.name.Named
import common.FutureEx

import javax.inject.{Inject, Singleton}
import model.EvaluationResults.{AssessmentEvaluationResult, CompetencyAverageResult, ExerciseAverageResult, Green}
import model.Exceptions.NoResultsReturned
import model.ProgressStatuses._
import model._
import model.assessmentscores.{AssessmentScoresExercise, FixUserStuckInScoresAccepted}
import model.command.ApplicationForProgression
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType
import model.exchange.passmarksettings.{AssessmentCentrePassMarkSettings, AssessmentCentrePassMarkSettingsPersistence}
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.{AnalysisExercise, AssessmentCentreTests}
import play.api.Logging
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.{AssessmentScoresRepository, CurrentSchemeStatusHelper}
import services.assessmentcentre.AssessmentCentreService.CandidateAlreadyHasAnAnalysisExerciseException
import services.evaluation.AssessmentCentreEvaluationEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService

import scala.concurrent.{ExecutionContext, Future}

object AssessmentCentreService {
  case class CandidateAlreadyHasAnAnalysisExerciseException(message: String) extends Exception(message)
  case class CandidateHasNoAnalysisExerciseException(message: String) extends Exception(message)
  case class CandidateHasNoAssessmentScoreEvaluationException(message: String) extends Exception(message)
}

@Singleton
class AssessmentCentreService @Inject() (applicationRepo: GeneralApplicationRepository,
                                         assessmentCentreRepo: AssessmentCentreRepository,
                                         passmarkService: AssessmentCentrePassMarkSettingsService,
                                         @Named("ReviewerAssessmentScoresRepo") assessmentScoresRepo: AssessmentScoresRepository,
                                         evaluationEngine: AssessmentCentreEvaluationEngine
                                        )(implicit ec: ExecutionContext) extends CurrentSchemeStatusHelper with Logging {

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
        logger.debug(s"$logPrefix Assessment evaluation found pass marks (abbreviated shown) - ${passmark.abbreviated}")
        assessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation(passmark.version, batchSize).flatMap { applicationIds =>
          commonProcessApplicationIds(applicationIds, passmark)
        }
      case None =>
        logger.debug(s"$logPrefix Assessment centre pass marks have not been set so terminating")
        Future.successful(Seq.empty)
    }
  }

  private def commonProcessApplicationIds(applicationIds: Seq[UniqueIdentifier], passmark: AssessmentCentrePassMarkSettingsPersistence) = {
    applicationIds match {
      case appIds if appIds.nonEmpty =>
        logger.warn(
          s"$logPrefix Assessment evaluation found ${appIds.size} candidate(s) to process - applicationIds = [${appIds.mkString(",")}]")
        Future.sequence(appIds.map(appId => tryToFindEvaluationData(appId, passmark))).map(_.flatten)
      case Nil =>
        logger.warn(s"$logPrefix Assessment evaluation completed - no candidates found")
        Future.successful(Seq.empty)
    }
  }

  def nextSpecificCandidateReadyForEvaluation(applicationId: String): Future[Seq[AssessmentPassMarksSchemesAndScores]] = {
    passmarkService.getLatestPassMarkSettings.flatMap {
      case Some(passmark) =>
        logger.debug(s"$logPrefix Assessment evaluation found pass marks - $passmark")
        assessmentCentreRepo.nextSpecificApplicationReadyForAssessmentScoreEvaluation(
          passmark.version, applicationId).flatMap { applicationIds =>
          commonProcessApplicationIds(applicationIds, passmark)
        }
      case None =>
        logger.debug(s"$logPrefix Cannot evaluate specific candidate $applicationId because assessment centre pass marks have not been set")
        Future.successful(Seq.empty)
    }
  }

  // Find existing evaluation data: 1. assessment centre pass marks, 2. the schemes to evaluate and 3. the scores awarded by the reviewer
  def tryToFindEvaluationData(appId: UniqueIdentifier,
                              passmark: AssessmentCentrePassMarkSettingsPersistence): Future[Option[AssessmentPassMarksSchemesAndScores]] = {

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
        logger.warn(s"$logPrefix AssessmentCentreService - tryToFindEvaluationData - appId=$appId, scores=$scores")
        val schemesToEvaluate = filterSchemesToEvaluate(currentSchemeStatusList)

        val msg = s"$logPrefix AssessmentCentreService - tryToFindEvaluationData - appId=$appId, current scheme status excluding Red, " +
          s"Withdrawn and Sdip = $schemesToEvaluate"
        logger.warn(msg)
        AssessmentPassMarksSchemesAndScores(passmark, schemesToEvaluate, scores)
      }
    }
  }

  //scalastyle:off method.length cyclomatic.complexity
  def setFsacAverageScore(applicationId: UniqueIdentifier, version: String, averageScoreName: String, averageScore: Double): Future[Unit] = {
    val prefix = s"AssessmentCentreService (applicationId=$applicationId)"
    for {
      // Get the reviewer entered scores for the candidate
      assessmentCentreScoresOpt <- assessmentScoresRepo.find(applicationId)
    } yield {
      if (assessmentCentreScoresOpt.isEmpty) {
        throw NoResultsReturned(s"No reviewed assessment scores found for applicationId:$applicationId")
      } else {
        assessmentCentreScoresOpt.foreach { scores =>
          logger.debug(s"$prefix - found AssessmentScoresAllExercises data for applicationId=$applicationId")
          // Find the exercise, which matches the version
          logger.debug(s"$prefix - looking for an exercise whose version=$version")
          val writtenExercise = scores.writtenExercise.filter { e => e.version.contains(version) }
          val groupExercise = scores.teamExercise.filter { e => e.version.contains(version) }
          val leadershipExercise = scores.leadershipExercise.filter { e => e.version.contains(version) }

          val assessmentScoresSectionType =
            if (writtenExercise.isDefined) {
              logger.debug(s"$prefix - found writtenExercise for version=$version")
              AssessmentScoresSectionType.writtenExercise
            } else if (groupExercise.isDefined) {
              logger.debug(s"$prefix - found groupExercise for version=$version")
              AssessmentScoresSectionType.teamExercise
            } else if (leadershipExercise.isDefined) {
              logger.debug(s"$prefix - found leadershipExercise for version=$version")
              AssessmentScoresSectionType.leadershipExercise
            } else {
              logger.debug(s"$prefix - found no exercise for version=$version")
              throw NoResultsReturned(s"No exercise found whose version=$version")
            }

          // Remove the empty options
          val singleExerciseOpt = List(writtenExercise, groupExercise, leadershipExercise).flatten.headOption

          def getAssessmentScoresExercise(singleExerciseOpt: Option[AssessmentScoresExercise]) =  singleExerciseOpt.getOrElse(
            throw new Exception(s"Failed to find AssessmentScoresExercise in which to set $averageScoreName")
          )

          def saveData(newAssessmentScores: AssessmentScoresExercise) =
            assessmentScoresRepo.saveExercise(applicationId, assessmentScoresSectionType, newAssessmentScores, Some(version)).map( _ =>
              logger.debug(s"$prefix - new value has been saved for $averageScoreName")
            )

          averageScoreName match {
            case "seeingTheBigPictureAverage" =>
              logger.debug(s"$prefix - matched score name: $averageScoreName")
              val oldAssessmentScores = getAssessmentScoresExercise(singleExerciseOpt)
              val newAssessmentScores = oldAssessmentScores.copy(seeingTheBigPictureAverage = Some(averageScore))
              saveData(newAssessmentScores)

            case "makingEffectiveDecisionsAverage" =>
              logger.debug(s"$prefix - matched score name: $averageScoreName")
              val oldAssessmentScores = getAssessmentScoresExercise(singleExerciseOpt)
              val newAssessmentScores = oldAssessmentScores.copy(makingEffectiveDecisionsAverage = Some(averageScore))
              saveData(newAssessmentScores)

            case "communicatingAndInfluencingAverage" =>
              logger.debug(s"$prefix - matched score name: $averageScoreName")
              val oldAssessmentScores = getAssessmentScoresExercise(singleExerciseOpt)
              val newAssessmentScores = oldAssessmentScores.copy(communicatingAndInfluencingAverage = Some(averageScore))
              saveData(newAssessmentScores)

            case "workingTogetherDevelopingSelfAndOthersAverage" =>
              logger.debug(s"$prefix - matched score name: $averageScoreName")
              val oldAssessmentScores = getAssessmentScoresExercise(singleExerciseOpt)
              val newAssessmentScores = oldAssessmentScores.copy(workingTogetherDevelopingSelfAndOthersAverage = Some(averageScore))
              saveData(newAssessmentScores)

            case _ => throw new Exception(s"The given averageScoreName ($averageScoreName) does not exist")
          }
        }
      }
    }
  }
  //scalastyle:on

  //here - entry point
  def evaluateAssessmentCandidate(assessmentPassMarksSchemesAndScores: AssessmentPassMarksSchemesAndScores): Future[Unit] = {

    logger.warn(s"$logPrefix evaluateAssessmentCandidate - running")

    val evaluationResult = evaluationEngine.evaluate(assessmentPassMarksSchemesAndScores)
    logger.warn(s"$logPrefix evaluation result for applicationId = ${assessmentPassMarksSchemesAndScores.scores.applicationId} = " +
      s"$evaluationResult")

    logger.warn(s"$logPrefix now writing to DB... applicationId = ${assessmentPassMarksSchemesAndScores.scores.applicationId}")

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
      logger.warn(s"$logPrefix written to DB... applicationId = ${assessmentPassMarksSchemesAndScores.scores.applicationId}")
    }
  }

  def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]] = {
    assessmentCentreRepo.getAssessmentScoreEvaluation(applicationId)
  }

  def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation,
                                    currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] =
    assessmentCentreRepo.saveAssessmentScoreEvaluation(evaluation, currentSchemeStatus)

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
        fsacResults = assessmentPassmarkEvaluation.evaluationResult.fsacResults,
        schemesEvaluation = allSchemes))
  }

  private def maybeMoveCandidateToPassedOrFailed(applicationId: UniqueIdentifier,
                                                 latestProgressStatusOpt: Option[ProgressStatus],
                                                 results: Seq[SchemeEvaluationResult],
                                                 isSdipFaststream: Boolean): Future[Unit] = {
    latestProgressStatusOpt.map { latestProgressStatus =>
      if (latestProgressStatus == ASSESSMENT_CENTRE_SCORES_ACCEPTED) {
        firstResidualPreference(results, isSdipFaststream) match {
          // First residual preference is green
          case Some(evaluationResult) if evaluationResult.result == Green.toString =>
            logger.warn(s"$logPrefix $applicationId - first residual preference (${evaluationResult.schemeId.toString()}) is green, " +
              s"moving candidate to $ASSESSMENT_CENTRE_PASSED")
            applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId.toString(), ASSESSMENT_CENTRE_PASSED)
          // No greens or ambers (i.e. all red or withdrawn)
          case None =>
            if (isSdipFaststream && results.contains(SchemeEvaluationResult(SchemeId(Scheme.Sdip), Green.toString))) {
              logger.warn(s"$logPrefix Sdip faststream candidate has failed or withdrawn from all faststream schemes, " +
                s"moving candidate to $ASSESSMENT_CENTRE_FAILED_SDIP_GREEN, applicationId = $applicationId")
              applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId.toString(), ASSESSMENT_CENTRE_FAILED_SDIP_GREEN)
            } else {
              logger.warn(s"$logPrefix $applicationId - there is no first non-red/withdrawn residual preference, " +
                s"moving candidate to $ASSESSMENT_CENTRE_FAILED")
              applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId.toString(), ASSESSMENT_CENTRE_FAILED)
            }
          case _ =>
            logger.warn(s"$logPrefix $applicationId - residual preferences are amber or red (but not all red), candidate status " +
              s"has not been changed")
            Future.successful(())
        }
      } else {
        // Don't move anyone not in a SCORES_ACCEPTED status
        logger.warn(s"$logPrefix $applicationId - this was a reevaluation, candidate is not in SCORES_ACCEPTED, candidate status has " +
          s"not been changed")
        Future.successful(())
      }
    }.getOrElse {
      logger.warn(s"$logPrefix $applicationId - no progress status, candidate status has not been changed")
      Future.successful(())
    }
  }

  def findUsersStuckInAssessmentScoresAccepted: Future[Seq[FixUserStuckInScoresAccepted]] = {
    assessmentCentreRepo.findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted
  }

  def calculateCurrentSchemeStatus(applicationId: UniqueIdentifier,
                                   evaluationResults: Seq[SchemeEvaluationResult]): Future[Seq[SchemeEvaluationResult]] = {
    for {
      currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(applicationId.toString())
    } yield {
      val newSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, evaluationResults)
      logger.warn(s"After evaluation newSchemeStatus = $newSchemeStatus for applicationId: $applicationId")
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

  def getFsacExerciseResultAverages(applicationId: String): Future[Option[ExerciseAverageResult]] = {
    for {
      averagesOpt <- assessmentCentreRepo.getFsacExerciseResultAverages(applicationId)
    } yield {
      averagesOpt
    }
  }
}
