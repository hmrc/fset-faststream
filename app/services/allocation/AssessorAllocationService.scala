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
import model.{ AllocationStatuses, command, exchange, persisted }
import model.exchange.{ EventAssessorAllocationsSummaryPerSkill, EventWithAllocationsSummary }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.Venue
import repositories.AssessorAllocationMongoRepository
import services.events.EventsService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessorAllocationService extends AssessorAllocationService {
  def allocationRepo = repositories.assessorAllocationRepository
  override val eventsService = EventsService
}

trait AssessorAllocationService {

  def allocationRepo: AssessorAllocationMongoRepository
  val eventsService: EventsService = EventsService


  def getAllocations(eventId: String): Future[exchange.AssessorAllocations] = {
    allocationRepo.allocationsForEvent(eventId).map { a => exchange.AssessorAllocations.apply(a) }
  }

  def allocate(newAllocations: command.AssessorAllocations): Future[Unit] = {
    getAllocations(newAllocations.eventId).flatMap { existingAllocation =>
      existingAllocation.allocations match {
        case Nil => allocationRepo.save(persisted.AssessorAllocation.fromCommand(newAllocations)).map(_ => ())
        case _ => updateExistingAllocations(existingAllocation, newAllocations).map(_ => ())

      }
    }
  }

  private def updateExistingAllocations(existingAllocation: exchange.AssessorAllocations,
    newAllocations: command.AssessorAllocations): Future[Unit] = {

    if (existingAllocation.version.forall(_ == newAllocations.version)) {
      // no prior update since reading so do update
      // check what's been updated here so we can send email notifications
      val toPersist = persisted.AssessorAllocation.fromCommand(newAllocations)
      allocationRepo.delete(toPersist).flatMap { _ =>
        allocationRepo.save(toPersist).map( _ => ())
      }
    } else {
        throw OptimisticLockException(s"Stored allocations for event ${newAllocations.eventId} have been updated since reading")
    }
  }

  def getEventsWithAllocationsSummary(venue: Venue, eventType: EventType): Future[List[EventWithAllocationsSummary]] = {
    eventsService.getEvents(eventType, venue).flatMap { events =>
      val res = events.map { event =>
        getAllocations(event.id).map { allocations =>
          val allocationsGroupedBySkill = allocations.allocations.groupBy(_.allocatedAs)
          val allocationsGroupedBySkillWithSummary = allocationsGroupedBySkill.map { allocationGroupedBySkill =>
            val assessorAllocation = allocationGroupedBySkill._2
            val skill = allocationGroupedBySkill._1.name
            val allocated = assessorAllocation.length
            val confirmed = assessorAllocation.filter(_.status == AllocationStatuses.CONFIRMED).length
            EventAssessorAllocationsSummaryPerSkill(skill, allocated, confirmed)
          }.toList
          EventWithAllocationsSummary(event, 0, allocationsGroupedBySkillWithSummary)
        }
      }
      // TODO MIGUEL: Check what happens with Thread pool
      Future.sequence(res)
    }
  }
}
