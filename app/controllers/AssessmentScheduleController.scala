/*
 * Copyright 2016 HM Revenue & Customs
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

package controllers

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import common.FutureEx
import connectors.AssessmentScheduleExchangeObjects.Implicits._
import connectors.AssessmentScheduleExchangeObjects._
import connectors.ExchangeObjects.AllocationDetails
import connectors.{CSREmailClient, EmailClient}
import model.AssessmentScheduleCommands.ApplicationForAssessmentAllocationResult
import model.AssessmentScheduleCommands.Implicits.ApplicationForAssessmentAllocationResultFormats
import model.Commands.ApplicationAssessment
import model.ApplicationStatus._
import model.Commands.Implicits.applicationAssessmentFormat
import model.Exceptions.NotFoundException
import model.{ ApplicationStatusOrder, Commands }
import org.joda.time.LocalDate
import play.api.libs.json.Json
import play.api.mvc.{ Action, Result }
import repositories.AssessmentCentreLocation.assessmentCentreVenueFormat
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import services.applicationassessment.ApplicationAssessmentService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object AssessmentScheduleController extends AssessmentScheduleController {
  val aaRepository = applicationAssessmentRepository
  val acRepository = AssessmentCentreYamlRepository
  val aRepository = applicationRepository
  val otRepository = phase1TestRepository
  val auditService = AuditService
  val pdRepository: PersonalDetailsRepository = personalDetailsRepository
  val cdRepository: ContactDetailsRepository = contactDetailsRepository
  val emailClient = CSREmailClient
  val aaService = ApplicationAssessmentService
}

trait AssessmentScheduleController extends BaseController {
  val aaRepository: ApplicationAssessmentRepository
  val acRepository: AssessmentCentreRepository
  val aRepository: GeneralApplicationRepository
  val otRepository: Phase1TestRepository
  val auditService: AuditService
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val emailClient: EmailClient
  val aaService: ApplicationAssessmentService

  private def calculateUsedCapacity(assessments: Option[List[ApplicationAssessment]], sessionCapacity: Int): UsedCapacity = {
    assessments.map { sessionSlots =>
      UsedCapacity(sessionSlots.size, sessionSlots.exists(x => !x.confirmed))
    }.getOrElse(
      UsedCapacity(0, hasUnconfirmedAttendees = false)
    )
  }

  def getAssessmentSchedule = Action.async { implicit request =>
    val assessments = aaRepository.applicationAssessments.map(_.groupBy(x => (x.venue, x.date, x.session)))

    for {
      assessmentMap <- assessments
      assessmentCentreCapacities <- acRepository.assessmentCentreCapacities
    } yield {
      val schedule = Schedule(
        assessmentCentreCapacities.map(assessmentCentreCapacity =>
          Location(
            assessmentCentreCapacity.locationName,
            assessmentCentreCapacity.venues.map(venue =>
              Venue(
                venue.venueName,
                venue.capacityDates.map(capacityDate =>
                  UsedCapacityDate(
                    capacityDate.date,
                    calculateUsedCapacity(assessmentMap.get((venue.venueName, capacityDate.date, "AM")), capacityDate.amCapacity),
                    calculateUsedCapacity(assessmentMap.get((venue.venueName, capacityDate.date, "PM")), capacityDate.pmCapacity)
                  ))
              ))
          ))
      )
      Ok(Json.toJson(schedule))
    }
  }

  def getAssessmentCentreCapacities(venue: String) = Action.async { implicit request =>
    val venueDecoded = URLDecoder.decode(venue, "UTF-8")
    val today = LocalDate.now()

    acRepository.assessmentCentreCapacities.map { apps =>
      val result = apps.flatMap(_.venues).find(_.venueName == venueDecoded).map { venue =>
        venue.copy(capacityDates = venue.capacityDates.filterNot(d => d.date.isBefore(today)))
      }

      result match {
        case Some(c) => Ok(Json.toJson(c))
        case None => NotFound(s"Cannot find capacity for the venue: $venue")
      }
    }
  }

  private def joinApplicationsAndApplicationAssessments(
    assessmentsFut: Future[List[ApplicationAssessment]],
    candidatesFut: Future[List[Commands.Candidate]]
  ) = {
    for {
      assessments <- assessmentsFut
      candidates <- candidatesFut
    } yield {
      assessments.flatMap { assessment =>
        candidates.collect {
          case candidate if candidate.applicationId == Some(assessment.applicationId) => (assessment, candidate)
        }
      }
    }
  }

  def getApplicationForAssessmentAllocation(assessmentCenterLocation: String, start: Int, end: Int) = Action.async { implicit request =>
    val assessmentCenterLocationDecoded = URLDecoder.decode(assessmentCenterLocation, "UTF-8")
    val locationsFuture = acRepository.locationsAndAssessmentCentreMapping.map { prefferedLocationToAsssessmentCenterLocation =>
      findPreferredLocations(prefferedLocationToAsssessmentCenterLocation, assessmentCenterLocationDecoded)
    }

    val allCandidatesForAssessmentCenterLocation = locationsFuture.flatMap { locations =>
      if (locations.isEmpty) {
        Future.successful(ApplicationForAssessmentAllocationResult(List(), 0))
      } else {
        aRepository.findApplicationsForAssessmentAllocation(locations, start, end)
      }
    }

    allCandidatesForAssessmentCenterLocation.map { apps =>
      Ok(Json.toJson(apps))
    }
  }

  private def findPreferredLocations(
    preferredLocationsToAsssessmentCenterLocation: Map[String, String],
    assessmentCenterLocation: String
  ): List[String] = {
    preferredLocationsToAsssessmentCenterLocation.filter {
      case (_, location) =>
        location == assessmentCenterLocation
    }.keys.toList
  }

  def getAllocationsForVenue(venue: String) = Action.async { implicit request =>
    val venueDecoded = URLDecoder.decode(venue, "UTF-8")
    val assessments = aaRepository.applicationAssessmentsForVenue(venueDecoded).map(_.groupBy(x => x.session))

    for {
      assessmentMap <- assessments
    } yield {
      val amAssessments = assessmentMap.get("AM").map(x => x.groupBy(_.date)).getOrElse(Map())
      val pmAssessments = assessmentMap.get("PM").map(x => x.groupBy(_.date)).getOrElse(Map())

      val amBookedSlots = amAssessments.mapValues { x => x.map(_.slot) }
      val pmBookedSlots = pmAssessments.mapValues { x => x.map(_.slot) }

      val dates = (amBookedSlots.keys ++ pmBookedSlots.keys).toList.distinct
      val venueAllocations = dates.map { d =>
        VenueAllocation(d, amBookedSlots.getOrElse(d, List()), pmBookedSlots.getOrElse(d, List()))
      }
      Ok(Json.toJson(venueAllocations))
    }
  }

  def allocate() = Action.async(parse.json) { implicit request =>
    def allocateCandidate(allocation: ApplicationAssessment): Future[Unit] = {
      //TODO FAST STREAM FIX ME
      Future.successful(Unit)
    /*  if (allocation.confirmed) {
        otRepository.saveCandidateAllocationStatus(allocation.applicationId, AllocationConfirmed, None)
      } else {
        val expireDate = allocation.expireDate

        for {
          pd <- pdRepository.findPersonalDetailsWithUserId(allocation.applicationId)
          cd <- cdRepository.find(pd.userId)
          _ <- emailClient.sendConfirmAttendance(cd.email, pd.preferredName, allocation.assessmentDateTime, expireDate)
          _ <- otRepository.saveCandidateAllocationStatus(allocation.applicationId, AllocationUnconfirmed, Some(expireDate))
        } yield {
          auditService.logEvent("AssessmentCentreAllocationUnconfirmed")
        }
      }*/
    }

    withJsonBody[List[ApplicationAssessment]] { apps =>
      val errorListFuture = aaRepository.create(apps)

      val result = errorListFuture.flatMap { errorList =>
        val allocationsFuture = apps.filterNot(errorList.contains(_)).map { successfulAllocation =>
          allocateCandidate(successfulAllocation)
        }

        Future.sequence(allocationsFuture).map { _ =>
          errorList
        }
      }

      result map {
        case errorList if errorList.isEmpty => Created
        case errorList =>
          Ok(Json.toJson(errorList))
      }
    }
  }

  def allocationStatus(applicationId: String) = Action.async { implicit request =>
    import connectors.ExchangeObjects.Implicits._

    acRepository.assessmentCentreCapacities.flatMap { assessmentCentreCapacities =>

      for {
        assessmentF <- aaRepository.applicationAssessment(applicationId)
        expiryDate <- aRepository.allocationExpireDateByApplicationId(applicationId)
      } yield {
        assessmentF match {
          case Some(assessment) => {
            val (locationName, venue) = assessmentCentreCapacities.flatMap(capacity =>
              capacity.venues.find(_.venueName == assessment.venue).map(capacity.locationName -> _)).head

            Ok(Json.toJson(AllocationDetails(locationName, venue.venueDescription, assessment.assessmentDateTime, expiryDate)))
          }
          case _ => NotFound
        }
      }
    }

  }

  def confirmAllocation(applicationId: String) = Action.async { implicit request =>

    aaRepository.confirmAllocation(applicationId).flatMap { _ =>
      aRepository.updateStatus(applicationId, ALLOCATION_CONFIRMED).map { _ =>
        auditService.logEvent("AssessmentCentreAllocationConfirmed")
        Ok("")
      }
    }
  }

  private def generatePaddedVenueDaySessionCandidateList(
    assessmentsInSession: Map[String, List[(Commands.ApplicationAssessment, Commands.Candidate)]],
    capacities: Future[AssessmentCentreVenueCapacityDate]
  ) = {

    val capacityMap = Map[String, Future[Int]](
      "AM" -> capacities.map { _.amCapacity - assessmentsInSession.get("AM").map(_.length).getOrElse(0) },
      "PM" -> capacities.map { _.pmCapacity - assessmentsInSession.get("PM").map(_.length).getOrElse(0) }
    )

    capacityMap.keys.map { sessionName =>
      val sessionList = assessmentsInSession.getOrElse(sessionName, Nil).map {
        case (applicationAssessment, applicationCandidate) =>
          Some(VenueDaySessionCandidate(
            applicationCandidate.userId,
            applicationAssessment.applicationId,
            applicationCandidate.firstName.get,
            applicationCandidate.lastName.get,
            confirmed = applicationAssessment.confirmed
          ))
      }

      sessionName -> capacityMap(sessionName).map { sessionSlotsAvailable =>
        sessionList ++ List.fill(sessionSlotsAvailable)(None) //Filling up the empty slots
      }
    }.toMap
  }

  private def getVenueDayDetails(venueEncoded: String, dateEncoded: String)(
    resultGenerator: (String, LocalDate, Future[VenueDayDetails]) => Future[Result]
  ) = {
    val date = Try(LocalDate.parse(dateEncoded)).getOrElse(
      throw new IllegalArgumentException("Invalid date passed")
    )

    val venue = Try(URLDecoder.decode(venueEncoded, "UTF-8")).getOrElse(
      throw new UnsupportedEncodingException("Invalid encoding in passed venue name ")
    )

    val capacities = acRepository.assessmentCentreCapacityDate(venue, date)

    val assessmentsForVenueDate = aaRepository.applicationAssessments(venue, date)

    val applicationRecordsForAllAssessments = assessmentsForVenueDate.map(_.map(_.applicationId)).flatMap(aRepository.find)

    val applicationRecordsForAllAssessmentsWithStatus = applicationRecordsForAllAssessments.flatMap { applications =>
      Future.sequence(applications.map { application =>
        aRepository.findProgress(application.applicationId.get).map { progressStatus =>
          ApplicationStatusOrder.getStatus(progressStatus)
        }.map { applicationStatus =>
          (application, applicationStatus)
        }
      })
    }

    val applicationRecordsForAllAssessmentsNotWithdrawn = applicationRecordsForAllAssessmentsWithStatus.map { list =>
      list.filter(_._2 != "withdrawn").map(_._1)
    }

    val assessmentApplicationsGroupedBySessionFut = joinApplicationsAndApplicationAssessments(
      assessmentsForVenueDate,
      applicationRecordsForAllAssessmentsNotWithdrawn
    ).map(_.groupBy(x => x._1.session))

    assessmentApplicationsGroupedBySessionFut.flatMap { assessmentApplicationsGroupedBySession =>

      val filledOrPaddedWithEmptySlotsToMaxCapacityMap =
        generatePaddedVenueDaySessionCandidateList(assessmentApplicationsGroupedBySession, capacities)

      val venueDateDetailsFut = for {
        amListPadded <- filledOrPaddedWithEmptySlotsToMaxCapacityMap("AM")
        pmListPadded <- filledOrPaddedWithEmptySlotsToMaxCapacityMap("PM")
      } yield {
        VenueDayDetails(amListPadded, pmListPadded)
      }

      resultGenerator(venue, date, venueDateDetailsFut)
    }
  }

  def getVenueDayCandidateScheduleWithDetails(venueEncoded: String, dateEncoded: String) = Action.async { implicit request =>

    getVenueDayDetails(venueEncoded, dateEncoded) { (venue, date, detailsF) =>
      detailsF.flatMap { venueDateDetails =>
        val amCandidates = venueDateDetails.amSession
        val pmCandidates = venueDateDetails.pmSession

        def toExchangeObjects(list: List[Option[VenueDaySessionCandidate]], session: String) = list.map(_.map { c =>
          (c.userId, c.applicationId,
            ScheduledCandidateDetail(c.firstName, c.lastName, None, None, None, venue, session,
              date, if (c.confirmed) "Confirmed" else "Unconfirmed"))
        })

        val candidates = (toExchangeObjects(amCandidates, "AM") ++ toExchangeObjects(pmCandidates, "PM")).flatten

        FutureEx.traverseSerial(candidates) {
          case (userId, applicationId, candidate) =>

            val cdF = cdRepository.find(userId)
            val pdF = pdRepository.find(applicationId)

            for {
              contact <- cdF
              details <- pdF
            } yield {
              candidate.copy(preferredName = Some(details.preferredName), email = Some(contact.email), phone = contact.phone)
            }
        }.map { list =>
          Ok(Json.toJson(list))
        }

      }
    }

  }

  def getVenueDayCandidateSchedule(venueEncoded: String, dateEncoded: String) = Action.async { implicit request =>
    getVenueDayDetails(venueEncoded, dateEncoded)((_, _, detailsFuture) => detailsFuture.map { venueDateDetails =>
      Ok(Json.toJson(venueDateDetails))
    })
  }

  def getApplicationAssessment(applicationId: String) = Action.async { implicit request =>
    aaRepository.applicationAssessment(applicationId).map {
      case Some(assessment) => Ok(Json.toJson(assessment))
      case _ => NotFound("No application assessment could be found")
    }
  }

  def deleteApplicationAssessment(applicationId: String) = Action.async { implicit request =>
    aRepository.findProgress(applicationId).flatMap { result =>
      ApplicationStatusOrder.getStatus(result) match {
        case "assessment_scores_accepted" =>
          Future.successful(Conflict(""))
        case _ =>
          aaService.removeFromApplicationAssessmentSlot(applicationId).map { _ =>
            Ok("")
          }.recover {
            case e: NotFoundException => NotFound(s"Could not find application assessment for application $applicationId")
          }
      }
    }

  }

  def locationToAssessmentCentreLocation(locationName: String) = Action.async { implicit request =>
    acRepository.locationsAndAssessmentCentreMapping.map(
      _.get(locationName).map(location => Ok(Json.toJson(LocationName(location)))).getOrElse(NotFound("No such location"))
    )
  }

  def assessmentCentres = Action.async { implicit request =>
    acRepository.assessmentCentreCapacities.map { m =>
      Ok(Json.toJson(m.flatMap(_.venues.map(_.venueName))))
    }
  }
}
