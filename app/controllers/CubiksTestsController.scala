/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model.Exceptions.CannotFindTestByCubiksId
import model.exchange.CubiksTestResultReady
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{ Action, ControllerComponents, Result }
//import services.NumericalTestService
//import services.onlinetesting.phase1.Phase1TestService
//import services.onlinetesting.phase2.Phase2TestService
import services.stc.StcEventService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

//TODO: cubiks delete this class
@Singleton
class CubiksTestsController @Inject() (cc: ControllerComponents,
//                                       phase1TestService: Phase1TestService,
//                                       phase2TestService: Phase2TestService,
//                                       numericalTestService: NumericalTestService,
                                       eventService: StcEventService
                                      ) extends BackendController(cc) with Logging {

  def start(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    logger.info(s"Cubiks userId $cubiksUserId assessment started")
    ???
/*    phase1TestService.markAsStarted(cubiksUserId)
      .recoverWith { case _: CannotFindTestByCubiksId =>
          phase2TestService.markAsStarted(cubiksUserId)
      }.map( _ => Ok )
      .recover(recoverNotFound)*/
  }

  def complete(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    logger.info(s"Cubiks userId $cubiksUserId assessment completed")
    ???
/*    phase1TestService.markAsCompleted(cubiksUserId).recoverWith {
      case _: CannotFindTestByCubiksId =>
          phase2TestService.markAsCompleted(cubiksUserId).recoverWith {
            case _: CannotFindTestByCubiksId =>
              numericalTestService.markAsCompleted(cubiksUserId)
          }
    }.map( _ => Ok )
      .recover(recoverNotFound)*/
  }

  /**
    * Note that this function will result with an ok even if the token is invalid.
    * This is done on purpose. We want to update the status of the user if the token is correct, but if for
    * any reason the token is wrong we still want to display the success page.
    */
  def completeTestByToken(token: String) = Action.async { implicit request =>
    logger.info(s"Complete test by token $token")
    ???
/*    phase1TestService.markAsCompleted(token)
      .recoverWith { case _: CannotFindTestByCubiksId =>
          phase2TestService.markAsCompleted(token).recoverWith {
            case _: CannotFindTestByCubiksId =>
              numericalTestService.markAsCompleted(token)
          }
      }.map( _ => Ok )
      .recover(recoverNotFound)*/
  }

  def markResultsReady(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    withJsonBody[CubiksTestResultReady] { testResultReady =>
      logger.info(s"Cubiks user $cubiksUserId has xml results report ready to download. " +
        s"Payload(json) = [${Json.toJson(testResultReady).toString}], (deserialized) = [$testResultReady]")
      ???
/*      phase1TestService.markAsReportReadyToDownload(cubiksUserId, testResultReady)
        .recoverWith { case _: CannotFindTestByCubiksId =>
            phase2TestService.markAsReportReadyToDownload(cubiksUserId, testResultReady).recoverWith {
              case _: CannotFindTestByCubiksId =>
                numericalTestService.markAsReportReadyToDownload(cubiksUserId, testResultReady)
            }
        }.map( _ => Ok )
        .recover(recoverNotFound)*/
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e @ CannotFindTestByCubiksId(msg) =>
      logger.warn(msg, e)
      NotFound
  }
}
