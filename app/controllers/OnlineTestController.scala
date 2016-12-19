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

import model.ApplicationStatus._
import model.Commands
import model.Exceptions.{ ContactDetailsNotFoundForEmail, ExpiredTestForTokenException, InvalidTokenException }
import model.OnlineTestCommands.OnlineTestApplication
import model.command.{ InvigilatedTestUrl, ResetOnlineTest, VerifyAccessCode }
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import repositories._
import repositories.application.GeneralApplicationRepository
import services.onlinetesting.ResetPhase2Test.{ CannotResetPhase2Tests, ResetLimitExceededException }
import services.onlinetesting.ResetPhase3Test.CannotResetPhase3Tests
import services.onlinetesting.{ Phase1TestService, Phase2TestService, Phase3TestService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success }

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
  override val phase1TestService = Phase1TestService
  override val phase2TestService = Phase2TestService
  override val phase3TestService = Phase3TestService
}

trait OnlineTestController extends BaseController {
  val appRepository: GeneralApplicationRepository
  val phase1TestService: Phase1TestService
  val phase2TestService: Phase2TestService
  val phase3TestService: Phase3TestService

  import Commands.Implicits._

  def getPhase1OnlineTest(applicationId: String) = Action.async { implicit request =>
    phase1TestService.getTestGroup(applicationId) map {
      case Some(phase1TestProfileWithNames) => Ok(Json.toJson(phase1TestProfileWithNames))
      case None => Logger.warn(s"No phase 1 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def getPhase2OnlineTest(applicationId: String) = Action.async { implicit request =>
    phase2TestService.getTestGroup(applicationId) map {
      case Some(phase2TestGroupWithNames) => Ok(Json.toJson(phase2TestGroupWithNames))
      case None => Logger.warn(s"No phase 2 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def getPhase3OnlineTest(applicationId: String) = Action.async { implicit request =>
    phase3TestService.getTestGroupWithActiveTest(applicationId) map {
      case Some(phase3TestGroup) => Ok(Json.toJson(phase3TestGroup))
      case None => Logger.warn(s"No phase 3 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def onlineTestStatusUpdate(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestStatus] { onlineTestStatus =>
      appRepository.updateStatus(applicationId, withName(onlineTestStatus.status)).map(_ => Ok)
    }
  }

  def resetPhase1OnlineTests(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest] { resetOnlineTest =>
      appRepository.getOnlineTestApplication(applicationId).flatMap {
        case Some(onlineTestApp) => phase1TestService.resetTests(onlineTestApp, resetOnlineTest.tests, resetOnlineTest.actionTriggeredBy)
          .map ( _ => Ok )
        case _ => Future.successful(NotFound)
      }
    }
  }

  def resetPhase2OnlineTest(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest] { resetOnlineTest =>

      def reset(onlineTestApp: OnlineTestApplication, actionTriggeredBy: String) =
        phase2TestService.resetTests(onlineTestApp, actionTriggeredBy)
          .map(_ => Ok)
          .recover {
            case _: ResetLimitExceededException =>
              Locked
            case _: CannotResetPhase2Tests =>
              NotFound
          }

      appRepository.getOnlineTestApplication(applicationId).flatMap {
        case Some(onlineTestApp) => reset(onlineTestApp, resetOnlineTest.actionTriggeredBy)
        case _ => Future.successful(NotFound)
      }
    }
  }

  def resetPhase3OnlineTest(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest] { resetOnlineTest =>

      def reset(onlineTestApp: OnlineTestApplication, actionTriggeredBy: String) = {
        phase3TestService.resetTests(onlineTestApp, resetOnlineTest.actionTriggeredBy)
          .map(_ => Ok)
          .recover {
            case _: CannotResetPhase3Tests => Conflict }
      }

      appRepository.getOnlineTestApplication(applicationId).flatMap {
        case Some(onlineTestApp) => reset(onlineTestApp, resetOnlineTest.actionTriggeredBy)
        case _ => Future.successful(NotFound)
      }
    }
  }

  def verifyAccessCode() = Action.async(parse.json) { implicit request =>
    withJsonBody[VerifyAccessCode] { verifyAccessCode =>
      phase2TestService.verifyAccessCode(verifyAccessCode.email, verifyAccessCode.accessCode).map {
        invigilatedTestUrl => Ok(Json.toJson(InvigilatedTestUrl(invigilatedTestUrl)))
      }.recover {
        case _: ExpiredTestForTokenException => Forbidden
        case _ => NotFound
      }
    }
  }
}
