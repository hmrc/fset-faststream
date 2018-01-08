/*
 * Copyright 2018 HM Revenue & Customs
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

import model.ApplicationRoute.{ apply => _ }
import model.Exceptions.CannotAddMedia
import model.persisted.Media
import model.persisted.Media._
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MediaRepository {
  def create(addMedia: Media): Future[Unit]

  def find(userId: String): Future[Option[Media]]

  def findAll(): Future[Map[String, Media]]

  def cloneAndArchive(originalUserId: String, userIdToArchiveWith: String): Future[Unit]
}

class MediaMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Media, BSONObjectID](CollectionNames.MEDIA, mongo,
    mediaFormat, ReactiveMongoFormats.objectIdFormats) with MediaRepository with BaseBSONReader with ReactiveRepositoryHelpers {

  override def create(addMedia: Media): Future[Unit] = insert(addMedia).map { _ => ()
  } recover {
    case _: WriteResult => throw CannotAddMedia(addMedia.userId)
  }

  override def find(userId: String): Future[Option[Media]] = {
    val query = BSONDocument("userId" -> userId)

    bsonCollection.find(query).one[Media]
  }

  override def findAll(): Future[Map[String, Media]] = {
    val query = BSONDocument.empty
    implicit val reader = bsonReader(docToMedia)
    val queryResult = bsonCollection.find(query)
      .cursor[(String, Media)](ReadPreference.nearest).collect[List]()
    queryResult.map(_.toMap)
  }

  override def cloneAndArchive(originalUserId: String, userIdToArchiveWith: String): Future[Unit] = {
    find(originalUserId).flatMap {
      case Some(media) => create(media.copy(userId = userIdToArchiveWith, originalUserId = Some(originalUserId)))
      case None => Future.successful(())
    }
  }

  private def docToMedia(document: BSONDocument): (String, Media) = {
    val userId = document.getAs[String]("userId").get
    val media = document.getAs[String]("media").get

    (userId, Media(userId, media))
  }

}
