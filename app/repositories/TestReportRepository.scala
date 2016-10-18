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

import model.report.{TestResultForOnlineTestPassMarkReportItem, TestResultForOnlineTestPassMarkReportItem$}
import model.OnlineTestCommands.TestResult
import model.PersistedObjects.CandidateTestReport
import model.PersistedObjects.Implicits._
import play.api.libs.json.Format
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONDouble, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TestReportRepository {
  def saveOnlineTestReport(report: CandidateTestReport): Future[Unit]

  def getOnlineTestReports: Future[Map[String, TestResultForOnlineTestPassMarkReportItem]]

  def getReportByApplicationId(applicationId: String): Future[Option[CandidateTestReport]]

  def remove(applicationId: String): Future[Unit]
}

class TestReportMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CandidateTestReport, BSONObjectID]("online-test-report", mongo,
    candidateTestReportFormats, ReactiveMongoFormats.objectIdFormats) with TestReportRepository {

  def saveOnlineTestReport(report: CandidateTestReport): Future[Unit] = {
    val query = BSONDocument("applicationId" -> report.applicationId)
    collection.update(query, report, upsert = true) map (_ => Unit)
  }

  override def getReportByApplicationId(applicationId: String): Future[Option[CandidateTestReport]] = {

    val query = BSONDocument("applicationId" -> applicationId)

    collection.find(query).one[BSONDocument].map {
      case Some(doc) =>
        val appId = doc.getAs[String]("applicationId").getOrElse("")
        val repType = doc.getAs[String]("reportType").getOrElse("")
        val numerical = getTest(doc.getAs[BSONDocument]("numerical"))
        val verbal = getTest(doc.getAs[BSONDocument]("verbal"))
        val competency = getTest(doc.getAs[BSONDocument]("competency"))
        val situational = getTest(doc.getAs[BSONDocument]("situational"))

        Some(CandidateTestReport(appId, repType, competency, numerical, verbal, situational))
      case _ => None
    }
  }

  private def getTest(document: Option[BSONDocument]): Option[TestResult] = {
    document match {
      case Some(doc) =>
        //Since the creation is tupled, the order of the elements in the tuple matters
        Some(
          TestResult(
            doc.getAs[String]("status").get,
            doc.getAs[String]("norm").get,
            doc.getAs[Double]("tScore"),
            doc.getAs[Double]("percentile"),
            doc.getAs[Double]("raw"),
            doc.getAs[Double]("sten")
          )
        )
      case _ => None
    }
  }

  def getOnlineTestReports: Future[Map[String, TestResultForOnlineTestPassMarkReportItem]] = {
    val query = BSONDocument()

    val projection = BSONDocument(
      "applicationId" -> "1",
      "competency.tScore" -> "1",
      "competency.percentile" -> "1",
      "competency.raw" -> "1",
      "competency.sten" -> "1",

      "numerical.tScore" -> "1",
      "numerical.percentile" -> "1",
      "numerical.raw" -> "1",
      "numerical.sten" -> "1",

      "verbal.tScore" -> "1",
      "verbal.percentile" -> "1",
      "verbal.raw" -> "1",
      "verbal.sten" -> "1",

      "situational.tScore" -> "1",
      "situational.percentile" -> "1",
      "situational.raw" -> "1",
      "situational.sten" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection) map { lst =>
      lst.map(docToReport).toMap
    }
  }

  private def docToReport(document: BSONDocument): (String, TestResultForOnlineTestPassMarkReportItem) = {
    def getTest(testName: String): Option[TestResult] = {
      val test = document.getAs[BSONDocument](testName)
      test.map { t =>
        TestResult(
          "N/A",
          "N/A",
          t.getAs[BSONDouble]("tScore").map(_.value),
          t.getAs[BSONDouble]("percentile").map(_.value),
          t.getAs[BSONDouble]("raw").map(_.value),
          t.getAs[BSONDouble]("sten").map(_.value)
        )
      }
    }

    val applicationId = document.getAs[String]("applicationId").get

    (applicationId, TestResultForOnlineTestPassMarkReportItem(
      getTest("behavioural"),
      getTest("situational")
    ))
  }

  private def reportQueryWithProjections[A](
    query: BSONDocument,
    prj: BSONDocument,
    upTo: Int = Int.MaxValue,
    stopOnError: Boolean = true
  )(implicit reader: Format[A]): Future[List[A]] =
    collection.find(query).projection(prj).cursor[A](ReadPreference.nearest).collect[List](upTo, stopOnError)

  def remove(applicationId: String) = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.remove(query, firstMatchOnly = false).map { writeResult => () }
  }

}
