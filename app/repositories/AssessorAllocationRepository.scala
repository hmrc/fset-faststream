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
import model.AllocationStatuses.AllocationStatus
import model.Exceptions.TooManyEventIdsException
import model.persisted.AssessorAllocation
import play.api.libs.json.OFormat
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{ Cursor, DB, ReadPreference }
import reactivemongo.bson._
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

trait AssessorAllocationRepository {
  def save(allocations: Seq[AssessorAllocation]): Future[Unit]
  def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]]
  def findAllocations(assessorIds: Seq[String], status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]]
  def find(id: String, eventId: String): Future[Option[AssessorAllocation]]
  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[AssessorAllocation]]
  def delete(allocations: Seq[AssessorAllocation]): Future[Unit]
  def allocationsForEvent(eventId: String): Future[Seq[AssessorAllocation]]
  def updateAllocationStatus(id: String, eventId: String, newStatus: AllocationStatus): Future[Unit]
}

@Singleton
class AssessorAllocationMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[AssessorAllocation, BSONObjectID](
    CollectionNames.ASSESSOR_ALLOCATION,
    mongoComponent.mongoConnector.db,
    AssessorAllocation.assessorAllocationFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with AssessorAllocationRepository with ReactiveRepositoryHelpers {

  val format: OFormat[AssessorAllocation] = AssessorAllocation.assessorAllocationFormat

  val projection = BSONDocument("_id" -> false)

  private val unlimitedMaxDocs = -1

  override def indexes: Seq[Index] = Seq(
    Index(Seq("id" -> Ascending, "eventId" -> Ascending), unique = false)
  )

  override def find(id: String, status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]] = {
    val query = List(
      Some(BSONDocument("id" -> id)),
      status.map(s => BSONDocument("status" -> s))
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query, Some(projection)).cursor[AssessorAllocation]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[AssessorAllocation]]())
  }

  override def findAllocations(assessorIds: Seq[String], status: Option[AllocationStatus] = None): Future[Seq[AssessorAllocation]] = {
    val query = List(
      Some(BSONDocument("id" -> BSONDocument("$in" -> assessorIds))),
      status.map(s => BSONDocument("status" -> s))
    ).flatten.fold(BSONDocument.empty)(_ ++ _)
    collection.find(query, Some(projection)).cursor[AssessorAllocation]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[AssessorAllocation]]())
  }

  override def find(id: String, eventId: String): Future[Option[AssessorAllocation]] = {
    val query = BSONDocument("id" -> id, "eventId" -> eventId)
    collection.find(query, Some(projection)).one[AssessorAllocation]
  }

  override def save(allocations: Seq[AssessorAllocation]): Future[Unit] = {
    collection.insert(ordered = false).many(allocations) map (_ => ())
  }

  override def delete(allocations: Seq[AssessorAllocation]): Future[Unit] = {
    val eventIds = allocations.map(_.eventId).distinct
    val eventId = if (eventIds.size > 1) {
      throw TooManyEventIdsException(s"The delete request contained too many event Ids [$eventIds]")
    } else {
      eventIds.head
    }

    val assessorOrApplicationId = allocations.map(_.id)
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("id" -> BSONDocument("$in" -> assessorOrApplicationId)),
      BSONDocument("eventId" -> eventId)
    ))

    val validator = multipleRemoveValidator(allocations.size, "Deleting allocations")

    collection.delete().one(query) map validator
  }

  override def allocationsForEvent(eventId: String): Future[Seq[AssessorAllocation]] = {
    collection.find(BSONDocument("eventId" -> eventId), Some(projection)).cursor[AssessorAllocation]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[AssessorAllocation]]())
  }

  override def updateAllocationStatus(id: String, eventId: String, newStatus: AllocationStatus): Future[Unit] = {
    val query = BSONDocument("id" -> id, "eventId" -> eventId)
    val update = BSONDocument("$set" -> BSONDocument("status" -> newStatus))
    val validator = singleUpdateValidator(id, s"updating allocation status to $newStatus")

    collection.update(ordered = false).one(query, update) map validator
  }
}
