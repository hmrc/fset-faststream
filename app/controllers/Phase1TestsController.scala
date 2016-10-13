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

import model.Exceptions.CannotFindTestByCubiksId
import model.exchange.Phase1TestResultReady
import play.api.mvc.{ Action, Result }
import uk.gov.hmrc.play.microservice.controller.BaseController
import play.api.Logger
import services.events.EventService
import services.onlinetesting.Phase1TestService

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1TestsController extends Phase1TestsController {
  override val phase1TestService = Phase1TestService
  val eventService = EventService
}

trait Phase1TestsController extends BaseController {
  val phase1TestService: Phase1TestService
  val eventService: EventService

  def start(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Assessment $cubiksUserId started")
    phase1TestService.markAsStarted(cubiksUserId).map( _ => Ok )
      .recover(recoverNotFound)
  }

  def complete(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Assessment $cubiksUserId completed")
    phase1TestService.markAsCompleted(cubiksUserId).map( _ => Ok )
      .recover(recoverNotFound)
  }

  /**
    * Note that this function will result with an ok even if the token is invalid.
    * This is done on purpose. We want to update the status of the user if the token is correct, but if for
    * any reason the token is wrong we still want to display the success page.
    */
  def completeTestByToken(token: String) = Action.async { implicit request =>
    Logger.info(s"Complete test by token $token")
    phase1TestService.markAsCompleted(token).map ( _ => Ok )
      .recover(recoverNotFound)
  }

  def markResultsReady(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    withJsonBody[Phase1TestResultReady] { testResultReady =>
      Logger.info(s"Assessment $cubiksUserId has report [$testResultReady] ready to download")
      phase1TestService.markAsReportReadyToDownload(cubiksUserId, testResultReady)
        .map(_ => Ok).recover(recoverNotFound)
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e @ CannotFindTestByCubiksId(msg) => {
      Logger.warn(msg, e)
      NotFound
    }
  }
}
