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
import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.upscan.UpscanService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.net.URL
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

sealed trait CallbackBody {
  def reference: Reference
}

case class ReadyCallbackBody(
                              reference: Reference,
                              downloadUrl: URL,
                              uploadDetails: UploadDetails
                            ) extends CallbackBody

case class FailedCallbackBody(
                               reference: Reference,
                               failureDetails: ErrorDetails
                             ) extends CallbackBody

object CallbackBody {

  given Reads[UploadDetails] = Json.reads[UploadDetails]
  given Reads[ErrorDetails] = Json.reads[ErrorDetails]

  given Reads[ReadyCallbackBody] =
    given Format[URL] = HttpUrlFormat.format
    Json.reads[ReadyCallbackBody]

  given Reads[FailedCallbackBody] = Json.reads[FailedCallbackBody]

  given Reads[CallbackBody] =
    (json: JsValue) =>
      json \ "fileStatus" match
        case JsDefined(JsString("READY")) => json.validate[ReadyCallbackBody]
        case JsDefined(JsString("FAILED")) => json.validate[FailedCallbackBody]
        case JsDefined(value) => JsError(s"Invalid type discriminator: $value")
        case _ => JsError(s"Missing type discriminator")
}

case class UploadDetails(
                          uploadTimestamp: Instant,
                          checksum: String,
                          fileMimeType: String,
                          fileName: String,
                          size: Long
                        )

case class ErrorDetails(
                         failureReason: String,
                         message: String
                       )

@Singleton
class UpscanCallbackController @Inject()(cc: ControllerComponents,
                                         upscanService: UpscanService) extends BackendController(cc) with Logging {

  implicit val ec: ExecutionContext = cc.executionContext

  // Process the callback we receive from upscan after it has scanned the uploaded file
  // to inform us of a successful scan or a failure if a virus is found
  def callback(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      logger.warn(s"Received upscan callback notification: ${request.body}")
      withJsonBody[CallbackBody]: feedback =>
        upscanService.handleCallback(feedback).map(_ => Ok)
    }
}
