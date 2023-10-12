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

package services.onlinetesting.phase2

import com.google.inject.name.Named
import config.MicroserviceAppConfig
import factories.UUIDFactory
import model.Phase
import model.exchange.passmarksettings.{Phase2PassMarkSettings, Phase2PassMarkSettingsPersistence}
import model.persisted.{ApplicationReadyForEvaluation, PsiTestResult}
import play.api.Logging
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase2PassMarkSettingsMongoRepository
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.onlinetesting.CurrentSchemeStatusHelper
import services.passmarksettings.PassMarkSettingsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EvaluatePhase2ResultService @Inject() (@Named("Phase2EvaluationRepository") val evaluationRepository: OnlineTestEvaluationRepository,
                                             val passMarkSettingsRepo: Phase2PassMarkSettingsMongoRepository,
                                             val generalAppRepository: GeneralApplicationRepository,
                                             appConfig: MicroserviceAppConfig,
                                             val uuidFactory: UUIDFactory
                                            )(implicit ec: ExecutionContext)
  extends EvaluateOnlineTestResultService[Phase2PassMarkSettingsPersistence] with Phase2TestSelector
  with Phase2TestEvaluation with PassMarkSettingsService[Phase2PassMarkSettingsPersistence] with CurrentSchemeStatusHelper with Logging {

  val phase = Phase.PHASE2
  val gatewayConfig = appConfig.onlineTestsGatewayConfig

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: Phase2PassMarkSettingsPersistence): Future[Unit] = {
    logger.warn(s"Evaluating PHASE2 appId=${application.applicationId}")

    val activeTests = application.activePsiTests
    require(activeTests.nonEmpty && activeTests.length == 2,
      s"Allowed active number of tests for PHASE2 is 2 - found ${activeTests.size} for AppId=${application.applicationId}")
    require(application.prevPhaseEvaluation.isDefined, "Phase1 results are required before we can evaluate phase2")

    val test1ResultOpt = findFirstTest1Test(activeTests).flatMap(_.testResult)
    val test2ResultOpt = findFirstTest2Test(activeTests).flatMap(_.testResult)
    val schemeResults = getSchemeResults(test1ResultOpt, test2ResultOpt)

    getSdipResults(application).flatMap { sdip =>
      if (application.isSdipFaststream) {
        logger.debug(s"PHASE2 appId=${application.applicationId} Sdip faststream application will persist the following Sdip results " +
          s"read from current scheme status: $sdip")
      }
      savePassMarkEvaluation(application, schemeResults ++ sdip , passmark, phase)
    }
  }

  private def getSchemeResults(test1ResultOpt: Option[PsiTestResult], test2ResultOpt: Option[PsiTestResult])
                              (implicit application: ApplicationReadyForEvaluation, passmark: Phase2PassMarkSettingsPersistence) = {
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
