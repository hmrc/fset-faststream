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

package repositories.fsacindicator

import javax.inject.{ Inject, Singleton }
import model.Exceptions.{ CannotUpdateFSACIndicator, FSACIndicatorNotFound }
import model.persisted.FSACIndicator
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FSACIndicatorRepository {
  def update(applicationId: String, userId: String, indicator: FSACIndicator): Future[Unit]

  def find(applicationId: String): Future[FSACIndicator]
}

@Singleton
class FSACIndicatorMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[FSACIndicator, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    FSACIndicator.jsonFormat,
    ReactiveMongoFormats.objectIdFormats) with FSACIndicatorRepository with ReactiveRepositoryHelpers {

  val FSACIndicatorDocumentKey = "fsac-indicator"

  override def find(applicationId: String): Future[FSACIndicator] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(FSACIndicatorDocumentKey -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](FSACIndicatorDocumentKey).isDefined =>
        document.getAs[FSACIndicator](FSACIndicatorDocumentKey).get
      case _ => throw FSACIndicatorNotFound(applicationId)
    }
  }

  override def update(applicationId: String, userId: String, indicator: FSACIndicator): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId, "userId" -> userId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      FSACIndicatorDocumentKey -> indicator
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating fsac indicator",
      CannotUpdateFSACIndicator(userId))

    collection.update(ordered = false).one(query, updateBSON) map validator
  }
}
