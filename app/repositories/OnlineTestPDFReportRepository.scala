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

import model.PersistedObjects.Implicits._
import model.PersistedObjects.OnlineTestPDFReport
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONBinary, BSONDocument, BSONObjectID, Subtype }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestPDFReportRepository {
  def get(applicationId: String): Future[Option[Array[Byte]]]

  def hasReport(applicationId: String): Future[Boolean]

  def save(applicationId: String, content: Array[Byte]): Future[Unit]

  def remove(applicationId: String): Future[Unit]
}

class OnlineTestPDFReportMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[OnlineTestPDFReport, BSONObjectID]("online-test-pdf-report", mongo,
    onlineTestPdfReportFormats, ReactiveMongoFormats.objectIdFormats) with OnlineTestPDFReportRepository with RandomSelection {

  override def hasReport(applicationId: String): Future[Boolean] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.find(query).cursor[BSONDocument]().collect[List]().map(_.length == 1)
  }

  override def get(applicationId: String): Future[Option[Array[Byte]]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.find(query).one[BSONDocument].map {
      case Some(doc) => Some(doc.getAs[BSONBinary]("content").get.byteArray)
      case _ => None
    }
  }

  override def save(applicationId: String, content: Array[Byte]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val doc = BSONDocument(
      "applicationId" -> applicationId,
      "content" -> BSONBinary(content, Subtype.GenericBinarySubtype)
    )
    collection.update(query, doc, upsert = true) map (_ => Unit)
  }

  def remove(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.remove(query, firstMatchOnly = false).map { writeResult => () }
  }
}
