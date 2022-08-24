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
import config.ScheduledJobConfig

import javax.inject.{Inject, Singleton}
import model.EmptyRequestHeader
import play.api.mvc.RequestHeader
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
//import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SendPhase1InvitationJob @Inject() (@Named("Phase1OnlineTestService") val onlineTestingService: OnlineTestService,
                                         val mongoComponent: MongoComponent,
                                         val config: SendPhase1InvitationJobConfig
                                        ) extends SendInvitationJob {
  val phase = "PHASE1"
}

@Singleton
class SendPhase2InvitationJob @Inject() (@Named("Phase2OnlineTestService") val onlineTestingService: OnlineTestService,
                                         val mongoComponent: MongoComponent,
                                          val config: SendPhase2InvitationJobConfig
                                         ) extends SendInvitationJob {
  val phase = "PHASE2"
}

@Singleton
class SendPhase3InvitationJob @Inject() (@Named("Phase3OnlineTestService") val onlineTestingService: OnlineTestService,
                                         val mongoComponent: MongoComponent,
                                         val config: SendPhase3InvitationJobConfig
                                        ) extends SendInvitationJob {
  val phase = "PHASE3"
}

trait SendInvitationJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] with Logging {
  val onlineTestingService: OnlineTestService
  val phase: String
  lazy val batchSize: Int = config.conf.batchSize.getOrElse(1)

  override def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    onlineTestingService.nextApplicationsReadyForOnlineTesting(batchSize).flatMap {
      case Nil =>
        logger.warn(s"No candidates found to invite to phase $phase")
        Future.successful(())
      case applications =>
        val applicationIds = applications.map( _.applicationId ).mkString(",")
        logger.warn(s"Inviting the following candidates to phase $phase: $applicationIds")
        implicit val hc: HeaderCarrier = HeaderCarrier()
        implicit val rh: RequestHeader = EmptyRequestHeader
        //        onlineTestingService.registerAndInviteForTestGroup(applications)
        onlineTestingService.registerAndInvite(applications)
    }
  }
}

@Singleton
class SendPhase1InvitationJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.send-phase1-invitation-job",
  name = "SendPhase1InvitationJob"
)

@Singleton
class SendPhase2InvitationJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.send-phase2-invitation-job",
  name = "SendPhase2InvitationJob"
)

@Singleton
class SendPhase3InvitationJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.send-phase3-invitation-job",
  name = "SendPhase3InvitationJob"
)
