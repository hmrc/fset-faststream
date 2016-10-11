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

package controllers

import model.Commands
import model.command.ResetOnlineTest
import org.joda.time.DateTime
import model.ApplicationStatus._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.events.EventService
import services.onlinetesting.{ OnlineTestExtensionService, OnlineTestService, Phase1TestService, Phase2TestService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class OnlineTestDetails(
  inviteDate: DateTime, expireDate: DateTime, onlineTestLink: String,
  cubiksEmailAddress: String, isOnlineTestEnabled: Boolean
)

case class OnlineTest(
  inviteDate: DateTime, expireDate: DateTime, onlineTestLink: String,
  cubiksEmailAddress: String, isOnlineTestEnabled: Boolean, pdfReportAvailable: Boolean
)

case class OnlineTestStatus(status: String)

case class OnlineTestExtension(extraDays: Int, actionTriggeredBy: String)

object OnlineTestExtension {
  implicit val onlineTestExtensionFormat = Json.format[OnlineTestExtension]
}

case class UserIdWrapper(userId: String)

object OnlineTestController extends OnlineTestController {
  override val appRepository: GeneralApplicationRepository = applicationRepository
  override val onlineRepository: Phase1TestRepository = phase1TestRepository
  override val onlineTestingService = Phase1TestService
  override val phase2TestService = Phase2TestService
  override val onlineTestExtensionService: OnlineTestExtensionService = OnlineTestExtensionService
  val eventService: EventService = EventService
}

trait OnlineTestController extends BaseController {
  val appRepository: GeneralApplicationRepository
  val onlineRepository: Phase1TestRepository
  val onlineTestingService: Phase1TestService
  val phase2TestService: Phase2TestService
  val onlineTestExtensionService: OnlineTestExtensionService
  val eventService: EventService

  import Commands.Implicits._

  def getOnlineTest(applicationId: String) = Action.async { implicit request =>
    onlineTestingService.getTestProfile(applicationId) map {
      case Some(phase1TestProfileWithNames) => Ok(Json.toJson(phase1TestProfileWithNames))
      case None => Logger.warn(s"No phase 1 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def getPhase2OnlineTest(applicationId: String) = Action.async { implicit request =>
    phase2TestService.getTestProfile(applicationId) map {
      case Some(phase2TestGroupWithNames) => Ok(Json.toJson(phase2TestGroupWithNames))
      case None => Logger.warn(s"No phase 2 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def onlineTestStatusUpdate(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestStatus] { onlineTestStatus =>
      appRepository.updateStatus(applicationId, withName(onlineTestStatus.status)).map(_ => Ok)
    }
  }

  def resetOnlineTests(appId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest] { resetOnlineTest =>
      appRepository.getOnlineTestApplication(appId).flatMap {
        case Some(onlineTestApp) => onlineTestingService.resetTests(onlineTestApp, resetOnlineTest.tests,
          resetOnlineTest.actionTriggeredBy).map ( _ => Ok )
        case _ => Future.successful(NotFound)
      }
    }

  }
}
