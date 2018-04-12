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

package scheduler.sift

import config.WaitingScheduledJobConfig
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob

import scala.concurrent.{ ExecutionContext, Future }

object NumeracyTestInvitationJob extends NumeracyTestInvitationJob {
  val config = NumeracyTestInvitationConfig
}

trait NumeracyTestInvitationJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  lazy val batchSize = NumeracyTestInvitationConfig.conf.batchSize.getOrElse(1)

  override def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.info("Inviting candidates to Numeracy tests")
    Future.successful(())
  }
}

object NumeracyTestInvitationConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.numeracy-test-invitation-job",
  name = "NumeracyTestInvitationJob"
)