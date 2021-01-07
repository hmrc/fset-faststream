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

import com.google.inject.name.Named
import config.ScheduledJobConfig
import javax.inject.{ Inject, Singleton }
import model._
import play.api.Configuration
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SuccessPhase1TestJob @Inject() (@Named("Phase1OnlineTestService") val service: OnlineTestService,
                                      val mongoComponent: ReactiveMongoComponent,
                                      val config: SuccessPhase1TestJobConfig
                                     ) extends SuccessTestJob {
  //  override val service = Phase1TestService
  override val successType: SuccessTestType = Phase1SuccessTestType
  override val phase = "PHASE1"
  //  val config = SuccessPhase1TestJobConfig
}

@Singleton
class SuccessPhase3TestJob @Inject() (@Named("Phase3OnlineTestService") val service: OnlineTestService,
                                      val mongoComponent: ReactiveMongoComponent,
                                      val config: SuccessPhase3TestJobConfig
                                     ) extends SuccessTestJob {
  //  override val service = Phase3TestService
  override val successType: SuccessTestType = Phase3SuccessTestType
  override val phase = "PHASE3"
  //  val config = SuccessPhase3TestJobConfig
}

@Singleton
class SuccessPhase3SdipFsTestJob @Inject() (@Named("Phase3OnlineTestService") val service: OnlineTestService,
                                            val mongoComponent: ReactiveMongoComponent,
                                            val config: SuccessPhase3SdipFsTestJobConfig
                                           ) extends SuccessTestJob {
  //  override val service = Phase3TestService
  override val successType: SuccessTestType = Phase3SuccessSdipFsTestType
  override val phase = "PHASE3"
  //  val config = SuccessPhase3TestJobConfig NOTE: wrong used config here
}

trait SuccessTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: OnlineTestService
  val successType: SuccessTestType
  val phase: String

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh: RequestHeader = EmptyRequestHeader
    implicit val hc: HeaderCarrier = HeaderCarrier()
    service.processNextTestForNotification(successType, phase, "passed")
  }
}

@Singleton
class SuccessPhase1TestJobConfig @Inject () (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.success-phase1-test-job",
  name = "SuccessPhase1TestJob"
)

@Singleton
class SuccessPhase3TestJobConfig @Inject () (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.success-phase3-test-job",
  name = "SuccessPhase3TestJob"
)

@Singleton
class SuccessPhase3SdipFsTestJobConfig @Inject () (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.success-phase3-sdipfs-test-job",
  name = "SuccessPhase3SdipFsTestJob"
)
