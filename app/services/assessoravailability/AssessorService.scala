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

package services.assessoravailability

import common.FutureEx
import model.AllocationStatuses.AllocationStatus
import model.{ SerialUpdateResult, exchange, persisted }
import model.Exceptions.AssessorNotFoundException
import model.command.AllocationWithEvent
import model.exchange.{ AssessorSkill, UpdateAllocationStatusRequest }
import model.persisted.eventschedules.SkillType.SkillType
import model.persisted.assessor.AssessorStatus
import org.joda.time.LocalDate
import repositories.{ AssessorAllocationRepository, AssessorAllocationMongoRepository, AssessorMongoRepository, AssessorRepository }
import repositories.events.{ EventsMongoRepository, EventsRepository, LocationsWithVenuesInMemoryRepository, LocationsWithVenuesRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessorService extends AssessorService {
  val assessorRepository: AssessorMongoRepository = repositories.assessorRepository
  val allocationRepo: AssessorAllocationMongoRepository = repositories.assessorAllocationRepository
  val eventsRepo: EventsMongoRepository = repositories.eventsRepository
  val locationsWithVenuesRepo: LocationsWithVenuesRepository = LocationsWithVenuesInMemoryRepository
}

trait AssessorService {
  val assessorRepository: AssessorRepository
  val allocationRepo: AssessorAllocationRepository
  val eventsRepo: EventsRepository
  val locationsWithVenuesRepo: LocationsWithVenuesRepository

  def saveAssessor(userId: String, assessor: model.exchange.Assessor): Future[Unit] = {
    assessorRepository.find(userId).flatMap {
      case Some(existing) =>
        // TODO: If we change skills, we have to decide if we want to reset the availability
        val assessorToPersist = model.persisted.assessor.Assessor(
          userId, assessor.skills, assessor.sifterSchemes, assessor.civilServant, existing.availability, existing.status
        )
        assessorRepository.save(assessorToPersist).map(_ => ())
      case _ =>
        val assessorToPersist = model.persisted.assessor.Assessor(
          userId, assessor.skills, assessor.sifterSchemes, assessor.civilServant, Nil, AssessorStatus.CREATED
        )
        assessorRepository.save(assessorToPersist).map(_ => ())
    }
  }

  def addAvailability(userId: String, assessorAvailabilities: List[model.exchange.AssessorAvailability]): Future[Unit] = {
    assessorRepository.find(userId).flatMap {
      case Some(existing) =>
        exchangeToPersistedAvailability(assessorAvailabilities).flatMap { newAvailabilities =>
          val mergedAvailability = existing.availability ++ newAvailabilities
          val assessorToPersist = model.persisted.assessor.Assessor(userId, existing.skills, existing.sifterSchemes,
            existing.civilServant, mergedAvailability, AssessorStatus.AVAILABILITIES_SUBMITTED)

          assessorRepository.save(assessorToPersist).map(_ => ())
        }
      case _ => throw AssessorNotFoundException(userId)
    }
  }

  def findAvailability(userId: String): Future[List[model.exchange.AssessorAvailability]] = {
    for {
      assessorOpt <- assessorRepository.find(userId)
    } yield {
      assessorOpt.fold(throw AssessorNotFoundException(userId)) {
        assessor => assessor.availability.map { availability =>
          model.exchange.AssessorAvailability.apply(availability)
        }
      }
    }
  }

  def findAvailabilitiesForLocationAndDate(locationName: String, date: LocalDate,
    skills: Seq[SkillType]): Future[Seq[model.exchange.Assessor]] = for {
    location <- locationsWithVenuesRepo.location(locationName)
    assessorList <- assessorRepository.findAvailabilitiesForLocationAndDate(location, date, skills)
  } yield assessorList.map { assessor =>
      model.exchange.Assessor.apply(assessor)
  }

  def findAssessor(userId: String): Future[model.exchange.Assessor] = {
    for {
      assessorOpt <- assessorRepository.find(userId)
    } yield {
      assessorOpt.fold(throw AssessorNotFoundException(userId)) {
        assessor => model.exchange.Assessor.apply(assessor)
      }
    }
  }

  def findAllocations(assessorId: String, status: Option[AllocationStatus] = None): Future[Seq[AllocationWithEvent]] = {
    allocationRepo.find(assessorId, status).flatMap { allocations =>
      FutureEx.traverseSerial(allocations) { allocation =>
        eventsRepo.getEvent(allocation.eventId).map { event =>
          AllocationWithEvent(
            allocation.id,
            event.id,
            event.date,
            event.startTime,
            event.endTime,
            event.venue,
            event.location,
            event.eventType,
            allocation.status,
            AssessorSkill.SkillMap(allocation.allocatedAs)
          )
        }
      }
    }
  }

  def countSubmittedAvailability(): Future[Int] = {
    assessorRepository.countSubmittedAvailability
  }

  def updateAssessorAllocationStatuses(statusUpdates: Seq[UpdateAllocationStatusRequest]
  ): Future[SerialUpdateResult[UpdateAllocationStatusRequest]] = {

    val rawResult = FutureEx.traverseSerial(statusUpdates) { statusUpdate =>
      FutureEx.futureToEither(statusUpdate,
        allocationRepo.updateAllocationStatus(statusUpdate.assessorId, statusUpdate.eventId, statusUpdate.newStatus)
      )
    }

    rawResult.map(SerialUpdateResult.fromEither)

  }


  private def exchangeToPersistedAvailability(a: Seq[exchange.AssessorAvailability]): Future[Seq[persisted.assessor.AssessorAvailability]] = {
    FutureEx.traverseSerial(a) { availability =>
      locationsWithVenuesRepo.location(availability.location).map { location =>
        model.persisted.assessor.AssessorAvailability(location, availability.date)
      }
    }
  }


}
