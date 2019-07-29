/*
 * Copyright 2019 HM Revenue & Customs
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
import model.Exceptions.ExpiredTestForTokenException
import model.OnlineTestCommands.OnlineTestApplication
import model.command.{ InvigilatedTestUrl, ResetOnlineTest, ResetOnlineTest2, VerifyAccessCode }
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{ JsValue, Json, OFormat }
import play.api.mvc._
import repositories._
import repositories.application.GeneralApplicationRepository
import services.onlinetesting.phase1.{ Phase1TestService, Phase1TestService2 }
import services.onlinetesting.phase2.{ Phase2TestService, Phase2TestService2 }
import services.onlinetesting.Exceptions.{ CannotResetPhase2Tests, ResetLimitExceededException }
import services.onlinetesting.phase3.Phase3TestService
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class OnlineTestDetails(
  inviteDate: DateTime, expireDate: DateTime, onlineTestLink: String,
  cubiksEmailAddress: String, isOnlineTestEnabled: Boolean
)

object OnlineTestDetails {
  implicit val onlineTestDetailsFormat: OFormat[OnlineTestDetails] = Json.format[OnlineTestDetails]
}

case class OnlineTest(
  inviteDate: DateTime, expireDate: DateTime, onlineTestLink: String,
  cubiksEmailAddress: String, isOnlineTestEnabled: Boolean, pdfReportAvailable: Boolean
)

object OnlineTest {
  implicit val onlineTestFormat: OFormat[OnlineTest] = Json.format[OnlineTest]
}

case class OnlineTestStatus(status: String)

object OnlineTestStatus {
  implicit val onlineTestStatusFormat: OFormat[OnlineTestStatus] = Json.format[OnlineTestStatus]
}

case class OnlineTestExtension(extraDays: Int, actionTriggeredBy: String)

object OnlineTestExtension {
  implicit val onlineTestExtensionFormat = Json.format[OnlineTestExtension]
}

case class SiftExtension(extraDays: Int, actionTriggeredBy: String)

object SiftExtension {
  implicit val siftExtensionFormat = Json.format[SiftExtension]
}

case class UserIdWrapper(userId: String)

object UserIdWrapper {
  implicit val userIdWrapperFormat: OFormat[UserIdWrapper] = Json.format[UserIdWrapper]
}

object OnlineTestController extends OnlineTestController {
  override val appRepository: GeneralApplicationRepository = applicationRepository
  override val phase1TestService = Phase1TestService
  override val phase1TestService2 = Phase1TestService2
  override val phase2TestService = Phase2TestService
  override val phase2TestService2 = Phase2TestService2
  override val phase3TestService = Phase3TestService
}

trait OnlineTestController extends BaseController {
  val appRepository: GeneralApplicationRepository
  val phase1TestService: Phase1TestService
  val phase1TestService2: Phase1TestService2
  val phase2TestService: Phase2TestService
  val phase2TestService2: Phase2TestService2
  val phase3TestService: Phase3TestService

  def getPhase1OnlineTest(applicationId: String) = Action.async { implicit request =>
    phase1TestService.getTestGroup(applicationId) map {
      case Some(phase1TestProfileWithNames) => Ok(Json.toJson(phase1TestProfileWithNames))
      case None => Logger.debug(s"No phase 1 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  //TODO: remove this
  def getPhase1OnlineTest2(applicationId: String) = Action.async { implicit request =>
    phase1TestService2.getTestGroup2(applicationId) map {
      case Some(phase1TestProfileWithNames) =>
        val response = Json.toJson(phase1TestProfileWithNames)
        Logger.debug(s"**** getPhase1OnlineTest2 response=$response")
        Ok(response)
      case None => Logger.debug(s"No phase 1 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def getPhase2OnlineTest(applicationId: String) = Action.async { implicit request =>
    phase2TestService2.getTestGroup(applicationId) map {
      case Some(phase2TestGroupWithNames) => Ok(Json.toJson(phase2TestGroupWithNames))
      case None => Logger.debug(s"No phase 2 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def getPhase3OnlineTest(applicationId: String) = Action.async { implicit request =>
    phase3TestService.getTestGroupWithActiveTest(applicationId) map {
      case Some(phase3TestGroup) => Ok(Json.toJson(phase3TestGroup))
      case None => Logger.debug(s"No phase 3 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def onlineTestStatusUpdate(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestStatus] { onlineTestStatus =>
      appRepository.updateStatus(applicationId, withName(onlineTestStatus.status)).map(_ => Ok)
    }
  }

  def resetPhase1OnlineTests(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest2] { resetOnlineTest =>
      Logger.debug(s"resetPhase1OnlineTest - $resetOnlineTest")
      appRepository.getOnlineTestApplication(applicationId).flatMap {
        case Some(onlineTestApp) =>
          phase1TestService2.resetTest(onlineTestApp, resetOnlineTest.orderId, resetOnlineTest.actionTriggeredBy)
            .map ( _ => Ok )
        case _ => Future.successful(NotFound)
      }
    }
  }

  def resetPhase2OnlineTest(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest] { resetOnlineTest =>
      play.api.Logger.debug(s"resetPhase2OnlineTests - request=${play.api.libs.json.Json.toJson(resetOnlineTest).toString}")
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
