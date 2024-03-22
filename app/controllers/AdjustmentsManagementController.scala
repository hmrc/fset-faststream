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
import model.Exceptions._
import model.{AdjustmentsComment, NeedsSupportAtFsac, NeedsSupportAtFsb}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.adjustmentsmanagement.AdjustmentsManagementService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class AdjustmentsManagementController @Inject() (cc: ControllerComponents,
                                                 adjustmentsManagementService: AdjustmentsManagementService) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext

  /*
  def confirmAdjustments(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Adjustments] { data =>
      adjustmentsManagementService.confirmAdjustment(applicationId, data).map { _ =>
        Ok
      }.recover {
        case e: ApplicationNotFound => NotFound(s"cannot find application for application with id: ${e.id}")
      }
    }
  }
   */

  /*
  def findAdjustments(applicationId: String): Action[AnyContent] = Action.async {
    adjustmentsManagementService.find(applicationId).map { adjustments =>
      if (adjustments.isDefined) {
        Ok(Json.toJson(adjustments))
      } else {
        NotFound
      }
    }
  }
   */

  def updateAdjustmentsComment(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[AdjustmentsComment] { data =>
      adjustmentsManagementService.updateAdjustmentsComment(applicationId, data).map { _ =>
        NoContent
      }.recover {
        case e: CannotUpdateAdjustmentsComment => BadRequest(s"cannot update adjustments comment for application with id: ${e.applicationId}")
      }
    }
  }

  def findAdjustmentsComment(applicationId: String): Action[AnyContent] = Action.async {
    adjustmentsManagementService.findAdjustmentsComment(applicationId).map { adjustmentsComment =>
      Ok(Json.toJson(adjustmentsComment))
    }.recover {
      case c: AdjustmentsCommentNotFound => NotFound(s"cannot find adjustments comment for application with id: ${c.applicationId}")
      case a: ApplicationNotFound => NotFound(s"cannot find application for application with id: ${a.id}")
    }
  }

  def removeAdjustmentsComment(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    adjustmentsManagementService.removeAdjustmentsComment(applicationId).map { _ =>
      NoContent
    }.recover {
      case e: CannotRemoveAdjustmentsComment => NotFound(s"cannot remove adjustments comment for application with id: ${e.applicationId}")
    }
  }

  def findNeedsSupportAtFsac(applicationId: String): Action[AnyContent] = Action.async {
    adjustmentsManagementService.findNeedsSupportAtFsac(applicationId).map { needsSupportAtFsac =>
      Ok(Json.toJson(needsSupportAtFsac))
    }.recover {
      case c: AdjustmentsNeedsSupportAtFsacNotFound =>
        NotFound(s"Cannot find adjustments needs support at FSAC for application with id: ${c.applicationId}")
      case a: ApplicationNotFound => NotFound(a.id)
    }
  }

  def updateNeedsSupportAtFsac(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NeedsSupportAtFsac] { data =>
      adjustmentsManagementService.updateNeedsSupportAtFsac(applicationId, data).map { _ =>
        NoContent
      }.recover {
        case e: CannotUpdateRecord => BadRequest(s"Cannot update adjustments needs support at venue for application with id: ${e.applicationId}")
      }
    }
  }

  def findNeedsSupportAtFsb(applicationId: String): Action[AnyContent] = Action.async {
    adjustmentsManagementService.findNeedsSupportAtFsb(applicationId).map { needsSupportAtFsb =>
      Ok(Json.toJson(needsSupportAtFsb))
    }.recover {
      case c: AdjustmentsNeedsSupportAtFsbNotFound =>
        NotFound(s"Cannot find adjustments needs support for phone interview for application with id: ${c.applicationId}")
      case a: ApplicationNotFound => NotFound(a.id)
    }
  }

  def updateNeedsSupportAtFsb(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NeedsSupportAtFsb] { data =>
      adjustmentsManagementService.updateNeedsSupportAtFsb(applicationId, data).map { _ =>
        NoContent
      }.recover {
        case e: CannotUpdateRecord =>
          BadRequest(s"Cannot update adjustments needs support for phone interview for application with id: ${e.applicationId}")
      }
    }
  }
}
