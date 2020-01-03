/*
 * Copyright 2020 HM Revenue & Customs
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
import model.persisted.{ ApplicationReadyForEvaluation2, PsiTest }
import model.{ Phase, SchemeId }
import play.api.Logger
import repositories._
import scheduler.onlinetesting.EvaluateOnlineTestResultService2

import scala.concurrent.Future

object EvaluatePhase1ResultService2 extends EvaluatePhase1ResultService2 {
  val evaluationRepository = repositories.faststreamPhase1EvaluationRepository
  val passMarkSettingsRepo = phase1PassMarkSettingsRepository
  val gatewayConfig = testIntegrationGatewayConfig
  val phase = Phase.PHASE1
}

trait EvaluatePhase1ResultService2 extends EvaluateOnlineTestResultService2[Phase1PassMarkSettings] with Phase1TestSelector2 with
  Phase1TestEvaluation2 with PassMarkSettingsService[Phase1PassMarkSettings] {

  def evaluate(implicit application: ApplicationReadyForEvaluation2, passmark: Phase1PassMarkSettings): Future[Unit] = {
    if (application.isSdipFaststream && !passmark.schemes.exists(_.schemeId == SchemeId("Sdip"))) {
      Logger.warn(s"Evaluating Phase1 Sdip Faststream candidate with no Sdip passmarks set, so skipping - appId=${application.applicationId}")
      Future.successful(())
    } else {
      Logger.warn(s"Evaluating Phase1 appId=${application.applicationId}")

      val activeTests = application.activePsiTests
      require(activeTests.nonEmpty && (activeTests.length == 2 || activeTests.length == 4), "Allowed active number of tests is 2 or 4")
      // TODO: change to list of tests?
      val test1Opt = findFirstTest1Test(activeTests)
      val test2Opt = findFirstTest2Test(activeTests)
      val test3Opt = findFirstTest3Test(activeTests)
      val test4Opt = findFirstTest4Test(activeTests)

      savePassMarkEvaluation(application, getSchemeResults(test1Opt, test2Opt, test3Opt, test4Opt), passmark)
    }
  }

  //scalastyle:off cyclomatic.complexity
  private def getSchemeResults(test1Opt: Option[PsiTest], test2Opt: Option[PsiTest], test3Opt: Option[PsiTest], test4Opt: Option[PsiTest])
                              (implicit application: ApplicationReadyForEvaluation2, passmark: Phase1PassMarkSettings) =
    (test1Opt, test2Opt, test3Opt, test4Opt) match {
      case (Some(test1), None, None, Some(test4)) if application.isGis && test1.testResult.isDefined && test4.testResult.isDefined =>
        evaluateForGis(getSchemesToEvaluate(application), test1.testResult.get, test4.testResult.get, passmark)
      case (Some(test1), Some(test2), Some(test3), Some(test4)) if application.nonGis &&
        test1.testResult.isDefined && test2.testResult.isDefined && test3.testResult.isDefined && test4.testResult.isDefined =>
        evaluateForNonGis(getSchemesToEvaluate(application),
          test1.testResult.get, test2.testResult.get, test3.testResult.get, test4.testResult.get, passmark)
      case _ =>
        val testCount = List(test1Opt, test2Opt, test3Opt, test4Opt).count(test => test.isDefined && test.get.testResult.isDefined)
        val gis = if (application.isGis) {
          s"This application is GIS so expecting ${gatewayConfig.phase1Tests.gis.size} tests with results in phase1 but found $testCount"
        } else {
          s"This application is not GIS so expecting ${gatewayConfig.phase1Tests.standard.size} tests " +
            s"with results in phase1 but found $testCount"
        }
        val msg = s"Illegal number of active tests with results for this application: ${application.applicationId}. $gis"
        throw new IllegalStateException(msg)
  }
  //scalastyle:on

  private def getSchemesToEvaluate(implicit application: ApplicationReadyForEvaluation2) =
    application.preferences.schemes
}
