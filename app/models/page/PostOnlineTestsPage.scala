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

package models.page

import connectors.exchange.SchemeEvaluationResult
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import connectors.exchange.referencedata.Scheme
import connectors.exchange.sift.SiftAnswersStatus
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import helpers.{CachedUserWithSchemeData, Timezones}
import models.{CachedData, SchemeStatus}
import models.events.EventType
import models.page.DashboardPage.Flags
import models.page.DashboardPage.Flags.{ProgressActive, ProgressInactiveDisabled}
import org.joda.time.{DateTime, LocalTime}
import play.twirl.api.Html
import security.{RoleUtils, Roles}

object PostOnlineTestsStage extends Enumeration {
  type PostOnlineTestsStage = Value
  val FAILED_TO_ATTEND, CONFIRMED_FOR_EVENT, UPLOAD_EXERCISES, ALLOCATED_TO_EVENT, EVENT_ATTENDED, OTHER = Value
}

case class PostOnlineTestsPage(
  userDataWithSchemes: CachedUserWithSchemeData,
  allocationsWithEvent: Seq[CandidateAllocationWithEvent],
  additionalQuestionsStatus: Option[SiftAnswersStatus],
  hasAnalysisExercise: Boolean,
  schemes: List[Scheme]
) {
  import PostOnlineTestsStage._

  // scalastyle:off cyclomatic.complexity
  def fsacStage: PostOnlineTestsStage =
    userDataWithSchemes.application.progress.assessmentCentre match {
      case a if a.failedToAttend => FAILED_TO_ATTEND
      case _ if hasAnalysisExercise => EVENT_ATTENDED
      case a if a.allocationConfirmed & assessmentCentreStarted => UPLOAD_EXERCISES
      case z if z.allocationConfirmed => CONFIRMED_FOR_EVENT
      case a if a.allocationUnconfirmed => ALLOCATED_TO_EVENT
      case _ => OTHER
  }
  // scalastyle:on

  def fsbStage: PostOnlineTestsStage = userDataWithSchemes.application.progress.fsb match {
    case e if e.failedToAttend => FAILED_TO_ATTEND
    case e if e.allocationConfirmed => CONFIRMED_FOR_EVENT
    case e if e.allocationUnconfirmed => ALLOCATED_TO_EVENT
    case _ => OTHER
  }


  def isFinalSuccess = RoleUtils.isEligibleForJobOffer(userDataWithSchemes.toCachedData)

  def isFinalFailure = RoleUtils.isFastStreamFailed(userDataWithSchemes.toCachedData)

  def haveAdditionalQuestionsBeenSubmitted = additionalQuestionsStatus.contains(SiftAnswersStatus.SUBMITTED)

  private def dateTimeToStringWithOptionalMinutes(localTime: LocalTime): String = {
    localTime.toString(if (localTime.toString("mm") == "00") "ha" else "h:mma")
  }

  def eventStartDateAndTime(al: Option[CandidateAllocationWithEvent]): String = al.map {e =>
    e.event.date.toString("EEEE d MMMM YYYY") + " at " + dateTimeToStringWithOptionalMinutes(e.event.sessions.head.startTime)
  }.getOrElse("No assessment centre")

  def eventLocation(al: Option[CandidateAllocationWithEvent]): String = al.map {_.event.venue.description}.getOrElse("")

  def eventTypeText(al: Option[CandidateAllocationWithEvent]): String = al.map {x => x.event.eventType.displayValue}.getOrElse("")

  def eventScheme(al: Option[CandidateAllocationWithEvent]): String = al.map(_.event).flatMap { e =>
    e.eventType match {
      case EventType.FSB => schemes.find(_.fsbType.map(_.key).contains(e.description)).map(_.name)
      case _ => None
    }
  }.getOrElse("")

  def allSchemesFailed: Boolean = userDataWithSchemes.noFailedSchemes == userDataWithSchemes.rawSchemesStatus.length

  def firstResidualPreferencePassed: Boolean = {

    val zippedData = userDataWithSchemes.rawSchemesStatus.zipWithIndex

    val firstAmber: Option[(SchemeEvaluationResult, Int)] =
      zippedData.find{ case (evaluationResult, _) => evaluationResult.result == SchemeStatus.Amber.toString }

    val firstGreen: Option[(SchemeEvaluationResult, Int)] =
      zippedData.find{ case (evaluationResult, _) => evaluationResult.result == SchemeStatus.Green.toString }

    (firstAmber, firstGreen) match {
      case (Some((_, amberIndex)), Some((_, greenIndex))) => greenIndex < amberIndex
      case (None, Some(green)) => true
      case _ => false
    }
  }

  val fsacAllocation = allocationsWithEvent.find(_.event.eventType == EventType.FSAC)
  val fsbAllocation = allocationsWithEvent.find(_.event.eventType != EventType.FSAC)

  val assessmentCentreStartDateAndTime: String = eventStartDateAndTime(fsacAllocation)
  val fsbStartDateAndTime: String = eventStartDateAndTime(fsbAllocation)

  val assessmentCentreStarted: Boolean = allocationsWithEvent.map(_.event).exists { event =>
    val eventDate = event.date
    val sessionTime = event.sessions.head.startTime
    val sessionDateTime = new DateTime(
      eventDate.year.get, eventDate.getMonthOfYear, eventDate.getDayOfMonth, sessionTime.getHourOfDay, sessionTime.getMinuteOfHour,
      sessionTime.getSecondOfMinute, Timezones.londonDateTimezone
    )
    val timeNow = DateTime.now.toDateTime(Timezones.londonDateTimezone)

    timeNow.isAfter(sessionDateTime)
  }

  val fourthStepVisibility: Flags.ProgressStepVisibility = {
    userDataWithSchemes.application.progress.assessmentCentre match {
      case a if a.failedToAttend | a.failed => ProgressInactiveDisabled
      case _ => ProgressActive
    }
  }
}
