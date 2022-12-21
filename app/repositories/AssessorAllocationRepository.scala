/*
 * Copyright 2022 HM Revenue & Customs
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
import model.AllocationStatuses.AllocationStatus
import model.Exceptions.TooManyEventIdsException
import model.persisted.AssessorAllocation
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.libs.json.OFormat
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

trait AssessorAllocationRepository {
  def save(allocations: Seq[AssessorAllocation]): Future[Unit]
  def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]]
  def findAllocations(assessorIds: Seq[String], status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]]
  def find(id: String, eventId: String): Future[Option[AssessorAllocation]]
  //TODO: mongo look at this
  //  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(
  //  implicit ec: ExecutionContext): Future[List[AssessorAllocation]]
  def findAll: Future[Seq[AssessorAllocation]]
  def delete(allocations: Seq[AssessorAllocation]): Future[Unit]
  def deleteOneAllocation(eventId: String, assessorId: String): Future[Unit]
  def allocationsForEvent(eventId: String): Future[Seq[AssessorAllocation]]
  def updateAllocationStatus(id: String, eventId: String, newStatus: AllocationStatus): Future[Unit]
}

@Singleton
class AssessorAllocationMongoRepository @Inject() (mongoComponent: MongoComponent)
  extends PlayMongoRepository[AssessorAllocation](
    collectionName = CollectionNames.ASSESSOR_ALLOCATION,
    mongoComponent = mongoComponent,
    domainFormat = AssessorAllocation.assessorAllocationFormat,
    indexes = Seq(
      IndexModel(ascending("id", "eventId"), IndexOptions().unique(false))
    )
  ) with AssessorAllocationRepository with ReactiveRepositoryHelpers {

  val format: OFormat[AssessorAllocation] = AssessorAllocation.assessorAllocationFormat

  override def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]] = {
    val query = List(
      Some(Document("id" -> id)),
      status.map(s => Document("status" -> Codecs.toBson(s)))
    ).flatten.fold(Document.empty)(_ ++ _)

    collection.find(query).toFuture()
  }

  override def findAllocations(assessorIds: Seq[String], status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]] = {
    val query = List(
      Some(Document("id" -> Document("$in" -> assessorIds))),
      status.map(s => Document("status" -> Codecs.toBson(s)))
    ).flatten.fold(Document.empty)(_ ++ _)
    collection.find(query).toFuture()
  }

  override def find(id: String, eventId: String): Future[Option[AssessorAllocation]] = {
    val query = Document("id" -> id, "eventId" -> eventId)
    collection.find(query).headOption()
  }

  override def save(allocations: Seq[AssessorAllocation]): Future[Unit] = {
    collection.insertMany(allocations).toFuture() map (_ => ())
  }

  override def findAll: Future[Seq[AssessorAllocation]] = {
    val query = Document.empty
    collection.find(query).toFuture()
  }

  override def delete(allocations: Seq[AssessorAllocation]): Future[Unit] = {
    val eventIds = allocations.map(_.eventId).distinct
    val eventId = if (eventIds.size > 1) {
      throw TooManyEventIdsException(s"The delete request contained too many event Ids [$eventIds]")
    } else {
      eventIds.head
    }

    val assessorOrApplicationId = allocations.map(_.id)
    val query = Document("$and" -> BsonArray(
      Document("id" -> Document("$in" -> assessorOrApplicationId)),
      Document("eventId" -> eventId)
    ))

    val validator = multipleRemoveValidator(allocations.size, "Deleting allocations")

    collection.deleteOne(query).toFuture() map validator
  }

  override def deleteOneAllocation(eventId: String, assessorId: String): Future[Unit] = {
    val query = Document("id" -> assessorId, "eventId" -> eventId)
    val validator = singleRemovalValidator(s"eventId:$eventId, assessorId:$assessorId", "Deleting allocation")

    collection.deleteOne(query).toFuture() map validator
  }

  override def allocationsForEvent(eventId: String): Future[Seq[AssessorAllocation]] = {
    collection.find(Document("eventId" -> eventId)).toFuture()
  }

  override def updateAllocationStatus(id: String, eventId: String, newStatus: AllocationStatus): Future[Unit] = {
    val query = Document("id" -> id, "eventId" -> eventId)
    val update = Document("$set" -> Document("status" -> newStatus.toBson))
    val validator = singleUpdateValidator(id, s"updating allocation status to $newStatus")

    collection.updateOne(query, update).toFuture map validator
  }
}
