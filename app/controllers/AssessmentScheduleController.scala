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

import com.github.nscala_time.time.OrderingImplicits.LocalDateOrdering
import connectors.AssessmentScheduleExchangeObjects._
import connectors.{ CSREmailClient, EmailClient }
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.application._
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentScheduleController extends AssessmentScheduleController {
  val acRepository = AssessmentCentreYamlRepository
  val aRepository: GeneralApplicationMongoRepository = applicationRepository
  val auditService = AuditService
  val emailClient = CSREmailClient
}

trait AssessmentScheduleController extends BaseController {
  val acRepository: AssessmentCentreRepository
  val aRepository: GeneralApplicationRepository
  val auditService: AuditService
  val emailClient: EmailClient

  def getAssessmentScheduleDatesByRegion(region: String): Action[AnyContent] = Action.async { implicit request =>
     acRepository.assessmentCentreCapacities.map { schedule =>
       val dates = schedule
         .filter(scheduleRegion => scheduleRegion.regionName == region)
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
  private def calculateUsedCapacity(sessionCapacity: Int,
    minViableAttendees: Int, preferredAttendeeMargin: Int): UsedCapacity = {
      UsedCapacity(usedCapacity = 0, false)
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
            assessmentCentreCapacity.regionName,
            assessmentCentreCapacity.venues.map(venue =>
              Venue(
                venue.venueName,
                venue.capacityDates.map(capacityDate =>
                  UsedCapacityDate(
                    capacityDate.date,
                    calculateUsedCapacity(
                      capacityDate.amCapacity, 0, 0 // dummy data line
                    ),
                    calculateUsedCapacity(
                      capacityDate.pmCapacity, 0, 0 // dummy data line
                    )
                  ))
              ))
          ))
      )
      Ok(Json.toJson(schedule))
    }
  }
}
