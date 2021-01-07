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

// TODO FIX ME!!! Once Cubiks callbacks are implemented
package scheduler.onlinetesting

import config.WaitingScheduledJobConfig
import javax.inject.{ Inject, Singleton }
import play.api.{ Configuration, Logger }
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService2
import services.onlinetesting.phase2.Phase2TestService2
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class RetrievePhase1ResultsJob @Inject() (val onlineTestingService: Phase1TestService2,
                                          val mongoComponent: ReactiveMongoComponent,
                                          val config: RetrievePhase1ResultsJobConfig
                                         ) extends RetrieveResultsJob {
  //  val onlineTestingService = Phase1TestService2
  //  val config = RetrievePhase1ResultsJobConfig
  val phase = "PHASE1"
}

@Singleton
class RetrievePhase2ResultsJob @Inject() (val onlineTestingService: Phase2TestService2,
                                          val mongoComponent: ReactiveMongoComponent,
                                          val config: RetrievePhase2ResultsJobConfig
                                         ) extends RetrieveResultsJob {
  //  val onlineTestingService = Phase2TestService2
  //  val config = RetrievePhase2ResultsJobConfig
  val phase = "PHASE2"
}

trait RetrieveResultsJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val onlineTestingService: OnlineTestService
  val phase: String

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    onlineTestingService.nextTestGroupWithReportReady.flatMap {
      case Some(richTestGroup) =>
        Logger.info(s"Now fetching results for candidate: ${richTestGroup.applicationId} in phase $phase")
        implicit val hc = HeaderCarrier()
        onlineTestingService.retrieveTestResult(richTestGroup)
      case None => {
        Logger.info(s"No candidates found when looking to download results for phase $phase")
        Future.successful(())
      }
    }
  }
}

@Singleton
class RetrievePhase1ResultsJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.retrieve-phase1-results-job",
  name = "RetrieveResultsJob"
)

@Singleton
class RetrievePhase2ResultsJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.retrieve-phase2-results-job",
  name = "RetrievePhase2ResultsJob"
)
