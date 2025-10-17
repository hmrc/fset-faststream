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

package repositories

import model.ApplicationRoute.apply as _
import model.Exceptions.CannotAddMedia
import model.persisted.Media
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import org.mongodb.scala.{MongoException, ObservableFuture, SingleObservableFuture}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait MediaRepository {
  def create(addMedia: Media): Future[Unit]
  def find(userId: String): Future[Option[Media]]
  def findAll(): Future[Map[String, Media]]
  def cloneAndArchive(originalUserId: String, userIdToArchiveWith: String): Future[Unit]
  def removeMedia(userId: String): Future[Unit]
}

@Singleton
class MediaMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Media](
    collectionName = CollectionNames.MEDIA,
    mongoComponent = mongoComponent,
    domainFormat = Media.mongoFormat,
    indexes = Seq(
      IndexModel(ascending("userId"), IndexOptions().unique(true))
    )
  ) with MediaRepository with BaseBSONReader with ReactiveRepositoryHelpers {

  override def create(addMedia: Media): Future[Unit] = collection.insertOne(addMedia).toFuture().map { _ => ()
  } recover {
    case _: MongoException => throw CannotAddMedia(addMedia.userId)
  }

  override def find(userId: String): Future[Option[Media]] = {
    val query = Document("userId" -> userId)
    collection.find(query).headOption()
  }

  override def findAll(): Future[Map[String, Media]] = {
    val query = Document.empty
    val result = collection.find(query).toFuture()

    result.map ( _.map ( data => data.userId -> data ).toMap )
  }

  override def cloneAndArchive(originalUserId: String, userIdToArchiveWith: String): Future[Unit] = {
    find(originalUserId).flatMap {
      case Some(media) => create(media.copy(userId = userIdToArchiveWith, originalUserId = Some(originalUserId)))
      case None => Future.successful(())
    }
  }

  override def removeMedia(userId: String): Future[Unit] = {
    val query = Document("userId" -> userId)
    val validator = singleRemovalValidator(userId, actionDesc = s"removing media for candidate $userId")
    collection.deleteOne(query).toFuture() map validator
  }
}
