/*
 * Copyright 2025 HM Revenue & Customs
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

package model.exchange.upscan

import org.bson.types.ObjectId
import play.api.libs.json.*

import java.util.UUID

case class Reference(value: String) extends AnyVal {
  override def toString: String = s"Reference(value=$value)"
}

object Reference {
  given Reads[Reference] = Reads.StringReads.map(Reference(_))
}

object UploadStatuses {

  sealed trait UploadStatus

  object UploadStatus {
    implicit val readsUploadStatus: Reads[UploadStatus] = new Reads[UploadStatus] {
      override def reads(json: JsValue): JsResult[UploadStatus] = {
        val jsObject = json.asInstanceOf[JsObject]
        jsObject.value.get("_type") match {
          case Some(JsString("InProgress")) => JsSuccess(InProgress)
          case Some(JsString("Failed")) => JsSuccess(Failed)
          case Some(JsString("UploadedSuccessfully")) =>
            Json.fromJson[UploadedSuccessfully](jsObject)(UploadedSuccessfully.uploadedSuccessfullyFormat)
          case Some(value) => JsError(s"Unexpected value of _type: $value")
          case None => JsError("Missing _type field")
        }
      }
    }

    implicit val writesUploadStatus: Writes[UploadStatus] = new Writes[UploadStatus] {
      override def writes(status: UploadStatus): JsValue =
        status match {
          case InProgress => JsObject(Map("_type" -> JsString("InProgress")))
          case Failed => JsObject(Map("_type" -> JsString("Failed")))
          case s: UploadedSuccessfully =>
            Json.toJson(s)(UploadedSuccessfully.uploadedSuccessfullyFormat).as[JsObject] + ("_type" -> JsString(
              "UploadedSuccessfully"
            ))
        }
    }
  }

  case object InProgress extends UploadStatus
  case object Failed extends UploadStatus
  case class UploadedSuccessfully(name: String, mimeType: String, downloadUrl: String, size: Option[Long]) extends UploadStatus

  object UploadedSuccessfully {
    implicit val uploadedSuccessfullyFormat: OFormat[UploadedSuccessfully] = Json.format[UploadedSuccessfully]
  }
}

case class UploadStatusWithAppId(applicationId: String, status: UploadStatuses.UploadStatus)

object UploadStatusWithAppId {
  implicit val format: OFormat[UploadStatusWithAppId] = Json.format[UploadStatusWithAppId]
}

case class UploadDetails(
                          id       : ObjectId,
                          applicationId: String,
                          uploadId : UploadId,
                          reference: Reference,
                          status   : UploadStatuses.UploadStatus
                        ) {
  override def toString: String =
    "UploadDetails(" +
      s"id=$id," +
      s"applicationId=$applicationId," +
      s"uploadId=$uploadId," +
      s"reference=$reference," +
      s"status=$status" +
      ")"
}

case class UploadId(value: String) extends AnyVal {
  override def toString: String = s"UploadId(value=$value)"
}
