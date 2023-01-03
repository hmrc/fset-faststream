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

import connectors.launchpadgateway.exchangeobjects.in._
import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest

import javax.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import model.Exceptions.NotFoundException
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, Result}
import repositories.onlinetesting.Phase3TestRepository.CannotFindTestByLaunchpadId
import services.onlinetesting.phase3.{Phase3TestCallbackService, Phase3TestService}

import scala.concurrent.ExecutionContext.Implicits.global

case class CannotFindTestByLaunchpadInviteId(message: String) extends NotFoundException(message)

@Singleton
class LaunchpadTestsController @Inject() (cc: ControllerComponents,
                                          phase3TestService: Phase3TestService,
                                          phase3TestCallbackService: Phase3TestCallbackService) extends BackendController(cc) with Logging {

  def markAsStarted(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    logger.info(s"Launchpad Assessment with invite ID $inviteId marked as started")
    phase3TestService.markAsStarted(inviteId)
      .map(_ => Ok)
      .recover(recoverNotFound)
  }

  def markAsComplete(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    logger.info(s"Launchpad Assessment with invite ID $inviteId marked as completed")
    phase3TestService.markAsCompleted(inviteId)
      .map(_ => Ok)
      .recover(recoverNotFound)
  }

  def setupProcessCallback(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[SetupProcessCallbackRequest] { jsonBody =>
      logger.info(s"Launchpad: Received setup process callback request json -> ${Json.toJson(jsonBody).toString}, deserialized -> $jsonBody")
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def viewPracticeQuestionCallback(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ViewPracticeQuestionCallbackRequest] { jsonBody =>
      logger.info("Launchpad: Received view practice question callback request " +
        s"json -> ${Json.toJson(jsonBody).toString}, deserialized -> $jsonBody")
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def questionCallback(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[QuestionCallbackRequest] { jsonBody =>
      logger.info(s"Launchpad: Received question request json -> ${Json.toJson(jsonBody).toString}, deserialized -> $jsonBody")
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def finalCallback(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FinalCallbackRequest] { jsonBody =>
      logger.info(s"Launchpad: Received final callback request json -> ${Json.toJson(jsonBody).toString}, deserialized -> $jsonBody")
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def finishedCallback(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FinishedCallbackRequest] { jsonBody =>
      logger.info(s"Launchpad: Received finished callback request json -> ${Json.toJson(jsonBody).toString}, deserialized -> $jsonBody")
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def reviewedCallback(inviteId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ReviewedCallbackRequest] { jsonBody =>
      logger.info(s"Launchpad: Received reviewed callback request json -> ${Json.toJson(jsonBody).toString}, deserialized -> $jsonBody")
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e @ CannotFindTestByLaunchpadInviteId(msg) =>
      logger.warn(msg, e)
      NotFound(e.getMessage)
    case e @ CannotFindTestByLaunchpadId(msg) =>
      logger.warn(msg, e)
      NotFound(e.getMessage)
  }
}
