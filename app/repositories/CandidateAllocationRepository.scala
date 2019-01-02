/*
 * Copyright 2019 HM Revenue & Customs
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

import model.AllocationStatuses
import model.AllocationStatuses.AllocationStatus
import model.Exceptions.{ TooManyEventIdsException, TooManySessionIdsException }
import model.persisted.CandidateAllocation
import org.joda.time.LocalDate
import play.api.libs.json.OFormat
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import repositories.BSONDateTimeHandler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CandidateAllocationRepository {

  def save(allocations: Seq[CandidateAllocation]): Future[Unit]
  def findAllAllocations(applications: Seq[String]): Future[Seq[CandidateAllocation]]
  def findAllUnconfirmedAllocated(days: Int): Future[Seq[CandidateAllocation]]
  def isAllocationExists(applicationId: String, eventId: String, sessionId: String, version: Option[String]): Future[Boolean]
  def activeAllocationsForSession(eventId: String, sessionId: String): Future[Seq[CandidateAllocation]]
  def allocationsForApplication(applicationId: String): Future[Seq[CandidateAllocation]]
  def removeCandidateAllocation(allocation: CandidateAllocation): Future[Unit]
  def removeCandidateRemovalReason(applicationId: String): Future[Unit]
  def markAsReminderSent(applicationId: String, eventId: String, sessionId: String): Future[Unit]

  def delete(allocations: Seq[CandidateAllocation]): Future[Unit]
  def updateStructure(): Future[Unit]
  def allAllocationUnconfirmed: Future[Seq[CandidateAllocation]]
}

class CandidateAllocationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CandidateAllocation, BSONObjectID](
    CollectionNames.CANDIDATE_ALLOCATION, mongo, CandidateAllocation.candidateAllocationFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with CandidateAllocationRepository with ReactiveRepositoryHelpers {
  val format: OFormat[CandidateAllocation] = CandidateAllocation.candidateAllocationFormat
  val projection = BSONDocument("_id" -> false)

  def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[CandidateAllocation]] = {
    val query = List(
      Some(BSONDocument("id" -> id)),
      status.map(s => BSONDocument("status" -> s))
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query, projection).cursor[CandidateAllocation]().collect[Seq]()
  }

  def save(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    delete(allocations, ignoreMissed = true).flatMap { _ =>
      val jsObjects = allocations.map(format.writes)
      collection.bulkInsert(jsObjects.toStream, ordered = false)
    } map ( _ => () )
  }

  def findAllAllocations(applications: Seq[String]): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument("id" -> BSONDocument("$in" -> applications)), projection)
      .cursor[CandidateAllocation]().collect[Seq]()
  }

  def findAllUnconfirmedAllocated(days: Int): Future[Seq[CandidateAllocation]] = {
    val today = LocalDate.now
    collection.find(BSONDocument(
      "createdAt" -> BSONDocument("$lte" -> today.minusDays(days)),
      "status" -> AllocationStatuses.UNCONFIRMED,
      "reminderSent" -> false
    ), projection)
      .cursor[CandidateAllocation]().collect[Seq]()
  }


  def activeAllocationsForEvent(eventId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument(
      "eventId" -> eventId,
      "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
    ), projection)
      .cursor[CandidateAllocation]().collect[Seq]()
  }


  def isAllocationExists(applicationId: String, eventId: String, sessionId: String, version: Option[String]): Future[Boolean] = {
    val query = List(
        Some(BSONDocument(
          "id" -> applicationId,
          "eventId" -> eventId,
          "sessionId" -> sessionId,
          "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
        )),
        version.map(v => BSONDocument("version" -> v))
      ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query, projection)
      .cursor[CandidateAllocation]().collect[Seq]().map(_.nonEmpty)
  }

  def activeAllocationsForSession(eventId: String, sessionId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument(
      "eventId" -> eventId,
      "sessionId" -> sessionId,
      "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
    ), projection)
      .cursor[CandidateAllocation]().collect[Seq]()
  }

  def allocationsForApplication(applicationId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument(
      "id" -> applicationId,
      "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
    ), projection).cursor[CandidateAllocation]().collect[Seq]()
  }

  def removeCandidateAllocation(allocation: CandidateAllocation): Future[Unit] = {
    val eventId = allocation.eventId
    val sessionId = allocation.sessionId

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("id" -> allocation.id),
      BSONDocument("eventId" -> eventId),
      BSONDocument("sessionId" -> sessionId)
    ))

    val update = BSONDocument("$set" ->
      BSONDocument(
        "status" -> AllocationStatuses.REMOVED,
        "removeReason" -> allocation.removeReason
      )
    )

    val validator = singleUpdateValidator(allocation.id, actionDesc = "confirming allocation")

    collection.update(query, update) map validator
  }

  def removeCandidateRemovalReason(applicationId: String): Future[Unit] = {
    val query = BSONDocument(
      "id" -> applicationId,
      "status" -> AllocationStatuses.REMOVED
    )
    collection.remove(query).map(_ => ())
  }

  def delete(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    delete(allocations, ignoreMissed = false)
  }

  def delete(allocations: Seq[CandidateAllocation], ignoreMissed: Boolean): Future[Unit] = {
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

    val applicationIds = allocations.map(_.id)
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("id" -> BSONDocument("$in" -> applicationIds)),
      BSONDocument("eventId" -> eventId),
      BSONDocument("sessionId" -> sessionId)
    ))

    val validator = multipleRemoveValidator(allocations.size, "Deleting allocations")

    val remove = collection.remove(query)
    if (!ignoreMissed) {
      remove.map(validator)
    } else {
      remove.map(_ => ())
    }
  }

  def markAsReminderSent(applicationId: String, eventId: String, sessionId: String): Future[Unit] = {
    val query = BSONDocument(
      "id" -> applicationId,
      "eventId" -> eventId,
      "sessionId" -> sessionId,
      "status" -> AllocationStatuses.UNCONFIRMED
    )
    val update = BSONDocument("$set" -> BSONDocument("reminderSent" -> true))

    val validator = singleUpdateValidator(applicationId, actionDesc = "mark allocation as notified")
    collection.update(query, update) map validator
  }

  def updateStructure(): Future[Unit] = {
    val updateQuery = BSONDocument("$set" -> BSONDocument("reminderSent" -> true, "createdAt" -> LocalDate.now))
    collection.update(BSONDocument.empty, updateQuery, multi = true).map(_ => ())
  }

  def allAllocationUnconfirmed: Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument("status" -> AllocationStatuses.UNCONFIRMED), projection).cursor[CandidateAllocation]().collect[Seq]()
  }
}
