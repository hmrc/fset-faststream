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

import org.joda.time.{DateTime, Duration}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import org.mongodb.scala.{MongoCollection, MongoException}
import play.api.Logging
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Lock(_id: String, owner: String, timeCreated: DateTime, expiryTime: DateTime)

object Lock {
  import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits.jotDateTimeFormat
  implicit val lockFormat: OFormat[Lock] = Json.format[Lock]
}

object LockFormats {
  val id = "_id"
  val owner = "owner"
  val timeCreated = "timeCreated"
  val expiryTime = "expiryTime"
}

trait LockRepository {
  def lock(reqLockId: String, reqOwner: String, forceReleaseAfter: Duration): Future[Boolean]
  def isLocked(reqLockId: String, reqOwner: String): Future[Boolean]
  def releaseLock(reqLockId: String, reqOwner: String): Future[Unit]
}

@Singleton
class LockMongoRepository @Inject() (mongoComponent: MongoComponent)
  extends PlayMongoRepository[Lock](
    collectionName = CollectionNames.LOCKS,
    mongoComponent = mongoComponent,
    domainFormat = Lock.lockFormat,
    indexes = Seq(
      IndexModel(ascending("owner"), IndexOptions().unique(false)),
      IndexModel(ascending("timeCreated"), IndexOptions().unique(false)),
      IndexModel(ascending("expiryTime"), IndexOptions().unique(false))
    )
  ) with LockRepository with Logging with CurrentTime {
  private val DuplicateKeyErrorCode = 11000

  import LockFormats._

  // Use this collection when using hand written bson documents
  val lockCollection: MongoCollection[Document] = mongoComponent.database.getCollection(CollectionNames.LOCKS)

  override def lock(reqLockId: String, reqOwner: String, forceReleaseAfter: Duration): Future[Boolean] = withCurrentTime { now =>
    val filter = Document(
      id -> reqLockId,
      expiryTime -> Document("$lte" -> dateTimeToBson(now))
    )

    collection.deleteOne(filter).toFuture().flatMap { writeResult =>
      if (writeResult.getDeletedCount != 0) {
        logger.info(s"Removed ${writeResult.getDeletedCount} expired locks for $reqLockId")
      }

      val expiryDateTime = now.plus(forceReleaseAfter)
      val lockBson = Document(
        id -> reqLockId,
        owner -> reqOwner,
        timeCreated -> dateTimeToBson(now),
        expiryTime -> dateTimeToBson(expiryDateTime)
      )
      lockCollection.insertOne(lockBson).toFuture()
        .map { _ =>
          logger.debug(s"Took lock '$reqLockId' for '$reqOwner' at $now.  Expires at: $expiryDateTime")
          true
        }
        .recover {
          case e: MongoException if e.getCode == DuplicateKeyErrorCode =>
            logger.debug(s"Unable to take lock '$reqLockId' for '$reqOwner'")
            false
        }
    }
  }

  def isLocked(reqLockId: String, reqOwner: String): Future[Boolean] = withCurrentTime { now =>
    val filter = Document(
      id -> reqLockId,
      owner -> reqOwner,
      expiryTime -> Document("$gt" -> dateTimeToBson(now))
    )
    collection.find(filter).headOption().map( _.isDefined )
  }

  def releaseLock(reqLockId: String, reqOwner: String): Future[Unit] = {
    logger.debug(s"Releasing lock '$reqLockId' for '$reqOwner'")
    collection.deleteOne(Document(id -> reqLockId, owner -> reqOwner)).toFuture().map(_ => ())
  }
}

// Copied from simple-reactivemongo_2.12-8.0.0-play-28.jar
// uk.gov.hmrc.mongo.CurrentTime when migrating from simple-reactivemongo to scala mongo driver
import org.joda.time.DateTimeZone

trait CurrentTime {
  // Invoke the passed function f with DateTime.now instant
  def withCurrentTime[A](f: DateTime => A) = f(DateTime.now.withZone(DateTimeZone.UTC))
}
