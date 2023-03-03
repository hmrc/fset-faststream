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

package repositories.fsacindicator

import com.mongodb.client.model.Projections

import javax.inject.{Inject, Singleton}
import model.Exceptions.{CannotUpdateFSACIndicator, FSACIndicatorNotFound}
import model.persisted.FSACIndicator
import org.mongodb.scala.bson.collection.immutable.Document
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import repositories.{CollectionNames, ReactiveRepositoryHelpers}

import scala.concurrent.{ExecutionContext, Future}

trait FSACIndicatorRepository {
  def update(applicationId: String, userId: String, indicator: FSACIndicator): Future[Unit]
  def find(applicationId: String): Future[FSACIndicator]
}

@Singleton
class FSACIndicatorMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[FSACIndicator](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = FSACIndicator.mongoFormat,
    indexes = Nil
  ) with FSACIndicatorRepository with ReactiveRepositoryHelpers {

  val FSACIndicatorDocumentKey = "fsac-indicator"

  override def find(applicationId: String): Future[FSACIndicator] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include(FSACIndicatorDocumentKey) // This is the sub-document key

    for {
      fsacIndicatorOpt <- collection.find(query).projection(projection).headOption()
    } yield {
      fsacIndicatorOpt match {
        case Some(fsac) => fsac
        case _ => throw FSACIndicatorNotFound(applicationId)
      }
    }
  }

  override def update(applicationId: String, userId: String, indicator: FSACIndicator): Future[Unit] = {
    val query = Document("applicationId" -> applicationId, "userId" -> userId)
    val update = Document("$set" -> Document(
      FSACIndicatorDocumentKey -> Codecs.toBson(indicator)
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating fsac indicator",
      CannotUpdateFSACIndicator(userId))

    collection.updateOne(query, update).toFuture map validator
  }
}
