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

package controllers.upscan

import model.exchange.upscan.{Reference, UploadId, UploadStatuses}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.upscan.UpscanService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class UpscanController @Inject() (cc: ControllerComponents,
                                  upscanService: UpscanService) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext

  def deleteAllData(): Action[AnyContent] = Action.async {
    upscanService.deleteAllData().map(_ => Ok)
  }

  def requestUpload(uploadId: UploadId, fileReference: Reference, applicationId: String): Action[AnyContent] = Action.async {
    upscanService.requestUpload(applicationId, uploadId, fileReference).map(_ => Ok)
  }

  def registerUploadResult(fileReference: Reference): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[UploadStatuses.UploadStatus] { uploadStatus =>
      upscanService.registerUploadResult(fileReference, uploadStatus).map(_ => Ok)
    }
  }

  def getUploadResult(uploadId: UploadId): Action[AnyContent] = Action.async {
    upscanService.getUploadResult(uploadId).map { statusOpt =>
      if(statusOpt.nonEmpty) {
        Ok(Json.toJson(statusOpt.head))
      } else {
        NotFound
      }
    }
  }

  def getUploadResultForFileReference(fileReference: Reference): Action[AnyContent] = Action.async {
    upscanService.getUploadResultForFileReference(fileReference).map { statusOpt =>
      if(statusOpt.nonEmpty) {
        Ok(Json.toJson(statusOpt.head))
      } else {
        NotFound
      }
    }
  }
}
