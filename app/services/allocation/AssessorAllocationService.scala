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

package services.allocation

import com.google.inject.name.Named
import connectors._
import javax.inject.{ Inject, Singleton }
import model.Exceptions.OptimisticLockException
import model.command.{ AssessorAllocation, AssessorAllocations }
import model.persisted.eventschedules.Event
import model.{ command, exchange, persisted, _ }
import repositories.AssessorAllocationRepository
import repositories.application.GeneralApplicationRepository
import services.allocation.AssessorAllocationService.CouldNotFindAssessorContactDetails
import services.events.EventsService
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessorAllocationService {
    case class CouldNotFindAssessorContactDetails(userId: String) extends Exception(userId)
}

@Singleton
class AssessorAllocationService @Inject() (assessorAllocationRepo: AssessorAllocationRepository,
                                           applicationRepo: GeneralApplicationRepository,
                                           eventsService: EventsService,
                                           allocationServiceCommon: AllocationServiceCommon, // Breaks circular dependencies
                                           val eventService: StcEventService,
                                           authProviderClient: AuthProviderClient,
                                           @Named("CSREmailClient") emailClient: OnlineTestEmailClient //TODO:fix changed type
                                          ) extends EventSink {

  def getAllocations(eventId: String): Future[exchange.AssessorAllocations] = {
    allocationServiceCommon.getAllocations(eventId)
  }

  def getAllocation(eventId: String, userId: String): Future[Option[exchange.AssessorAllocation]] = {
    assessorAllocationRepo.find(userId, eventId).map { allocationOpt =>
      allocationOpt.map { allocation =>
        exchange.AssessorAllocation(allocation.id, allocation.status, model.exchange.AssessorSkill(allocation.allocatedAs, ""))
      }
    }
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

  private def notifyNewlyAllocatedAssessors(allocations: command.AssessorAllocations)(implicit hc: HeaderCarrier): Future[Unit] = {
    getContactDetails(allocations).map {
      _.map { case (contactDetailsForUser, eventDetails, allocationForUser) =>
        emailClient.sendAssessorAllocatedToEvent(
          contactDetailsForUser.email,
          contactDetailsForUser.firstName + " " + contactDetailsForUser.lastName,
          eventDetails.date.toString("d MMMM YYYY"),
          allocationForUser.allocatedAs.displayText,
          allocationForUser.allocatedAs.name.toString,
          eventDetails.eventType.toString,
          eventDetails.location.name,
          eventDetails.startTime.toString("h:mma")
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
          eventDetails.startTime.toString("h:ma")
        )
      }
    }.map(_ => ())
  }

  private def notifyAllocationUnallocatedAssessors(
                                                    allocations: command.AssessorAllocations
                                                  )(implicit hc: HeaderCarrier): Future[Unit] = {
    val eligibleAllocations = allocations.copy(allocations = allocations.allocations.filterNot(_.status == AllocationStatuses.DECLINED))
    getContactDetails(eligibleAllocations).map { userInfo =>
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
          throw CouldNotFindAssessorContactDetails(contactDetail.userId)
        )
        allocationForUser = allocations.allocations.find(_.id == contactDetailsForUser.userId).get
      } yield (contactDetailsForUser, eventDetails, allocationForUser)
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
}
