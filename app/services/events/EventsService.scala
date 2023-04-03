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

package services.events

import javax.inject.{Inject, Singleton}
import model._
import model.exchange.{CandidateAllocationPerSession, EventAssessorAllocationsSummaryPerSkill, EventWithAllocationsSummary}
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{Event, UpdateEvent, Venue}
import play.api.Logging
import repositories.SchemeRepository
import repositories.events._
import services.allocation.AllocationServiceCommon

import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

trait EventsService {

  def saveAssessmentEvents(): Future[Unit]
  def save(event: Event): Future[Unit]
  def update(eventUpdate: UpdateEvent): Future[Unit]
  def getEvent(id: String): Future[Event]
  def delete(id: String): Future[Unit]
  def getEvents(eventType: EventType, venue: Venue, description: Option[String] = None): Future[List[Event]]
  def getEvents(ids: List[String]): Future[List[Event]]
  def getEventsWithAllocationsSummary(venue: Venue, eventType: EventType,
                                      description: Option[String] = None): Future[List[EventWithAllocationsSummary]]
  def getEventsCreatedAfter(dateTime: OffsetDateTime): Future[Seq[Event]]
  def updateStructure(): Future[Unit]
  def getFsbTypes: Seq[FsbType]
  def findSchemeByEvent(eventId: String): Future[Scheme]
}

@Singleton
class EventsServiceImpl @Inject() (eventsRepo: EventsRepository,
                                   schemeRepo: SchemeRepository,
                                   allocationServiceCommon: AllocationServiceCommon, // Breaks circular dependencies
                                   eventsConfigRepo: EventsConfigRepository)(implicit ec: ExecutionContext) extends EventsService with Logging {

  override def saveAssessmentEvents(): Future[Unit] = {
    eventsRepo.countLong.flatMap {
      case eventCount if eventCount >= 1 =>
        throw new Exception("Events already exist in the system, batch import not possible.")
      case _ =>
        eventsConfigRepo.events.flatMap { events =>
          logger.debug(s"Batch import of events was successful - ${events.size} events processed from yaml.")
          eventsRepo.save(events)
        }
    }
  }

  override def save(event: Event): Future[Unit] = eventsRepo.save(event :: Nil)

  override def update(eventUpdate: UpdateEvent): Future[Unit] = {
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

  override def getEvent(id: String): Future[Event] = eventsRepo.getEvent(id)

  override def delete(id: String): Future[Unit] = eventsRepo.remove(id)

  override def getEvents(eventType: EventType, venue: Venue, description: Option[String] = None): Future[List[Event]] = {
    eventsRepo.getEvents(Some(eventType), Some(venue), description = description).map { events => events.toList}
  }

  override def getEvents(ids: List[String]): Future[List[Event]] = eventsRepo.getEventsById(ids).map { events => events.toList }

  override def getEventsWithAllocationsSummary(venue: Venue, eventType: EventType,
                                      description: Option[String] = None): Future[List[EventWithAllocationsSummary]] = {
    getEvents(eventType, venue, description = description).flatMap { events =>
      val res = events.map { event =>
        allocationServiceCommon.getAllocations(event.id).flatMap { allocations =>
          val allocationsGroupedBySkill = allocations.allocations.groupBy(_.allocatedAs)
          val allocationsGroupedBySkillWithSummary = allocationsGroupedBySkill.map { allocationGroupedBySkill =>
            val assessorAllocation = allocationGroupedBySkill._2
            val skill = allocationGroupedBySkill._1.name
            val allocated = assessorAllocation.length
            val confirmed = assessorAllocation.count(_.status == AllocationStatuses.CONFIRMED)
            EventAssessorAllocationsSummaryPerSkill(skill, allocated, confirmed)
          }.toList
          val candidateAllocBySession = event.sessions.sortBy(_.startTime.getMillisOfDay).map { session =>
            allocationServiceCommon.getCandidateAllocations(event.id, session.id).map { candidateAllocations =>
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

  override def getEventsCreatedAfter(dateTime: OffsetDateTime): Future[Seq[Event]] = {
    eventsRepo.getEventsManuallyCreatedAfter(dateTime)
  }

  override def updateStructure(): Future[Unit] = {
    eventsRepo.updateStructure()
  }

  override def getFsbTypes: Seq[FsbType] = schemeRepo.getFsbTypes

  override def findSchemeByEvent(eventId: String): Future[Scheme] = {
    getEvent(eventId).map { event => schemeRepo.getSchemeForFsb(event.description) }
  }
}
