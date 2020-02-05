/*
 * Copyright 2020 HM Revenue & Customs
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

package services.events

import model._
import model.exchange.{ CandidateAllocationPerSession, EventAssessorAllocationsSummaryPerSkill, EventWithAllocationsSummary }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Event, UpdateEvent, Venue }
import org.joda.time.DateTime
import play.api.Logger
import repositories.events.{ EventsConfigRepository, EventsMongoRepository, EventsRepository }
import repositories.{ SchemeRepository, SchemeYamlRepository, eventsRepository }
import services.allocation.{ AssessorAllocationService, CandidateAllocationService }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EventsService extends EventsService {
  val eventsRepo: EventsMongoRepository = eventsRepository
  val schemeRepo = SchemeYamlRepository
  val eventsConfigRepo = EventsConfigRepository
  val assessorAllocationService: AssessorAllocationService = AssessorAllocationService
  val candidateAllocationService: CandidateAllocationService = CandidateAllocationService
}

trait EventsService {

  def eventsRepo: EventsRepository
  def schemeRepo: SchemeRepository
  def assessorAllocationService: AssessorAllocationService

  def candidateAllocationService: CandidateAllocationService

  def eventsConfigRepo: EventsConfigRepository

  def saveAssessmentEvents(): Future[Unit] = {
    eventsRepo.countLong.flatMap {
      case eventCount if eventCount >= 1 =>
        throw new Exception("Events already exist in the system, batch import not possible.")
      case _ =>
      eventsConfigRepo.events.flatMap { events =>
        Logger.debug("Events have been processed!")
        eventsRepo.save(events)
      }
    }
  }

  def save(event: Event): Future[Unit] = eventsRepo.save(event :: Nil)

  def update(eventUpdate: UpdateEvent): Future[Unit] = {
    getEvent(eventUpdate.id).flatMap { event =>
      val updatedEvent = event.copy(skillRequirements = eventUpdate.skillRequirements,
        sessions = event.sessions.map { s =>
          val sessionUpdate = eventUpdate.session(s.id)
          s.copy(capacity = sessionUpdate.capacity,
            attendeeSafetyMargin = sessionUpdate.attendeeSafetyMargin,
            minViableAttendees = sessionUpdate.minViableAttendees)
        }
      )
      eventsRepo.updateEvent(updatedEvent)
    }
  }

  def getEvent(id: String): Future[Event] = eventsRepo.getEvent(id)

  def delete(id: String): Future[Unit] = eventsRepo.remove(id)

  def getEvents(eventType: EventType, venue: Venue, description: Option[String] = None): Future[List[Event]] = {
    eventsRepo.getEvents(Some(eventType), Some(venue), description = description)
  }

  def getEvents(ids: List[String]): Future[List[Event]] = eventsRepo.getEventsById(ids)

  def getEventsWithAllocationsSummary(venue: Venue, eventType: EventType,
    description: Option[String] = None): Future[List[EventWithAllocationsSummary]] = {
    getEvents(eventType, venue, description = description).flatMap { events =>
      val res = events.map { event =>
        assessorAllocationService.getAllocations(event.id).flatMap { allocations =>
          val allocationsGroupedBySkill = allocations.allocations.groupBy(_.allocatedAs)
          val allocationsGroupedBySkillWithSummary = allocationsGroupedBySkill.map { allocationGroupedBySkill =>
            val assessorAllocation = allocationGroupedBySkill._2
            val skill = allocationGroupedBySkill._1.name
            val allocated = assessorAllocation.length
            val confirmed = assessorAllocation.count(_.status == AllocationStatuses.CONFIRMED)
            EventAssessorAllocationsSummaryPerSkill(skill, allocated, confirmed)
          }.toList
          val candidateAllocBySession = event.sessions.sortBy(_.startTime.getMillisOfDay).map { session =>
            candidateAllocationService.getCandidateAllocations(event.id, session.id).map { candidateAllocations =>
              CandidateAllocationPerSession(UniqueIdentifier(session.id),
                candidateAllocations.allocations.count(_.status == AllocationStatuses.CONFIRMED))
            }
          }
          Future.sequence(candidateAllocBySession).map { cs =>
            EventWithAllocationsSummary(event.date, event, cs, allocationsGroupedBySkillWithSummary)
          }
        }
      }
      Future.sequence(res)
    }
  }

  def getEventsCreatedAfter(dateTime: DateTime): Future[Seq[Event]] = {
    eventsRepo.getEventsManuallyCreatedAfter(dateTime)
  }

  def updateStructure(): Future[Unit] = {
    eventsRepo.updateStructure()
  }

  def getFsbTypes: Seq[FsbType] = schemeRepo.getFsbTypes

  def findSchemeByEvent(eventId: String): Future[Scheme] = {
    getEvent(eventId).map { event => schemeRepo.getSchemeForFsb(event.description) }
  }

}
