/*
 * Copyright 2017 HM Revenue & Customs
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

import model.Exceptions.{ TooManyEventIdsException, TooManySessionIdsException }
import model.persisted.CandidateAllocation
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.Session
import play.api.libs.json.OFormat
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CandidateAllocationRepository {
  def save(allocations: Seq[CandidateAllocation]): Future[Unit]
  def allocationsForSession(eventId: String, sessionId: String): Future[Seq[CandidateAllocation]]
  def allocationsForApplication(applicationId: String): Future[Seq[CandidateAllocation]]
  def removeCandidateAllocation(allocation: CandidateAllocation): Future[Unit]
  def delete(allocations: Seq[CandidateAllocation]): Future[Unit]
}

class CandidateAllocationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CandidateAllocation, BSONObjectID](
    CollectionNames.CANDIDATE_ALLOCATION, mongo, CandidateAllocation.candidateAllocationFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with CandidateAllocationRepository with ReactiveRepositoryHelpers {
  val format: OFormat[CandidateAllocation] = CandidateAllocation.candidateAllocationFormat
  val projection = BSONDocument("_id" -> false)

  def save(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    val jsObjects = allocations.map(format.writes)
    collection.bulkInsert(jsObjects.toStream, ordered = false) map (_ => ())
  }

  def allocationsForSession(eventId: String, sessionId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument("eventId" -> eventId, "sessionId" -> sessionId), projection)
      .cursor[CandidateAllocation]().collect[Seq]()
  }

  def allocationsForApplication(applicationId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument("id" -> applicationId), projection).cursor[CandidateAllocation]().collect[Seq]()
  }

  def removeCandidateAllocation(allocation: CandidateAllocation): Future[Unit] = {
    val eventId = allocation.eventId
    val sessionId = allocation.sessionId

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("id" -> allocation.id),
      BSONDocument("eventId" -> eventId),
      BSONDocument("sessionId" -> sessionId)
    ))

    collection.remove(query).map (_ => ())
  }

  def delete(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    val eventIds = allocations.map(_.eventId).distinct
    val eventId = if (eventIds.size > 1) {
      throw TooManyEventIdsException(s"The delete request contained too many event Ids [$eventIds]")
    } else {
      eventIds.head
    }

    val sessionIds = allocations.map(_.sessionId).distinct
    val sessionId = if (sessionIds.size > 1) {
      throw TooManySessionIdsException(s"The delete request contained too many session Ids [$sessionIds]")
    } else {
      sessionIds.head
    }

    val applicationId = allocations.map(_.id)
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("id" -> BSONDocument("$in" -> applicationId)),
      BSONDocument("eventId" -> eventId),
      BSONDocument("sessionId" -> sessionId)
    ))

    val validator = multipleRemoveValidator(allocations.size, "Deleting allocations")

    collection.remove(query) map validator
  }
}
