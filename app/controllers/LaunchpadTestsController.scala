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

package controllers

import connectors.launchpadgateway.exchangeobjects.in._
import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest
import controllers.LaunchpadTestsController.CannotFindTestByLaunchpadInviteId
import model.Exceptions.NotFoundException
import play.api.Logger
import play.api.mvc.{ Action, Result }
import services.stc.StcEventService
import services.onlinetesting.phase3.{ Phase3TestCallbackService, Phase3TestService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object LaunchpadTestsController extends LaunchpadTestsController {
  override val phase3TestService = Phase3TestService
  override val phase3TestCallbackService = Phase3TestCallbackService
  val eventService = StcEventService

  case class CannotFindTestByLaunchpadInviteId(message: String) extends NotFoundException(message)
}

trait LaunchpadTestsController extends BaseController {
  val phase3TestService: Phase3TestService
  val phase3TestCallbackService: Phase3TestCallbackService
  val eventService: StcEventService

  def markAsStarted(inviteId: String) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Launchpad Assessment with invite ID $inviteId marked as started")
    phase3TestService.markAsStarted(inviteId)
      .map(_ => Ok)
      .recover(recoverNotFound)
  }

  def markAsComplete(inviteId: String) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Launchpad Assessment with invite ID $inviteId marked as completed")
    phase3TestService.markAsCompleted(inviteId)
      .map(_ => Ok)
      .recover(recoverNotFound)
  }

  def setupProcessCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[SetupProcessCallbackRequest] { jsonBody =>
      Logger.info("Launchpad: Received setup process callback request -> " + jsonBody)
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def viewPracticeQuestionCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ViewPracticeQuestionCallbackRequest] { jsonBody =>
      Logger.info("Launchpad: Received view practice question callback request -> " + jsonBody)
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def questionCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[QuestionCallbackRequest] { jsonBody =>
      Logger.info("Launchpad: Received question request -> " + jsonBody)
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def finalCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FinalCallbackRequest] { jsonBody =>
      Logger.info("Launchpad: Received final callback request -> " + jsonBody)
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def finishedCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FinishedCallbackRequest] { jsonBody =>
      Logger.info("Launchpad: Received finished callback request -> " + jsonBody)
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  def reviewedCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ReviewedCallbackRequest] { jsonBody =>
      Logger.info("Launchpad: Received reviewed callback request -> " + jsonBody)
      phase3TestCallbackService.recordCallback(jsonBody).map(_ => Ok).recover(recoverNotFound)
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e@CannotFindTestByLaunchpadInviteId(msg) =>
      Logger.warn(msg, e)
      NotFound
  }
}
