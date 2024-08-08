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

package scheduler.onlinetesting

import factories.UUIDFactory
import model.Phase
import model.Phase.Phase
import model.exchange.passmarksettings.PassMarkSettingsPersistence
import model.persisted.{ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult}
import play.api.Logging
import play.api.libs.json.Format
import repositories.CurrentSchemeStatusHelper
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.OnlineTestEvaluationRepository
import services.onlinetesting.ApplicationStatusCalculator
import services.passmarksettings.PassMarkSettingsService

import scala.concurrent.{ExecutionContext, Future}

trait EvaluateOnlineTestResultService[T <: PassMarkSettingsPersistence] extends ApplicationStatusCalculator
  with CurrentSchemeStatusHelper with Logging {
  this: PassMarkSettingsService[T] =>

  val evaluationRepository: OnlineTestEvaluationRepository

  val applicationRepo: GeneralApplicationRepository

  val phase: Phase.Phase

  val uuidFactory: UUIDFactory

  def nextCandidatesReadyForEvaluation(batchSize: Int)(implicit jsonFormat: Format[T], ec: ExecutionContext):
    Future[Option[(Seq[ApplicationReadyForEvaluation], T)]] = {
    logger.warn(s"Evaluate $phase job - looking for candidates. Batch size=$batchSize")

    getLatestPassMarkSettings flatMap {
      case Some(passmark) =>
        evaluationRepository.nextApplicationsReadyForEvaluation(passmark.version, batchSize) map { candidates =>
          val appIds = if (candidates.nonEmpty) {
            candidates.map(_.applicationId).mkString(",")
          } else {
            "none found"
          }
          val msg = s"Evaluate $phase job - There are pass marks. Processing the following candidates: $appIds"
          logger.warn(msg)
          Some(candidates -> passmark)
        }
      case _ =>
        logger.warn(s"Evaluate $phase job. No pass marks found")
        Future.successful(None)
    }
  }

  private def calculateCurrentSchemeStatus(applicationId: String,
                                           evaluationResults: Seq[SchemeEvaluationResult])(
    implicit ec: ExecutionContext): Future[Seq[SchemeEvaluationResult]] = {
    for {
      currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(applicationId)
    } yield {
      val newSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, evaluationResults)
      logger.warn(s"After evaluation newSchemeStatus = $newSchemeStatus for applicationId: $applicationId")
      newSchemeStatus
    }
  }

  def savePassMarkEvaluation(application: ApplicationReadyForEvaluation,
                             schemeResults: List[SchemeEvaluationResult],
                             passMarkSettings: T, phase: Phase)(implicit ec: ExecutionContext): Future[Unit] = {
    if (schemeResults.nonEmpty) {
      for {
        newCss <- calculateCurrentSchemeStatus(application.applicationId, schemeResults)
        _ <- evaluationRepository.savePassmarkEvaluation(
          application.applicationId,
          PassmarkEvaluation(passMarkSettings.version, application.prevPhaseEvaluation.map(_.passmarkVersion),
            schemeResults, uuidFactory.generateUUID().toString, application.prevPhaseEvaluation.map(_.resultVersion)),
          determineApplicationStatus(application.applicationRoute, application.applicationStatus, schemeResults, phase),
          newCss
        )
      } yield ()
    } else {
      logger.warn(s"$phase - appId=${application.applicationId} has no schemeResults so will not evaluate. " +
        s"Have all pass marks been set including Edip/Sdip?")
      Future.successful(())
    }
  }

  def getPassmarkEvaluation(applicationId: String)(implicit ec: ExecutionContext): Future[PassmarkEvaluation] = {
    evaluationRepository.getPassMarkEvaluation(applicationId)
  }

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: T): Future[Unit]
}
