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

import connectors.{ AuthProviderClient, CSREmailClient, EmailClient }
import model.Exceptions.OptimisticLockException
import model._
import model.command.CandidateAllocation
import model.exchange.{ EventAssessorAllocationsSummaryPerSkill, EventWithAllocationsSummary }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.Venue
import model.stc.EmailEvents.{ CandidateAllocationConfirmed, CandidateAllocationConfirmationRequest }
import model.stc.StcEventTypes.StcEvents
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.{ AssessorAllocationMongoRepository, CandidateAllocationMongoRepository }
import services.events.EventsService
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessorAllocationService extends AssessorAllocationService {

  val assessorAllocationRepo: AssessorAllocationMongoRepository = repositories.assessorAllocationRepository
  val candidateAllocationRepo: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  val applicationRepo: GeneralApplicationRepository = repositories.applicationRepository

  val eventsService = EventsService
  val eventService: StcEventService = StcEventService

  val authProviderClient = AuthProviderClient
  val emailClient = CSREmailClient
}

trait AssessorAllocationService extends EventSink {

  def assessorAllocationRepo: AssessorAllocationMongoRepository
  def candidateAllocationRepo: CandidateAllocationMongoRepository
  val applicationRepo: GeneralApplicationRepository

  def eventsService: EventsService

  def emailClient: EmailClient
  def authProviderClient: AuthProviderClient

  def getAllocations(eventId: String): Future[exchange.AssessorAllocations] = {
    assessorAllocationRepo.allocationsForEvent(eventId).map { a => exchange.AssessorAllocations.apply(a) }
  }

  def getCandidateAllocations(eventId: String): Future[exchange.CandidateAllocations] = {
    candidateAllocationRepo.allocationsForEvent(eventId).map { a => exchange.CandidateAllocations.apply(a) }
  }

  def allocate(newAllocations: command.AssessorAllocations): Future[Unit] = {
    assessorAllocationRepo.allocationsForEvent(newAllocations.eventId).flatMap {
      case Nil => assessorAllocationRepo.save(persisted.AssessorAllocation.fromCommand(newAllocations)).map(_ => ())
      case existingAllocations => updateExistingAllocations(existingAllocations, newAllocations).map(_ => ())
    }
  }

  private val dateFormat = "dd MMMM YYYY"
  private val timeFormat = "HH:mma"


  def allocateCandidates(newAllocations: command.CandidateAllocations)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    eventsService.getEvent(newAllocations.eventId).flatMap { event =>
      val eventDate = event.date.toString(dateFormat)
      val eventTime = event.startTime.toString(timeFormat)
      val deadlineDateTime = event.date.minusDays(10).toString(dateFormat)


      getCandidateAllocations(newAllocations.eventId).flatMap { existingAllocation =>
        existingAllocation.allocations match {
          case Nil =>
            candidateAllocationRepo.save(persisted.CandidateAllocation.fromCommand(newAllocations)).flatMap {
              _ => Future.sequence(newAllocations.allocations.map(sendCandidateEmail(_, eventDate, eventTime, deadlineDateTime)))
            }.map(_ => ())
          case _ =>
            val existingIds = existingAllocation.allocations.map(_.id)
            updateExistingAllocations(existingAllocation, newAllocations).flatMap { _ =>
              Future.sequence(
                newAllocations.allocations
                  .filter(alloc => !existingIds.contains(alloc.id))
                  .map(sendCandidateEmail(_, eventDate, eventTime, deadlineDateTime))
              )
            }.map(_ => ())
        }
      }
    }
  }

  private def sendCandidateEmail(
                                  candidateAllocation: CandidateAllocation,
                                  eventDate: String,
                                  eventTime: String,
                                  deadlineDateTime: String)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    applicationRepo.find(candidateAllocation.id).flatMap {
      case Some(candidate) =>
        eventSink {
          val res = authProviderClient.findByUserIds(Seq(candidate.userId)).map { candidates =>
            candidates.map { candidate =>
              candidateAllocation.status match {
                case AllocationStatuses.UNCONFIRMED =>
                  CandidateAllocationConfirmationRequest(candidate.email, candidate.name, eventDate, eventTime, deadlineDateTime)
                case AllocationStatuses.CONFIRMED =>
                  CandidateAllocationConfirmed(candidate.email, candidate.name, eventDate, eventTime)
              }
            }
          } recover { case ex => throw new RuntimeException(s"Was not able to retrieve user details for candidate ${candidate.userId}", ex) }
          res.asInstanceOf[Future[StcEvents]]
        }
      case None => throw new RuntimeException(s"Can not find user application: ${candidateAllocation.id}")
    }
  }

  private def updateExistingAllocations(existingAllocations: Seq[persisted.AssessorAllocation],
                                        newAllocations: command.AssessorAllocations): Future[Unit] = {

    if (existingAllocations.forall(_.version == newAllocations.version)) {
      // no prior update since reading so do update
      // check what's been updated here so we can send email notifications
      val toPersist = persisted.AssessorAllocation.fromCommand(newAllocations)
      assessorAllocationRepo.delete(existingAllocations).flatMap { _ =>
        assessorAllocationRepo.save(toPersist).map(_ => ())
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
            val confirmed = assessorAllocation.count(_.status == AllocationStatuses.CONFIRMED)
            EventAssessorAllocationsSummaryPerSkill(skill, allocated, confirmed)
          }.toList
          EventWithAllocationsSummary(event.date, event, 0, allocationsGroupedBySkillWithSummary)
        }
      }
      Future.sequence(res)
    }
  }

  private def updateExistingAllocations(existingAllocations: exchange.CandidateAllocations,
                                        newAllocations: command.CandidateAllocations): Future[Unit] = {

    if (existingAllocations.version.forall(_ == newAllocations.version)) {
      // no prior update since reading so do update
      // check what's been updated here so we can send email notifications

      // Convert the existing exchange allocations to persisted objects so we can delete what is currently in the db
      val toDelete = persisted.CandidateAllocation.fromExchange(existingAllocations, newAllocations.eventId)

      val toPersist = persisted.CandidateAllocation.fromCommand(newAllocations)
      candidateAllocationRepo.delete(toDelete).flatMap { _ =>
        candidateAllocationRepo.save(toPersist).map(_ => ())
      }
    } else {
      throw OptimisticLockException(s"Stored allocations for event ${newAllocations.eventId} have been updated since reading")
    }
  }
}
