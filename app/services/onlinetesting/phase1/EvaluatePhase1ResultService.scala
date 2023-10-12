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

package services.onlinetesting.phase1

import com.google.inject.name.Named
import config.MicroserviceAppConfig
import factories.UUIDFactory
import model.exchange.passmarksettings.Phase1PassMarkSettingsPersistence
import model.persisted.{ApplicationReadyForEvaluation, PsiTest}
import model.{Phase, Schemes}
import play.api.Logging
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase1PassMarkSettingsMongoRepository
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.passmarksettings.PassMarkSettingsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EvaluatePhase1ResultService @Inject() (@Named("Phase1EvaluationRepository") val evaluationRepository: OnlineTestEvaluationRepository,
                                             val passMarkSettingsRepo: Phase1PassMarkSettingsMongoRepository,
                                             appConfig: MicroserviceAppConfig,
                                             val uuidFactory: UUIDFactory
                                            )(implicit ec: ExecutionContext)
  extends EvaluateOnlineTestResultService[Phase1PassMarkSettingsPersistence] with Phase1TestSelector with
  Phase1TestEvaluation with PassMarkSettingsService[Phase1PassMarkSettingsPersistence] with Logging with Schemes {

  val phase = Phase.PHASE1
  val gatewayConfig = appConfig.onlineTestsGatewayConfig

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: Phase1PassMarkSettingsPersistence): Future[Unit] = {
    if (application.isSdipFaststream && !passmark.schemes.exists(_.schemeId == Sdip)) {
      logger.warn(s"Evaluating PHASE1 Sdip Faststream candidate with no Sdip passmarks set, so skipping - appId=${application.applicationId}")
      Future.successful(())
    } else {
      logger.warn(s"Evaluating PHASE1 appId=${application.applicationId}")

      val activeTests = application.activePsiTests
      require(activeTests.nonEmpty && (activeTests.length == 2 || activeTests.length == 3), "Allowed active number of tests is 2 or 3")
      // TODO: change to list of tests?
      val test1Opt = findFirstTest1Test(activeTests)
      val test2Opt = findFirstTest2Test(activeTests)
      val test3Opt = findFirstTest3Test(activeTests)

      savePassMarkEvaluation(application, getSchemeResults(test1Opt, test2Opt, test3Opt), passmark, phase)
    }
  }

  //scalastyle:off cyclomatic.complexity
  private def getSchemeResults(test1Opt: Option[PsiTest], test2Opt: Option[PsiTest], test3Opt: Option[PsiTest])
                              (implicit application: ApplicationReadyForEvaluation, passmark: Phase1PassMarkSettingsPersistence) =
    (test1Opt, test2Opt, test3Opt) match {
      case (Some(test1), None, Some(test3)) if application.isGis && test1.testResult.isDefined && test3.testResult.isDefined =>
        evaluateForGis(getSchemesToEvaluate(application), test1.testResult.get, test3.testResult.get, passmark)
      case (Some(test1), Some(test2), Some(test3)) if application.nonGis &&
        test1.testResult.isDefined && test2.testResult.isDefined && test3.testResult.isDefined =>
        evaluateForNonGis(getSchemesToEvaluate(application),
          test1.testResult.get, test2.testResult.get, test3.testResult.get, passmark)
      case _ =>
        val testCount = List(test1Opt, test2Opt, test3Opt).count(test => test.isDefined && test.get.testResult.isDefined)
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

  private def getSchemesToEvaluate(implicit application: ApplicationReadyForEvaluation) =
    application.preferences.schemes
}
