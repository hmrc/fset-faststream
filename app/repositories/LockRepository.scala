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

package repositories

import javax.inject.{ Inject, Singleton }
import org.joda.time.{ DateTime, Duration }
import play.api.libs.json.{ Format, JsObject, JsValue, Json }
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

case class Lock(id: String, owner: String, timeCreated: DateTime, expiryTime: DateTime)

object LockFormats {
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val format = ReactiveMongoFormats.mongoEntity({
    Format(Json.reads[Lock], Json.writes[Lock])
  })

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
class LockMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Lock, String](
    CollectionNames.LOCKS,
    mongoComponent.mongoConnector.db,
    LockFormats.format,
    implicitly[Format[String]]) with LockRepository {
  private val DuplicateKey = 11000

  import LockFormats._

  override def indexes: Seq[Index] = Seq(
    Index(Seq((owner, Ascending)), unique = false),
    Index(Seq((timeCreated, Ascending)), unique = false),
    Index(Seq((expiryTime, Ascending)), unique = false)
  )

  def lock(reqLockId: String, reqOwner: String, forceReleaseAfter: Duration): Future[Boolean] = withCurrentTime { now =>
    collection.delete().one(Json.obj(id -> reqLockId, expiryTime -> Json.obj("$lte" -> now))).flatMap { writeResult =>
      if (writeResult.n != 0) {
        logger.info(s"Removed ${writeResult.n} expired locks for $reqLockId")
      }

      collection.insert(ordered = false).one(
        Json.obj(id -> reqLockId, owner -> reqOwner, timeCreated -> now, expiryTime -> now.plus(forceReleaseAfter)))
        .map { _ =>
          logger.debug(s"Took lock '$reqLockId' for '$reqOwner' at $now.  Expires at: ${now.plus(forceReleaseAfter)}")
          true
        }
        .recover {
          case e: DatabaseException if e.code.contains(DuplicateKey) =>
            logger.debug(s"Unable to take lock '$reqLockId' for '$reqOwner'")
            false
        }
    }
  }

  def isLocked(reqLockId: String, reqOwner: String): Future[Boolean] = withCurrentTime { now =>
    collection.find(
      Json.obj(id -> reqLockId, owner -> reqOwner, expiryTime -> Json.obj("$gt" -> now)),
      projection = Option.empty[JsObject])
      .one[JsValue].map(_.isDefined)
  }

  def releaseLock(reqLockId: String, reqOwner: String): Future[Unit] = {
    logger.debug(s"Releasing lock '$reqLockId' for '$reqOwner'")
    collection.delete().one(Json.obj(id -> reqLockId, owner -> reqOwner)).map(_ => ())
  }
}
