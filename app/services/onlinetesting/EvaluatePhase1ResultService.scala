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

import _root_.services.passmarksettings.PassMarkSettingsService
import services.onlinetesting.phase1.{ ApplicationStatusCalculator, Phase1TestEvaluation, Phase1TestSelector }
import config.MicroserviceAppConfig._
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.{ ApplicationPhase1ReadyForEvaluation, PassmarkEvaluation }
import play.api.Logger
import repositories._
import repositories.onlinetesting.Phase1EvaluationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EvaluatePhase1ResultService extends EvaluatePhase1ResultService {
  val phase1EvaluationRepository: Phase1EvaluationRepository = repositories.faststreamPhase1EvaluationRepository
  val gatewayConfig = cubiksGatewayConfig
  val phase1PMSRepository = phase1PassMarkSettingsRepository
}

trait EvaluatePhase1ResultService extends Phase1TestSelector with Phase1TestEvaluation with PassMarkSettingsService
  with ApplicationStatusCalculator {
  val phase1EvaluationRepository: Phase1EvaluationRepository

  def nextCandidatesReadyForEvaluation(batchSize: Int): Future[Option[(List[ApplicationPhase1ReadyForEvaluation], Phase1PassMarkSettings)]] = {
    getLatestPhase1PassMarkSettings flatMap {
      case Some(passmark) =>
        phase1EvaluationRepository.nextApplicationsReadyForEvaluation(passmark.version, batchSize) map { candidates =>
          Some(candidates -> passmark)
        }
      case _ =>
        Future.successful(None)
    }
  }

  def evaluate(application: ApplicationPhase1ReadyForEvaluation, passmark: Phase1PassMarkSettings): Future[Unit] = {
    Logger.debug(s"Evaluating phase1 appId=${application.applicationId}")

    val tests = application.phase1.activeTests
    require(tests.nonEmpty && tests.length <= 2, "Allowed active number of tests is 1 or 2")
    val sjqTestOpt = findFirstSjqTest(tests)
    val bqTestOpt = findFirstBqTest(tests)

    val schemeResults = (sjqTestOpt, bqTestOpt) match {
      case (Some(sjqTest), None) if application.isGis && sjqTest.testResult.isDefined =>
        evaluateForGis(application.preferences.schemes, sjqTest.testResult.get, passmark)
      case (Some(sjqTest), Some(bqTest)) if application.nonGis && sjqTest.testResult.isDefined && bqTest.testResult.isDefined =>
        evaluateForNonGis(application.preferences.schemes, sjqTest.testResult.get, bqTest.testResult.get, passmark)
      case _ =>
        throw new IllegalStateException(s"Illegal number of active tests with results for this application: ${application.applicationId}")
    }

    phase1EvaluationRepository.savePassmarkEvaluation(
      application.applicationId,
      PassmarkEvaluation(passmark.version, schemeResults),
      determineApplicationStatus(application.applicationStatus, schemeResults)
    )
  }
}
