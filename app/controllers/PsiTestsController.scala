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

import model.Exceptions.{ CannotFindApplicationByOrderId, CannotFindTestByOrderId }
import model.exchange.PsiRealTimeResults
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Result }
import services.NumericalTestService2
import services.onlinetesting.phase1.Phase1TestService2
import services.onlinetesting.phase2.Phase2TestService2
import services.stc.StcEventService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object PsiTestsController extends PsiTestsController {
  override val phase1TestService2 = Phase1TestService2
  override val phase2TestService2 = Phase2TestService2
  val numericalTestService2: NumericalTestService2 = NumericalTestService2
  val eventService = StcEventService
}

trait PsiTestsController extends BaseController {
  val phase1TestService2: Phase1TestService2
  val phase2TestService2: Phase2TestService2
  val numericalTestService2: NumericalTestService2
  val eventService: StcEventService


  def start(orderId: String) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Psi assessment started orderId=$orderId")

    phase1TestService2.markAsStarted2(orderId)
      .recoverWith {
        case e =>
          Logger.warn(s"Something went wrong while saving start time: ${e.getMessage}")
          phase2TestService2.markAsStarted2(orderId)
      }.map(_ => Ok)
        .recover(recoverNotFound)
  }

  /**
    * Note that this function will result with an ok even if the token is invalid.
    * This is done on purpose. We want to update the status of the user if the token is correct, but if for
    * any reason the token is wrong we still want to display the success page.
    */
  def completeTestByOrderId(orderId: String): Action[AnyContent] = Action.async { implicit request =>
    Logger.info(s"Complete psi test by orderId=$orderId")
    phase1TestService2.markAsCompleted2(orderId)
      .recoverWith { case _: CannotFindTestByOrderId =>
          phase2TestService2.markAsCompleted2(orderId).recoverWith {
            case _: CannotFindTestByOrderId =>
                numericalTestService2.markAsCompletedByOrderId(orderId)
            }
      }.map(_ => Ok).recover(recoverNotFound)
  }

  def realTimeResults(orderId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[PsiRealTimeResults] { realTimeResults =>
      Logger.info(s"We have received real time results for psi test orderId=$orderId. " +
        s"Payload(json) = [${Json.toJson(realTimeResults).toString}], (deserialized) = [$realTimeResults]")

      phase1TestService2.storeRealTimeResults(orderId, realTimeResults)
        .recoverWith { case _: CannotFindTestByOrderId =>
          phase2TestService2.storeRealTimeResults(orderId, realTimeResults).recoverWith {
            case _: CannotFindTestByOrderId =>
              numericalTestService2.storeRealTimeResults(orderId, realTimeResults)
          }
        }.map(_ => Ok).recover(recoverNotFound)
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e @ CannotFindTestByOrderId(msg) =>
      Logger.warn(msg, e)
      NotFound(msg)
    case e @ CannotFindApplicationByOrderId(msg) =>
      Logger.warn(msg, e)
      NotFound(msg)
  }
}
