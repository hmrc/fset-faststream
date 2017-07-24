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

import connectors.{ AuthProviderClient, CSREmailClient, EmailClient, ExchangeObjects }
import model.Exceptions.OptimisticLockException
import model._
import model.command.{ AssessorAllocation, AssessorAllocations, CandidateAllocation }
import model.exchange.{ CandidateAllocationPerSession, EventAssessorAllocationsSummaryPerSkill, EventWithAllocationsSummary }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Event, Venue }
import model.stc.EmailEvents.{ CandidateAllocationConfirmationRequest, CandidateAllocationConfirmed }
import model.stc.StcEventTypes.StcEvents
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsMongoRepository
import repositories.personaldetails.PersonalDetailsMongoRepository
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

  def getCandidateAllocations(eventId: String, sessionId: String): Future[exchange.CandidateAllocations] = {
    candidateAllocationRepo.allocationsForSession(eventId, sessionId).map { a => exchange.CandidateAllocations.apply(a) }
  }

  def allocate(newAllocations: command.AssessorAllocations)(implicit hc: HeaderCarrier): Future[Unit] = {
    assessorAllocationRepo.allocationsForEvent(newAllocations.eventId).flatMap {
      case Nil =>
        for {
          _ <- assessorAllocationRepo.save(persisted.AssessorAllocation.fromCommand(newAllocations))
          _ <- notifyNewlyAllocatedAssessors(newAllocations)
        } yield ()
      case existingAllocations => updateExistingAllocations(existingAllocations, newAllocations).map(_ => ())
    }
  }

  private val dateFormat = "dd MMMM YYYY"
  private val timeFormat = "HH:mma"

  private def notifyNewlyAllocatedAssessors(allocations: command.AssessorAllocations)(implicit hc: HeaderCarrier): Future[Unit] = {
    getContactDetails(allocations).map {
      _.map { case (contactDetailsForUser, eventDetails, allocationForUser) =>
        emailClient.sendAssessorAllocatedToEvent(
          contactDetailsForUser.email,
          contactDetailsForUser.firstName + " " + contactDetailsForUser.lastName,
          eventDetails.date.toString("d MMMM YYYY"),
          allocationForUser.allocatedAs.displayText,
          eventDetails.eventType.toString,
          eventDetails.location.name,
          eventDetails.startTime.toString("ha")
        )
      }
    }.map(_ => ())
  }

  private def notifyAllocationChangedAssessors(allocations: command.AssessorAllocations)(implicit hc: HeaderCarrier): Future[Unit] = {
    getContactDetails(allocations).map { userInfo =>
      userInfo.map { case (contactDetailsForUser, eventDetails, allocationForUser) =>
        emailClient.sendAssessorEventAllocationChanged(
          contactDetailsForUser.email,
          contactDetailsForUser.firstName + " " + contactDetailsForUser.lastName,
          eventDetails.date.toString("d MMMM YYYY"),
          allocationForUser.allocatedAs.displayText,
          eventDetails.eventType.toString,
          eventDetails.location.name,
          eventDetails.startTime.toString("ha")
        )
      }
    }.map(_ => ())
  }

  private def notifyAllocationUnallocatedAssessors(
    allocations: command.AssessorAllocations
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    getContactDetails(allocations).map { userInfo =>
      userInfo.map { case (contactDetailsForUser, eventDetails, _) =>
        emailClient.sendAssessorUnAllocatedFromEvent(
          contactDetailsForUser.email,
          contactDetailsForUser.firstName + " " + contactDetailsForUser.lastName,
          eventDetails.date.toString("d MMMM YYYY")
        )
      }
    }.map(_ => ())
  }

  private def getContactDetails(allocations: AssessorAllocations)(implicit hc: HeaderCarrier)
  : Future[Seq[(ExchangeObjects.Candidate, Event, AssessorAllocation)]] = {
    if (allocations.allocations.isEmpty) {
      Future.successful(Seq())
    } else {
      for {
        eventDetails <- eventsService.getEvent(allocations.eventId)
        contactDetails <- authProviderClient.findByUserIds(allocations.allocations.map(_.id))
      } yield for {
        contactDetail <- contactDetails
        contactDetailsForUser = contactDetails.find(_.userId == contactDetail.userId).getOrElse(
          throw new Exception("Could not find contact details for assessor user " + contactDetail.userId)
        )
        allocationForUser = allocations.allocations.find(_.id == contactDetailsForUser.userId).get
      } yield (contactDetailsForUser, eventDetails, allocationForUser)
    }
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

  private def getAllocationDifferences(existingAllocationsUnsanitised: Seq[persisted.AssessorAllocation],
    newAllocationsUnsanitised: Seq[persisted.AssessorAllocation]) = {

    // Make versions equal so we can do comparison based on values
    val existingAllocations = existingAllocationsUnsanitised.map(_.copy(version = "version"))
    val newAllocations = newAllocationsUnsanitised.map(_.copy(version = "version"))

    // Check for changes to the assessor guest list
    val changedUsers = existingAllocations.flatMap { existingAllocation =>
      newAllocations.find(_.id == existingAllocation.id).flatMap { matchingItem =>
        if (matchingItem != existingAllocation) {
          Some(matchingItem)
        } else {
          None
        }
      }
    }

    val removedUsers = existingAllocations
      .filterNot(user => changedUsers.exists(_.id == user.id))
      .filterNot(user => newAllocations.exists(_.id == user.id))
    val newUsers = newAllocations
      .filterNot(user => changedUsers.exists(_.id == user.id))
      .filterNot(user => existingAllocations.exists(_.id == user.id))

    (changedUsers, removedUsers, newUsers)
  }

  private def updateExistingAllocations(existingAllocations: Seq[persisted.AssessorAllocation],
                                        newAllocations: command.AssessorAllocations)(implicit hc: HeaderCarrier): Future[Unit] = {

    // If versions match there has been no update from another user while this user was editing, do update
    if (existingAllocations.forall(_.version == newAllocations.version)) {
      val toPersist = persisted.AssessorAllocation.fromCommand(newAllocations)

      val (changedUsers, removedUsers, newUsers) = getAllocationDifferences(existingAllocations, toPersist)

      for {
        // Persist the changes
        _ <- assessorAllocationRepo.delete(existingAllocations)
        _ <- assessorAllocationRepo.save(toPersist).map(_ => ())
        // Notify users
        _ <- notifyNewlyAllocatedAssessors(AssessorAllocations(newAllocations.eventId, newUsers))
        _ <- notifyAllocationChangedAssessors(AssessorAllocations(newAllocations.eventId, changedUsers))
        _ <- notifyAllocationUnallocatedAssessors(AssessorAllocations(newAllocations.eventId, removedUsers))
      } yield ()
    } else {
      throw OptimisticLockException(s"Stored allocations for event ${newAllocations.eventId} have been updated since reading")
    }
  }

  def getEventsWithAllocationsSummary(venue: Venue, eventType: EventType): Future[List[EventWithAllocationsSummary]] = {
    eventsService.getEvents(eventType, venue).flatMap { events =>
      val res = events.map { event =>
        getAllocations(event.id).flatMap { allocations =>
          val allocationsGroupedBySkill = allocations.allocations.groupBy(_.allocatedAs)
          val allocationsGroupedBySkillWithSummary = allocationsGroupedBySkill.map { allocationGroupedBySkill =>
            val assessorAllocation = allocationGroupedBySkill._2
            val skill = allocationGroupedBySkill._1.name
            val allocated = assessorAllocation.length
            val confirmed = assessorAllocation.count(_.status == AllocationStatuses.CONFIRMED)
            EventAssessorAllocationsSummaryPerSkill(skill, allocated, confirmed)
          }.toList
          val candidateAllocBySession = event.sessions.sortBy(_.startTime.getMillisOfDay).map { session =>
            getCandidateAllocations(event.id, session.id).map { candidateAllocations =>
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
}
