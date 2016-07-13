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

import model.Commands
import model.Commands.ApplicationAssessment
import model.Exceptions.{ NotFoundException, TooManyEntries }
import org.joda.time.LocalDate
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ApplicationAssessmentRepository {
  def find(applicationId: String): Future[ApplicationAssessment]
  def applicationAssessment(applicationId: String): Future[Option[ApplicationAssessment]]
  def applicationAssessments: Future[List[ApplicationAssessment]]
  def applicationAssessmentsForVenue(venue: String): Future[List[ApplicationAssessment]]
  def create(applications: List[ApplicationAssessment]): Future[Seq[ApplicationAssessment]]
  def applicationAssessments(venue: String, date: LocalDate): Future[List[ApplicationAssessment]]
  def confirmAllocation(applicationId: String): Future[Unit]
  def delete(applicationId: String): Future[Unit]
}

class ApplicationAssessmentMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationAssessment, BSONObjectID]("application-assessment", mongo,
    Commands.Implicits.applicationAssessmentFormat, ReactiveMongoFormats.objectIdFormats) with ApplicationAssessmentRepository {

  def find(applicationId: String): Future[ApplicationAssessment] = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )

    collection.find(query).one[BSONDocument] map {
      case Some(applicationAssessment) => parseApplicationAssessment(applicationAssessment)
      case _ => throw new NotFoundException(s"Application assessment not found for id $applicationId")
    }
  }

  def applicationAssessment(applicationId: String): Future[Option[ApplicationAssessment]] = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )

    collection.find(query).one[BSONDocument] map {
      case Some(applicationAssessment) => Some(parseApplicationAssessment(applicationAssessment))
      case _ => None
    }
  }

  def applicationAssessments: Future[List[ApplicationAssessment]] = {
    val query = BSONDocument()

    getApplicationAssessments(query)
  }

  def applicationAssessments(venue: String, date: LocalDate): Future[List[ApplicationAssessment]] = {
    val query = BSONDocument(
      "venue" -> venue,
      "date" -> date
    )

    getApplicationAssessments(query)
  }

  def delete(applicationId: String): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )

    collection.remove(query, firstMatchOnly = false).map { writeResult =>
      if (writeResult.n == 0) {
        throw new NotFoundException(s"No application assessments were found with applicationId $applicationId")
      } else if (writeResult.n > 1) {
        throw new TooManyEntries(s"Deletion successful, but too many application assessments matched for applicationId $applicationId.")
      } else {
        ()
      }
    }
  }

  private def getApplicationAssessments(query: BSONDocument) = {
    collection.find(query).cursor[BSONDocument]().collect[List]().map {
      _.map(parseApplicationAssessment)
    }
  }

  def applicationAssessmentsForVenue(venue: String): Future[List[ApplicationAssessment]] = {
    val query = BSONDocument("venue" -> venue)

    collection.find(query).cursor[BSONDocument]().collect[List]().map {
      _.map(parseApplicationAssessment)
    }
  }

  def create(applications: List[ApplicationAssessment]): Future[Seq[ApplicationAssessment]] = {
    val applicationsBSON = applications.map { app =>
      BSONDocument(
        "applicationId" -> app.applicationId,
        "venue" -> app.venue,
        "date" -> app.date,
        "session" -> app.session,
        "slot" -> app.slot,
        "confirmed" -> app.confirmed
      )
    }

    val bulkDocs = applicationsBSON.map(implicitly[collection.ImplicitlyDocumentProducer](_))

    val errors = collection.bulkInsert(ordered = false)(bulkDocs: _*) map {
      result => result.writeErrors.map(r => applications(r.index))
    }

    errors
  }

  def confirmAllocation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val confirmedBSON = BSONDocument("$set" ->
      BSONDocument(
        "confirmed" -> true
      ))

    collection.update(query, confirmedBSON, upsert = false) map {
      case _ => ()
    }
  }

  private def parseApplicationAssessment(item: BSONDocument): ApplicationAssessment = {
    ApplicationAssessment(
      item.getAs[String]("applicationId").get,
      item.getAs[String]("venue").get,
      item.getAs[LocalDate]("date").get,
      item.getAs[String]("session").get,
      item.getAs[Int]("slot").get,
      item.getAs[Boolean]("confirmed").get
    )
  }
}
