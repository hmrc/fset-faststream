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
import model.NumericalTestCommands.NumericalTestApplication
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.NumericalTestsService
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object NumeracyTestInvitationJob extends NumeracyTestInvitationJob {
  val siftService = ApplicationSiftService
  val config = NumeracyTestInvitationConfig
  val numericalTestsService: NumericalTestsService = NumericalTestsService
}

trait NumeracyTestInvitationJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val siftService: ApplicationSiftService
  val numericalTestsService: NumericalTestsService
  lazy val batchSize = NumeracyTestInvitationConfig.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    implicit val rh = EmptyRequestHeader
    Logger.info("Inviting candidates to Numeracy tests")
    siftService.nextApplicationsReadyForNumericTestsInvitation(batchSize).map {
      case Nil =>
        Logger.info("No application found for numeric test invitation")
        Future.successful(())
      case applications =>
        Logger.info(s"${applications.size} application(s) found for numeric test invitation")
        Logger.info(s"Inviting Candidates with IDs: ${applications.map(_.applicationId)}")
        val apps = applications.map(app => NumericalTestApplication(app.applicationId, app.applicationStatus, app.userId)).toList
        numericalTestsService.registerAndInviteForTests(apps)
    }
  }
}

object NumeracyTestInvitationConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.numeracy-test-invitation-job",
  name = "NumeracyTestInvitationJob"
)