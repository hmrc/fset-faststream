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

package repositories.fileupload

import com.google.inject.ImplementedBy
import model.Exceptions.NotFoundException
import model.persisted.fileupload.{FileUpload, FileUploadInfo}
import org.mongodb.scala.bson.BsonObjectId
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.gridfs.{GridFSBucket, GridFSFile, GridFSUploadOptions}
import org.mongodb.scala.{Observable, ObservableFuture}
import repositories.CollectionNames
import repositories.fileupload.FileUploadRepository.FileUploadNotFoundException
import uk.gov.hmrc.mongo.MongoComponent

import java.nio.ByteBuffer
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object FileUploadRepository {
  case class FileUploadNotFoundException(message: String) extends Exception(message)
}

@ImplementedBy(classOf[FileUploadMongoRepository])
trait FileUploadRepository {
  def add(contentType: String, fileContents: Array[Byte]): Future[String]
  def retrieve(fileId: String): Future[FileUpload]
  def retrieveAllIdsAndSizes: Future[List[FileUploadInfo]]
  def retrieveMetaData(fileId: String): Future[Option[FileUploadInfo]]
  def deleteDocument(objectId: String, applicationId: String): Future[Unit]
}

@Singleton
class FileUploadMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext) extends FileUploadRepository {

  private lazy val gridFS: GridFSBucket = GridFSBucket(mongoComponent.database, CollectionNames.FILE_UPLOAD)

  override def add(contentType: String, fileContents: Array[Byte]): Future[String] = {
    val fileId = UUID.randomUUID().toString

    import scala.jdk.CollectionConverters.*

    val options: GridFSUploadOptions = new GridFSUploadOptions().metadata(org.bson.Document(Map("contentType" -> contentType).asJava))

    val observableToUploadFrom: Observable[ByteBuffer] = Observable(
      Seq(ByteBuffer.wrap(fileContents))
    )

    gridFS.uploadFromObservable(fileId, observableToUploadFrom, options).head().map { _ => fileId }
      .recover {
        case e: Throwable =>
          throw new RuntimeException(s"Failed to save file due to error: ${e.getMessage}")
      }
  }

  override def retrieve(fileId: String): Future[FileUpload] = {
    gridFS.find(Document("filename" -> fileId)).headOption().flatMap {
      case Some(file) =>
        gridFS.downloadToObservable(file.getObjectId)
          .toFuture()
          .map( seq => seq.map(bb => bb.array).reduceLeft(_ ++ _))
          .map( array =>
            FileUpload(
              fileId, getContentType(file), OffsetDateTime.ofInstant(file.getUploadDate.toInstant, ZoneOffset.UTC), array
            )
          )
      case _ => throw FileUploadNotFoundException(s"No file upload found with id $fileId")
    }
  }

  override def retrieveAllIdsAndSizes: Future[List[FileUploadInfo]] = {
    gridFS.find(Document.empty).toFuture().map ( _.map ( processFile ).toList )
  }

  override def retrieveMetaData(fileId: String): Future[Option[FileUploadInfo]] = {
    gridFS.find(Document("filename" -> fileId)).headOption().map ( _.map ( processFile ) )
  }

  // Try and get the contentType out of metadata, which is a nullable field
  private def getContentType(file: GridFSFile) = Try(file.getMetadata.getString("contentType")).toOption.getOrElse("")

  private def processFile(file: GridFSFile) = {
    FileUploadInfo(
      file.getObjectId.toString,
      file.getFilename,
      getContentType(file),
      OffsetDateTime.ofInstant(file.getUploadDate.toInstant, ZoneOffset.UTC).toString,
      file.getLength
    )
  }

  override def deleteDocument(objectId: String, applicationId: String): Future[Unit] = {
    // The Try will fail if the objectId is not a correctly formatted bson object id so we deal with it
    // here in order to throw the common NotFoundException back instead of the IllegalArgumentException
    // that gets thrown by the BsonObjectId code
    val bsonObjectId = Try(BsonObjectId(objectId)).toEither match {
      case Right(bsonObjectId) => bsonObjectId
      case _ => throw new NotFoundException(s"No uploaded file found with the ObjectId: $objectId for applicationId: $applicationId")
    }
    gridFS.delete(bsonObjectId).toFuture().map {
      _ => ()
    }.recover {
      case e: com.mongodb.MongoGridFSException =>
        if (e.getMessage.startsWith("No file found with the ObjectId:")) {
          throw new NotFoundException(s"No uploaded file found with the ObjectId: $objectId for applicationId: $applicationId")
        } else {
          throw e
        }
    }
  }
}
