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

package scheduler.onlinetesting

import com.google.inject.name.Named
import config.ScheduledJobConfig

import javax.inject.{Inject, Singleton}
import model._
import play.api.Configuration
import play.api.mvc.RequestHeader
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SuccessPhase1TestJob @Inject() (@Named("Phase1OnlineTestService") val service: OnlineTestService,
                                      val mongoComponent: MongoComponent,
                                      val config: SuccessPhase1TestJobConfig
                                     ) extends SuccessTestJob {
  override val successType: SuccessTestType = Phase1SuccessTestType
  override val phase = "PHASE1"
}

@Singleton
class SuccessPhase3TestJob @Inject() (@Named("Phase3OnlineTestService") val service: OnlineTestService,
                                      val mongoComponent: MongoComponent,
                                      val config: SuccessPhase3TestJobConfig
                                     ) extends SuccessTestJob {
  override val successType: SuccessTestType = Phase3SuccessTestType
  override val phase = "PHASE3"
}

@Singleton
class SuccessPhase3SdipFsTestJob @Inject() (@Named("Phase3OnlineTestService") val service: OnlineTestService,
                                            val mongoComponent: MongoComponent,
                                            val config: SuccessPhase3SdipFsTestJobConfig
                                           ) extends SuccessTestJob {
  override val successType: SuccessTestType = Phase3SuccessSdipFsTestType
  override val phase = "PHASE3"
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
