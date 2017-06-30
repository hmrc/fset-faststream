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

import common.FutureEx
import model.Exceptions.TooManyEventIdsException
import model.persisted.{ Allocation, AssessorAllocation, CandidateAllocation }
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AllocationRepository[T <: Allocation] extends ReactiveRepositoryHelpers { this: ReactiveRepository[_, _] =>

  val projection = BSONDocument("_id" -> false)

  def find(id: String): Future[Option[T]] = {
    collection.find(BSONDocument("id" -> id), projection).one[T]
  }

  // TODO what do we do when one of these upserts fails?
  def save(allocations: Seq[T]): Future[Unit] = {
    FutureEx.traverseSerial(allocations) { allocation =>
      collection.update(BSONDocument("eventId" -> allocation.eventId, "id" -> allocation.id), allocation, upsert = true)
        .map { result =>
          ()
        }
    }.map { _ => () }
  }

  def delete(allocations: Seq[T]): Future[Unit] = {
    val eventId = allocations.map(_.eventId).distinct match {
      case head :: tail => throw TooManyEventIdsException(s"The delete request contained too many event Ids [${head ++ tail}]")
      case head :: Nil => head
    }
    val assessorOrApplicationId = allocations.map(_.id)

    val query = BSONDocument(
      "id" -> BSONDocument("$in" -> assessorOrApplicationId),
      "eventId" -> eventId
    )

    val validator = multipleRemoveValidator(allocations.size, "Deleting allocations before updating")

    collection.remove(query) map validator

  }

  def allocationsForEvent(eventId: String): Future[Seq[T]] = {
    collection.find(BSONDocument("eventId" -> eventId), projection).cursor[T]().collect[Seq]()
  }

}

class AssessorAllocationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[AssessorAllocation, BSONObjectID](CollectionNames.ALLOCATION, mongo, AssessorAllocation.assessorAllocationFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with AllocationRepository[AssessorAllocation] with ReactiveRepositoryHelpers { }

class CandidateAllocationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CandidateAllocation, BSONObjectID](CollectionNames.ALLOCATION, mongo, CandidateAllocation.candidateAllocationFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with AllocationRepository[CandidateAllocation] with ReactiveRepositoryHelpers { }
