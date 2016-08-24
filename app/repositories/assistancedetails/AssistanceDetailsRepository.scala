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

package repositories.assistancedetails

import model.Exceptions.{ AssistanceDetailsNotFound, CannotUpdateAssistanceDetails }
import model.persisted.AssistanceDetails
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssistanceDetailsRepository {
  def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit]

  def find(applicationId: String): Future[AssistanceDetails]
}

class AssistanceDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[AssistanceDetails, BSONObjectID]("application", mongo,
    AssistanceDetails.assistanceDetailsFormat, ReactiveMongoFormats.objectIdFormats) with AssistanceDetailsRepository {
  val AssistanceDetailsCollection = "assistance-details"

  override def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId, "userId" -> userId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.assistance-details" -> true,
      AssistanceDetailsCollection -> ad
    ))

    collection.update(query, updateBSON, upsert = true) map {
      case result if result.nModified == 0 && result.n == 0 =>
        logger.error(s"""Failed to write assistance details for user: $userId -> ${result.writeConcernError.map(_.errmsg).mkString(",")}""")
        throw CannotUpdateAssistanceDetails(userId)
      case _ => ()
    }
  }

  override def find(applicationId: String): Future[AssistanceDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(AssistanceDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](AssistanceDetailsCollection).isDefined =>
        document.getAs[AssistanceDetails](AssistanceDetailsCollection).get
      case _ => throw AssistanceDetailsNotFound(applicationId)
    }
  }

}
