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

package controllers

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import com.github.nscala_time.time.OrderingImplicits.LocalDateOrdering
import common.FutureEx
import connectors.AssessmentScheduleExchangeObjects._
import connectors.ExchangeObjects.AllocationDetails
import connectors.{ CSREmailClient, EmailClient }
import model.AssessmentScheduleCommands.ApplicationForAssessmentAllocationResult
import model.AssessmentScheduleCommands.Implicits.ApplicationForAssessmentAllocationResultFormats
import model.Commands.Implicits.applicationAssessmentFormat
import model.Exceptions.NotFoundException
import model.command.AssessmentCentreAllocation
import model.{ Commands, ProgressStatuses }
import org.joda.time.LocalDate
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, Result }
import repositories.AssessmentCentreLocation.assessmentCentreVenueFormat
import repositories._
import repositories.application._
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object AssessmentScheduleController extends AssessmentScheduleController {
  // val aaRepository: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  val acRepository = AssessmentCentreYamlRepository
  val aRepository: GeneralApplicationMongoRepository = applicationRepository
  // val otRepository: OnlineTestMongoRepository = onlineTestRepository
  val auditService = AuditService
  // val pdRepository: PersonalDetailsRepository = personalDetailsRepository
  // val cdRepository: ContactDetailsRepository = contactDetailsRepository
  val emailClient = CSREmailClient
  // val aaService = AssessmentCentreService
  // val assessmentScoresService = AssessorAssessmentScoresService
}

trait AssessmentScheduleController extends BaseController {
  // val aaRepository: AssessmentCentreAllocationRepository
  val acRepository: AssessmentCentreRepository
  val aRepository: GeneralApplicationRepository
  // val otRepository: OnlineTestRepository
  val auditService: AuditService
  // val pdRepository: PersonalDetailsRepository
  // val cdRepository: ContactDetailsRepository
  val emailClient: EmailClient
  // val aaService: AssessmentCentreService
  // val assessmentScoresService: AssessmentCentreScoresService

  def getAssessmentScheduleDatesByRegion(region: String): Action[AnyContent] = Action.async { implicit request =>
     acRepository.assessmentCentreCapacities.map { schedule =>
       val dates = schedule
         .filter(scheduleRegion => scheduleRegion.locationName == region)
         .flatMap(scheduleRegion =>
           scheduleRegion.venues.flatMap(venue =>
             venue.capacityDates.map(capacityDates =>
               capacityDates.date
             )
           )
         ).distinct.sorted
       Ok(Json.toJson(dates))
     }
  }


  // TODO: uncomment all comments line in this method when implementing assessment centre schedule
  // Remove dummy data lines under comments in some cases
  private def calculateUsedCapacity(assessments: Option[List[AssessmentCentreAllocation]], sessionCapacity: Int,
    minViableAttendees: Int, preferredAttendeeMargin: Int): UsedCapacity = {
    //assessments.map { sessionSlots =>
      //UsedCapacity(sessionSlots.size, sessionSlots.count(x => x.confirmed), minViableAttendees, preferredAttendeeMargin)
    //}.getOrElse(
      // UsedCapacity(usedCapacity = 0, confirmedAttendees = 0, minViableAttendees, preferredAttendeeMargin)
      UsedCapacity(usedCapacity = 0, false) // dummy data line
    //)
  }

  // TODO: uncomment all comments line in this method when implementing assessment centre schedule.
  // Remove dummy data lines under comments in some cases.
  def getAssessmentSchedule: Action[AnyContent] = Action.async { implicit request =>
    // val assessments = aaRepository.findAll.map(_.groupBy(x => (x.venue, x.date, x.session)))

    for {
      // assessmentMap <- assessments
      assessmentCentreCapacities <- acRepository.assessmentCentreCapacities
    } yield {
      val schedule = Schedule(
        assessmentCentreCapacities.map(assessmentCentreCapacity =>
          Region(
            assessmentCentreCapacity.locationName,
            assessmentCentreCapacity.venues.map(venue =>
              Venue(
                venue.venueName,
                venue.capacityDates.map(capacityDate =>
                  UsedCapacityDate(
                    capacityDate.date,
                    calculateUsedCapacity(
                      //assessmentMap.get((venue.venueName, capacityDate.date, "AM")),
                      None,
                      // capacityDate.amCapacity, capacityDate.amMinViableAttendees, capacityDate.amPreferredAttendeeMargin
                      capacityDate.amCapacity, 0, 0 // dummy data line
                    ),
                    calculateUsedCapacity(
                      // assessmentMap.get((venue.venueName, capacityDate.date, "PM")),
                      None,
                      // capacityDate.pmCapacity, capacityDate.pmMinViableAttendees, capacityDate.pmPreferredAttendeeMargin
                      capacityDate.pmCapacity, 0, 0 // dummy data line
                    )
                  ))
              ))
          ))
      )
      Ok(Json.toJson(schedule))
    }
  }

  /*
  def getAssessmentCentreCapacities(venue: String): Action[AnyContent] = Action.async { implicit request =>
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
    assessmentsFut: Future[List[AssessmentCentreAllocation]],
    candidatesFut: Future[List[Commands.Candidate]]
  ) = {
    for {
      assessments <- assessmentsFut
      candidates <- candidatesFut
    } yield {
      assessments.flatMap { assessment =>
        candidates.collect {
          case candidate if candidate.applicationId.contains(assessment.applicationId) => (assessment, candidate)
        }
      }
    }
  }

  def getApplicationForAssessmentAllocation(assessmentCenterLocation: String, start: Int, end: Int): Action[AnyContent] = Action.async {
    implicit request =>
      aRepository.findApplicationsForAssessmentAllocation(List(assessmentCenterLocation), start, end) map { apps =>
        Ok(Json.toJson(apps))
      }
  }

  def getAllocationsForVenue(venue: String): Action[AnyContent] = Action.async { implicit request =>
    val venueDecoded = URLDecoder.decode(venue, "UTF-8")
    val assessments = aaRepository.findAllForVenue(venueDecoded).map(_.groupBy(x => x.session))

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

  def allocate(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    def allocateCandidate(allocation: AssessmentCentreAllocation): Future[Unit] = {
      if (allocation.confirmed) {
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
      }
    }

    withJsonBody[List[AssessmentCentreAllocation]] { apps =>
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

  def allocationStatus(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    import connectors.ExchangeObjects.Implicits._

    acRepository.assessmentCentreCapacities.flatMap { assessmentCentres =>

      for {
        assessmentF <- aaRepository.find(applicationId)
        expiryDate <- aRepository.allocationExpireDateByApplicationId(applicationId)
      } yield {
        assessmentF match {
          case Some(assessment) => {
            val (locationName, venue) = assessmentCentres.flatMap(capacity =>
              capacity.venues.find(_.venueName == assessment.venue).map(capacity.locationName -> _)).head

            Ok(Json.toJson(AllocationDetails(locationName, venue.venueDescription, assessment.assessmentDateTime, expiryDate)))
          }
          case _ => NotFound
        }
      }
    }

  }

  def confirmAllocation(applicationId: String): Action[AnyContent] = Action.async { implicit request =>

    aaRepository.confirmAllocation(applicationId).flatMap { _ =>
      aRepository.updateStatus(applicationId, AllocationConfirmed).map { _ =>
        auditService.logEvent("AssessmentCentreAllocationConfirmed")
        Ok("")
      }
    }
  }

  private def generatePaddedVenueDaySessionCandidateList(
    assessmentsInSession: Map[String, List[(Commands.AssessmentCentreAllocation, Commands.Candidate)]],
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
            applicationAssessment.slot,
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

    val assessmentsForVenueDate = aaRepository.findAllForDate(venue, date)

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

  def getVenueDayCandidateScheduleWithDetails(venueEncoded: String, dateEncoded: String): Action[AnyContent] = Action.async { implicit request =>

    getVenueDayDetails(venueEncoded, dateEncoded) { (venue, date, detailsF) =>
      detailsF.flatMap { venueDateDetails =>
        val amCandidates = venueDateDetails.amSession
        val pmCandidates = venueDateDetails.pmSession

        def toExchangeObjects(list: List[Option[VenueDaySessionCandidate]], session: String) = list.map(_.map { c =>
          (c.userId, c.applicationId,
            ScheduledCandidateDetail(c.slotNumber, c.firstName, c.lastName, None, None, None, venue, session,
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

  def getVenueDayCandidateSchedule(venueEncoded: String, dateEncoded: String): Action[AnyContent] = Action.async { implicit request =>
    getVenueDayDetails(venueEncoded, dateEncoded)((_, _, detailsFuture) => detailsFuture.map { venueDateDetails =>
      Ok(Json.toJson(venueDateDetails))
    })
  }

  def getApplicationAssessment(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    aaRepository.find(applicationId).map {
      case Some(assessment) => Ok(Json.toJson(assessment))
      case _ => NotFound("No application assessment could be found")
    }
  }

  def deleteApplicationAssessment(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    aRepository.findProgress(applicationId).flatMap { result =>
      ApplicationStatusOrder.getStatus(result) match {
        case ProgressStatuses.AssessmentScoresAcceptedProgress =>
          Future.successful(Conflict(""))
        case _ =>
          aaService.removeFromAssessmentCentreSlot(applicationId).map { _ =>
            Ok("")
          }.recover {
            case _: NotFoundException => NotFound(s"Could not find application assessment for application $applicationId")
          }
      }
    }

  }

  def locationToAssessmentCentreLocation(locationName: String): Action[AnyContent] = Action.async { implicit request =>
    acRepository.assessmentCentreCapacities.map { assessmentCentres =>
      assessmentCentres.find(_.locationName == locationName).map { location =>
        Ok(Json.toJson(LocationName(location.locationName)))
      }.getOrElse(NotFound("No such location"))
    }
  }

  def assessmentCentres: Action[AnyContent] = Action.async { implicit request =>
    acRepository.assessmentCentreCapacities.map { m =>
      Ok(Json.toJson(m.flatMap(_.venues.map(_.venueName))))
    }
  }

  // TODO Do we need to get un-submitted scores for both assessors and reviewers?
  def getNonSubmittedScores(assessorId: String): Action[AnyContent] = Action.async { implicit request =>
    assessmentScoresService.getNonSubmittedCandidateScores(assessorId).map { applicationScores =>
      Ok(Json.toJson(applicationScores))
    }
  }
  */
}
