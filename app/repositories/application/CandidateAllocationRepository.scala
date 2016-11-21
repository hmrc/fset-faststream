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

package repositories.application

import factories.DateTimeFactory
import model.PersistedObjects.{ AllocatedCandidate, PersonalDetailsWithUserId }
import model.PersistedObjects
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.api.DB
import model.ApplicationStatus._
import reactivemongo.bson.{ BSONArray, BSONBoolean, BSONDocument, BSONObjectID }
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CandidateAllocationRepository {

  def nextUnconfirmedCandidateToSendReminder(daysBeforeExpiration: Int): Future[Option[AllocatedCandidate]]

  def saveAllocationReminderSentDate(applicationId: String, date: DateTime): Future[Unit]
}

class CandidateAllocationMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[AllocatedCandidate, BSONObjectID]("application", mongo,
    PersistedObjects.Implicits.allocatedCandidateFormats) with CandidateAllocationRepository
    with RandomSelection with BSONHelpers with BaseBSONReader {

  def nextUnconfirmedCandidateToSendReminder(daysBeforeExpiration: Int): Future[Option[AllocatedCandidate]] = {
    val now = dateTime.nowLocalDate
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ALLOCATION_UNCONFIRMED),
      BSONDocument("allocation-reminder-sent-date" -> BSONDocument("$exists" -> BSONBoolean(false))),
      BSONDocument("allocation-expire-date" -> BSONDocument("$gte" -> now)),
      BSONDocument("allocation-expire-date" -> BSONDocument("$lte" -> now.plusDays(daysBeforeExpiration)))
    ))

    implicit val reader = bsonReader(bsonToAllocatedCandidate)
    selectOneRandom[AllocatedCandidate](query)
  }

  def saveAllocationReminderSentDate(applicationId: String, date: DateTime): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val result = BSONDocument("$set" -> BSONDocument(
      "allocation-reminder-sent-date" -> date
    ))

    collection.update(query, result, upsert = false) map {
       _ => ()
    }
  }

  private def bsonToAllocatedCandidate(doc: BSONDocument) = {
    val userId = doc.getAs[String]("userId").get
    val applicationId = doc.getAs[String]("applicationId").get
    val expireDate = doc.getAs[LocalDate]("allocation-expire-date").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    AllocatedCandidate(PersonalDetailsWithUserId(preferredName, userId), applicationId, expireDate)
  }
}
