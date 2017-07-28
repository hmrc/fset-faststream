/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories.fileupload

import java.util.UUID

import model.persisted.fileupload.FileUpload
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson.Subtype.GenericBinarySubtype
import reactivemongo.bson.{ BSONArray, BSONBinary, BSONDocument, BSONObjectID }
import repositories.{ BSONDateTimeHandler, CollectionNames }
import repositories.fileupload.FileUploadRepository.FileUploadNotFoundException
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FileUploadRepository {
  case class FileUploadNotFoundException(message: String) extends Exception(message)
}

trait FileUploadRepository {
  def add(contentType: String, fileContents: Array[Byte]): Future[String]
  def retrieve(fileId: String): Future[FileUpload]
}

class FileUploadMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[FileUpload, BSONObjectID](CollectionNames.FILE_UPLOAD,
    mongo, FileUpload.fileUploadFormat, ReactiveMongoFormats.objectIdFormats)
    with FileUploadRepository {

  def add(contentType: String, fileContents: Array[Byte]): Future[String] = {
    val created = DateTime.now
    val newId = UUID.randomUUID().toString

    collection.insert(BSONDocument(
        "id" -> newId,
        "contentType" -> contentType,
        "created" -> created,
        "fileContents" -> BSONBinary(fileContents, GenericBinarySubtype)
      )).map(_ => newId)
  }

  def retrieve(fileId: String): Future[FileUpload] = {
    collection.find(BSONDocument("id" -> fileId)).one[FileUpload].map {
      case Some(upload) => upload
      case None => throw FileUploadNotFoundException(s"No file upload found with id $fileId")
    }
  }
}
