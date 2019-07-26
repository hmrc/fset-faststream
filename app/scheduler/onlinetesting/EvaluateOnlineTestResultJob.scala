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

package scheduler.onlinetesting

import common.FutureEx
import config.ScheduledJobConfig
import model.Phase
import model.Phase.Phase
import model.exchange.passmarksettings.{ PassMarkSettings, Phase1PassMarkSettings, Phase2PassMarkSettings, Phase3PassMarkSettings }
import model.persisted.ApplicationReadyForEvaluation2
import play.api.Logger
import play.api.libs.json.Format
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import scheduler.onlinetesting.EvaluatePhase3ResultJobConfig.conf
import services.onlinetesting.phase1.{ EvaluatePhase1ResultService, EvaluatePhase1ResultService2 }
import services.onlinetesting.phase2.{ EvaluatePhase2ResultService, EvaluatePhase2ResultService2 }
import services.onlinetesting.phase3.{ EvaluatePhase3ResultService, EvaluatePhase3ResultService2 }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object EvaluatePhase1ResultJob extends EvaluateOnlineTestResultJob[Phase1PassMarkSettings] {
  val evaluateService = EvaluatePhase1ResultService
  val evaluateService2 = EvaluatePhase1ResultService2
  val phase = Phase.PHASE1
  val config = EvaluatePhase1ResultJobConfig
}

object EvaluatePhase2ResultJob extends EvaluateOnlineTestResultJob[Phase2PassMarkSettings] {
  val evaluateService = EvaluatePhase2ResultService
  val evaluateService2 = EvaluatePhase2ResultService2
  val phase = Phase.PHASE2
  val config = EvaluatePhase2ResultJobConfig
}

object EvaluatePhase3ResultJob extends EvaluateOnlineTestResultJob[Phase3PassMarkSettings] {
  val evaluateService = EvaluatePhase3ResultService
  val evaluateService2 = EvaluatePhase3ResultService2
  val phase = Phase.PHASE3
  override val errorLog = (app: ApplicationReadyForEvaluation2) =>
    s"${app.applicationId}, Launchpad test Id: ${app.activeLaunchpadTest.map(_.token)}"
  val config = EvaluatePhase3ResultJobConfig
}

abstract class EvaluateOnlineTestResultJob[T <: PassMarkSettings](implicit jsonFormat: Format[T]) extends
  SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {

  val evaluateService: EvaluateOnlineTestResultService[T]
  val evaluateService2: EvaluateOnlineTestResultService2[T]
  val phase: Phase
  val batchSize = conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    evaluateService2.nextCandidatesReadyForEvaluation(batchSize) flatMap {
      case Some((apps, passmarkSettings)) =>
        evaluateInBatch(apps, passmarkSettings)
      case None =>
        Logger.info(s"Passmark settings or an application to evaluate $phase result not found")
        Future.successful(())
    }
  }

  val errorLog = (app: ApplicationReadyForEvaluation2) =>
    s"${app.applicationId}, psi order ids: ${app.activePsiTests.map(_.orderId).mkString(",")}"

  private def evaluateInBatch(apps: List[ApplicationReadyForEvaluation2],
                              passmarkSettings: T)(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.info(s"Evaluate $phase Job found ${apps.size} application(s), the passmarkVersion=${passmarkSettings.version}")
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
        Future.successful(())
      }
    }
  }
}

object EvaluatePhase1ResultJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.evaluate-phase1-result-job",
  name = "EvaluatePhase1ResultJob"
)

object EvaluatePhase2ResultJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.evaluate-phase2-result-job",
  name = "EvaluatePhase2ResultJob"
)

object EvaluatePhase3ResultJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.evaluate-phase3-result-job",
  name = "EvaluatePhase3ResultJob"
)
