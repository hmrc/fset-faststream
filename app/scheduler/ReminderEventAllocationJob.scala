/*
 * Copyright 2018 HM Revenue & Customs
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
import model.EmptyRequestHeader
import scheduler.clustering.SingleInstanceScheduledJob
import services.allocation.CandidateAllocationService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object ReminderEventAllocationJob extends ReminderEventAllocationJob {
  override val service = CandidateAllocationService
  val config = ReminderEventAllocationJobConfig
}

trait ReminderEventAllocationJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: CandidateAllocationService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = HeaderCarrier()
    service.processUnconfirmedCandidates()
  }
}

object ReminderEventAllocationJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.remind-candidate-event-allocated",
  name = "ReminderEventAllocationJob"
)