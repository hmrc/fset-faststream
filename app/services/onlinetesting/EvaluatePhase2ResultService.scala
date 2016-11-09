/*
 * Copyright 2016 HM Revenue & Customs
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

package services.onlinetesting

import _root_.services.onlinetesting.phase2.Phase2TestEvaluation
import _root_.services.passmarksettings.PassMarkSettingsService
import config.MicroserviceAppConfig._
import model.Phase
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation }
import play.api.Logger
import repositories._
import repositories.onlinetesting.OnlineTestEvaluationRepository
import scheduler.onlinetesting.EvaluateOnlineTestResultService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EvaluatePhase2ResultService extends EvaluatePhase2ResultService {
  val phase2EvaluationRepository: OnlineTestEvaluationRepository[ApplicationReadyForEvaluation]
    = repositories.faststreamPhase2EvaluationRepository
  val gatewayConfig = cubiksGatewayConfig
  val phase1PMSRepository = phase1PassMarkSettingsRepository
}

trait EvaluatePhase2ResultService extends EvaluateOnlineTestResultService with Phase2TestEvaluation with PassMarkSettingsService
  with ApplicationStatusCalculator {
  val phase2EvaluationRepository: OnlineTestEvaluationRepository[ApplicationReadyForEvaluation]

  def nextCandidatesReadyForEvaluation(batchSize: Int): Future[Option[(List[ApplicationReadyForEvaluation], Phase1PassMarkSettings)]] = {
    getLatestPhase1PassMarkSettings flatMap {
      case Some(passmark) =>
        phase2EvaluationRepository.nextApplicationsReadyForEvaluation(passmark.version, batchSize) map { candidates =>
          Some(candidates -> passmark)
        }
      case _ => Future.successful(None)
    }
  }

  def evaluate(application: ApplicationReadyForEvaluation, passmark: Phase1PassMarkSettings): Future[Unit] = {
    Logger.debug(s"Evaluating phase2 appId=${application.applicationId}")

    val activeTests = application.activeTests
    require(activeTests.nonEmpty && activeTests.length == 1, "Allowed active number of tests is 1")
    require(application.prevPhaseEvaluation.isDefined, "Phase1 results required to evaluate phase2")

    val prevPhaseEvaluation = application.prevPhaseEvaluation.get

    val schemeResults = activeTests.head match {
      case etrayTest if etrayTest.testResult.isDefined =>
        evaluate(application.preferences.schemes, etrayTest.testResult.get, prevPhaseEvaluation.result, passmark)
      case _ => throw new IllegalStateException(s"Illegal number of phase2 active tests with results " +
        s"for this application: ${application.applicationId}")
    }

    schemeResults.nonEmpty match {
      case true => phase2EvaluationRepository.savePassmarkEvaluation(
        application.applicationId,
        PassmarkEvaluation(passmark.version, Some(prevPhaseEvaluation.passmarkVersion), schemeResults),
        determineApplicationStatus(application.applicationStatus, schemeResults, Phase.PHASE2)
      )
      case false => Future.successful(())
    }
  }
}



