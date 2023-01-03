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

package scheduler.fsb

import config.WaitingScheduledJobConfig

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.FsbService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EvaluateFsbJobImpl @Inject() (val fsbService: FsbService,
                                    val mongoComponent: MongoComponent,
                                    val config: EvaluateFsbJobConfig
                                   ) extends EvaluateFsbJob {
}

trait EvaluateFsbJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val fsbService: FsbService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    logger.debug(s"EvaluateFsbJob starting")
    fsbService.nextFsbCandidateReadyForEvaluation.flatMap { appIdOpt =>
      appIdOpt.map { appId =>
        logger.debug(s"EvaluateFsbJob found a candidate - now evaluating...")
        fsbService.evaluateFsbCandidate(appId)
      }.getOrElse {
        logger.debug(s"EvaluateFsbJob no candidates found - going back to sleep...")
        Future.successful(())
      }
    }
  }
}

@Singleton
class EvaluateFsbJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.evaluate-fsb-job",
  name = "EvaluateFsbJob"
)
