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

import common.FutureEx
import connectors.{ AuthProviderClient, CSREmailClient, EmailClient }
import model.Exceptions.OptimisticLockException
import model.command.CandidateAllocation
import model.stc.EmailEvents.{ CandidateAllocationConfirmationRequest, CandidateAllocationConfirmed }
import model.stc.StcEventTypes.StcEvents
import model._
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Event, Session }
import play.api.mvc.RequestHeader
import repositories.{ CandidateAllocationMongoRepository, CandidateAllocationRepository }
import repositories.application.GeneralApplicationRepository
import services.events.EventsService
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object CandidateAllocationService extends CandidateAllocationService {
  val candidateAllocationRepo: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  val applicationRepo: GeneralApplicationRepository = repositories.applicationRepository

  val eventsService = EventsService
  val eventService: StcEventService = StcEventService

  val authProviderClient = AuthProviderClient
  val emailClient = CSREmailClient
}

trait CandidateAllocationService extends EventSink {
  def candidateAllocationRepo: CandidateAllocationRepository
  def applicationRepo: GeneralApplicationRepository

  def eventsService: EventsService

  def emailClient: EmailClient
  def authProviderClient: AuthProviderClient


  private val dateFormat = "dd MMMM YYYY"
  private val timeFormat = "HH:mma"


  def getCandidateAllocations(eventId: String, sessionId: String): Future[exchange.CandidateAllocations] = {
    candidateAllocationRepo.allocationsForSession(eventId, sessionId).map { a => exchange.CandidateAllocations.apply(a) }
  }

  def getSessionsForApplication(applicationId: String, sessionEventType: EventType): Future[List[Event]] = {
    for {
      allocations <- candidateAllocationRepo.allocationsForApplication(applicationId)
      events <- eventsService.getEvents(allocations.map(_.eventId).toList, sessionEventType)
    } yield {
      val sessionIdsToMatch = allocations.map(_.sessionId)
      events
        .filter(event => event.sessions.exists(session => sessionIdsToMatch.contains(session.id)))
        .map(event => event.copy(sessions = event.sessions.filter(session => sessionIdsToMatch.contains(session.id))))
    }
  }

  def unAllocateCandidates(allocations: List[model.persisted.CandidateAllocation]): Future[SerialUpdateResult[persisted.CandidateAllocation]] = {
    // For each allocation, reset the progress status and delete the corresponding allocation
    val res = FutureEx.traverseSerial(allocations) { allocation =>
      val fut = candidateAllocationRepo.removeCandidateAllocation(allocation).flatMap { _ =>
        applicationRepo.resetApplicationAllocationStatus(allocation.id)
      }
      FutureEx.futureToEither(allocation, fut)
    }
    res.map(SerialUpdateResult.fromEither)
  }

  def allocateCandidates(newAllocations: command.CandidateAllocations)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    eventsService.getEvent(newAllocations.eventId).flatMap { event =>
      val eventDate = event.date.toString(dateFormat)
      val eventTime = event.startTime.toString(timeFormat)
      val deadlineDateTime = event.date.minusDays(10).toString(dateFormat)

      getCandidateAllocations(newAllocations.eventId, newAllocations.sessionId).flatMap { existingAllocation =>
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

  private def updateExistingAllocations(existingAllocations: exchange.CandidateAllocations,
                                        newAllocations: command.CandidateAllocations): Future[Unit] = {

    if (existingAllocations.version.forall(_ == newAllocations.version)) {
      // no prior update since reading so do update
      // check what's been updated here so we can send email notifications

      // Convert the existing exchange allocations to persisted objects so we can delete what is currently in the db
      val toDelete = persisted.CandidateAllocation.fromExchange(existingAllocations, newAllocations.eventId, newAllocations.sessionId)

      val toPersist = persisted.CandidateAllocation.fromCommand(newAllocations)
      candidateAllocationRepo.delete(toDelete).flatMap { _ =>
        candidateAllocationRepo.save(toPersist).map(_ => ())
      }
    } else {
      throw OptimisticLockException(s"Stored allocations for event ${newAllocations.eventId} have been updated since reading")
    }
  }

  private def sendCandidateEmail(candidateAllocation: CandidateAllocation,
                                 eventDate: String,
                                 eventTime: String,
                                 deadlineDateTime: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
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

}
