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

import model.PersistedObjects
import model.PersistedObjects.{ DiversityReport, DiversityReportRow }
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{ JsNumber, JsObject }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ReportingRepository {

  def finalizeReportStatus(timeStamp: DateTime): Future[Unit]

  def update(location: String, timeStamp: DateTime, data: DiversityReportRow): Future[Unit]

  def findLatest(): Future[Option[DiversityReport]]

}

class ReportingMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[DiversityReport, BSONObjectID]("diversity_reporting", mongo,
    PersistedObjects.Implicits.diversityReportFormats, ReactiveMongoFormats.objectIdFormats) with ReportingRepository {

  override def update(location: String, timeStamp: DateTime, data: DiversityReportRow): Future[Unit] = {

    val query = BSONDocument("timeStamp" -> timeStamp)
    val dataBson = BSONDocument("$addToSet" -> BSONDocument(
      "data" -> data
    ))

    collection.update(query, dataBson, upsert = true) map {
      case r if r.errmsg.isDefined =>
        Logger.error(s"${r.errmsg.get}"); ()
      case _ => ()
    }
  }

  override def findLatest(): Future[Option[DiversityReport]] = {
    //db.reporting.find({}).sort({"timeStamp": -1}).limit(1)
    val query = BSONDocument("status" -> "COMPLETED")
    val sort = new JsObject(Seq("timeStamp" -> JsNumber(-1)))
    collection.find(query).sort(sort).one[BSONDocument].map {
      case Some(document) =>
        val timeStamp = document.getAs[DateTime]("timeStamp").get
        val dataRow = document.getAs[List[DiversityReportRow]]("data").get

        Some(DiversityReport(timeStamp, dataRow))

      case _ => None
    }
  }

  override def finalizeReportStatus(timeStamp: DateTime): Future[Unit] = {

    val query = BSONDocument("timeStamp" -> timeStamp)
    val dataBson = BSONDocument("$set" -> BSONDocument(
      "status" -> "COMPLETED"
    ))

    collection.update(query, dataBson, upsert = true) map {
      case _ => ()
    }
  }
}
