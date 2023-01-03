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

package services.allocation

import com.google.inject.name.Named
import config.MicroserviceAppConfig
import connectors.{AuthProviderClient, OnlineTestEmailClient}

import javax.inject.{Inject, Singleton}
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.OptimisticLockException
import model.ProgressStatuses.EventProgressStatuses
import model._
import model.command.CandidateAllocation
import model.exchange.CandidatesEligibleForEventResponse
import model.exchange.candidateevents.{CandidateAllocationSummary, CandidateAllocationWithEvent, CandidateRemoveReason}
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{Event, EventType}
import model.persisted.{ContactDetails, PersonalDetails}
import model.stc.EmailEvents.{CandidateAllocationConfirmationReminder, CandidateAllocationConfirmationRequest, CandidateAllocationConfirmed}
import model.stc.StcEventTypes.StcEvents
import org.joda.time.LocalDate
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{CandidateAllocationMongoRepository, SchemeRepository}
import services.allocation.CandidateAllocationService.CouldNotFindCandidateWithApplication
import services.events.EventsService
import services.stc.{EventSink, StcEventService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CandidateAllocationService {
  case class CouldNotFindCandidateWithApplication(appId: String) extends Exception(appId)
}

@Singleton
class CandidateAllocationService @Inject() (candidateAllocationRepo: CandidateAllocationMongoRepository,
                                             applicationRepo: GeneralApplicationRepository,
                                             contactDetailsRepo: ContactDetailsRepository,
                                             personalDetailsRepo: PersonalDetailsRepository,
                                             schemeRepository: SchemeRepository,
                                             eventsService: EventsService,
                                             allocationServiceCommon: AllocationServiceCommon, // Breaks circular dependencies
                                             val eventService: StcEventService,
                                             @Named("CSREmailClient") emailClient: OnlineTestEmailClient, //TODO:fix change type
                                             authProviderClient: AuthProviderClient,
                                             appConfig: MicroserviceAppConfig
                                            ) extends EventSink with Logging {

  private val dateFormat = "dd MMMM YYYY"
  private val eventsConfig = appConfig.eventsConfig

  def getCandidateAllocations(eventId: String, sessionId: String): Future[exchange.CandidateAllocations] = {
    allocationServiceCommon.getCandidateAllocations(eventId, sessionId)
  }

  def allocationsForApplication(applicationId: String)
                               (implicit hc: HeaderCarrier): Future[Seq[model.persisted.CandidateAllocation]] = {
    candidateAllocationRepo.allocationsForApplication(applicationId)
  }

  def getSessionsForApplication(applicationId: String): Future[Seq[CandidateAllocationWithEvent]] = {
    for {
      allocations <- candidateAllocationRepo.allocationsForApplication(applicationId)
      events <- eventsService.getEvents(allocations.map(_.eventId).toList)
    } yield {
      allocations.flatMap { allocation =>
        events.filter(event => event.sessions.exists(session => allocation.sessionId == session.id))
          .map(event => event.copy(sessions = event.sessions.filter(session => allocation.sessionId == session.id)))
          .map { allocEvent =>
            CandidateAllocationWithEvent(applicationId, allocation.version, allocation.status, model.exchange.Event(allocEvent))
          }
      }
    }
  }

  def unAllocateCandidates(allocations: List[model.persisted.CandidateAllocation],
                           eligibleForReallocation: Boolean = true)(implicit hc: HeaderCarrier): Future[Unit] = {
    val checkedAllocs = allocations.map { allocation =>
      candidateAllocationRepo.isAllocationExists(allocation.id, allocation.eventId, allocation.sessionId, Some(allocation.version))
        .map { ex =>
          if (!ex) throw OptimisticLockException(s"Allocation for application ${allocation.id} already removed")
          allocation
        }
    }
    Future.sequence(checkedAllocs).flatMap { allocations =>
      Future.sequence(allocations.map { allocation =>
        eventsService.getEvent(allocation.eventId).flatMap { event =>
          candidateAllocationRepo.removeCandidateAllocation(allocation).flatMap { _ =>
            processCandidateAllocation(allocation, eligibleForReallocation, event.eventType)
          }
        }
      })
    }.map(_ => ())
  }

  private def processCandidateAllocation(allocation: model.persisted.CandidateAllocation,
                                         eligibleForReallocation: Boolean, eventType: EventType)(implicit hc: HeaderCarrier) = {
    ( allocation.removeReason.flatMap { rr => CandidateRemoveReason.find(rr).map(_.failApp) } match {
      case Some(true) => applicationRepo.setFailedToAttendAssessmentStatus(allocation.id, eventType)
      case _ if eligibleForReallocation => applicationRepo.resetApplicationAllocationStatus(allocation.id, eventType)
      // Do nothing in this scenario
      case _ => Future.successful(())
    } ).flatMap { _ =>
      notifyCandidateUnallocated(allocation.eventId, model.command.CandidateAllocation.fromPersisted(allocation))
    }
  }

  def confirmCandidateAllocation(newAllocations: command.CandidateAllocations
                                )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[command.CandidateAllocations] = {
    val allocation = newAllocations.allocations.head
    candidateAllocationRepo.isAllocationExists(allocation.id, newAllocations.eventId, newAllocations.sessionId, Some(newAllocations.version))
      .flatMap { ex =>
        if (!ex) throw OptimisticLockException(s"There are no relevant allocation for candidate ${allocation.id}")
        allocateCandidates(newAllocations, append = true)
      }
  }

  def allocateCandidates(newAllocations: command.CandidateAllocations,
                         append: Boolean
                        )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[command.CandidateAllocations] = {
    eventsService.getEvent(newAllocations.eventId).flatMap { event =>

      getCandidateAllocations(newAllocations.eventId, newAllocations.sessionId).flatMap { existingAllocation =>
        existingAllocation.allocations match {
          case Nil =>
            val toPersist = persisted.CandidateAllocation.fromCommand(newAllocations)
            candidateAllocationRepo.save(toPersist).flatMap { _ =>
              updateStatusInvited(toPersist, event.eventType).flatMap { _ =>
                Future.sequence(newAllocations.allocations.map(sendCandidateEmail(_, event, UniqueIdentifier(newAllocations.sessionId))))
              }
            }.map { _ =>
              command.CandidateAllocations(newAllocations.eventId, newAllocations.sessionId, toPersist)
            }
          case _ =>
            updateExistingAllocations(existingAllocation, newAllocations, event.eventType, append).flatMap { res =>
              // Do not send emails to the existing allocations
              val existingIds = existingAllocation.allocations.map(_.id)
              Future.sequence(
                newAllocations.allocations
                  .filter(alloc => !existingIds.contains(alloc.id))
                  .map(sendCandidateEmail(_, event, UniqueIdentifier(newAllocations.sessionId)))
              ).map { _ => res }
            }
        }
      }
    }
  }

  def findCandidatesEligibleForEventAllocation(assessmentCentreLocation: String, eventType: EventType, eventDescription: String) = {
    val schemeId = eventType match {
      case EventType.FSAC => None
      case EventType.FSB => Some(schemeRepository.getSchemeForFsb(eventDescription).id)
    }

    applicationRepo.findCandidatesEligibleForEventAllocation(List(assessmentCentreLocation), eventType, schemeId)
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

  def removeCandidateRemovalReason(appId: String, eventType: EventType): Future[Unit] = {
    candidateAllocationRepo.removeCandidateRemovalReason(appId).flatMap(_ =>
      applicationRepo.resetApplicationAllocationStatus(appId, eventType)
    )
  }

  def processUnconfirmedCandidates()(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    candidateAllocationRepo.findAllUnconfirmedAllocated(eventsConfig.daysBeforeInvitationReminder).flatMap { allocations =>
      Future.sequence(allocations.map { alloc =>
        eventsService.getEvent(alloc.eventId).flatMap { event =>
          sendCandidateEmail(CandidateAllocation.fromPersisted(alloc), event, UniqueIdentifier(alloc.sessionId), isAwaitingReminder = true)
            .flatMap { _ =>
              candidateAllocationRepo.markAsReminderSent(alloc.id, alloc.eventId, alloc.sessionId)
            }
        }
      }).map(_ => ())
    }
  }

  // this can be generalised for all cases
  private def updateStatusInvited(allocs: Seq[persisted.CandidateAllocation], eventType: EventType) = {
    val status = EventProgressStatuses.get(eventType.applicationStatus)
    val awaitingAlloc = status.awaitingAllocation
    val unconfirmedAlloc = status.allocationUnconfirmed
    val confirmedAlloc = status.allocationConfirmed
    Future.sequence(
      allocs.map { alloc =>
        applicationRepo.removeProgressStatuses(alloc.id, List(awaitingAlloc)).flatMap(_ =>
          applicationRepo.addProgressStatusAndUpdateAppStatus(alloc.id, alloc.status match {
            case AllocationStatuses.CONFIRMED => confirmedAlloc
            case AllocationStatuses.UNCONFIRMED => unconfirmedAlloc
          }
          ))
      })
  }

  private def updateExistingAllocations(
                                         existingAllocations: exchange.CandidateAllocations,
                                         newAllocations: command.CandidateAllocations,
                                         eventType: EventType,
                                         append: Boolean
                                       ): Future[command.CandidateAllocations] = {

    def candidatesNotInJobOffer(toPersist: Seq[model.persisted.CandidateAllocation], idsWithAppStatus: Seq[(String, ApplicationStatus)]) = {
      // Identify the candidates who are in ELIGIBLE_FOR_JOB_OFFER as these will be excluded from being processed
      val idsInJobOffer = idsWithAppStatus.collect { case (appId, appStatus) if appStatus == ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER => appId }
      toPersist.filterNot(allocation => idsInJobOffer.contains(allocation.id))
    }

    if (existingAllocations.version.forall(_ == newAllocations.version)) {
      val newAllocsAll = if (append) {
        val oldToStay = existingAllocations.allocations
          .filter(a => !newAllocations.allocations.exists(_.id == a.id)).map(CandidateAllocation.fromExchange)
        newAllocations.copy(allocations = newAllocations.allocations ++ oldToStay)
      } else {
        newAllocations
      }
      val toDelete = persisted.CandidateAllocation.fromExchange(existingAllocations, newAllocations.eventId, newAllocations.sessionId)
      val toPersist = persisted.CandidateAllocation.fromCommand(newAllocsAll)
      for {
        _ <- candidateAllocationRepo.delete(toDelete)
        _ <- candidateAllocationRepo.save(toPersist)
        appIds = toPersist.map(_.id)
        // Fetch the application statuses of the existing allocations so we can see if any candidates have moved out of fsb and are in job offer
        idsWithAppStatus <- applicationRepo.getApplicationStatusForCandidates(appIds)
        _ <- updateStatusInvited(candidatesNotInJobOffer(toPersist, idsWithAppStatus), eventType)
      } yield {
        command.CandidateAllocations(newAllocations.eventId, newAllocations.sessionId, toPersist)
      }
    } else {
      logger.debug(s"Going to throw OptimisticLockException because newAllocations.version=${newAllocations.version} " +
        s"does not match existing allocations version=${existingAllocations.version}")
      throw OptimisticLockException(s"Stored allocations for event ${newAllocations.eventId} have been updated since reading")
    }
  }

  private def eventGuide(event: Event) = event.eventType match {
    case EventType.FSAC => Some(eventsConfig.fsacGuideUrl)
    case EventType.FSB => schemeRepository.getSchemeForFsb(event.description).schemeGuide
  }

  private def sendCandidateEmail(
                                  candidateAllocation: CandidateAllocation,
                                  event: Event,
                                  sessionId: UniqueIdentifier,
                                  isAwaitingReminder: Boolean = false
                                )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    val eventDate = event.date.toString(dateFormat)
    val localTime = event.sessions.find(_.id == sessionId.toString).map(_.startTime).getOrElse(event.startTime)
    val eventTime = localTime.toString(if (localTime.toString("mm") == "00") "ha" else "h:mma")

    val tenBeforeEventDate = event.date.minusDays(10)
    val deadlineDateTime = if (tenBeforeEventDate.isBefore(LocalDate.now())) {
      LocalDate.now().plusDays(1).toString(dateFormat)
    } else {
      tenBeforeEventDate.toString(dateFormat)
    }

    val eventGuideUrl = eventGuide(event).getOrElse("")
    applicationRepo.find(candidateAllocation.id).flatMap {
      case Some(candidate) =>
        eventSink {
          val res = authProviderClient.findByUserIds(Seq(candidate.userId)).map { candidates =>
            candidates.map { candidate =>
              candidateAllocation.status match {
                case AllocationStatuses.UNCONFIRMED if isAwaitingReminder =>
                  CandidateAllocationConfirmationReminder(candidate.email, candidate.name, eventDate, eventTime,
                    event.eventType.displayValue, event.venue.description, deadlineDateTime, eventGuideUrl)
                case AllocationStatuses.UNCONFIRMED =>
                  CandidateAllocationConfirmationRequest(candidate.email, candidate.name, eventDate, eventTime,
                    event.eventType.displayValue, event.venue.description, deadlineDateTime, eventGuideUrl)
                case AllocationStatuses.CONFIRMED =>
                  CandidateAllocationConfirmed(candidate.email, candidate.name, eventDate, eventTime,
                    event.eventType.displayValue, event.venue.description, eventGuideUrl)
              }
            }
          } recover { case ex => throw new RuntimeException(s"Was not able to retrieve user details for candidate ${candidate.userId}", ex) }
          res.asInstanceOf[Future[StcEvents]]
        }
      case None => throw new RuntimeException(s"Cannot find user application: ${candidateAllocation.id}")
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

  private def getFullDetails(eventId: String,
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

  def updateStructure(): Future[Unit] = candidateAllocationRepo.updateStructure()
}
