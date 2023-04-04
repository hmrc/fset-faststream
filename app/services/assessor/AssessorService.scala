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

package services.assessor

import com.google.inject.name.Named
import common.FutureEx
import connectors.{AuthProviderClient, OnlineTestEmailClient}

import javax.inject.{Inject, Singleton}
import model.AllocationStatuses.AllocationStatus
import model.Exceptions._
import model.command.AllocationWithEvent
import model.exchange.{AssessorAvailabilities, AssessorSkill, UpdateAllocationStatusRequest}
import model.persisted.AssessorAllocation
import model.persisted.assessor.{Assessor, AssessorStatus}
import model.persisted.eventschedules.SkillType.SkillType
import model.persisted.eventschedules.{Event, Location, SkillType}
import model.{SerialUpdateResult, UniqueIdentifier, exchange, persisted}
import org.joda.time.{DateTime, LocalDate}
import play.api.Logging
import repositories.events.LocationsWithVenuesRepository
import repositories.{AssessorAllocationRepository, AssessorRepository}
import services.events.EventsService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssessorService @Inject() (assessorRepository: AssessorRepository,
                                 assessorAllocationRepo: AssessorAllocationRepository,
                                 eventsService: EventsService,
                                 locationsWithVenuesRepo: LocationsWithVenuesRepository,
                                 authProviderClient: AuthProviderClient,
                                 @Named("CSREmailClient") emailClient: OnlineTestEmailClient //TODO:fix changed type
                                )(implicit ec: ExecutionContext) extends Logging {

  private[assessor] def newVersion = Some(UniqueIdentifier.randomUniqueIdentifier.toString())

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
        updateAssessor(userId, assessor, existing)
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

  private def updateAssessor(userId: String, assessor: model.exchange.Assessor, existing: Assessor): Future[Unit] = {
    val oldSkills = existing.skills.toSet
    val newSkills = assessor.skills.toSet
    val skillsToRemove = oldSkills -- newSkills

    hasFutureAssessorAllocations(userId, skillsToRemove).map { hasFutureAssessorAllocationsVal =>
      if (hasFutureAssessorAllocationsVal) {
        throw CannotUpdateAssessorWhenSkillsAreRemovedAndFutureAllocationExistsException(userId,
          s"You cannot remove skills from user with id $userId when the user has been allocated to those future events with those skills." +
          " Please remove the allocations from those events before removing the skills")
      } else {
        val assessorToPersist = model.persisted.assessor.Assessor(
          userId, newVersion, assessor.skills, assessor.sifterSchemes, assessor.civilServant, existing.availability, existing.status
        )
        assessorRepository.save(assessorToPersist).map(_ => ())
      }
    }
  }

  private def hasFutureAssessorAllocations(userId: String, skills: Set[String]): Future[Boolean] = {
    val today = LocalDate.now()

    def filterOnlyFutureAssessorAllocations(assessorAllocations: Seq[AssessorAllocation]) = {
      def addIsFutureEvent(assessorAllocations: Seq[AssessorAllocation]) = {
        def isFutureEvent(eventId: String): Future[Boolean] = {
          eventsService.getEvent(eventId).map { event =>
            val eventDate = event.date
            today.isBefore(eventDate)
          }
        }

        assessorAllocations.foldLeft(Future.successful(List.empty[(AssessorAllocation, Boolean)])) { (futAccumulator, item) =>
          futAccumulator.flatMap { accumulator => isFutureEvent(item.eventId).map((item, _)).map(_ :: accumulator) }
        }
      }
      addIsFutureEvent(assessorAllocations).map(tuple => tuple.filter(_._2).map(_._1))

      case class AssessorAllocationIsFutureEvent(assessorAlllocation: AssessorAllocation, isFutureEvent: Boolean)
      addIsFutureEvent(assessorAllocations).map(_.map(tuple =>
        AssessorAllocationIsFutureEvent(tuple._1, tuple._2)).filter(_.isFutureEvent).map(_.assessorAlllocation))
    }

    if (skills.isEmpty) {
      Future.successful(false)
    } else {
      for {
        allAssessorAllocations <- assessorAllocationRepo.find(userId)
        onlyFutureAssessorAllocations <- filterOnlyFutureAssessorAllocations(allAssessorAllocations)
      } yield {
        val firstAssessorAllocationWithSkillToRemove = onlyFutureAssessorAllocations.find(assessorAllocation =>
          skills.contains(assessorAllocation.allocatedAs))
        firstAssessorAllocationWithSkillToRemove.isDefined
      }
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

  def findAssessorsByIds(userIds: Seq[String]): Future[Seq[model.exchange.Assessor]] = {
    assessorRepository.findByIds(userIds).map(_.map(a => model.exchange.Assessor(a)))
  }

  def findAssessorAllocations(assessorId: String, status: Option[AllocationStatus] = None): Future[Seq[AllocationWithEvent]] = {
    assessorAllocationRepo.find(assessorId, status).flatMap { allocations =>
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

  def findAllocations(assessorIds: Seq[String], status: Option[AllocationStatus] = None): Future[Seq[AllocationWithEvent]] = {
    assessorAllocationRepo.findAllocations(assessorIds, status).flatMap{ allocations =>
      FutureEx.traverseSerial(allocations) { alloc =>
        eventsService.getEvent(alloc.eventId).map { event =>
          AllocationWithEvent(
            alloc.id,
            event.id,
            event.date,
            event.startTime,
            event.endTime,
            event.venue,
            event.location,
            event.eventType,
            alloc.status,
            AssessorSkill.SkillMap(alloc.allocatedAs)
          )
        }
      }
    }
  }

  def countSubmittedAvailability(): Future[Long] = {
    assessorRepository.countSubmittedAvailability
  }

  def updateAssessorAllocationStatuses(
                                        statusUpdates: Seq[UpdateAllocationStatusRequest]
                                      ): Future[SerialUpdateResult[UpdateAllocationStatusRequest]] = {

    val rawResult = FutureEx.traverseSerial(statusUpdates) { req =>
      FutureEx.futureToEither(
        req,
        updateVersion(req.assessorId).flatMap { _ =>
          assessorAllocationRepo.updateAllocationStatus(req.assessorId, req.eventId, req.newStatus)
        }
      )
    }
    rawResult.map(SerialUpdateResult.fromEither)
  }

  def findUnavailableAssessors(skills: Seq[SkillType], location: Location, date: LocalDate): Future[Seq[Assessor]] = {
    assessorRepository.findUnavailableAssessors(skills, location, date)
  }

  private[assessor] def assessorToEventsMappingSince(eventsSince: Instant): Future[Map[Assessor, Seq[Event]]] = {
    val newlyCreatedEvents = eventsService.getEventsCreatedAfter(eventsSince)
    val assessorToEventsTableFut: Future[Seq[(Assessor, Event)]] = newlyCreatedEvents.flatMap { events =>
      val mapping = events.map { event =>
        val skills = event.skillRequirements.keySet.map(SkillType.withName).toSeq
        findUnavailableAssessors(skills, event.location, event.date).map { assessors =>
          assessors.map(_ -> event)
        }
      }
      Future.sequence(mapping).map(_.flatten)
    }
    val assessorEventsMapping = assessorToEventsTableFut.map(_.groupBy(_._1).map {
      case (assessor, assessorEventSeq) => assessor -> assessorEventSeq.map(_._2)
    })
    assessorEventsMapping
  }

  def notifyAssessorsOfNewEvents(lastNotificationDate: Instant)(implicit hc: HeaderCarrier): Future[Seq[Unit]] = {
    logger.info(s"Notifying assessors of new events created since $lastNotificationDate")
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
        event.eventType match {
          // FSACs are virtual in 20/21 campaign even though the location remains London or Newcastle
          case model.persisted.eventschedules.EventType.FSAC =>
            s"${event.date.toString("EEEE, dd MMMM YYYY")} (${event.eventType} - Virtual)"
          case _ =>
            s"${event.date.toString("EEEE, dd MMMM YYYY")} (${event.eventType} - ${event.location.name})"
        }
      }.mkString("\n")
      eventsStr
    }

    def buildEmailHtml: String = {
      val eventsHtml = events.map { event =>
        event.eventType match {
          // FSACs are virtual in 20/21 campaign even though the location remains London or Newcastle
          case model.persisted.eventschedules.EventType.FSAC =>
            s"<li>${event.date.toString("EEEE, dd MMMM YYYY")} (${event.eventType} - Virtual)</li>"
          case _ =>
            s"<li>${event.date.toString("EEEE, dd MMMM YYYY")} (${event.eventType} - ${event.location.name})</li>"
        }
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

  def remove(userId: UniqueIdentifier): Future[Unit] = {
    assessorRepository.find(userId.toString).flatMap {
      case Some(existing) =>
        val skillsToRemove = existing.skills.toSet
        hasFutureAssessorAllocations(userId.toString, skillsToRemove).map { hasFutureAssessorAllocationsVal =>
          if (hasFutureAssessorAllocationsVal) {
            throw CannotRemoveAssessorWhenFutureAllocationExistsException(userId.toString,
              s"You cannot remove assessor from user with id $userId when the user has been allocated to future events." +
                " Please remove the allocations from those events before removing the assessor")
          } else {
            assessorRepository.remove(userId).map(_ => ())
          }
        }
      case _ =>
        throw AssessorNotFoundException(s"Assessor with id [$userId] could not be removed because it does not exist.")
    }
  }
}
