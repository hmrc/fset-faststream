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

import model.Exceptions.{ CannotUpdateFastPassDetails, FastPassDetailsNotFound }
import model.FastPassDetails
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait FastPassDetailsRepository {

  val FastPassDetailsDocumentKey = "fastpass-details"

  def update(applicationId: String, fastPassDetails: FastPassDetails): Future[Unit]

  def find(applicationId: String): Future[FastPassDetails]

}

class FastPassDetailsMongoRepository(implicit mongo: () => DB) extends
  ReactiveRepository[FastPassDetails, BSONObjectID]("application", mongo, FastPassDetails.fastPassDetailsFormat,
    ReactiveMongoFormats.objectIdFormats) with FastPassDetailsRepository {

  override def update(applicationId: String, fastPassDetails: FastPassDetails): Future[Unit] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      FastPassDetailsDocumentKey -> fastPassDetails
    ))

    collection.update(query, updateBSON, upsert = false) map {
      case result if result.nModified == 0 && result.n == 0 =>
        logger.error(
          s"""Failed to write fast pass details for application Id: $applicationId ->
              |${result.writeConcernError.map(_.errmsg).mkString(",")}""".stripMargin)
        throw CannotUpdateFastPassDetails(applicationId)
      case _ => ()
    }

  }

  override def find(applicationId: String): Future[FastPassDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(FastPassDetailsDocumentKey -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](FastPassDetailsDocumentKey).isDefined =>
        document.getAs[FastPassDetails](FastPassDetailsDocumentKey).get
      case _ => throw FastPassDetailsNotFound(applicationId)
    }
  }
}
