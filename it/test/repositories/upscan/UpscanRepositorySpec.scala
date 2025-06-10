/*
 * Copyright 2024 HM Revenue & Customs
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

package repositories.upscan

import model.exchange.upscan.{Reference, UploadDetails, UploadId, UploadStatuses}
import org.bson.types.ObjectId
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class UpscanRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.UPSCAN

  def repository = new UpscanRepository(mongo)

  private val applicationId = "appId"
  private val uploadId = UploadId("5c750c3a-d933-4db4-837a-793abc098cc0")
  private val fileReference = Reference("47545ff3-336d-42ea-a9c6-0f0dcbf6f1d4")

  "insert and find" should {
    "insert and find data" in {
      val objectId = ObjectId.get()
      for {
        _ <- repository.insert(UploadDetails(objectId, applicationId, uploadId, fileReference, UploadStatuses.InProgress))
        uploadDetailsOpt <- repository.findByUploadId(uploadId)
      } yield {
        uploadDetailsOpt mustBe Some(UploadDetails(objectId, applicationId, uploadId, fileReference, UploadStatuses.InProgress))
      }
    }
  }

  "updateStatus" should {
    "update the data" in {
      val objectId = ObjectId.get()
      val newStatus: UploadStatuses.UploadStatus = UploadStatuses.Failed
      for {
        _ <- repository.insert(UploadDetails(objectId, applicationId, uploadId, fileReference, UploadStatuses.InProgress))
        updatedData <- repository.updateStatus(fileReference, newStatus)
      } yield {
        updatedData mustBe newStatus
      }
    }
  }
}
