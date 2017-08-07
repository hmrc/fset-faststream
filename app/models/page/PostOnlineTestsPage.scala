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

import connectors.events.{ Event, Session }
import connectors.exchange.SchemeEvaluationResult
import connectors.exchange.referencedata.{ Scheme, SiftRequirement }
import connectors.exchange.sift.SiftAnswersStatus
import helpers.{ CachedUserMetadata, Timezones }
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import models.{ CachedData, CachedDataWithApp, SchemeStatus }
import org.joda.time.{ DateTime, LocalTime }



case class PostOnlineTestsPage(
  userDataWithApp: CachedUserMetadata,
  assessmentCentreEvent: Option[Event],
  additionalQuestionsStatus: Option[SiftAnswersStatus],
  hasAnalysisExercise: Boolean
) {
  def toCachedData: CachedData = CachedData(userDataWithApp.user, Some(userDataWithApp.application))

  def hasAssessmentCentreRequirement: Boolean = true

  def haveAdditionalQuestionsBeenSubmitted = additionalQuestionsStatus.contains(SiftAnswersStatus.SUBMITTED)

  private def dateTimeToStringWithOptionalMinutes(localTime: LocalTime): String = {
    val minutes = localTime.toString("mm")
    if (minutes == "00") {
      localTime.toString("ha")
    } else {
      localTime.toString("h:mma")
    }
  }

  val assessmentCentreStartDateAndTime: String = assessmentCentreEvent.map { ac =>
    ac.date.toString("EEEE d MMMM YYYY") + " at " + dateTimeToStringWithOptionalMinutes(ac.sessions.head.startTime)
  }.getOrElse("No assessment centre")

  val assessmentCentreNameAndLocation: String = assessmentCentreEvent.map { ac => ac.venue.description }.getOrElse("No assessment centre")

  val assessmentCentreStarted = assessmentCentreEvent.exists { event =>
    val eventDate = event.date
    val sessionTime = event.sessions.head.startTime
    val sessionDateTime = new DateTime(
      eventDate.year.get, eventDate.getMonthOfYear, eventDate.getDayOfMonth, sessionTime.getHourOfDay, sessionTime.getMinuteOfHour,
      sessionTime.getSecondOfMinute, Timezones.londonDateTimezone
    )
    val timeNow = DateTime.now.toDateTime(Timezones.londonDateTimezone)

    timeNow.isAfter(sessionDateTime)
  }

  val allocatedToAssessmentCentre = assessmentCentreEvent.isDefined
}
