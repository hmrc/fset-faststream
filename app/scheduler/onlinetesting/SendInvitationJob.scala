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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import model.EmptyRequestHeader
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object SendPhase1InvitationJob extends SendInvitationJob {
  val onlineTestingService = Phase1TestService
  val config = SendPhase1InvitationJobConfig
}

object SendPhase2InvitationJob extends SendInvitationJob {
  val onlineTestingService = Phase2TestService
  val config = SendPhase2InvitationJobConfig
}

object SendPhase3InvitationJob extends SendInvitationJob {
  val onlineTestingService = Phase3TestService
  val config = SendPhase3InvitationJobConfig
}

trait SendInvitationJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val onlineTestingService: OnlineTestService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    onlineTestingService.nextApplicationsReadyForOnlineTesting(config.conf.batchSize.getOrElse(1)).flatMap {
      case Nil =>
        Future.successful(())
      case applications =>
        implicit val hc = HeaderCarrier()
        implicit val rh = EmptyRequestHeader
        onlineTestingService.registerAndInviteForTestGroup(applications)
    }
  }
}

object SendPhase1InvitationJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.send-phase1-invitation-job",
  name = "SendPhase1InvitationJob"
)

object SendPhase2InvitationJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.send-phase2-invitation-job",
  name = "SendPhase2InvitationJob"
)

object SendPhase3InvitationJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.send-phase3-invitation-job",
  name = "SendPhase3InvitationJob"
)
