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

import connectors.events.Event
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import connectors.exchange.sift.SiftAnswersStatus
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import helpers.{ CachedUserWithSchemeData, Timezones }
import models.CachedData
import models.page.DashboardPage.Flags
import models.page.DashboardPage.Flags.{ ProgressActive, ProgressInactiveDisabled }
import org.joda.time.{ DateTime, LocalTime }
import play.twirl.api.Html
import security.{ RoleUtils, Roles }


object PostOnlineTestsStage extends Enumeration {
  type PostOnlineTestsStage = Value
  val FAILED_TO_ATTEND, CONFIRMED_FOR_EVENT, UPLOAD_EXERCISES, ALLOCATED_TO_EVENT, EVENT_ATTENDED, OTHER = Value
}

case class PostOnlineTestsPage(
  userDataWithSchemes: CachedUserWithSchemeData,
  assessmentCentreAllocation: Option[CandidateAllocationWithEvent],
  additionalQuestionsStatus: Option[SiftAnswersStatus],
  hasAnalysisExercise: Boolean
) {
  import PostOnlineTestsStage._

  // scalastyle:off cyclomatic.complexity
  def stage: PostOnlineTestsStage =
    userDataWithSchemes.application.progress.assessmentCentre match {
      case a if a.failedToAttend => FAILED_TO_ATTEND
      case _ if hasAnalysisExercise => EVENT_ATTENDED
      case a if a.allocationConfirmed & assessmentCentreStarted => UPLOAD_EXERCISES
      case z if z.allocationConfirmed => CONFIRMED_FOR_EVENT
      case a if a.allocationUnconfirmed => ALLOCATED_TO_EVENT
      case _ => OTHER
  }
  // scalastyle:on

  def haveAdditionalQuestionsBeenSubmitted = additionalQuestionsStatus.contains(SiftAnswersStatus.SUBMITTED)

  private def dateTimeToStringWithOptionalMinutes(localTime: LocalTime): String = {
    localTime.toString(if (localTime.toString("mm") == "00") "ha" else "h:mma")
  }

  val assessmentCentreStartDateAndTime: String = assessmentCentreAllocation.map(_.event).map { ac =>
    ac.date.toString("EEEE d MMMM YYYY") + " at " + dateTimeToStringWithOptionalMinutes(ac.sessions.head.startTime)
  }.getOrElse("No assessment centre")

  val assessmentCentreNameAndLocation: String = {
    assessmentCentreAllocation.map(_.event).map { ac => ac.venue.description }.getOrElse("No assessment centre")
  }

  val assessmentCentreStarted: Boolean = assessmentCentreAllocation.map(_.event).exists { event =>
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
