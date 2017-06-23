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

import common.{ FutureEx, TryEx }
import model.Exceptions.AssessorNotFoundException
import model.persisted.AssessorAvailability
import model.persisted.eventschedules.Location
import org.joda.time.LocalDate
import repositories._
import repositories.events.{ LocationsWithVenuesRepository, LocationsWithVenuesYamlRepository }

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

object AssessorService extends AssessorService {
  val assessorRepository: AssessorMongoRepository = repositories.assessorRepository
  val locationsWithVenuesRepo: LocationsWithVenuesRepository = LocationsWithVenuesYamlRepository
}

trait AssessorService {
  val assessorRepository: AssessorRepository
  val locationsWithVenuesRepo: LocationsWithVenuesRepository

  lazy val locations: Set[Location] = locationsWithVenuesRepo.allLocations

  def saveAssessor(userId: String, assessor: model.exchange.Assessor): Future[Unit] = {
    assessorRepository.find(userId).flatMap {
      case Some(existing) =>
        // TODO: If we change skills, we have to decide if we want to reset the availability
        val assessorToPersist = model.persisted.Assessor(
          userId, assessor.skills, assessor.civilServant, existing.availability
        )
        assessorRepository.save(assessorToPersist).map(_ => ())
      case _ =>
        val assessorToPersist = model.persisted.Assessor(
          userId, assessor.skills, assessor.civilServant, Nil
        )
        assessorRepository.save(assessorToPersist).map(_ => ())
    }
  }

  def addAvailability(userId: String, newAvailabilities: List[model.persisted.AssessorAvailability]): Future[Unit] = {
    assessorRepository.find(userId).flatMap {
      case Some(existing) =>
        val mergedAvailability = existing.availability ++ newAvailabilities
        val assessorAvailabilityToPersist = model.persisted.Assessor(userId, existing.skills, existing.civilServant, mergedAvailability)
        assessorRepository.save(assessorAvailabilityToPersist).map(_ => ())
      case _ => throw AssessorNotFoundException(userId)
    }
  }

  def findAvailability(userId: String): Future[model.exchange.AssessorAvailability] = {
    for {
      assessorOpt <- assessorRepository.find(userId)
    } yield {
      assessorOpt.fold(throw AssessorNotFoundException(userId)) {
        model.exchange.AssessorAvailability.apply
      }
    }
  }

  def findAvailabilities(location: Location, date: LocalDate): Future[Seq[model.exchange.AssessorAvailability]] = {
    assessorRepository.findAvailabilitiesForVenueAndDate()
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

  def countSubmittedAvailability(): Future[Int] = {
    //assessorRepository.countSubmittedAvailability
    Future.successful(0)
  }

  def exchangeToPersistedAvailability(a: model.exchange.AssessorAvailability): Try[List[model.persisted.AssessorAvailability]] = {
    TryEx.traverseSerial(a.availability) { case (locationName, dates) =>
      locationsWithVenuesRepo.location(locationName).map { location =>
        dates.map(d => AssessorAvailability(location, d))
      }
    }.map(_.toList.flatten)
  }


}
