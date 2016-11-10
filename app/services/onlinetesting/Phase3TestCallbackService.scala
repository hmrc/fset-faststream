/*
 * Copyright 2016 HM Revenue & Customs
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

package services.onlinetesting

import _root_.services.AuditService
import config.LaunchpadGatewayConfig
import connectors.launchpadgateway.exchangeobjects.in._
import play.api.libs.json.Format
import repositories.onlinetesting.Phase3TestRepository
import services.events.EventService
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

object Phase3TestCallbackService extends Phase3TestCallbackService {

  import config.MicroserviceAppConfig._

  val phase3TestRepo = phase3TestRepository
  val auditService = AuditService
  val gatewayConfig = launchpadGatewayConfig
  val eventService = EventService

  case class InviteIdNotRecognisedException(message: String) extends Exception(message)

}

trait Phase3TestCallbackService {
  val phase3TestRepo: Phase3TestRepository
  val auditService: AuditService
  val gatewayConfig: LaunchpadGatewayConfig
  val eventService: EventService

  def recordCallback(callbackData: SetupProcessCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, SetupProcessCallbackRequest.key, callbackData)
  }

  def recordCallback(callbackData: ViewPracticeQuestionCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, ViewPracticeQuestionCallbackRequest.key, callbackData)
  }

  def recordCallback(callbackData: QuestionCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, QuestionCallbackRequest.key, callbackData)
  }

  def recordCallback(callbackData: FinalCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, FinalCallbackRequest.key, callbackData)
  }

  def recordCallback(callbackData: FinishedCallbackRequest): Future[Unit] = {
    phase3TestRepo.appendCallback(callbackData.customInviteId, FinishedCallbackRequest.key, callbackData)
  }
}