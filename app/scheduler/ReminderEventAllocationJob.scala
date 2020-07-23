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

package scheduler

import config.ScheduledJobConfig
import javax.inject.{ Inject, Singleton }
import model.EmptyRequestHeader
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.clustering.SingleInstanceScheduledJob
import services.allocation.CandidateAllocationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class ReminderEventAllocationJobImpl @Inject() (val service: CandidateAllocationService,
                                                val mongoComponent: ReactiveMongoComponent,
                                                val config: ReminderEventAllocationJobConfig
                                               ) extends ReminderEventAllocationJob {
}

trait ReminderEventAllocationJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: CandidateAllocationService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = HeaderCarrier()
    service.processUnconfirmedCandidates()
  }
}

@Singleton
class ReminderEventAllocationJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.remind-candidate-event-allocated",
  name = "ReminderEventAllocationJob"
)