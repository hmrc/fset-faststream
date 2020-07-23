/*
 * Copyright 2020 HM Revenue & Customs
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

package services.onlinetesting.phase3

import connectors.launchpadgateway.exchangeobjects.in._
import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest
import javax.inject.{ Inject, Singleton }
import play.api.mvc.RequestHeader
import repositories.onlinetesting.Phase3TestRepository
import services.stc.StcEventService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

//object Phase3TestCallbackService2 {

//  import config.MicroserviceAppConfig._

//  val phase3TestRepo = phase3TestRepository
//  val phase3TestService = Phase3TestService
//  val auditService = AuditService //TODO:fix (not used)
//  val gatewayConfig = launchpadGatewayConfig
//  val eventService = StcEventService

case class InviteIdNotRecognisedException(message: String) extends Exception(message)
//}

@Singleton
class Phase3TestCallbackService @Inject() (phase3TestRepo: Phase3TestRepository,
                                           phase3TestService: Phase3TestService,
//                                           appConfig: MicroserviceAppConfig2, NOT USED
                                           val eventService: StcEventService) {
  //  val phase3TestRepo: Phase3TestRepository
  //  val phase3TestService: Phase3TestService
  //  val auditService: AuditService
  //  val gatewayConfig: LaunchpadGatewayConfig
  //  val eventService: StcEventService

  def recordCallback(callbackData: SetupProcessCallbackRequest)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for{
      _ <- phase3TestRepo.appendCallback(callbackData.customInviteId, SetupProcessCallbackRequest.key, callbackData)
      _ <- phase3TestService.addResetEventMayBe(callbackData.customInviteId)
    } yield {}
  }

  def recordCallback(callbackData: ViewPracticeQuestionCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, ViewPracticeQuestionCallbackRequest.key, callbackData)
  }

  def recordCallback(callbackData: QuestionCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, QuestionCallbackRequest.key, callbackData)
  }

  def recordCallback(callbackData: FinalCallbackRequest)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      _ <- phase3TestRepo.appendCallback(callbackData.customInviteId, FinalCallbackRequest.key, callbackData)
      _ <- phase3TestService.markAsCompleted(callbackData.customInviteId)
    } yield {}
  }

  def recordCallback(callbackData: FinishedCallbackRequest)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      _ <- phase3TestRepo.appendCallback(callbackData.customInviteId, FinishedCallbackRequest.key, callbackData)
      _ <- phase3TestService.markAsCompleted(callbackData.customInviteId)
    } yield {}
  }

  def recordCallback(callbackData: ViewBrandedVideoCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, ViewBrandedVideoCallbackRequest.key, callbackData)
  }

  def recordCallback(callbackData: ReviewedCallbackRequest)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      _ <- phase3TestRepo.appendCallback(callbackData.customInviteId, ReviewedCallbackRequest.key, callbackData)
      _ <- phase3TestService.markAsResultsReceived(callbackData.customInviteId)
    } yield {}
  }
}
