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

package scheduler.onlinetesting

import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import common.FutureEx
import config.ScheduledJobConfig
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.ApplicationPhase1ReadyForEvaluation
import play.api.Logger
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.EvaluatePhase1ResultService

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object EvaluatePhase1ResultJob extends EvaluatePhase1ResultJob {
  val evaluateService: EvaluatePhase1ResultService = EvaluatePhase1ResultService
}

trait EvaluatePhase1ResultJob extends SingleInstanceScheduledJob with EvaluatePhase1ResultJobConfig {
  val evaluateService: EvaluatePhase1ResultService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    evaluateService.nextCandidatesReadyForEvaluation(batchSizeLimit) flatMap {
      case Some((apps, passmarkSettings)) =>
        evaluateInBatch(apps, passmarkSettings)
      case None =>
        Logger.info("Passmark settings or an application to evaluate phase1 result not found")
        Future.successful(())
    }
  }

  private def evaluateInBatch(apps: List[ApplicationPhase1ReadyForEvaluation],
                              passmarkSettings: Phase1PassMarkSettings)(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.debug(s"Evaluate Phase1 Job found ${apps.size} application(s), the passmarkVersion=${passmarkSettings.version}")
    val evaluationResultsFut = FutureEx.traverseToTry(apps) { app =>
      Try(evaluateService.evaluate(app, passmarkSettings)) match {
        case Success(fut) => fut
        case Failure(e) =>
          Future.failed(e)
      }
    }

    evaluationResultsFut flatMap { evaluationResults =>
      val errors = evaluationResults flatMap {
        case Failure(e) => Some(e)
        case _ => None
      }

      if (errors.nonEmpty) {
        val errorMsg = apps map {a =>
          s"${a.applicationId}, cubiks Ids: ${a.phase1.tests.map(_.cubiksUserId).mkString(",")}".mkString("\n")
        }

        Logger.error(s"There were errors in batch Phase 1 evaluation: $errorMsg, number of failed applications: ${errors.size}")
        Future.failed(errors.head)
      } else {
        Future.successful(())
      }
    }
  }
}

trait EvaluatePhase1ResultJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  val conf = config.MicroserviceAppConfig.evaluatePhase1ResultJobConfig
  val configPrefix = "scheduling.online-testing.evaluate-phase1-result-job."
  val name = "EvaluatePhase1ResultJob"
  val batchSizeLimit = config.MicroserviceAppConfig.maxNumberOfApplicationsInScheduler
  Logger.debug(s"Max number of applications in scheduler: $batchSizeLimit")
}
