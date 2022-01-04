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

import com.google.inject.name.Named
import config.{ MicroserviceAppConfig, ScheduledJobConfig }
import javax.inject.{ Inject, Singleton }
import model._
import play.api.mvc.RequestHeader
import play.api.{ Configuration, Logging }
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ExpirePhase1TestJob @Inject() (@Named("Phase1OnlineTestService") val onlineTestingService: OnlineTestService,
                                     val mongoComponent: ReactiveMongoComponent,
                                     val config: ExpirePhase1TestJobConfig,
                                     appConfig: MicroserviceAppConfig) extends ExpireOnlineTestJob {
  override val expiryTest = Phase1ExpirationEvent(gracePeriodInSecs = appConfig.onlineTestsGatewayConfig.phase1Tests.gracePeriodInSecs)
}

@Singleton
class ExpirePhase2TestJob @Inject() (@Named("Phase2OnlineTestService") val onlineTestingService: OnlineTestService,
                                     val mongoComponent: ReactiveMongoComponent,
                                     val config: ExpirePhase2TestJobConfig,
                                     appConfig: MicroserviceAppConfig
                                    ) extends ExpireOnlineTestJob {
  override val expiryTest = Phase2ExpirationEvent(gracePeriodInSecs = appConfig.onlineTestsGatewayConfig.phase2Tests.gracePeriodInSecs)
}

@Singleton
class ExpirePhase3TestJob @Inject() (@Named("Phase3OnlineTestService") val onlineTestingService: OnlineTestService,
                                     val mongoComponent: ReactiveMongoComponent,
                                     val config: ExpirePhase3TestJobConfig,
                                     appConfig: MicroserviceAppConfig) extends ExpireOnlineTestJob {
  override val expiryTest = Phase3ExpirationEvent(gracePeriodInSecs = appConfig.launchpadGatewayConfig.phase3Tests.gracePeriodInSecs)
}

trait ExpireOnlineTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] with Logging {
  val onlineTestingService: OnlineTestService
  val expiryTest: TestExpirationEvent

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = EmptyRequestHeader
    logger.debug(s"Running online test expiry job for ${expiryTest.phase} with gracePeriodInSecs = ${expiryTest.gracePeriodInSecs}")
    onlineTestingService.processNextExpiredTest(expiryTest)
  }
}

@Singleton
class ExpirePhase1TestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.expiry-phase1-job",
  name = "ExpirePhase1TestJob"
)

@Singleton
class ExpirePhase2TestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.expiry-phase2-job",
  name = "ExpirePhase2TestJob"
)

@Singleton
class ExpirePhase3TestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.expiry-phase3-job",
  name = "ExpirePhase3TestJob"
)
