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

package services.onlinetesting.phase3

import _root_.services.passmarksettings.PassMarkSettingsService
import com.google.inject.name.Named
import config.MicroserviceAppConfig
import factories.UUIDFactory

import javax.inject.{Inject, Singleton}
import model.Phase
import model.exchange.passmarksettings.Phase3PassMarkSettings
import model.persisted.ApplicationReadyForEvaluation
import play.api.Logging
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase3PassMarkSettingsMongoRepository
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.onlinetesting.{ApplicationStatusCalculator, CurrentSchemeStatusHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EvaluatePhase3ResultService @Inject() (@Named("Phase3EvaluationRepository") val evaluationRepository: OnlineTestEvaluationRepository,
                                             val passMarkSettingsRepo: Phase3PassMarkSettingsMongoRepository,
                                             val generalAppRepository: GeneralApplicationRepository,
                                             appConfig: MicroserviceAppConfig,
                                             val uuidFactory: UUIDFactory
                                            )(implicit ec: ExecutionContext)
  extends EvaluateOnlineTestResultService[Phase3PassMarkSettings] with Phase3TestEvaluation
  with PassMarkSettingsService[Phase3PassMarkSettings] with ApplicationStatusCalculator with CurrentSchemeStatusHelper with Logging {

  val phase = Phase.PHASE3
  val launchpadGWConfig = appConfig.launchpadGatewayConfig

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: Phase3PassMarkSettings): Future[Unit] = {
    logger.warn(s"Evaluating Phase3 appId=${application.applicationId}")

    val optLaunchpadTest = application.activeLaunchpadTest
    require(optLaunchpadTest.isDefined, "Active launchpad test not found")
    require(application.prevPhaseEvaluation.isDefined, "Phase2 results required to evaluate Phase3")

    val optLatestReviewed = optLaunchpadTest.flatMap(_.callbacks.getLatestReviewed)

    val allQuestionsReviewed = optLatestReviewed.exists(_.allQuestionsReviewed)

    if (launchpadGWConfig.phase3Tests.verifyAllScoresArePresent && !allQuestionsReviewed) {
      val msg = s"Some of the launchpad questions are not reviewed for application Id = ${application.applicationId} so terminating evaluation"
      logger.info(msg)
      Future.successful(())
    } else {
      val schemeResults = (optLatestReviewed, application.prevPhaseEvaluation) match {
        case (Some(launchpadReview), Some(prevPhaseEvaluation)) =>
          evaluate(application.preferences.schemes, launchpadReview, prevPhaseEvaluation.result, passmark)

        case _ => throw new IllegalStateException(s"Illegal number of phase3 active tests with results " +
          s"for this application: ${application.applicationId}")
      }

      getSdipResults(application).flatMap { sdip =>
        if (application.isSdipFaststream) {
          logger.debug(s"Phase3 appId=${application.applicationId} Sdip faststream application will persist the following Sdip results " +
            s"read from current scheme status: $sdip")
        }
        savePassMarkEvaluation(application, schemeResults ++ sdip, passmark)
      }
    }
  }
}
