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

import config.CubiksGatewayConfig
import config.MicroserviceAppConfig._
import model.EvaluationResults._
import model.SchemeType.SchemeType
import model.persisted.{ ApplicationToPhase1Evaluation, TestResult }
import repositories.onlinetesting.Phase1EvaluationRepository

import scala.concurrent.Future

object EvaluatePhase1ResultService extends EvaluatePhase1ResultService {
  val phase1EvaluationRepository: Phase1EvaluationRepository = repositories.faststreamPhase1EvaluationRepository
  val gatewayConfig = cubiksGatewayConfig
}

trait EvaluatePhase1ResultService {
  val phase1EvaluationRepository: Phase1EvaluationRepository
  val gatewayConfig: CubiksGatewayConfig

  def nextCandidateReadyForEvaluation: Future[Option[ApplicationToPhase1Evaluation]] = {
    phase1EvaluationRepository.nextApplicationReadyForPhase1ResultEvaluation
  }

  def evaluate(application: ApplicationToPhase1Evaluation): Future[Unit] = {
    val tests = application.phase1.activeTests
    require(tests.nonEmpty && tests.length <= 2, "Allowed active number of tests is 1 or 2")

    val sjqTestOpt = tests find (_.scheduleId == sjq)
    val bqTestOpt = tests find (_.scheduleId == bq)
    val passmark: Any = "TODO"

    val schemeResults = (sjqTestOpt, bqTestOpt) match {
      case (Some(sjqTest), None) if application.isGis =>
        application.preferences.schemes map { scheme =>
          scheme -> evaluateResultsForExercise(sjqTest.testResult.get, scheme, passmark)
        }
      case (Some(sjqTest), Some(bqTest)) =>
        application.preferences.schemes map { scheme =>
          val sjqResult = evaluateResultsForExercise(sjqTest.testResult.get, scheme, passmark)
          val bqResult = evaluateResultsForExercise(bqTest.testResult.get, scheme, passmark)
          // TODO do the math here
          scheme -> Amber
        }
    }

    // update the db [schemeResults]
    Future.successful()
  }

  private def sjq = gatewayConfig.phase1Tests.scheduleIds("sjq")

  private def bq = gatewayConfig.phase1Tests.scheduleIds("bq")

  private def evaluateResultsForExercise(testResult: TestResult, scheme: SchemeType, passmarkSettings: Any): Result = {
    val tScore = testResult.tScore.get
    // TODO Integrate with Passmark
    val failmark = 20.0
    val passmark = 80.0
    determineResult(tScore, failmark, passmark)
  }

  private def determineResult(tScore: Double, failmark: Double, passmark: Double): Result = {
    val isAmberGapPresent = failmark < passmark

    isAmberGapPresent match {
      case true =>
        if (tScore <= failmark) {
          Red
        } else if (tScore >= passmark) {
          Green
        } else {
          Amber
        }
      case false =>
        if (tScore >= passmark) {
          Green
        } else {
          Red
        }
    }
  }

}
