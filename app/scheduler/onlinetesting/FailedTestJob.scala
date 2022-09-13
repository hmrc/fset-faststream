/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model._
import play.api.Configuration
import play.api.mvc.RequestHeader
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class FailedPhase1TestJob @Inject() (val service: Phase1TestService,
                                     val mongoComponent: MongoComponent,
                                     val config: FailedPhase1TestJobConfig
                                    ) extends FailedTestJob {
  override val failedType: FailedTestType = Phase1FailedTestType
  override val phase = "PHASE1"
}

@Singleton
class FailedPhase2TestJob @Inject() (val service: Phase2TestService,
                                     val mongoComponent: MongoComponent,
                                     val config: FailedPhase2TestJobConfig) extends FailedTestJob {
  override val failedType: FailedTestType = Phase2FailedTestType
  override val phase = "PHASE2"
}

@Singleton
class FailedPhase3TestJob @Inject() (val service: Phase3TestService,
                                     val mongoComponent: MongoComponent,
                                     val config: FailedPhase3TestJobConfig
                                    ) extends FailedTestJob {
  override val failedType: FailedTestType = Phase3FailedTestType
  override val phase = "PHASE3"
}

trait FailedTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: OnlineTestService
  val failedType: FailedTestType
  val phase: String

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh: RequestHeader = EmptyRequestHeader
    implicit val hc: HeaderCarrier = HeaderCarrier()
    service.processNextTestForNotification(failedType, phase, "failed")
  }
}

@Singleton
class FailedPhase1TestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.failed-phase1-test-job",
  name = "FailedPhase1TestJob"
)

@Singleton
class FailedPhase2TestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.failed-phase2-test-job",
  name = "FailedPhase2TestJob"
)

@Singleton
class FailedPhase3TestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.failed-phase3-test-job",
  name = "FailedPhase3TestJob"
)
