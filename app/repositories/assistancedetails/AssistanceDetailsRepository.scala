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

package repositories.assistancedetails

import javax.inject.{ Inject, Singleton }
import model.Exceptions.{ AssistanceDetailsNotFound, CannotUpdateAssistanceDetails }
import model.persisted.AssistanceDetails
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssistanceDetailsRepository {
  def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit]
  def find(applicationId: String): Future[AssistanceDetails]
}

@Singleton
class AssistanceDetailsMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[AssistanceDetails, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    AssistanceDetails.assistanceDetailsFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with AssistanceDetailsRepository with ReactiveRepositoryHelpers {

  val AssistanceDetailsDocumentKey = "assistance-details"

  override def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId, "userId" -> userId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.assistance-details" -> true,
      AssistanceDetailsDocumentKey -> ad
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating assistance details",
      CannotUpdateAssistanceDetails(userId))

    collection.update(ordered = false).one(query, updateBSON) map validator
  }

  override def find(applicationId: String): Future[AssistanceDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(AssistanceDetailsDocumentKey -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](AssistanceDetailsDocumentKey).isDefined =>
        document.getAs[AssistanceDetails](AssistanceDetailsDocumentKey).get
      case _ => throw AssistanceDetailsNotFound(s"AssistanceDetails not found for $applicationId")
    }
  }
}
