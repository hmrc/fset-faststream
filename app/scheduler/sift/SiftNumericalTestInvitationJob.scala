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
import model.EmptyRequestHeader
import model.NumericalTestApplication
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.NumericalTestService
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object SiftNumericalTestInvitationJob extends SiftNumericalTestInvitationJob {
  val siftService = ApplicationSiftService
  val config = SiftNumericalTestInvitationConfig
  val numericalTestService: NumericalTestService = NumericalTestService
}

trait SiftNumericalTestInvitationJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val siftService: ApplicationSiftService
  val numericalTestService: NumericalTestService
  lazy val batchSize = SiftNumericalTestInvitationConfig.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    implicit val rh = EmptyRequestHeader
    Logger.info("Looking for candidates to invite to sift numerical test")
    siftService.nextApplicationsReadyForNumericTestsInvitation(batchSize).map {
      case Nil =>
        Logger.info("No application found for sift numerical test invitation")
        Future.successful(())
      case applications =>
        Logger.info(s"${applications.size} application(s) found for sift numerical test invitation")
        Logger.info(s"Inviting candidates to take a sift numerical test with IDs: ${applications.map(_.applicationId)}")
        numericalTestService.registerAndInviteForTests(applications.toList)
    }
  }
}

object SiftNumericalTestInvitationConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.sift-numerical-test-invitation-job",
  name = "SiftNumericalTestInvitationJob"
)