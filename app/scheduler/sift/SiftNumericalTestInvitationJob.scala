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

package scheduler.sift

import config.WaitingScheduledJobConfig
import javax.inject.{ Inject, Singleton }
import model.EmptyRequestHeader
import play.api.mvc.RequestHeader
import play.api.{ Configuration, Logger }
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.NumericalTestService2
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SiftNumericalTestInvitationJobImpl @Inject() (val siftService: ApplicationSiftService,
                                                    val numericalTestService: NumericalTestService2,
                                                    val mongoComponent: ReactiveMongoComponent,
                                                    val config: SiftNumericalTestInvitationConfig
                                                   ) extends SiftNumericalTestInvitationJob {
  //  val siftService = ApplicationSiftService
  //  val config = SiftNumericalTestInvitationConfig
  //  val numericalTestService: NumericalTestService2 = NumericalTestService2
}

trait SiftNumericalTestInvitationJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val siftService: ApplicationSiftService
  val numericalTestService: NumericalTestService2
  lazy val batchSize = config.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = EmptyRequestHeader
    log(s"Looking for candidates to invite to sift numerical test with a batch size of $batchSize")
    siftService.nextApplicationsReadyForNumericTestsInvitation(batchSize).flatMap {
      case Nil =>
        log("No application found for sift numerical test invitation")
        Future.successful(())
      case applications =>
        log(s"${applications.size} application(s) found for sift numerical test invitation")
        log(s"Inviting candidates to take a sift numerical test with IDs: ${applications.map(_.applicationId)}")
        numericalTestService.registerAndInviteForTests(applications.toList).map(_ => ())
          .recover { case e: Throwable =>
            val msg = s"Error occurred while registering and inviting candidates $applications " +
              s"for sift numeric tests - $e. Caused by ${e.getCause}"
            Logger.error(msg)
          }
    }
  }

  // Logging set to WARN so we can see it in PROD
  private def log(msg: String) = Logger.warn(msg)
}

@Singleton
class SiftNumericalTestInvitationConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.sift-numerical-test-invitation-job",
  name = "SiftNumericalTestInvitationJob"
)
