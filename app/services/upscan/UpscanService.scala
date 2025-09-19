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

package services.upscan

import controllers.upscan.{CallbackBody, FailedCallbackBody, ReadyCallbackBody}
import model.exchange.upscan.{Reference, UploadDetails, UploadId, UploadStatusWithAppId, UploadStatuses}
import org.bson.types.ObjectId

import javax.inject.{Inject, Singleton}
import repositories.upscan.UpscanRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanService @Inject()(repository: UpscanRepository)(using ExecutionContext) {

  def deleteAllData(): Future[Unit] =
    repository.deleteAllData()

  def requestUpload(applicationId: String, uploadId: UploadId, fileReference: Reference): Future[Unit] =
    repository.insert(UploadDetails(ObjectId.get(), applicationId, uploadId, fileReference, UploadStatuses.InProgress))

  def registerUploadResult(fileReference: Reference, uploadStatus: UploadStatuses.UploadStatus): Future[Unit] =
    repository.updateStatus(fileReference, uploadStatus).map(_ => ())

  def getUploadResult(id: UploadId): Future[Option[UploadStatusWithAppId]] =
    repository.findByUploadId(id).map(_.map( uploadDetails => UploadStatusWithAppId(uploadDetails.applicationId, uploadDetails.status)))

  def getUploadResultForFileReference(fileReference: Reference): Future[Option[UploadStatusWithAppId]] =
    repository.findByFileReference(fileReference).map(_.map(uploadDetails =>
      UploadStatusWithAppId(uploadDetails.applicationId, uploadDetails.status)
    ))

  def handleCallback(callback: CallbackBody): Future[Unit] =
    val uploadStatus =
      callback match
        case s: ReadyCallbackBody =>
          UploadStatuses.UploadedSuccessfully(
            name        = s.uploadDetails.fileName,
            mimeType    = s.uploadDetails.fileMimeType,
            downloadUrl = s.downloadUrl.toString,
            size        = Some(s.uploadDetails.size)
          )
        case _: FailedCallbackBody =>
          UploadStatuses.Failed

    registerUploadResult(callback.reference, uploadStatus)
}
