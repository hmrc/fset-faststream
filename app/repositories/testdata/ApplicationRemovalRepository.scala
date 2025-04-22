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

package repositories.testdata

import model.CreateApplicationRequest
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture}
import play.api.Logging
import repositories.{CollectionNames, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait ApplicationRemovalRepository {
  def remove(applicationStatus: Option[String]): Future[Seq[String]]
}

@Singleton
class ApplicationRemovalMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[CreateApplicationRequest](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongoComponent,
    domainFormat = CreateApplicationRequest.createApplicationRequestFormat,
    indexes = Nil
  ) with ApplicationRemovalRepository with ReactiveRepositoryHelpers with Logging {

  val applicationCollection: MongoCollection[Document] = mongoComponent.database.getCollection(collectionName)

  def remove(applicationStatus: Option[String]): Future[Seq[String]] = {
    val query = applicationStatus.map(as => Document("applicationStatus" -> as)).getOrElse(Document.empty)
    val projection = Projections.include("userId")

    applicationCollection.find(query).projection(projection).toFuture().map { docList =>
      docList.map { doc => doc.get("userId").get.asString().getValue }
    }.map { userIds =>
      collection.deleteMany(query).toFuture().map(_.getDeletedCount).map { deletedCount =>
        logger.debug(s"Deleted $deletedCount document(s) where applicationStatus=$applicationStatus")
      }
      userIds
    }
  }
}
