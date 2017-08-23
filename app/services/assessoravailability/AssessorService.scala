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
import connectors.{ AuthProviderClient, CSREmailClient, EmailClient }
import model.AllocationStatuses.AllocationStatus
import model.Exceptions.{ AssessorNotFoundException, OptimisticLockException }
import model.command.AllocationWithEvent
import model.exchange.{ AssessorAvailabilities, AssessorSkill, UpdateAllocationStatusRequest }
import model.persisted.assessor.{ Assessor, AssessorStatus }
import model.persisted.eventschedules.{ Event, Location, SkillType }
import model.persisted.eventschedules.SkillType.SkillType
import model.{ SerialUpdateResult, UniqueIdentifier, exchange, persisted }
import org.joda.time.{ DateTime, LocalDate }
import repositories.events.{ LocationsWithVenuesInMemoryRepository, LocationsWithVenuesRepository }
import repositories.{ AssessorAllocationMongoRepository, AssessorAllocationRepository, AssessorMongoRepository, AssessorRepository }
import services.events.EventsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessorService extends AssessorService {
  val assessorRepository: AssessorMongoRepository = repositories.assessorRepository
  val allocationRepo: AssessorAllocationMongoRepository = repositories.assessorAllocationRepository
  val eventsService: EventsService = EventsService
  val locationsWithVenuesRepo: LocationsWithVenuesRepository = LocationsWithVenuesInMemoryRepository
  val authProviderClient: AuthProviderClient = AuthProviderClient
  val emailClient: EmailClient = CSREmailClient
}

trait AssessorService {
  val assessorRepository: AssessorRepository
  val allocationRepo: AssessorAllocationRepository
  val eventsService: EventsService
  val locationsWithVenuesRepo: LocationsWithVenuesRepository
  def authProviderClient: AuthProviderClient
  def emailClient: EmailClient

  private def newVersion = Some(UniqueIdentifier.randomUniqueIdentifier.toString())

  def updateVersion(userId: String): Future[Unit] = {
    assessorRepository.find(userId).flatMap {
      case Some(existing) =>
        assessorRepository.save(existing.copy(version = newVersion)).map(_ => ())
      case None => throw AssessorNotFoundException(userId)
    }
  }

  def saveAssessor(userId: String, assessor: model.exchange.Assessor): Future[Unit] = {
    assessorRepository.find(userId).flatMap {
      case Some(existing) if assessor.version != existing.version =>
        throw OptimisticLockException(s"Assessor profile $userId has been modified.")
      case Some(existing) =>
        // TODO: If we change skills, we have to decide if we want to reset the availability
        val assessorToPersist = model.persisted.assessor.Assessor(
          userId,
          newVersion,
          assessor.skills,
          assessor.sifterSchemes,
          assessor.civilServant,
          existing.availability,
          existing.status
        )
        assessorRepository.save(assessorToPersist).map(_ => ())
      case _ =>
        val assessorToPersist = model.persisted.assessor.Assessor(
          userId,
          newVersion,
          assessor.skills,
          assessor.sifterSchemes,
          assessor.civilServant,
          Set.empty,
          AssessorStatus.CREATED
        )
        assessorRepository.save(assessorToPersist).map(_ => ())
    }
  }

  def saveAvailability(assessorAvailabilities: AssessorAvailabilities): Future[Unit] = {
    val userId = assessorAvailabilities.userId
    assessorRepository.find(userId).flatMap {
      case Some(existing) if assessorAvailabilities.version != existing.version =>
        throw OptimisticLockException(s"Assessor profile $userId has been modified.")
      case Some(existing) =>
        exchangeToPersistedAvailability(assessorAvailabilities.availabilities).flatMap { newAvailabilities =>
          val assessorToPersist =
            model.persisted.assessor.Assessor(
              userId,
              newVersion,
              existing.skills,
              existing.sifterSchemes,
              existing.civilServant,
              newAvailabilities,
              AssessorStatus.AVAILABILITIES_SUBMITTED)

          assessorRepository.save(assessorToPersist).map(_ => ())
        }
      case _ => throw AssessorNotFoundException(userId)
    }
  }

  def findAvailability(userId: String): Future[AssessorAvailabilities] = {
    assessorRepository.find(userId).map {
      case None => throw AssessorNotFoundException(userId)
      case Some(a) => AssessorAvailabilities(a)
    }
  }

  def findAvailabilitiesForLocationAndDate(
    locationName: String, date: LocalDate, skills: Seq[SkillType]
  ): Future[Seq[model.exchange.Assessor]] = {
    for {
      location <- locationsWithVenuesRepo.location(locationName)
      assessorList <- assessorRepository.findAvailabilitiesForLocationAndDate(location, date, skills)
    } yield assessorList.map(assessor => model.exchange.Assessor(assessor))
  }


  def findAssessor(userId: String): Future[model.exchange.Assessor] = {
    assessorRepository.find(userId).map {
      case None => throw AssessorNotFoundException(userId)
      case Some(assessor) => model.exchange.Assessor(assessor)
    }
  }

  def findAllocations(assessorId: String, status: Option[AllocationStatus] = None): Future[Seq[AllocationWithEvent]] = {
    allocationRepo.find(assessorId, status).flatMap { allocations =>
      FutureEx.traverseSerial(allocations) { allocation =>
        eventsService.getEvent(allocation.eventId).map { event =>
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

  def updateAssessorAllocationStatuses(
    statusUpdates: Seq[UpdateAllocationStatusRequest]
  ): Future[SerialUpdateResult[UpdateAllocationStatusRequest]] = {

    val rawResult = FutureEx.traverseSerial(statusUpdates) { req =>
      FutureEx.futureToEither(
        req,
        updateVersion(req.assessorId).flatMap { _ =>
          allocationRepo.updateAllocationStatus(req.assessorId, req.eventId, req.newStatus)
        }
      )
    }
    rawResult.map(SerialUpdateResult.fromEither)
  }

  def findUnavailableAssessors(skills: Seq[SkillType], location: Location, date: LocalDate): Future[Seq[Assessor]] = {
    assessorRepository.findUnavailableAssessors(skills, location, date)
  }

  def assessorToEventsMappingSince(eventsSince: DateTime): Future[Map[Assessor, Seq[Event]]] = {
    val newlyCreatedEvents = eventsService.getEventsCreatedAfter(eventsSince)
    val assessorToEventsTableFut: Future[Seq[(Assessor, Event)]] = newlyCreatedEvents.flatMap { events =>
      val mapping = events.map { event =>
        val skills = event.skillRequirements.keySet.map(SkillType.withName).toSeq
        findUnavailableAssessors(skills, event.location, event.date).map { assessors =>
          assessors.map( _ -> event)
        }
      }
      Future.sequence(mapping).map(_.flatten)
    }
    val assessorEventsMapping = assessorToEventsTableFut.map(_.groupBy(_._1).map {
      case (assessor, assessorEventSeq) => assessor -> assessorEventSeq.map(_._2)
    })
    assessorEventsMapping
  }

  def notifyAssessorsOfNewEvents(lastNotificationDate: DateTime)(implicit hc: HeaderCarrier): Future[Seq[Unit]] = {
    val assessorEventsMapping: Future[Map[Assessor, Seq[Event]]] = assessorToEventsMappingSince(lastNotificationDate)
    assessorEventsMapping.flatMap { assessorToEvent =>
      val assessorsIds = assessorToEvent.keySet.map(_.userId).toSeq
      val assessorsContactDetailsFut = authProviderClient.findByUserIds(assessorsIds)
      assessorsContactDetailsFut.flatMap { assessorsContactDetails =>
        Future.sequence(
          assessorToEvent.flatMap { case (assessor, assessorEvents) =>
            assessorsContactDetails.find(_.userId == assessor.userId).map { contact =>
              val (htmlBody, txtBody) = buildEmailContent(assessorEvents)
              emailClient.notifyAssessorsOfNewEvents(contact.email, contact.firstName, htmlBody, txtBody)
            }
          }
        )
      }
    }.map(_.toSeq)
  }

  private def buildEmailContent(events: Seq[Event]): (String, String) = {
    def buildEmailTxt: String = {
      val eventsStr = events.map { event =>
        s"${event.date.toString("EEEE, dd MMMM YYYY")} (${event.eventType} - ${event.location.name})"
      }.mkString("\n")
      eventsStr
    }

    def buildEmailHtml: String = {
      val eventsHtml = events.map { event =>
        s"<li>${event.date.toString("EEEE, dd MMMM YYYY")} (${event.eventType} - ${event.location.name}) </li>"
      }.mkString("")
      val body =
        s"""
           |<ul>
           |$eventsHtml
           |</ul>
      """.stripMargin
      body
    }

    (buildEmailHtml, buildEmailTxt)
  }

  private def exchangeToPersistedAvailability(
    a: Set[exchange.AssessorAvailability]
  ): Future[Set[persisted.assessor.AssessorAvailability]] = {
    FutureEx.traverseSerial(a) { availability =>
      locationsWithVenuesRepo.location(availability.location).map { location =>
        model.persisted.assessor.AssessorAvailability(location, availability.date)
      }
    }
  }

}
