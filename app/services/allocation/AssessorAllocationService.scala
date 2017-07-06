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

package services.allocation

import model.Exceptions.OptimisticLockException
import model.exchange
import model.persisted
import model.command
import repositories.AssessorAllocationMongoRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessorAllocationService extends AssessorAllocationService {
  def allocationRepo = repositories.assessorAllocationRepository
}

trait AssessorAllocationService {

  def allocationRepo: AssessorAllocationMongoRepository

  def getAllocations(eventId: String): Future[exchange.AssessorAllocations] = {
    allocationRepo.allocationsForEvent(eventId).map { a => exchange.AssessorAllocations.apply(a) }
  }

  def allocate(newAllocations: command.AssessorAllocations): Future[Unit] = {
    allocationRepo.allocationsForEvent(newAllocations.eventId).flatMap {
      case Nil => allocationRepo.save(persisted.AssessorAllocation.fromCommand(newAllocations)).map(_ => ())
      case existingAllocations => updateExistingAllocations(existingAllocations, newAllocations).map(_ => ())
    }
  }

  private def updateExistingAllocations(existingAllocations: Seq[persisted.AssessorAllocation],
    newAllocations: command.AssessorAllocations): Future[Unit] = {

    if (existingAllocations.forall(_.version == newAllocations.version)) {
      // no prior update since reading so do update
      // check what's been updated here so we can send email notifications
      val toPersist = persisted.AssessorAllocation.fromCommand(newAllocations)
      allocationRepo.delete(existingAllocations).flatMap { _ =>
        allocationRepo.save(toPersist).map( _ => ())
      }
    } else {
        throw OptimisticLockException(s"Stored allocations for event ${newAllocations.eventId} have been updated since reading")
    }
  }
}
