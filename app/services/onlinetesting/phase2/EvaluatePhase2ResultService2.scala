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

package services.onlinetesting.phase2

import _root_.services.passmarksettings.PassMarkSettingsService
import config.MicroserviceAppConfig.testIntegrationGatewayConfig
import model.Phase
import model.exchange.passmarksettings.Phase2PassMarkSettings
import model.persisted.{ ApplicationReadyForEvaluation2, PsiTestResult }
import play.api.Logger
import repositories._
import scheduler.onlinetesting.EvaluateOnlineTestResultService2
import services.onlinetesting.CurrentSchemeStatusHelper2

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EvaluatePhase2ResultService2 extends EvaluatePhase2ResultService2 {
  val evaluationRepository = repositories.faststreamPhase2EvaluationRepository
  val passMarkSettingsRepo = phase2PassMarkSettingsRepository
  val generalAppRepository = repositories.applicationRepository
  val gatewayConfig = testIntegrationGatewayConfig //TODO: use p2 config instead
  val phase = Phase.PHASE2
}

trait EvaluatePhase2ResultService2 extends EvaluateOnlineTestResultService2[Phase2PassMarkSettings] with Phase2TestSelector2
  with Phase2TestEvaluation2 with PassMarkSettingsService[Phase2PassMarkSettings] with CurrentSchemeStatusHelper2 {

  def evaluate(implicit application: ApplicationReadyForEvaluation2, passmark: Phase2PassMarkSettings): Future[Unit] = {
    Logger.warn(s"Evaluating phase2 appId=${application.applicationId}")

    val activeTests = application.activePsiTests
    require(activeTests.nonEmpty && activeTests.length == 2, s"Allowed active number of tests for phase2 is 2 - found ${activeTests.size}")
    require(application.prevPhaseEvaluation.isDefined, "Phase1 results are required before we can evaluate phase2")

    val test1ResultOpt = findFirstTest1Test(activeTests).flatMap(_.testResult)
    val test2ResultOpt = findFirstTest2Test(activeTests).flatMap(_.testResult)
    val schemeResults = getSchemeResults(test1ResultOpt, test2ResultOpt)

    getSdipResults(application).flatMap { sdip =>
      if (application.isSdipFaststream) {
        Logger.debug(s"Phase2 appId=${application.applicationId} Sdip faststream application will persist the following Sdip results " +
          s"read from current scheme status: $sdip")
      }
      savePassMarkEvaluation(application, schemeResults ++ sdip , passmark)
    }
  }

  private def getSchemeResults(test1ResultOpt: Option[PsiTestResult], test2ResultOpt: Option[PsiTestResult])
                              (implicit application: ApplicationReadyForEvaluation2, passmark: Phase2PassMarkSettings) = {
    val schemeResults = (test1ResultOpt, test2ResultOpt, application.prevPhaseEvaluation) match {
      case (Some(test1Result), Some(test2Result), Some(prevPhaseEvaluation)) =>
        evaluate(application.preferences.schemes, test1Result, test2Result, prevPhaseEvaluation.result, passmark)
      case _ => throw new IllegalStateException(s"Illegal number of phase2 active tests with results " +
        s"for this application: ${application.applicationId} - expecting a result for each of the 2 tests and the " +
        s"previous phase evaluation. Test1Result defined=${test1ResultOpt.isDefined}, test2Result defined=${test2ResultOpt.isDefined}, " +
        s"previous phase evaluation defined=${application.prevPhaseEvaluation.isDefined}")
    }
    schemeResults
  }
}
