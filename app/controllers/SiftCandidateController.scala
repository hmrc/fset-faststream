/*
 * Copyright 2020 HM Revenue & Customs
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
import model.Exceptions.{ CannotFindApplicationByOrderIdException, CannotFindTestByCubiksId, CannotFindTestByOrderIdException }
import play.api.Logger
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, ControllerComponents }
import services.sift.{ ApplicationSiftService, SiftExpiryExtensionService }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class SiftCandidateController @Inject() (cc: ControllerComponents,
                                         siftExpiryExtensionService: SiftExpiryExtensionService,
                                         applicationSiftService: ApplicationSiftService
                                        ) extends BackendController(cc) {

  def extend(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[SiftExtension] { extension =>
      siftExpiryExtensionService.extendExpiryTime(applicationId, extension.extraDays, extension.actionTriggeredBy)
        .map( _ => Ok )
    }
  }

  def getSiftState(applicationId: String) = Action.async { implicit request =>
    applicationSiftService.getSiftState(applicationId) map {
      case Some(siftState) =>
        Ok(Json.toJson(siftState))
      case None => Logger.debug(s"No sift state found for applicationId: $applicationId")
        NotFound
    }
  }

  def getSiftTestGroup(applicationId: String) = Action.async { implicit request =>
    applicationSiftService.getTestGroup(applicationId) map {
      case Some(siftTest) =>
        Ok(Json.toJson(siftTest))
      case None => Logger.debug(s"No sift test group found for applicationId: $applicationId")
        NotFound
    }
  }

  def getSiftTestGroup2(applicationId: String) = Action.async { implicit request =>
    applicationSiftService.getTestGroup2(applicationId) map {
      case Some(siftTest) =>
        Ok(Json.toJson(siftTest))
      case None => Logger.debug(s"No sift test group found for applicationId: $applicationId")
        NotFound
    }
  }

  def startTest(cubiksUserId: Int) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Sift test started for cubiks id: $cubiksUserId")
    applicationSiftService.markTestAsStarted(cubiksUserId)
      .map( _ => Ok )
      .recover {
        case _: CannotFindTestByCubiksId => NotFound
      }
  }

  def startTest2(orderId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    Logger.info(s"Sift test started for order id: $orderId")
    applicationSiftService.markTestAsStarted2(orderId)
      .map( _ => Ok )
      .recover {
        case _: CannotFindTestByOrderIdException => NotFound
        case _: CannotFindApplicationByOrderIdException => NotFound
      }
  }
}
