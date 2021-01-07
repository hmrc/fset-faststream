/*
 * Copyright 2021 HM Revenue & Customs
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

import config.ScheduledJobConfig
import javax.inject.{ Inject, Singleton }
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.phase1.Phase1TestService

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ProgressSdipForFaststreamCandidateJobImpl @Inject() (val service: Phase1TestService,
                                                           val mongoComponent: ReactiveMongoComponent,
                                                           val config: ProgressSdipForFaststreamCandidateJobConfig
                                                          ) extends ProgressSdipForFaststreamCandidateJob {
  //  override val service = Phase1TestService
  //  val config = ProgressSdipForFaststreamCandidateJobConfig
}

trait ProgressSdipForFaststreamCandidateJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: Phase1TestService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.nextSdipFaststreamCandidateReadyForSdipProgression.flatMap {
      case Some(o) => service.progressSdipFaststreamCandidateForSdip(o)
      case None => Future.successful(())
    }
  }
}

@Singleton
class ProgressSdipForFaststreamCandidateJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.progress-sdipFs-candidate-for-sdip-job",
  name = "ProgressSdipForFaststreamCandidateJob"
)
