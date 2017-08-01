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

package services.events

import connectors.AuthProviderClient
import model.exchange.{ CandidateAllocationPerSession, EventAssessorAllocationsSummaryPerSkill, EventWithAllocationsSummary }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Event, Venue }
import model.stc.EmailEvents.AssessorNewEventCreated
import model.{ AllocationStatuses, FsbType, TelephoneInterviewType, UniqueIdentifier }
import org.joda.time.LocalDate
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.events.{ EventsConfigRepository, EventsMongoRepository, EventsRepository }
import repositories.eventsRepository
import services.allocation.{ AssessorAllocationService, CandidateAllocationService }
import services.assessoravailability.AssessorService
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EventsService extends EventsService {
  val eventsRepo: EventsMongoRepository = eventsRepository
  val eventsConfigRepo = EventsConfigRepository
  val assessorService = AssessorService
  val authProviderClient = AuthProviderClient
  val assessorAllocationService: AssessorAllocationService = AssessorAllocationService
  val candidateAllocationService: CandidateAllocationService = CandidateAllocationService
  val eventService: StcEventService = StcEventService
}

trait EventsService extends EventSink {

  def eventsRepo: EventsRepository

  def assessorService: AssessorService

  def authProviderClient: AuthProviderClient

  def assessorAllocationService: AssessorAllocationService

  def candidateAllocationService: CandidateAllocationService

  def eventsConfigRepo: EventsConfigRepository

  def saveAssessmentEvents(): Future[Unit] = {
    eventsConfigRepo.events.flatMap { events =>
      Logger.debug("Events have been processed!")
      eventsRepo.save(events)
    }
  }

  protected[events] def renderLongDate(date: LocalDate): String = date.toString("EEEE, d MMMM yyyy")
  private def sendNewEventEmailToAssessors(event: Event)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    val date = renderLongDate(event.date)
    val requiredSkills = event.skillRequirements.keySet
    assessorService.findAssessorsNotAvailableOnDay(requiredSkills.toList, event.date, event.location).flatMap { assessors =>
      eventSink {
        authProviderClient.findByUserIds(assessors.map(_.userId)).map { candidates =>
          candidates.toList.map(c => AssessorNewEventCreated(c.email, c.name, date))
        }
      }
    }
  }

  def save(event: Event)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    eventsRepo.save(List(event)).flatMap(_ => sendNewEventEmailToAssessors(event))
  }

  def getEvent(id: String): Future[Event] = {
    eventsRepo.getEvent(id)
  }

  def getEvents(eventType: EventType, venue: Venue): Future[List[Event]] = {
    eventsRepo.getEvents(Some(eventType), Some(venue))
  }

  def getEventsWithAllocationsSummary(venue: Venue, eventType: EventType): Future[List[EventWithAllocationsSummary]] = {
    getEvents(eventType, venue).flatMap { events =>
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

  def getFsbTypes: Future[List[FsbType]] = eventsConfigRepo.fsbTypes

  def getTelephoneInterviewTypes: Future[List[TelephoneInterviewType]] = eventsConfigRepo.telephoneInterviewTypes
}
