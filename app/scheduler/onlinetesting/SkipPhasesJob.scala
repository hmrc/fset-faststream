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

import config.WaitingScheduledJobConfig
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.clustering.SingleInstanceScheduledJob

import javax.inject.Inject
import play.api.{Configuration, Logging}
import scheduler.BasicJobConfig
import services.onlinetesting.phase2.Phase2TestService

import scala.concurrent.{ExecutionContext, Future}

class SkipPhasesJobImpl @Inject() (val phase2TestService: Phase2TestService,
                                   val mongoComponent: MongoComponent,
                                   val config: SkipPhasesJobConfig
                                  ) extends SkipPhasesJob {
}

trait SkipPhasesJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val phase2TestService: Phase2TestService

  val batchSize: Int = config.conf.batchSize.getOrElse(10)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    phase2TestService.nextApplicationsReadyToSkipPhases(batchSize).flatMap {
      case Nil =>
        // Warn level so we see it in the prod logs
        logger.warn(s"Skip phases job complete - batchSize = $batchSize, no candidates found")
        Future.successful(())
      case applications => phase2TestService.progressApplicationsToSkipPhases(applications).map { result =>
        logger.warn(
          s"Skip phases job complete - batchSize = $batchSize, ${result.successes.size} updated " +
            s"appIds: ${result.successes.map(_.applicationId).mkString(",")} and ${result.failures.size} failed to update " +
            s"appIds: ${result.failures.map(_.applicationId).mkString(",")}"
        )
      }
    }
  }
}

class SkipPhasesJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.skip-phases-job",
  name = "SkipPhasesJob"
)
