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

import services.onlinetesting.phase1.{ Phase1TestEvaluation, Phase1TestSelector }
import config.MicroserviceAppConfig._
import model.persisted.{ ApplicationPhase1Evaluation, PassmarkEvaluation }
import repositories.onlinetesting.Phase1EvaluationRepository
import _root_.services.passmarksettings.PassMarkSettingsService
import model.ApplicationStatus
import model.ApplicationStatus.ApplicationStatus
import model.exchange.passmarksettings.Phase1PassMarkSettings
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EvaluatePhase1ResultService extends EvaluatePhase1ResultService {
  val phase1EvaluationRepository: Phase1EvaluationRepository = repositories.faststreamPhase1EvaluationRepository
  val gatewayConfig = cubiksGatewayConfig
  val phase1PMSRepository = phase1PassMarkSettingsRepository
}

//scalastyle:off
trait EvaluatePhase1ResultService extends Phase1TestSelector with Phase1TestEvaluation with PassMarkSettingsService {
  val phase1EvaluationRepository: Phase1EvaluationRepository

  def nextCandidateReadyForEvaluation: Future[Option[(ApplicationPhase1Evaluation, Phase1PassMarkSettings)]] = {
    getLatestPhase1PassMarkSettings flatMap {
      case Some(passmark) =>
        phase1EvaluationRepository.nextApplicationReadyForPhase1ResultEvaluation("version1").map { candidateOpt =>
          candidateOpt.map(e => (e, passmark))
        }
      case _ =>
        Future.successful(None)
    }
  }

  def evaluate(application: ApplicationPhase1Evaluation, passmark: Phase1PassMarkSettings): Future[Unit] = {
    val tests = application.phase1.activeTests
    require(tests.nonEmpty && tests.length <= 2, "Allowed active number of tests is 1 or 2")
    val sjqTestOpt = findFirstSjqTest(tests)
    val bqTestOpt = findFirstBqTest(tests)

    val schemeResults = (sjqTestOpt, bqTestOpt) match {
      case (Some(sjqTest), None) if application.isGis && sjqTest.testResult.isDefined =>
        evaluateForGis(application.preferences.schemes, sjqTest.testResult.get, passmark)
      case (Some(sjqTest), Some(bqTest)) if sjqTest.testResult.isDefined && bqTest.testResult.isDefined =>
        evaluateForNonGis(application.preferences.schemes, sjqTest.testResult.get, bqTest.testResult.get, passmark)
      case _ =>
        throw new IllegalStateException(s"Illegal number of active tests with results for this application: ${application.applicationId}")
    }

    phase1EvaluationRepository.savePassmarkEvaluation(
      application.applicationId,
      PassmarkEvaluation(passmark.version, schemeResults),
      determineApplicationStatus(application)
    )
  }

  private def determineApplicationStatus(application: ApplicationPhase1Evaluation): Option[ApplicationStatus] = {
    if (application.applicationStatus == ApplicationStatus.PHASE1_TESTS) {
      // TODO: do the logic
      Some(ApplicationStatus.PHASE1_TESTS_FAILED)
    } else {
      None
    }
  }
}
