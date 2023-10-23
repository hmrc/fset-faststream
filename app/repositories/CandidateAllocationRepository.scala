/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.AllocationStatuses
import model.AllocationStatuses.AllocationStatus
import model.Exceptions.{TooManyEventIdsException, TooManySessionIdsException}
import model.persisted.CandidateAllocation
import org.joda.time.LocalDate
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.concurrent.{ExecutionContext, Future}

trait CandidateAllocationRepository {
  def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[CandidateAllocation]]
  def findAll: Future[Seq[CandidateAllocation]]
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
  def deleteOneAllocation(eventId: String, sessionId: String, applicationId: String, version: String): Future[Unit]
  def updateStructure(): Future[Unit]
  def allAllocationUnconfirmed: Future[Seq[CandidateAllocation]]
  def findAllConfirmedOrUnconfirmedAllocations(applicationIds: Seq[String], eventIds: Seq[String]): Future[Seq[CandidateAllocation]]
}

@Singleton
class CandidateAllocationMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[CandidateAllocation](
    collectionName = CollectionNames.CANDIDATE_ALLOCATION,
    mongoComponent = mongoComponent,
    domainFormat = CandidateAllocation.candidateAllocationFormat,
    indexes = Seq(
      IndexModel(ascending("id", "eventId", "sessionId"), IndexOptions().unique(false))
    )
  ) with CandidateAllocationRepository with ReactiveRepositoryHelpers {

  override def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[CandidateAllocation]] = {
    val query = List(
      Some(Document("id" -> id)),
      status.map(s => Document("status" -> s.toBson))
    ).flatten.fold(Document.empty)(_ ++ _)

    collection.find(query).toFuture()
  }

  override def findAll: Future[Seq[CandidateAllocation]] = {
    val query = Document.empty
    collection.find(query).toFuture()
  }

  override def save(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    delete(allocations, ignoreMissed = true).flatMap { _ =>
      collection.insertMany(allocations).toFuture()
    } map ( _ => () )
  }

  override def findAllAllocations(applicationIds: Seq[String]): Future[Seq[CandidateAllocation]] = {
    collection.find(Document("id" -> Document("$in" -> applicationIds))).toFuture()
  }

  override def findAllConfirmedOrUnconfirmedAllocations(applicationIds: Seq[String], eventIds: Seq[String]): Future[Seq[CandidateAllocation]] = {
    collection.find(Document(
      "id" -> Document("$in" -> applicationIds),
      "eventId" -> Document("$in" -> eventIds),
      "status" -> Document("$in" -> Seq(AllocationStatuses.CONFIRMED.toBson, AllocationStatuses.UNCONFIRMED.toBson))
    )).toFuture()
  }

  override def findAllUnconfirmedAllocated(days: Int): Future[Seq[CandidateAllocation]] = {
    val today = LocalDate.now
    import play.api.libs.json.JodaWrites._ // This is needed for LocalDate serialization

    collection.find(Document(
      "createdAt" -> Document("$lte" -> Codecs.toBson(today.minusDays(days))),
      "status" -> AllocationStatuses.UNCONFIRMED.toBson,
      "reminderSent" -> false
    )).toFuture()
  }

  override def activeAllocationsForEvent(eventId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(Document(
      "eventId" -> eventId,
      "status" -> Document("$ne" -> AllocationStatuses.REMOVED.toBson)
    )).toFuture()
  }

  override def isAllocationExists(applicationId: String, eventId: String, sessionId: String, version: Option[String]): Future[Boolean] = {
    val query = List(
      Some(Document(
        "id" -> applicationId,
        "eventId" -> eventId,
        "sessionId" -> sessionId,
        "status" -> Document("$ne" -> AllocationStatuses.REMOVED.toBson)
      )),
      version.map(v => Document("version" -> v))
    ).flatten.fold(Document.empty)(_ ++ _)

    collection.find(query).toFuture().map(_.nonEmpty)
  }

  override def activeAllocationsForSession(eventId: String, sessionId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(Document(
      "eventId" -> eventId,
      "sessionId" -> sessionId,
      "status" -> Document("$ne" -> AllocationStatuses.REMOVED.toBson)
    )).toFuture()
  }

  override def allocationsForApplication(applicationId: String): Future[Seq[CandidateAllocation]] = {
    collection.find(Document(
      "id" -> applicationId,
      "status" -> Document("$ne" -> AllocationStatuses.REMOVED.toBson)
    )).toFuture()
  }

  override def removeCandidateAllocation(allocation: CandidateAllocation): Future[Unit] = {
    val eventId = allocation.eventId
    val sessionId = allocation.sessionId

    val query = Document("$and" -> BsonArray(
      Document("id" -> allocation.id),
      Document("eventId" -> eventId),
      Document("sessionId" -> sessionId)
    ))

    val update = Document("$set" ->
      Document(
        "status" -> AllocationStatuses.REMOVED.toBson,
        "removeReason" -> allocation.removeReason
      )
    )

    val validator = singleUpdateValidator(allocation.id, actionDesc = "confirming allocation")
    collection.updateOne(query, update).toFuture() map validator
  }

  override def removeCandidateRemovalReason(applicationId: String): Future[Unit] = {
    val query = Document(
      "id" -> applicationId,
      "status" -> AllocationStatuses.REMOVED.toBson
    )
    collection.deleteOne(query).toFuture().map(_ => ())
  }

  override def delete(allocations: Seq[CandidateAllocation]): Future[Unit] = {
    delete(allocations, ignoreMissed = false)
  }

  override def deleteOneAllocation(eventId: String, sessionId: String, applicationId: String, version: String): Future[Unit] = {
    val query = Document("$and" -> BsonArray(
      Document("id" -> applicationId),
      Document("eventId" -> eventId),
      Document("sessionId" -> sessionId),
      Document("version" -> version)
    ))

    val validator = multipleRemoveValidator(1, "Deleting candidate allocation")

    val remove = collection.deleteOne(query).toFuture()
    remove.map(validator)
  }

  private def delete(allocations: Seq[CandidateAllocation], ignoreMissed: Boolean): Future[Unit] = {
    val eventIds = allocations.map(_.eventId).distinct
    val eventId = if (eventIds.size > 1) {
      throw TooManyEventIdsException(s"The delete request contained too many event Ids: [$eventIds]")
    } else {
      eventIds.head
    }

    val sessionIds = allocations.map(_.sessionId).distinct
    val sessionId = if (sessionIds.size > 1) {
      throw TooManySessionIdsException(s"The delete request contained too many session Ids: [$sessionIds]")
    } else {
      sessionIds.head
    }

    val applicationIds = allocations.map(_.id)
    val query = Document("$and" -> BsonArray(
      Document("id" -> Document("$in" -> applicationIds)),
      Document("eventId" -> eventId),
      Document("sessionId" -> sessionId)
    ))

    val validator = multipleRemoveValidator(allocations.size, "Deleting allocations")

    val remove = collection.deleteMany(query).toFuture()
    if (!ignoreMissed) {
      remove.map(validator)
    } else {
      remove.map(_ => ())
    }
  }

  override def markAsReminderSent(applicationId: String, eventId: String, sessionId: String): Future[Unit] = {
    val query = Document(
      "id" -> applicationId,
      "eventId" -> eventId,
      "sessionId" -> sessionId,
      "status" -> AllocationStatuses.UNCONFIRMED.toBson
    )
    val update = Document("$set" -> Document("reminderSent" -> true))

    val validator = singleUpdateValidator(applicationId, actionDesc = "mark allocation as notified")
    collection.updateOne(query, update).toFuture() map validator
  }

  override def updateStructure(): Future[Unit] = {
    import play.api.libs.json.JodaWrites._ // This is needed for LocalDate serialization

    val updateQuery = Document("$set" -> Document("reminderSent" -> true, "createdAt" -> Codecs.toBson(LocalDate.now)))
    collection.updateMany(Document.empty, updateQuery).toFuture().map(_ => ())
  }

  override def allAllocationUnconfirmed: Future[Seq[CandidateAllocation]] = {
    collection.find(Document("status" -> AllocationStatuses.UNCONFIRMED.toBson)).toFuture()
  }
}
