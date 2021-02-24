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
import model.Exceptions.{ CannotFindApplicationByOrderIdException, CannotFindTestByOrderIdException }
import model.exchange.PsiRealTimeResults
import play.api.Logging
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result }
import services.NumericalTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PsiTestsController @Inject() (cc: ControllerComponents,
                                    phase1TestService: Phase1TestService,
                                    phase2TestService: Phase2TestService,
                                    numericalTestService: NumericalTestService) extends BackendController(cc) with Logging {

  def start(orderId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    logger.info(s"Psi assessment started orderId=$orderId")

    phase1TestService.markAsStarted2(orderId)
      .recoverWith {
        case e =>
          logger.warn(s"Something went wrong while saving start time: ${e.getMessage}")
          phase2TestService.markAsStarted2(orderId)
      }.map(_ => Ok)
        .recover(recoverNotFound)
  }

  /**
    * Note that this function will result with an ok even if the token is invalid.
    * This is done on purpose. We want to update the status of the user if the token is correct, but if for
    * any reason the token is wrong we still want to display the success page.
    */
  def completeTestByOrderId(orderId: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"Complete psi test by orderId=$orderId")
    phase1TestService.markAsCompleted2(orderId)
      .recoverWith { case _: CannotFindTestByOrderIdException =>
          phase2TestService.markAsCompleted2(orderId).recoverWith {
            case _: CannotFindTestByOrderIdException =>
                numericalTestService.markAsCompletedByOrderId(orderId)
            }
      }.map(_ => Ok).recover(recoverNotFound)
  }

  def realTimeResults(orderId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[PsiRealTimeResults] { realTimeResults =>
      logger.info(s"We have received real time results for psi test orderId=$orderId. " +
        s"Payload(json) = [${Json.toJson(realTimeResults).toString}], (deserialized) = [$realTimeResults]")

      phase1TestService.storeRealTimeResults(orderId, realTimeResults)
        .recoverWith { case _: CannotFindTestByOrderIdException =>
          phase2TestService.storeRealTimeResults(orderId, realTimeResults).recoverWith {
            case _: CannotFindTestByOrderIdException =>
              numericalTestService.storeRealTimeResults(orderId, realTimeResults)
          }
        }.map(_ => Ok).recover(recoverNotFound)
    }
  }

  private def recoverNotFound[U >: Result]: PartialFunction[Throwable, U] = {
    case e @ CannotFindTestByOrderIdException(msg) =>
      logger.warn(msg, e)
      NotFound(msg)
    case e @ CannotFindApplicationByOrderIdException(msg) =>
      logger.warn(msg, e)
      NotFound(msg)
  }
}
