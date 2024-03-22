/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.ApplicationStatus._
import model.Exceptions.{CannotFindTestByOrderIdException, ExpiredTestForTokenException}
import model.OnlineTestCommands.OnlineTestApplication
import model.command.{InvigilatedTestUrl, ResetOnlineTest, ResetOnlineTest2, VerifyAccessCode}
import play.api.Logging
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc._
import repositories.application.GeneralApplicationRepository
import services.onlinetesting.Exceptions.{CannotResetPhase2Tests, ResetLimitExceededException}
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

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

@Singleton
class OnlineTestController @Inject() (cc: ControllerComponents,
                                      appRepository: GeneralApplicationRepository,
                                      phase1TestService: Phase1TestService,
                                      phase2TestService: Phase2TestService,
                                      phase3TestService: Phase3TestService
                                     ) extends BackendController(cc) with Logging {

  implicit val ec: ExecutionContext = cc.executionContext

  def getPhase1OnlineTest(applicationId: String) = Action.async {
    phase1TestService.getTestGroup(applicationId) map {
      case Some(phase1TestProfileWithNames) => Ok(Json.toJson(phase1TestProfileWithNames))
      case None => logger.debug(s"No phase 1 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def getPhase2OnlineTest(applicationId: String) = Action.async {
    phase2TestService.getTestGroup(applicationId) map {
      case Some(phase2TestGroupWithNames) => Ok(Json.toJson(phase2TestGroupWithNames))
      case None => logger.debug(s"No phase 2 tests found for applicationId '$applicationId'")
        NotFound
    }
  }

  def getPhase1OnlineTestByOrderId(orderId: String) = Action.async {
    phase1TestService.getTestGroupByOrderId(orderId).map { phase1TestGroupWithNames =>
      Ok(Json.toJson(phase1TestGroupWithNames))
    }.recover {
      case _: CannotFindTestByOrderIdException =>
        logger.debug(s"No phase 1 tests found for orderId '$orderId'")
        NotFound
    }
  }

  def getPhase2OnlineTestByOrderId(orderId: String) = Action.async {
    phase2TestService.getTestGroupByOrderId(orderId).map { phase2TestGroupWithActiveTest =>
      Ok(Json.toJson(phase2TestGroupWithActiveTest))
    }.recover {
      case _: CannotFindTestByOrderIdException =>
        logger.debug(s"No phase 2 tests found for orderId '$orderId'")
        NotFound
    }
  }

  def getPhase3OnlineTest(applicationId: String) = Action.async {
    phase3TestService.getTestGroupWithActiveTest(applicationId) map {
      case Some(phase3TestGroup) => Ok(Json.toJson(phase3TestGroup))
      case None => logger.debug(s"No phase 3 tests found for applicationId '$applicationId'")
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
      logger.debug(s"resetPhase1OnlineTest - $resetOnlineTest")
      appRepository.getOnlineTestApplication(applicationId).flatMap {
        case Some(onlineTestApp) =>
          phase1TestService.resetTest(onlineTestApp, resetOnlineTest.orderId, resetOnlineTest.actionTriggeredBy)
            .map ( _ => Ok )
        case _ => Future.successful(NotFound)
      }
    }
  }

  def resetPhase2OnlineTest(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ResetOnlineTest2] { resetOnlineTest =>
      logger.debug(s"resetPhase2OnlineTests - $resetOnlineTest")
      appRepository.getOnlineTestApplication(applicationId).flatMap {
        case Some(onlineTestApp) =>
          phase2TestService.resetTest(onlineTestApp, resetOnlineTest.orderId, resetOnlineTest.actionTriggeredBy)
            .map(_ => Ok)
            .recover {
              case _: ResetLimitExceededException =>
                Locked
              case _: CannotResetPhase2Tests =>
                NotFound
            }
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
