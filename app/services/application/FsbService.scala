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

package services.application

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.EvaluationResults.{ Green, Red }
import model.ProgressStatuses._
import model.{ AssessmentPassMarkEvaluation, AssessmentPassMarksSchemesAndScores, SchemeId, UniqueIdentifier }
import model.exchange.ApplicationResult
import model.fsb.FSBProgressStatus
import model.persisted.{ FsbEvaluation, FsbSchemeResult, SchemeEvaluationResult }
import play.api.Logger
import repositories.application.GeneralApplicationMongoRepository
import repositories.fsb.{ FsbMongoRepository, FsbRepository }
import repositories.{ CurrentSchemeStatusHelper, SchemeRepository, SchemeYamlRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object FsbService extends FsbService {
  override val applicationRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
  override val fsbRepo: FsbMongoRepository = repositories.fsbRepository
  override val schemeRepo: SchemeYamlRepository.type = SchemeYamlRepository
}

trait FsbService extends CurrentSchemeStatusHelper {
  val applicationRepo: GeneralApplicationMongoRepository
  val fsbRepo: FsbRepository
  val schemeRepo: SchemeRepository

  val logPrefix = "[FsbEvaluation]"

  def nextFsbCandidateReadyForEvaluation: Future[Option[UniqueIdentifier]] = {
    fsbRepo.nextApplicationReadyForFsbEvaluation
  }

  def evaluateFsbCandidate(applicationId: UniqueIdentifier): Future[Unit] = {

    Logger.debug(s"$logPrefix running for application $applicationId")

    val appId = applicationId.toString()

    for {
      fsbEvaluation <- fsbRepo.findByApplicationId(appId).map(_.map(_.evaluation.result))
      currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(appId)
      firstPreference = firstResidualPreference(currentSchemeStatus)
      _ <- passOrFailFsb(appId, fsbEvaluation, firstPreference, currentSchemeStatus)
    } yield {
      Logger.debug(s"$logPrefix written to DB... applicationId = $appId")
    }
  }

  private def passOrFailFsb(appId: String, fsbEvaluation: Option[Seq[SchemeEvaluationResult]],
    firstResidualPreferenceOpt: Option[SchemeEvaluationResult], currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {

    require(fsbEvaluation.isDefined, "Evaluation for scheme must be defined to reach this stage, unexpected error.")
    require(firstResidualPreferenceOpt.isDefined, "First residual preference must be defined to reach this stage, unexpected error.")

    val firstResidualPreference = firstResidualPreferenceOpt.get

    val firstResidualInEvaluation = fsbEvaluation.get.find(_.schemeId == firstResidualPreference.schemeId)

    require(firstResidualInEvaluation.isDefined,
      "First residual preference must be present in FSB fsbEvaluation for pass/fail, unexpected error.")

    val firstResidualInEval = firstResidualInEvaluation.get

    if (firstResidualInEval.result == Green.toString) {
      for {
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, FSB_PASSED)
        // There are no notifications before going to eligible but we want audit trail to show we've passed
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ELIGIBLE_FOR_JOB_OFFER)
      } yield ()

    } else if (firstResidualInEvaluation.get.result == Red.toString) {
        for {
          _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, FSB_FAILED)
          newCurrentSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, fsbEvaluation.get)
          _ <- fsbRepo.updateCurrentSchemeStatus(appId, newCurrentSchemeStatus)
          _ <- maybeMarkAsFailedAll(appId, newCurrentSchemeStatus)
        } yield ()
    } else {
      throw new Exception(s"Unexpected result in FSB scheme fsbEvaluation (${firstResidualInEvaluation.get})")
    }
  }

  private def maybeMarkAsFailedAll(appId: String, newCurrentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {
    if (firstResidualPreference(newCurrentSchemeStatus).isEmpty) {
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ALL_FSBS_FAILED)
    } else {
      Future.successful(())
    }
  }

  def saveResults(schemeId: SchemeId, applicationResults: List[ApplicationResult]): Future[List[Unit]] = {
    Future.sequence(
      applicationResults.map(applicationResult => saveResult(schemeId, applicationResult))
    )
  }

  def saveResult(schemeId: SchemeId, applicationResult: ApplicationResult): Future[Unit] = {
    saveResult(applicationResult.applicationId, SchemeEvaluationResult(schemeId, applicationResult.result))
  }

  def saveResult(applicationId: String, schemeEvaluationResult: SchemeEvaluationResult): Future[Unit] = {
    for {
      _ <- fsbRepo.saveResult(applicationId, schemeEvaluationResult)
      _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId, FSB_RESULT_ENTERED)
    } yield ()
  }

  def findByApplicationIdsAndFsbType(applicationIds: List[String], mayBeFsbType: Option[String]): Future[List[FsbSchemeResult]] = {
    val maybeSchemeId = mayBeFsbType.flatMap { fsb =>
      Try(schemeRepo.getSchemeForFsb(fsb)).toOption
    }.map(_.id)
    findByApplicationIdsAndScheme(applicationIds, maybeSchemeId)
  }

  def findByApplicationIdsAndScheme(applicationIds: List[String], mayBeSchemeId: Option[SchemeId]): Future[List[FsbSchemeResult]] = {
    fsbRepo.findByApplicationIds(applicationIds, mayBeSchemeId)
  }

}