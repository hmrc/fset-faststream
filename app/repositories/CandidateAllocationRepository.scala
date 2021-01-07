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

package repositories

import javax.inject.{ Inject, Singleton }
import model.AllocationStatuses
import model.AllocationStatuses.AllocationStatus
import model.Exceptions.{ TooManyEventIdsException, TooManySessionIdsException }
import model.persisted.CandidateAllocation
import org.joda.time.LocalDate
import play.api.libs.json.OFormat
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CandidateAllocationRepository {
  def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[CandidateAllocation]]
  def save(allocations: Seq[CandidateAllocation]): Future[Unit]
  def findAllAllocations(applications: Seq[String]): Future[Seq[CandidateAllocation]]
  def findAllUnconfirmedAllocated(days: Int): Future[Seq[CandidateAllocation]]
  def isAllocationExists(applicationId: String, eventId: String, sessionId: String, version: Option[String]): Future[Boolean]
  def activeAllocationsForSession(eventId: String, sessionId: String): Future[Seq[CandidateAllocation]]
  def activeAllocationsForEvent(eventId: String): Future[Seq[CandidateAllocation]]
  def allocationsForApplication(applicationId: String): Future[Seq[CandidateAllocation]]
  def removeCandidateAllocation(allocation: CandidateAllocation): Future[Unit]
  def removeCandidateRemovalReason(applicationId: String): Future[Unit]
  def markAsReminderSent(applicationId: String, eventId: String, sessionId: String): Future[Unit]
  def delete(allocations: Seq[CandidateAllocation]): Future[Unit]
  def updateStructure(): Future[Unit]
  def allAllocationUnconfirmed: Future[Seq[CandidateAllocation]]
}

@Singleton
class CandidateAllocationMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[CandidateAllocation, BSONObjectID](
    CollectionNames.CANDIDATE_ALLOCATION,
    mongoComponent.mongoConnector.db,
    CandidateAllocation.candidateAllocationFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with CandidateAllocationRepository with ReactiveRepositoryHelpers {

  override def indexes: Seq[Index] = Seq(
    Index(Seq("id" -> Ascending, "eventId" -> Ascending, "sessionId" -> Ascending), unique = false)
  )

  val format: OFormat[CandidateAllocation] = CandidateAllocation.candidateAllocationFormat
  val projection = BSONDocument("_id" -> false)
  private val unlimitedMaxDocs = -1

  override def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[CandidateAllocation]] = {
    val query = List(
      Some(BSONDocument("id" -> id)),
      status.map(s => BSONDocument("status" -> s))
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query, Some(projection)).cursor[CandidateAllocation]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]())
  }

  override def save(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    delete(allocations, ignoreMissed = true).flatMap { _ =>
      collection.insert(ordered = false).many(allocations)
    } map ( _ => () )
  }

  override def findAllAllocations(applications: Seq[String]): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument("id" -> BSONDocument("$in" -> applications)), Some(projection))
      .cursor[CandidateAllocation]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]())
  }

  override def findAllUnconfirmedAllocated(days: Int): Future[Seq[CandidateAllocation]] = {
    val today = LocalDate.now
    collection.find(BSONDocument(
      "createdAt" -> BSONDocument("$lte" -> today.minusDays(days)),
      "status" -> AllocationStatuses.UNCONFIRMED,
      "reminderSent" -> false
    ), Some(projection))
      .cursor[CandidateAllocation]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]())
  }

  override def activeAllocationsForEvent(eventId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument(
      "eventId" -> eventId,
      "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
    ), Some(projection))
      .cursor[CandidateAllocation]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]())
  }

  override def isAllocationExists(applicationId: String, eventId: String, sessionId: String, version: Option[String]): Future[Boolean] = {
    val query = List(
      Some(BSONDocument(
        "id" -> applicationId,
        "eventId" -> eventId,
        "sessionId" -> sessionId,
        "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
      )),
      version.map(v => BSONDocument("version" -> v))
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query, Some(projection))
      .cursor[CandidateAllocation]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]()).map(_.nonEmpty)
  }

  override def activeAllocationsForSession(eventId: String, sessionId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument(
      "eventId" -> eventId,
      "sessionId" -> sessionId,
      "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
    ), Some(projection))
      .cursor[CandidateAllocation]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]())
  }

  override def allocationsForApplication(applicationId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument(
      "id" -> applicationId,
      "status" -> BSONDocument("$ne" -> AllocationStatuses.REMOVED)
    ), Some(projection)).cursor[CandidateAllocation]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]())
  }

  override def removeCandidateAllocation(allocation: CandidateAllocation): Future[Unit] = {
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

    collection.update(ordered = false).one(query, update) map validator
  }

  override def removeCandidateRemovalReason(applicationId: String): Future[Unit] = {
    val query = BSONDocument(
      "id" -> applicationId,
      "status" -> AllocationStatuses.REMOVED
    )
    collection.delete().one(query).map(_ => ())
  }

  override def delete(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    delete(allocations, ignoreMissed = false)
  }

  private def delete(allocations: Seq[CandidateAllocation], ignoreMissed: Boolean): Future[Unit] = {
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

    val remove = collection.delete().one(query)
    if (!ignoreMissed) {
      remove.map(validator)
    } else {
      remove.map(_ => ())
    }
  }

  override def markAsReminderSent(applicationId: String, eventId: String, sessionId: String): Future[Unit] = {
    val query = BSONDocument(
      "id" -> applicationId,
      "eventId" -> eventId,
      "sessionId" -> sessionId,
      "status" -> AllocationStatuses.UNCONFIRMED
    )
    val update = BSONDocument("$set" -> BSONDocument("reminderSent" -> true))

    val validator = singleUpdateValidator(applicationId, actionDesc = "mark allocation as notified")
    collection.update(ordered = false).one(query, update) map validator
  }

  override def updateStructure(): Future[Unit] = {
    val updateQuery = BSONDocument("$set" -> BSONDocument("reminderSent" -> true, "createdAt" -> LocalDate.now))
    collection.update(ordered = false).one(BSONDocument.empty, updateQuery, multi = true).map(_ => ())
  }

  override def allAllocationUnconfirmed: Future[Seq[CandidateAllocation]] = {
    collection.find(BSONDocument("status" -> AllocationStatuses.UNCONFIRMED), Some(projection)).cursor[CandidateAllocation]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[CandidateAllocation]]())
  }
}
