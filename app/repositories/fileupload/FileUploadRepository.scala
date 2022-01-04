/*
 * Copyright 2022 HM Revenue & Customs
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

import java.io.ByteArrayInputStream
import java.util.UUID

import com.google.inject.ImplementedBy
import javax.inject.{ Inject, Singleton }
import model.persisted.fileupload.{ FileUpload, FileUploadInfo }
import org.joda.time.{ DateTime, DateTimeZone }
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.gridfs.{ DefaultFileToSave, ReadFile }
import reactivemongo.play.iteratees.GridFS
import reactivemongo.api._
import reactivemongo.bson.{ BSONDocument, BSONValue }
import repositories.CollectionNames
import repositories.fileupload.FileUploadRepository.FileUploadNotFoundException
import reactivemongo.api.gridfs.Implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FileUploadRepository {
  case class FileUploadNotFoundException(message: String) extends Exception(message)
}

@ImplementedBy(classOf[FileUploadMongoRepository])
trait FileUploadRepository {
  def add(contentType: String, fileContents: Array[Byte]): Future[String]
  def retrieve(fileId: String): Future[FileUpload]
  def retrieveAllIdsAndSizes: Future[List[FileUploadInfo]]
  def retrieveMetaData(fileId: String): Future[Option[FileUploadInfo]]
}

@Singleton
class FileUploadMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent) extends FileUploadRepository {

  private lazy val gridFS = GridFS[BSONSerializationPack.type](mongoComponent.mongoConnector.db(), CollectionNames.FILE_UPLOAD)

  override def add(contentType: String, fileContents: Array[Byte]): Future[String] = {
    val newId = UUID.randomUUID().toString

    val fileToSave = DefaultFileToSave(Some(newId), Some(contentType), Some(DateTime.now.getMillis))

    gridFS.writeFromInputStream(fileToSave, new ByteArrayInputStream(fileContents)) map(_ => newId)
  }

  override def retrieve(fileId: String): Future[FileUpload] = {
     gridFS.find(BSONDocument("filename" -> fileId)).headOption.map {
      case Some(res) =>
        val body = gridFS.enumerate(res)
        FileUpload(
          fileId,
          res.contentType.get,
          new DateTime(res.uploadDate.get),
          body
        )
      case _ => throw FileUploadNotFoundException(s"No file upload found with id $fileId")
    }
  }

  override def retrieveAllIdsAndSizes: Future[List[FileUploadInfo]] = {
    val unlimitedMaxDocs = -1
    gridFS.find(BSONDocument.empty)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[ReadFile[BSONSerializationPack.type, BSONValue]]]()).map { fileList =>
      fileList.map { file =>
        FileUploadInfo(
          file.filename.get,
          file.contentType.get,
          new DateTime(file.uploadDate.get, DateTimeZone.UTC).toString,
          file.length
        )
      }
    }
  }

  override def retrieveMetaData(fileId: String): Future[Option[FileUploadInfo]] = {
    gridFS.find(BSONDocument("filename" -> fileId)).headOption.map {
      _.map { file =>
        FileUploadInfo(
          file.filename.get,
          file.contentType.get,
          new DateTime(file.uploadDate.get, DateTimeZone.UTC).toString,
          file.length
        )
      }
    }
  }
}
