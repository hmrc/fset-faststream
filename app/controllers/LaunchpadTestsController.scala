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

import connectors.launchpadgateway.exchangeobjects.in._
import controllers.LaunchpadTestsController.CannotFindTestByLaunchpadInviteId
import model.Exceptions.{CannotFindTestByCubiksId, NotFoundException}
import model.exchange.CubiksTestResultReady
import play.api.Logger
import play.api.mvc.{Action, Result}
import services.events.EventService
import services.onlinetesting.{Phase1TestService, Phase2TestService, Phase3TestService}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object LaunchpadTestsController extends LaunchpadTestsController {
  override val phase3TestService = Phase3TestService
  val eventService = EventService

  case class CannotFindTestByLaunchpadInviteId(message: String) extends NotFoundException(message)
}

trait LaunchpadTestsController extends BaseController {
  val phase3TestService: Phase3TestService
  val eventService: EventService

  def setupProcessCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[SetupProcessCallbackRequest] { jsonBody =>
      Logger.debug("Received setup process callback request -> " + jsonBody)
      Future.successful(Ok)
    }
  }

  def viewPracticeQuestionCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ViewPracticeQuestionCallbackRequest] { jsonBody =>
      Logger.debug("Received view practice question callback request -> " + jsonBody)
      Future.successful(Ok)
    }
  }

  def questionCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[QuestionCallbackRequest] { jsonBody =>
      Logger.debug("Received question request -> " + jsonBody)
      Future.successful(Ok)
    }
  }

  def finalCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FinalCallbackRequest] { jsonBody =>
      Logger.debug("Received final callback request -> " + jsonBody)
      Future.successful(Ok)
    }
  }
  def finishedCallback(inviteId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FinishedCallbackRequest] { jsonBody =>
      Logger.debug("Received finished callback request -> " + jsonBody)
      Future.successful(Ok)
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e @ CannotFindTestByLaunchpadInviteId(msg) =>
      Logger.warn(msg, e)
      NotFound
  }
}
