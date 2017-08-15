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
import model.exchange.CandidatesEligibleForEventResponse
import model.exchange.candidateevents.{ CandidateAllocationSummary, CandidateAllocationWithEvent, CandidateRemoveReason }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.{ ContactDetails, PersonalDetails }
import model.persisted.eventschedules.Event
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.{ CandidateAllocationMongoRepository, CandidateAllocationRepository }
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.allocation.CandidateAllocationService.CouldNotFindCandidateWithApplication
import services.events.EventsService
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object CandidateAllocationService extends CandidateAllocationService {
  val candidateAllocationRepo: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  val applicationRepo: GeneralApplicationRepository = repositories.applicationRepository
  val contactDetailsRepo: ContactDetailsRepository = repositories.faststreamContactDetailsRepository
  val personalDetailsRepo: PersonalDetailsRepository = repositories.personalDetailsRepository

  val eventsService = EventsService
  val eventService: StcEventService = StcEventService

  val authProviderClient = AuthProviderClient
  val emailClient = CSREmailClient

  case class CouldNotFindCandidateWithApplication(appId: String) extends Exception(appId)

}

trait CandidateAllocationService extends EventSink {
  def candidateAllocationRepo: CandidateAllocationRepository

  def applicationRepo: GeneralApplicationRepository

  def contactDetailsRepo: ContactDetailsRepository

  def personalDetailsRepo: PersonalDetailsRepository

  def eventsService: EventsService

  def emailClient: EmailClient

  def authProviderClient: AuthProviderClient


  private val dateFormat = "dd MMMM YYYY"

  def getCandidateAllocations(eventId: String, sessionId: String): Future[exchange.CandidateAllocations] = {
    candidateAllocationRepo.activeAllocationsForSession(eventId, sessionId).map { a => exchange.CandidateAllocations.apply(a) }
  }

  def getSessionsForApplication(applicationId: String, sessionEventType: EventType): Future[Seq[CandidateAllocationWithEvent]] = {
    for {
      allocations <- candidateAllocationRepo.allocationsForApplication(applicationId)
      events <- eventsService.getEvents(allocations.map(_.eventId).toList, sessionEventType)
    } yield {
      allocations.map { allocation =>
        val allocEvent = events.filter(event => event.sessions.exists(session => allocation.sessionId == session.id))
          .map(event => event.copy(sessions = event.sessions.filter(session => allocation.sessionId == session.id)))
          .headOption.getOrElse(sys.error(s"Event was not found for allocation application: $applicationId"))
        CandidateAllocationWithEvent(applicationId, allocation.version, allocation.status, model.exchange.Event(allocEvent))
      }
    }
  }

  def unAllocateCandidates(allocations: List[model.persisted.CandidateAllocation])
    (implicit hc: HeaderCarrier): Future[Unit] = {
    val checkedAllocs = allocations.map { allocation =>
      candidateAllocationRepo.isAllocationExists(allocation.id, allocation.eventId, allocation.sessionId, Some(allocation.version))
        .map { ex =>
        if (!ex) throw OptimisticLockException(s"Allocation for application ${allocation.id} already removed")
        allocation
      }
    }
    Future.sequence(checkedAllocs).flatMap { allocations =>
      Future.sequence(allocations.map { allocation =>
        candidateAllocationRepo.removeCandidateAllocation(allocation).flatMap { _ =>
          ( allocation.removeReason.flatMap { rr => CandidateRemoveReason.find(rr).map(_.failApp) } match {
            case Some(true) => applicationRepo.setFailedToAttendAssessmentStatus(allocation.id)
            case _ => applicationRepo.resetApplicationAllocationStatus(allocation.id)
          }).flatMap { _ =>
            notifyCandidateUnallocated(allocation.eventId, model.command.CandidateAllocation.fromPersisted(allocation))
          }
        }
      })
    }.map(_ => ())
  }

  def confirmCandidateAllocation(
    newAllocations: command.CandidateAllocations
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[command.CandidateAllocations] = {
    val allocation = newAllocations.allocations.head
    candidateAllocationRepo.isAllocationExists(allocation.id, newAllocations.eventId, newAllocations.sessionId, Some(newAllocations.version))
      .flatMap { ex =>
        if (!ex) throw OptimisticLockException(s"There are no relevant allocation for candidate ${allocation.id}")
        allocateCandidates(newAllocations, append = true)
    }
  }

  def allocateCandidates(
    newAllocations: command.CandidateAllocations, append: Boolean
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[command.CandidateAllocations] = {

    eventsService.getEvent(newAllocations.eventId).flatMap { event =>

      getCandidateAllocations(newAllocations.eventId, newAllocations.sessionId).flatMap { existingAllocation =>
        existingAllocation.allocations match {
          case Nil =>
            val toPersist = persisted.CandidateAllocation.fromCommand(newAllocations)
            candidateAllocationRepo.save(toPersist).flatMap { _ =>
              updateStatusInvited(toPersist).flatMap { _ =>
                Future.sequence(newAllocations.allocations.map(sendCandidateEmail(_, event, UniqueIdentifier(newAllocations.sessionId))))
              }
            }.map { _ =>
              command.CandidateAllocations(newAllocations.eventId, newAllocations.sessionId, toPersist)
            }
          case _ =>
            val existingIds = existingAllocation.allocations.map(_.id)
            updateExistingAllocations(existingAllocation, newAllocations, append).flatMap { res =>
              Future.sequence(
                newAllocations.allocations
                  .filter(alloc => !existingIds.contains(alloc.id))
                  .map(sendCandidateEmail(_, event, UniqueIdentifier(newAllocations.sessionId)))
              ).map { _ => res}
            }
        }
      }
    }
  }

  def findCandidatesEligibleForEventAllocation(assessmentCenterLocation: String) = {
    applicationRepo.findCandidatesEligibleForEventAllocation(List(assessmentCenterLocation))
  }

  def findAllocatedApplications(appIds: List[String]): Future[CandidatesEligibleForEventResponse] = {
    applicationRepo.findAllocatedApplications(appIds)
  }


  def getCandidateAllocationsSummary(appIds: Seq[String]): Future[Seq[CandidateAllocationSummary]] = {
    candidateAllocationRepo.findAllAllocations(appIds).flatMap { allocs =>
      Future.sequence(allocs.map { ca =>
        eventsService.getEvent(ca.eventId).map { event =>
          CandidateAllocationSummary(
            event.eventType,
            event.date,
            event.sessions.find(_.id == ca.sessionId).map(_.description).getOrElse(""),
            ca.status,
            CandidateRemoveReason.find(ca.removeReason.getOrElse(""))
          )
        }
      })
    }
  }

  def removeCandidateRemovalReason(appId: String): Future[Unit] = {
    candidateAllocationRepo.removeCandidateRemovalReason(appId).flatMap(_ =>
      applicationRepo.resetApplicationAllocationStatus(appId)
    )
  }

  // this can be generalised for all cases
  private def updateStatusInvited(allocs: Seq[persisted.CandidateAllocation]) = {
    Future.sequence(
    allocs.map { alloc =>
      applicationRepo.removeProgressStatuses(alloc.id, List(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)).flatMap(_ =>
        applicationRepo.addProgressStatusAndUpdateAppStatus(alloc.id, alloc.status match {
          case AllocationStatuses.CONFIRMED => ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED
          case AllocationStatuses.UNCONFIRMED => ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED
        }
        ))
    })
  }

  private def updateExistingAllocations(
    existingAllocations: exchange.CandidateAllocations,
    newAllocations: command.CandidateAllocations,
    append: Boolean
  ): Future[command.CandidateAllocations] = {

    if (existingAllocations.version.forall(_ == newAllocations.version)) {
      val toDelete = persisted.CandidateAllocation.fromExchange(existingAllocations, newAllocations.eventId, newAllocations.sessionId)
      val newAllocsAll = if (append) {
        val oldToStay = existingAllocations.allocations
          .filter(a => !newAllocations.allocations.exists(_.id == a.id)).map(CandidateAllocation.fromExchange)
        newAllocations.copy(allocations = newAllocations.allocations ++ oldToStay)
      } else {
        newAllocations
      }
      val toPersist = persisted.CandidateAllocation.fromCommand(newAllocsAll)
      candidateAllocationRepo.delete(toDelete).flatMap { _ =>
        candidateAllocationRepo.save(toPersist).flatMap { _ =>
          updateStatusInvited(toPersist).map { _ =>
            command.CandidateAllocations(newAllocations.eventId, newAllocations.sessionId, toPersist)
          }
        }
      }
    } else {
      throw OptimisticLockException(s"Stored allocations for event ${newAllocations.eventId} have been updated since reading")
    }
  }

    private def sendCandidateEmail(
      candidateAllocation: CandidateAllocation,
      event: Event,
      sessionId: UniqueIdentifier)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

      val eventDate = event.date.toString(dateFormat)
      val localTime = event.sessions.find(_.id == sessionId).map(_.startTime).getOrElse(event.startTime)
      val eventTime = localTime.toString(if (localTime.toString("mm") == "00") "ha" else "h:mma")
      val deadlineDateTime = event.date.minusDays(10).toString(dateFormat)
      applicationRepo.find(candidateAllocation.id).flatMap {
        case Some(candidate) =>
          eventSink {
            val res = authProviderClient.findByUserIds(Seq(candidate.userId)).map { candidates =>
              candidates.map { candidate =>
                candidateAllocation.status match {
                  case AllocationStatuses.UNCONFIRMED =>
                    CandidateAllocationConfirmationRequest(candidate.email, candidate.name, eventDate, eventTime,
                      event.eventType.displayValue, event.venue.description, deadlineDateTime)
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

    private def notifyCandidateUnallocated(eventId: String, allocation: CandidateAllocation)(implicit hc: HeaderCarrier) = {
      getFullDetails(eventId, allocation).flatMap { case (event, personalDetails, contactDetails) =>
        emailClient.sendCandidateUnAllocatedFromEvent(
          contactDetails.email,
          s"${personalDetails.firstName} ${personalDetails.lastName}",
          event.date.toString("d MMMM YYYY")
        )
      }
    }

    private def getFullDetails(
      eventId: String,
      allocation: command.CandidateAllocation)
      (implicit hc: HeaderCarrier): Future[(Event, PersonalDetails, ContactDetails)] = {
      for {
        eventDetails <- eventsService.getEvent(eventId)
        candidates <- applicationRepo.find(allocation.id :: Nil)
        candidate = candidates.headOption.getOrElse(throw CouldNotFindCandidateWithApplication(allocation.id))
        personalDetails <- personalDetailsRepo.find(allocation.id)
        contactDetails <- contactDetailsRepo.find(candidate.userId)
      } yield (eventDetails, personalDetails, contactDetails)
    }

  }
