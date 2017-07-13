/*
 * Copyright 2017 HM Revenue & Customs
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

package services.sift

import _root_.services.AuditService
import common.Phase3TestConcern
import config.LaunchpadGatewayConfig
import connectors._
import connectors.launchpadgateway.LaunchpadGatewayClient
import connectors.launchpadgateway.exchangeobjects.out._
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.NotFoundException
import model.OnlineTestCommands._
import model.ProgressStatuses._
import model._
import model.command.ProgressResponse
import model.stc.StcEventTypes.StcEventType
import model.stc.{ AuditEvents, DataStoreEvents }
import model.exchange.Phase3TestGroupWithActiveTest
import model.persisted.phase3tests.{ LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup }
import model.persisted.{ ApplicationReadyForEvaluation, NotificationExpiringOnlineTest, Phase3TestGroupWithAppId }
import org.joda.time.{ DateTime, LocalDate }
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase3TestRepository
import repositories.sift.ProgressToSiftRepository
import services.stc.StcEventService
import services.onlinetesting.Exceptions.NoActiveTestException
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

object SiftService extends SiftService {
  val progressToSiftRepository = ProgressToSiftRepository
}

trait SiftService {
  def progressToSiftRepository : ProgressToSiftRepository

  def nextCandidatesReadyForEvaluation(batchSize: Int) = {
    Future[Option[(List[ApplicationReadyForEvaluation], T)]] = {
      progressToSiftRepository.nextApplicationsReadyForSift(batchSize) map { candidates => Some(candidates -> passmark) }
    }
  }
}
