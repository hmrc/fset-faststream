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

package scheduler.onlinetesting

import com.google.inject.name.Named
import common.FutureEx
import config.ScheduledJobConfig
import javax.inject.{ Inject, Singleton }
import model.Phase
import model.Phase.Phase
import model.exchange.passmarksettings.{ PassMarkSettings, Phase1PassMarkSettings, Phase2PassMarkSettings, Phase3PassMarkSettings }
import model.persisted.ApplicationReadyForEvaluation2
import play.api.libs.json.Format
import play.api.{ Configuration, Logger }
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
class EvaluatePhase1ResultJob @Inject() (@Named("Phase1EvaluationService")
                                         val evaluateService2: EvaluateOnlineTestResultService2[Phase1PassMarkSettings],
                                         val mongoComponent: ReactiveMongoComponent,
                                         val config: EvaluatePhase1ResultJobConfig
                                        ) extends EvaluateOnlineTestResultJob[Phase1PassMarkSettings] {
  //  val evaluateService = EvaluatePhase1ResultService
  //  val evaluateService2 = EvaluatePhase1ResultService2X
  val phase = Phase.PHASE1
  //  val config = EvaluatePhase1ResultJobConfig
}

@Singleton
class EvaluatePhase2ResultJob @Inject() (@Named("Phase2EvaluationService")
                                         val evaluateService2: EvaluateOnlineTestResultService2[Phase2PassMarkSettings],
                                         val mongoComponent: ReactiveMongoComponent,
                                         val config: EvaluatePhase2ResultJobConfig
                                        ) extends EvaluateOnlineTestResultJob[Phase2PassMarkSettings] {
  //  val evaluateService = EvaluatePhase2ResultService
  //  val evaluateService2 = EvaluatePhase2ResultService2
  val phase = Phase.PHASE2
  //  val config = EvaluatePhase2ResultJobConfig
}

@Singleton
class EvaluatePhase3ResultJob @Inject() (@Named("Phase3EvaluationService")
                                         val evaluateService2: EvaluateOnlineTestResultService2[Phase3PassMarkSettings],
                                         val mongoComponent: ReactiveMongoComponent,
                                         val config: EvaluatePhase3ResultJobConfig
                                        ) extends EvaluateOnlineTestResultJob[Phase3PassMarkSettings] {
  //  val evaluateService = EvaluatePhase3ResultService
  //  val evaluateService2 = EvaluatePhase3ResultService2
  val phase = Phase.PHASE3
  override val errorLog = (app: ApplicationReadyForEvaluation2) =>
    s"${app.applicationId}, Launchpad test Id: ${app.activeLaunchpadTest.map(_.token)}"
  //  val config = EvaluatePhase3ResultJobConfig
}

abstract class EvaluateOnlineTestResultJob[T <: PassMarkSettings](implicit jsonFormat: Format[T]) extends
  SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {

  //  val evaluateService: EvaluateOnlineTestResultService[T]
  val evaluateService2: EvaluateOnlineTestResultService2[T]
  val phase: Phase
  lazy val batchSize = config.conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    evaluateService2.nextCandidatesReadyForEvaluation(batchSize) flatMap {
      case Some((apps, passmarkSettings)) =>
        evaluateInBatch(apps, passmarkSettings)
      case None =>
        Logger.warn(s"Passmark settings or an application to evaluate $phase result not found")
        Future.successful(())
    }
  }

  val errorLog = (app: ApplicationReadyForEvaluation2) =>
    s"${app.applicationId}, psi order ids: ${app.activePsiTests.map(_.orderId).mkString(",")}"

  private def evaluateInBatch(apps: List[ApplicationReadyForEvaluation2],
                              passmarkSettings: T)(implicit ec: ExecutionContext): Future[Unit] = {
    // Warn level so we see it in the prod logs
    val applicationIds = apps.map ( _.applicationId ).mkString(",")
    Logger.warn(s"Evaluate $phase job found ${apps.size} application(s), applicationIds=$applicationIds, " +
      s"passmarkVersion=${passmarkSettings.version}")
    val evaluationResultsFut = FutureEx.traverseToTry(apps) { app =>
      Try(evaluateService2.evaluate(app, passmarkSettings)) match {
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

        Logger.error(s"There were ${errors.size} errors in batch $phase evaluation:\n$errorMsg")
        Future.failed(errors.head)
      } else {
        Logger.warn(s"Evaluate $phase job successfully evaluated ${apps.size} application(s), applicationIds=$applicationIds, " +
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
  name = "EvaluatePhase1ResultJob"
)

@Singleton
class EvaluatePhase2ResultJobConfig @Inject()(config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.evaluate-phase2-result-job",
  name = "EvaluatePhase2ResultJob"
)

@Singleton
class EvaluatePhase3ResultJobConfig @Inject()(config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.evaluate-phase3-result-job",
  name = "EvaluatePhase3ResultJob"
)
