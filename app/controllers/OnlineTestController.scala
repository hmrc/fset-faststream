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
import model.OnlineTestCommands.Implicits._
import model.command.ResetOnlineTest
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import services.onlinetesting.{ OnlineTestExtensionService, OnlineTestService }
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

case class OnlineTestExtension(extraDays: Int)

case class UserIdWrapper(userId: String)

object OnlineTestController extends OnlineTestController {
  override val appRepository: GeneralApplicationRepository = applicationRepository
  override val onlineRepository: OnlineTestRepository = onlineTestRepository
  override val onlineTestingService: OnlineTestService = OnlineTestService
  override val onlineTestExtensionService: OnlineTestExtensionService = OnlineTestExtensionService
}

trait OnlineTestController extends BaseController {

  val appRepository: GeneralApplicationRepository
  val onlineRepository: OnlineTestRepository
  val onlineTestingService: OnlineTestService
  val onlineTestExtensionService: OnlineTestExtensionService

  import Commands.Implicits._

  def getOnlineTest(userId: String) = Action.async { implicit request =>
    onlineTestingService.getPhase1TestProfile(userId).map {
      case Some(phase1TestProfile) => Ok(Json.toJson(phase1TestProfile))
      case None => Logger.error(s"No phase 1 test found for userID [$userId]")
        NotFound
    }
  }

  def onlineTestStatusUpdate(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestStatus] { onlineTestStatus =>
      appRepository.updateStatus(applicationId, onlineTestStatus.status).map(_ => Ok)
    }
  }

  /**
   * Note that this function will result with an ok even if the token is invalid.
   * This is done on purpose. We want to update the status of the user if the token is correct, but if for
   * any reason the token is wrong we still want to display the success page. The admin report will handle
   * potential errors
   *
   * @return
   */
  def completeTests(token: String) = Action.async { implicit request =>
    // TODO FIX ME - get appId from online test repo by token
    //for {
    //  appId <- onlineRepository.findByToken(token)
    //  _ <- applicationRepository.setOnlineTestStatus(appId, "ONLINE_TESTS_COMPLETE")
    //} yield Ok

    Future.successful(Ok)
  }

  def resetOnlineTests(appId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest] { resetOnlineTest =>
      appRepository.getOnlineTestApplication(appId).flatMap {
        case Some(onlineTestApp) =>
          onlineTestingService.registerAndInviteForTestGroup(onlineTestApp, resetOnlineTest.tests).map { _ =>
            Ok
          }
        case _ => Future.successful(NotFound)
      }
    }

  }
}
