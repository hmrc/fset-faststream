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

package scheduler.onlinetesting

import com.google.inject.name.Named
import common.FutureEx
import config.ScheduledJobConfig

import javax.inject.{Inject, Singleton}
import model.Phase
import model.Phase.Phase
import model.exchange.passmarksettings.{PassMarkSettings, PassMarkSettingsPersistence, Phase1PassMarkSettings, Phase1PassMarkSettingsPersistence, Phase2PassMarkSettings, Phase2PassMarkSettingsPersistence, Phase3PassMarkSettings, Phase3PassMarkSettingsPersistence}
import model.persisted.ApplicationReadyForEvaluation
import play.api.libs.json.Format
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class EvaluatePhase1ResultJob @Inject() (@Named("Phase1EvaluationService")
                                         val evaluateService: EvaluateOnlineTestResultService[Phase1PassMarkSettingsPersistence],
                                         val mongoComponent: MongoComponent,
                                         val config: EvaluatePhase1ResultJobConfig
                                        ) extends EvaluateOnlineTestResultJob[Phase1PassMarkSettingsPersistence] {
  val phase = Phase.PHASE1
}

@Singleton
class EvaluatePhase2ResultJob @Inject() (@Named("Phase2EvaluationService")
                                         val evaluateService: EvaluateOnlineTestResultService[Phase2PassMarkSettingsPersistence],
                                         val mongoComponent: MongoComponent,
                                         val config: EvaluatePhase2ResultJobConfig
                                        ) extends EvaluateOnlineTestResultJob[Phase2PassMarkSettingsPersistence] {
  val phase = Phase.PHASE2
}

@Singleton
class EvaluatePhase3ResultJob @Inject() (@Named("Phase3EvaluationService")
                                         val evaluateService: EvaluateOnlineTestResultService[Phase3PassMarkSettingsPersistence],
                                         val mongoComponent: MongoComponent,
                                         val config: EvaluatePhase3ResultJobConfig
                                        ) extends EvaluateOnlineTestResultJob[Phase3PassMarkSettingsPersistence] {
  val phase = Phase.PHASE3
  override val errorLog = (app: ApplicationReadyForEvaluation) =>
    s"${app.applicationId}, Launchpad test Id: ${app.activeLaunchpadTest.map(_.token)}"
}

abstract class EvaluateOnlineTestResultJob[T <: PassMarkSettingsPersistence](implicit jsonFormat: Format[T]) extends
  SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] with Logging {

  val evaluateService: EvaluateOnlineTestResultService[T]
  val phase: Phase
  lazy val batchSize = config.conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    evaluateService.nextCandidatesReadyForEvaluation(batchSize) flatMap {
      case Some((apps, passmarkSettings)) =>
        evaluateInBatch(apps, passmarkSettings)
      case None =>
        logger.warn(s"Evaluate $phase job - passmark settings or an application to evaluate not found")
        Future.successful(())
    }
  }

  val errorLog = (app: ApplicationReadyForEvaluation) =>
    s"${app.applicationId}, psi order ids: ${app.activePsiTests.map(_.orderId).mkString(",")}"

  private def evaluateInBatch(apps: Seq[ApplicationReadyForEvaluation],
                              passmarkSettings: T)(implicit ec: ExecutionContext): Future[Unit] = {
    // Warn level so we see it in the prod logs
    val applicationIds = apps.map ( _.applicationId ).mkString(",")
    logger.warn(s"Evaluate $phase job found ${apps.size} application(s), applicationIds=$applicationIds, " +
      s"passmarkVersion=${passmarkSettings.version}")
    val evaluationResultsFut = FutureEx.traverseToTry(apps) { app =>
      Try(evaluateService.evaluate(app, passmarkSettings)) match {
        case Success(fut) => fut
        case Failure(e) => Future.failed(e)
      }
    }

    evaluationResultsFut flatMap { evaluationResults =>
      val errors = evaluationResults flatMap {
        case Failure(e) => Some(e)
        case _ => None
      }

      if (errors.nonEmpty) {
        val errorMsg = apps.map(errorLog).mkString("\n")

        logger.error(s"There were ${errors.size} errors in batch $phase evaluation:\n$errorMsg")
        Future.failed(errors.head)
      } else {
        logger.warn(s"Evaluate $phase job successfully evaluated ${apps.size} application(s), applicationIds=$applicationIds, " +
          s"passmarkVersion=${passmarkSettings.version}")
        Future.successful(())
      }
    }
  }
}

@Singleton
class EvaluatePhase1ResultJobConfig @Inject()(config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.evaluate-phase1-result-job",
  jobName = "EvaluatePhase1ResultJob"
)

@Singleton
class EvaluatePhase2ResultJobConfig @Inject()(config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.evaluate-phase2-result-job",
  jobName = "EvaluatePhase2ResultJob"
)

@Singleton
class EvaluatePhase3ResultJobConfig @Inject()(config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.evaluate-phase3-result-job",
  jobName = "EvaluatePhase3ResultJob"
)
