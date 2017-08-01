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
import model.persisted.{ ContactDetails, PersonalDetails }
import model.persisted.eventschedules.Event
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
  def emailClient: EmailClient = CSREmailClient

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
  private val timeFormat = "HH:mma"


  def getCandidateAllocations(eventId: String, sessionId: String): Future[exchange.CandidateAllocations] = {
    candidateAllocationRepo.allocationsForSession(eventId, sessionId).map { a => exchange.CandidateAllocations.apply(a) }
  }

  def unAllocateCandidates(allocations: List[model.persisted.CandidateAllocation])
                          (implicit hc: HeaderCarrier): Future[SerialUpdateResult[persisted.CandidateAllocation]] = {
    // For each allocation, reset the progress status and delete the corresponding allocation
    val res = FutureEx.traverseSerial(allocations) { allocation =>
      val fut = candidateAllocationRepo.removeCandidateAllocation(allocation).flatMap { _ =>
        applicationRepo.resetApplicationAllocationStatus(allocation.id).flatMap { _ =>
          notifyCandidateUnallocated(allocation.eventId, model.command.CandidateAllocation.fromPersisted(allocation))
        }
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

}
