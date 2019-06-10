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

import model.Exceptions.CannotFindTestByOrderId
import model.exchange.PsiTestResultReady
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{ Action, Result }
import services.NumericalTestService
import services.stc.StcEventService
import services.onlinetesting.phase1.Phase1TestService2
import services.onlinetesting.phase2.Phase2TestService2
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PsiTestsController extends PsiTestsController {
  override val phase1TestService2 = Phase1TestService2
  override val phase2TestService2 = Phase2TestService2
//  val numericalTestService: NumericalTestService = NumericalTestService
  val eventService = StcEventService
}

trait PsiTestsController extends BaseController {
  val phase1TestService2: Phase1TestService2
  val phase2TestService2: Phase2TestService2
//  val numericalTestService: NumericalTestService
  val eventService: StcEventService


  def start(orderId: String) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Order ID $orderId assessment started")

    phase1TestService2.markAsStarted2(orderId)
      .recoverWith {
        case e =>
          Logger.warn(s"Something went wrong while saving start time: ${e.getMessage}")
          phase2TestService2.markAsStarted2(orderId)
      }.map(_ => Ok)
        .recover(recoverNotFound)
  }

/*
  def complete(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Cubiks userId $cubiksUserId assessment completed")
    phase1TestService.markAsCompleted(cubiksUserId).recoverWith {
      case _: CannotFindTestByCubiksId =>
        phase2TestService.markAsCompleted(cubiksUserId).recoverWith {
          case _: CannotFindTestByCubiksId =>
            numericalTestService.markAsCompleted(cubiksUserId)
        }
    }.map( _ => Ok )
      .recover(recoverNotFound)
  }
*/

  /**
    * Note that this function will result with an ok even if the token is invalid.
    * This is done on purpose. We want to update the status of the user if the token is correct, but if for
    * any reason the token is wrong we still want to display the success page.
    */
  def completeTestByOrderId(orderId: String) = Action.async { implicit request =>
    Logger.info(s"Complete test by orderId=$orderId")

    phase1TestService2.markAsCompleted2(orderId)
      .recoverWith { case _ =>
          phase2TestService2.markAsCompleted2(orderId)
      }.map(_ => Ok).recover(recoverNotFound)
  }

  def markResultsReady(orderId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[PsiTestResultReady] { testResultReady =>
      Logger.info(s"Psi test orderId=$orderId has results ready to download. " +
        s"Payload(json) = [${Json.toJson(testResultReady).toString}], (deserialized) = [$testResultReady]")
      phase1TestService2.markAsReportReadyToDownload2(orderId, testResultReady).map( _ => Ok )
        .recover(recoverNotFound)

//      phase1TestService.markAsReportReadyToDownload2(orderId, testResultReady)
//        .recoverWith { case _: CannotFindTestByCubiksId =>
//          phase2TestService.markAsReportReadyToDownload(orderId, testResultReady).recoverWith {
//            case _: CannotFindTestByCubiksId =>
//              numericalTestService.markAsReportReadyToDownload(orderId, testResultReady)
//          }
//        }.map( _ => Ok )
//        .recover(recoverNotFound)
//      Future.successful(Ok)
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e @ CannotFindTestByOrderId(msg) =>
      Logger.warn(msg, e)
      NotFound
  }
}
