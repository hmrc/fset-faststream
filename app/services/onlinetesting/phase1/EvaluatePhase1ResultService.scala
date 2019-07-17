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

package services.onlinetesting.phase1

import _root_.services.passmarksettings.PassMarkSettingsService
import config.MicroserviceAppConfig._
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.{ApplicationReadyForEvaluation, CubiksTest}
import model.{Phase, SchemeId}
import play.api.Logger
import repositories._
import scheduler.onlinetesting.EvaluateOnlineTestResultService

import scala.concurrent.Future

object EvaluatePhase1ResultService extends EvaluatePhase1ResultService {
  val evaluationRepository = repositories.faststreamPhase1EvaluationRepository
  val passMarkSettingsRepo = phase1PassMarkSettingsRepository
  val gatewayConfig = cubiksGatewayConfig
  val phase = Phase.PHASE1
}

trait EvaluatePhase1ResultService extends EvaluateOnlineTestResultService[Phase1PassMarkSettings] with Phase1TestSelector with
  Phase1TestEvaluation with PassMarkSettingsService[Phase1PassMarkSettings] {

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: Phase1PassMarkSettings): Future[Unit] = {
    if (application.isSdipFaststream && !passmark.schemes.exists(_.schemeId == SchemeId("Sdip"))) {
      Logger.info(s"Evaluating Phase1 Sdip Faststream candidate with no Sdip passmarks set, so skipping - appId=${application.applicationId}")
      Future.successful(())
    } else {
      Logger.info(s"**** Evaluating Phase1 appId=${application.applicationId}")

      val activeTests = application.activeCubiksTests
      require(activeTests.nonEmpty && activeTests.length <= 2, "Allowed active number of tests is 1 or 2")
      val sjqTestOpt = findFirstSjqTest(activeTests)
      val bqTestOpt = findFirstBqTest(activeTests)

      savePassMarkEvaluation(application, getSchemeResults(sjqTestOpt, bqTestOpt), passmark)
    }
  }

  private def getSchemeResults(sjqTestOpt: Option[CubiksTest], bqTestOpt: Option[CubiksTest])
                              (implicit application: ApplicationReadyForEvaluation,passmark: Phase1PassMarkSettings) =
    (sjqTestOpt, bqTestOpt) match {
      case (Some(sjqTest), None) if application.isGis && sjqTest.testResult.isDefined =>
        evaluateForGis(getSchemesToEvaluate(application), sjqTest.testResult.get, passmark)
      case (Some(sjqTest), Some(bqTest)) if application.nonGis && sjqTest.testResult.isDefined && bqTest.testResult.isDefined =>
        evaluateForNonGis(getSchemesToEvaluate(application), sjqTest.testResult.get, bqTest.testResult.get, passmark)
      case _ =>
        throw new IllegalStateException(s"Illegal number of active tests with results for this application: ${application.applicationId}")
  }

  private def getSchemesToEvaluate(implicit application: ApplicationReadyForEvaluation) =
    application.preferences.schemes
}
