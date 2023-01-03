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

package repositories.assistancedetails

import javax.inject.{Inject, Singleton}
import model.Exceptions.{AssistanceDetailsNotFound, CannotUpdateAssistanceDetails}
import model.persisted.AssistanceDetails
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Projections}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssistanceDetailsRepository {
  def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit]
  def find(applicationId: String): Future[AssistanceDetails]
}

@Singleton
class AssistanceDetailsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[AssistanceDetails](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = AssistanceDetails.mongoFormat,
    indexes = Nil
  ) with AssistanceDetailsRepository with ReactiveRepositoryHelpers {

  val AssistanceDetailsDocumentKey = "assistance-details"

  override def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit] = {
    val query = Document("applicationId" -> applicationId, "userId" -> userId)
    val updateBSON = Document("$set" -> Document(
      "progress-status.assistance-details" -> true,
      AssistanceDetailsDocumentKey -> Codecs.toBson(ad)
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating assistance details",
      CannotUpdateAssistanceDetails(userId))

    collection.updateOne(query, updateBSON).toFuture map validator
  }

  override def find(applicationId: String): Future[AssistanceDetails] = {
    val query = Filters.and(
      Filters.equal("applicationId", applicationId),
      Filters.exists(AssistanceDetailsDocumentKey)
    )
    val projection = Projections.include(AssistanceDetailsDocumentKey)

    collection.find(query).projection(projection).headOption() map {
      case Some(ad) => ad
      case _ => throw AssistanceDetailsNotFound(s"AssistanceDetails not found for $applicationId")
    }
  }
}
