/*
 * Copyright 2022 HM Revenue & Customs
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
import services.assessmentcentre.AssessmentCentreService
import services.onlinetesting.phase3.Phase3TestService

import scala.concurrent.{ExecutionContext, Future}

class SkipPhase3JobImpl @Inject() (val phase3TestService: Phase3TestService,
                                   val mongoComponent: MongoComponent,
                                   val config: SkipPhase3JobConfig
                                  ) extends SkipPhase3Job {
}

trait SkipPhase3Job extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val phase3TestService: Phase3TestService

  val batchSize: Int = config.conf.batchSize.getOrElse(10)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    phase3TestService.nextApplicationsReadyToSkipPhase3(batchSize).flatMap {
      case Nil =>
        logger.info(s"Skip phase3 job complete - batchSize = $batchSize, no candidates found")
        Future.successful(())
      case applications => phase3TestService.progressApplicationsToSkipPhase3(applications).map { result =>
        logger.info(
          s"Skip phase3 job complete - batchSize = $batchSize, ${result.successes.size} updated " +
            s"appIds: ${result.successes.map(_.applicationId).mkString(",")} and ${result.failures.size} failed to update " +
            s"appIds: ${result.failures.map(_.applicationId).mkString(",")}"
        )
      }
    }
  }
}

class SkipPhase3JobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.skip-phase3-job",
  name = "SkipPhase3Job"
)
