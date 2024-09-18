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
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.{CandidateAlreadyAssignedToOtherEventException, OptimisticLockException}
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
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{CandidateAllocationMongoRepository, CurrentSchemeStatusHelper, SchemeRepository}
import services.allocation.CandidateAllocationService.CouldNotFindCandidateWithApplication
import services.events.EventsService
import services.stc.{EventSink, StcEventService}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object CandidateAllocationService {
  case class CouldNotFindCandidateWithApplication(appId: String) extends Exception(appId)
}

@Singleton
class CandidateAllocationService @Inject()(candidateAllocationRepo: CandidateAllocationMongoRepository,
                                           applicationRepo: GeneralApplicationRepository,
                                           contactDetailsRepo: ContactDetailsRepository,
                                           personalDetailsRepo: PersonalDetailsRepository,
                                           schemeRepository: SchemeRepository,
                                           fsbRepo: FsbRepository,
                                           eventsService: EventsService,
                                           allocationServiceCommon: AllocationServiceCommon, // Breaks circular dependencies
                                           val eventService: StcEventService,
                                           @Named("CSREmailClient") emailClient: OnlineTestEmailClient, //TODO:fix change type
                                           authProviderClient: AuthProviderClient,
                                           appConfig: MicroserviceAppConfig
                                          )(implicit ec: ExecutionContext) extends EventSink with Logging with CurrentSchemeStatusHelper {

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
    (allocation.removeReason.flatMap { rr => CandidateRemoveReason.find(rr).map(_.failApp) } match {
      case Some(true) => applicationRepo.setFailedToAttendAssessmentStatus(allocation.id, eventType)
      case _ if eligibleForReallocation => applicationRepo.resetApplicationAllocationStatus(allocation.id, eventType)
      // Do nothing in this scenario
      case _ => Future.successful(())
    }).flatMap { _ =>
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

  //scalastyle:off method.length
  // Protected instead of private so the test class has access
  protected[allocation] def isAlreadyAssignedToAnEvent(eventId: String,
                                                       newAllocations: command.CandidateAllocations) = {
    def isFsbEvent(event: Event) = event.eventType == EventType.FSB

    /**
      * Verifies if the candidate has been assigned to other fsb events and the candidate has failed them all
      *
      * @param eventType            the type of event we are dealing with
      * @param candidateAllocations the other fsb event allocations to which the candidate has been assigned
      * @return
      * If this method returns false that means the candidate can be assigned to the new event
      * If this method returns true that means the candidate cannot be assigned to the new event
      */
    def isFsbCandidateWithFailedEvents(eventType: EventType,
                                       candidateAllocations: Seq[model.persisted.CandidateAllocation]) = {
      if (eventType == EventType.FSB) {
        Future.sequence(candidateAllocations.map { allocation =>
          eventsService.getEvent(allocation.eventId).flatMap { event =>
            val scheme = schemeRepository.getSchemeForFsb(event.description)
            // Now we need to find the fsb evaluation for the scheme
            fsbRepo.findByApplicationId(allocation.id).map { fsbResultsOpt =>
              fsbResultsOpt.exists { fsbResults =>
                val schemeEvaluationResultOpt = fsbResults.evaluation.result.find(schemeEvaluationResult =>
                  schemeEvaluationResult.schemeId == scheme.id)
                // If the evaluation result is Red then the candidate can be added to the event
                schemeEvaluationResultOpt.exists(_.result == EvaluationResults.Red.toString)
              }
            }
          }
          // We want all the same value and that value to be true indicating the result is Red for each FSB event
        }).map(seqOfBool => seqOfBool.distinct.length == 1 && seqOfBool.distinct.head)
      } else {
        Future.successful(false)
      }
    }

    /**
      * Verifies that the candidate has not been assigned to the same fsb event type by another admin
      *
      * @param event                the event we are dealing with
      * @param candidateAllocations the other fsb event allocations to which the candidate has been assigned
      * @return true indicates the candidate has already been allocated to another event of the same type, otherwise false
      */
    def hasFsbCandidateAlreadyBeenAssignedToSameEvent(event: Event, candidateAllocations: Seq[model.persisted.CandidateAllocation]) = {
      if (event.eventType == EventType.FSB) {
        Future.sequence(candidateAllocations.map { allocation =>
          eventsService.getEvent(allocation.eventId).map(_.description)
        }).map(_.contains(event.description))
      } else {
        Future.successful(false)
      }
    }

    for {
      event <- eventsService.getEvent(eventId)
      sameEvents <- eventsService.getEvents(event.eventType)

      // Identify the eventIds of the other events of the same type
      otherEventIds = sameEvents.filterNot(_.id == eventId).map(_.id)
      appIdsToAssign = newAllocations.allocations.map(_.id)

      // Have any candidates already been allocated to the same event type?
      candidatesAlreadyAssignedToSameEvent <- candidateAllocationRepo.findAllConfirmedOrUnconfirmedAllocations(appIdsToAssign, otherEventIds)

      // Only candidates in FSB can be assigned to multiple events so we need to check the candidate failed those events
      isFsbCandidateWithAllFailedEvents <- isFsbCandidateWithFailedEvents(event.eventType, candidatesAlreadyAssignedToSameEvent)
      hasFsbCandidateAlreadyBeenAssignedToSameEvent <- hasFsbCandidateAlreadyBeenAssignedToSameEvent(event, candidatesAlreadyAssignedToSameEvent)
      isFsb = isFsbEvent(event)
    } yield {
      val canBeAssigned = false
      val cannotBeAssigned = true
      (candidatesAlreadyAssignedToSameEvent.nonEmpty, isFsb,
        isFsbCandidateWithAllFailedEvents, hasFsbCandidateAlreadyBeenAssignedToSameEvent) match {
        case (true, false, _, _) => // FSAC candidate has already been assigned to another FSAC event so cannot be assigned again
          val data = candidatesAlreadyAssignedToSameEvent.map { caa =>
            s"applicationId=${caa.id}, eventId=${caa.eventId}, sessionId=${caa.sessionId}, status=${caa.status}"
          }.mkString(" | ")
          logger.warn(
            s"When assigning candidates to event $eventId, the following candidates are already assigned to a different event: $data"
          )
          cannotBeAssigned
        // FSB candidate can be assigned to new event
        case (true, true, true, _) => canBeAssigned
        // FSB candidate is already assigned to an event of the same type so cannot be assigned twice
        case (true, true, _, true) => cannotBeAssigned
        case _ => canBeAssigned
      }
    }
  }

  def allocateCandidates(newAllocations: command.CandidateAllocations,
                         append: Boolean
                        )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[command.CandidateAllocations] = {
    eventsService.getEvent(newAllocations.eventId).flatMap { event =>

      getCandidateAllocations(newAllocations.eventId, newAllocations.sessionId).flatMap { existingAllocation =>
        existingAllocation.allocations match {
          case Nil =>
            (for {
              alreadyAssigned <- isAlreadyAssignedToAnEvent(newAllocations.eventId, newAllocations)
            } yield {
              if (alreadyAssigned) {
                throw CandidateAlreadyAssignedToOtherEventException(
                  s"There are candidate(s) who have already been assigned to another ${event.eventType} event"
                )
              }
              val toPersist = persisted.CandidateAllocation.fromCommand(newAllocations)
              candidateAllocationRepo.save(toPersist).flatMap { _ =>
                updateStatusInvited(toPersist, event.eventType).flatMap { _ =>
                  Future.sequence(newAllocations.allocations.map(sendCandidateEmail(_, event, UniqueIdentifier(newAllocations.sessionId))))
                }
              }.map { _ =>
                command.CandidateAllocations(newAllocations.eventId, newAllocations.sessionId, toPersist)
              }
            }).flatten
          case _ =>
            (for {
              alreadyAssigned <- isAlreadyAssignedToAnEvent(newAllocations.eventId, newAllocations)
            } yield {
              if (alreadyAssigned) {
                throw CandidateAlreadyAssignedToOtherEventException(
                  s"There are candidate(s) who have already been assigned to another ${event.eventType} event"
                )
              }
              updateExistingAllocations(existingAllocation, newAllocations, event.eventType, append).flatMap { res =>
                // Do not send emails to the existing allocations
                val existingIds = existingAllocation.allocations.map(_.id)
                Future.sequence(
                  newAllocations.allocations
                    .filter(alloc => !existingIds.contains(alloc.id))
                    .map(sendCandidateEmail(_, event, UniqueIdentifier(newAllocations.sessionId)))
                ).map { _ => res }
              }
            }).flatten
        }
      }
    }
  }

  def findCandidatesEligibleForEventAllocation(assessmentCentreLocation: String, eventType: EventType, eventDescription: String) = {
    eventType match {
      case EventType.FSAC =>
        applicationRepo.findCandidatesEligibleForEventAllocation(List(assessmentCentreLocation), eventType, schemeId = None)

      case EventType.FSB =>
        val schemeForFsb = schemeRepository.getSchemeForFsb(eventDescription).id

        def filterCandidate(applicationId: String): Future[Boolean] = {
          applicationRepo.getCurrentSchemeStatus(applicationId).map { css =>
            // This actually looks for Amber or Green but we are only processing candidates whose fsb scheme is Green so it is ok
            val firstResidualPref = firstResidualPreference(css)
            logger.debug(s"findCandidatesEligibleForEventAllocation - assessmentCentreLocation=$assessmentCentreLocation")
            logger.debug(s"findCandidatesEligibleForEventAllocation - eventType=$eventType")
            logger.debug(s"findCandidatesEligibleForEventAllocation - eventDescription=$eventDescription")
            logger.debug(s"findCandidatesEligibleForEventAllocation - applicationId=$applicationId")
            logger.debug(s"findCandidatesEligibleForEventAllocation - firstResidualPref=$firstResidualPref")
            logger.debug(s"findCandidatesEligibleForEventAllocation - schemeForFsb=$schemeForFsb")
            val include = firstResidualPref.exists(ser => ser.schemeId == schemeForFsb)
            logger.debug(s"findCandidatesEligibleForEventAllocation - should include this candidate = $include")
            include
          }
        }

        for {
          // Note that to appear in the potentialCandidates list, the scheme associated with the fsb must be Green in the css
          // so if it is Amber the candidate will not appear in the list
          potentialCandidates <- applicationRepo.findCandidatesEligibleForEventAllocation(
            List(assessmentCentreLocation), eventType, schemeId = Some(schemeForFsb)
          )
          _ = logger.debug(s"findCandidatesEligibleForEventAllocation - found these potential candidates: ${potentialCandidates.candidates}")
          filtered <- Future.traverse(potentialCandidates.candidates) { candidate =>
            filterCandidate(candidate.applicationId).map { shouldInclude =>
              candidate -> shouldInclude
            }
          }
          validCandidates = filtered.collect { case (c, true) => c }
        } yield {
          CandidatesEligibleForEventResponse(validCandidates, validCandidates.size)
        }
    }
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

  def deleteOneAllocation(eventId: String, sessionId: String, applicationId: String, version: String)(
    implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      _ <- candidateAllocationRepo.deleteOneAllocation(eventId, sessionId, applicationId, version)
    } yield ()
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

    val eventDate = DateTimeFormatter.ofPattern(dateFormat).format(event.date)
    val localTime = event.sessions.find(_.id == sessionId.toString).map(_.startTime).getOrElse(event.startTime)
    val eventTime = DateTimeFormatter.ofPattern(
      if (DateTimeFormatter.ofPattern("mm").format(localTime) == "00") "ha" else "h:mma"
    ).format(localTime)

    val tenBeforeEventDate = event.date.minusDays(10)
    val deadlineDateTime = if (tenBeforeEventDate.isBefore(LocalDate.now())) {
      DateTimeFormatter.ofPattern(dateFormat).format(LocalDate.now.plusDays(1))
    } else {
      DateTimeFormatter.ofPattern(dateFormat).format(tenBeforeEventDate)
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
        DateTimeFormatter.ofPattern("d MMMM YYYY").format(event.date)
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
