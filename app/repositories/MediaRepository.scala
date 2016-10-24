/*
 * Copyright 2016 HM Revenue & Customs
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

import model.Exceptions.CannotAddMedia
import model.persisted.Media
import model.persisted.Media._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MediaRepository {
  def create(addMedia: Media): Future[Unit]

  def findAll(): Future[Map[String, Media]]
}

class MediaMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Media, BSONObjectID]("media", mongo,
    mediaFormat, ReactiveMongoFormats.objectIdFormats) with MediaRepository {

  // Use the BSON collection instead of in the inbuilt JSONCollection when performance matters
  lazy val bsonCollection = mongo().collection[BSONCollection](this.collection.name)

  override def create(addMedia: Media): Future[Unit] = insert(addMedia).map { _ => ()
  } recover {
    case e: WriteResult => throw new CannotAddMedia(addMedia.userId)
  }

  override def findAll(): Future[Map[String, Media]] = {
    val query = BSONDocument()
    implicit val reader = bsonReader(docToMedia)
    val queryResult = bsonCollection.find(query)
      .cursor[(String, Media)](ReadPreference.nearest).collect[List]()
    queryResult.map(_.toMap)
  }

  private def docToMedia(document: BSONDocument): (String, Media) = {
    val userId = document.getAs[String]("userId").get
    val media = document.getAs[String]("media").get

    (userId, Media(userId, media))
  }

  private def bsonReader[T](f: BSONDocument => T): BSONDocumentReader[T] = {
    new BSONDocumentReader[T] {
      def read(bson: BSONDocument) = f(bson)
    }
  }
}
