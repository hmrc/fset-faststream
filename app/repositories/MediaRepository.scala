/*
 * Copyright 2021 HM Revenue & Customs
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

package repositories

import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.{apply => _}
import model.Exceptions.CannotAddMedia
import model.persisted.Media
import model.persisted.Media._
import org.mongodb.scala.MongoException
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
//import play.api.libs.json.JsObject
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
//import play.modules.reactivemongo.ReactiveMongoComponent
//import reactivemongo.api.commands.WriteResult
//import reactivemongo.api.{ Cursor, DB, ReadPreference }
//import reactivemongo.bson.{ BSONDocument, BSONObjectID }
//import reactivemongo.play.json.ImplicitBSONHandlers._
//import uk.gov.hmrc.mongo.ReactiveRepository
//import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MediaRepository {
  def create(addMedia: Media): Future[Unit]
  def find(userId: String): Future[Option[Media]]
  def findAll(): Future[Map[String, Media]]
  def cloneAndArchive(originalUserId: String, userIdToArchiveWith: String): Future[Unit]
  def removeMedia(userId: String): Future[Unit]
}

@Singleton
class MediaMongoRepository @Inject() (mongoComponent: MongoComponent)
  extends PlayMongoRepository[Media](
    collectionName = CollectionNames.MEDIA,
    mongoComponent = mongoComponent,
    domainFormat = mediaFormat,
    indexes = Nil
  ) with MediaRepository with BaseBSONReader with ReactiveRepositoryHelpers {

  /*
  override def create(addMedia: Media): Future[Unit] = insert(addMedia).map { _ => ()
  } recover {
    case _: WriteResult => throw CannotAddMedia(addMedia.userId)
  }*/
  override def create(addMedia: Media): Future[Unit] = collection.insertOne(addMedia).toFuture().map { _ => ()
  } recover {
    case _: MongoException => throw CannotAddMedia(addMedia.userId)
  }

  /*
  override def find(userId: String): Future[Option[Media]] = {
    val query = BSONDocument("userId" -> userId)

    bsonCollection.find(query, projection = Option.empty[JsObject]).one[Media]
  }*/
  override def find(userId: String): Future[Option[Media]] = {
    val query = Document("userId" -> userId)
    collection.find(query).first().toFutureOption()
  }

/*
  override def findAll(): Future[Map[String, Media]] = {
    val query = BSONDocument.empty
    implicit val reader = bsonReader(docToMedia)
    val queryResult = bsonCollection.find(query, projection = Option.empty[JsObject])
      .cursor[(String, Media)](ReadPreference.nearest).collect[List](maxDocs = -1, Cursor.FailOnError[List[(String, Media)]]())
    queryResult.map(_.toMap)
  }*/
  //TODO: mongo
  override def findAll(): Future[Map[String, Media]] = ???

  private def docToMedia(document: BsonDocument): (String, Media) = {
    val userId = document.getString("userId").getValue
    val media = document.getString("media").getValue
    userId -> Media(userId, media)
  }

  override def cloneAndArchive(originalUserId: String, userIdToArchiveWith: String): Future[Unit] = {
    find(originalUserId).flatMap {
      case Some(media) => create(media.copy(userId = userIdToArchiveWith, originalUserId = Some(originalUserId)))
      case None => Future.successful(())
    }
  }

  override def removeMedia(userId: String): Future[Unit] = {
    val query = Document("userId" -> userId)
    collection.deleteOne(query).toFuture().map(_ => ())
  }

  /*
  private def docToMedia(document: BSONDocument): (String, Media) = {
    val userId = document.getAs[String]("userId").get
    val media = document.getAs[String]("media").get

    (userId, Media(userId, media))
  }*/
}
