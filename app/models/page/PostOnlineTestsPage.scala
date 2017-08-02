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
import helpers.Timezones
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import models.{ CachedData, CachedDataWithApp, SchemeStatus }
import org.joda.time.{ DateTime, LocalTime }

case class CurrentSchemeStatus(
  scheme: Scheme,
  status: SchemeStatus.Status,
  failedAtStage: Option[String]
)

case class PostOnlineTestsPage(
  userDataWithApp: CachedDataWithApp,
  schemes: Seq[CurrentSchemeStatus],
  additionalQuestionsStatus: Option[SiftAnswersStatus],
  assessmentCentreEvent: Option[Event],
  hasAnalysisExercise: Boolean
) {
  def toCachedData: CachedData = CachedData(userDataWithApp.user, Some(userDataWithApp.application))
  def successfulSchemes: Seq[CurrentSchemeStatus] = schemes.filter(_.status == SchemeStatus.Green)
  def failedSchemes: Seq[CurrentSchemeStatus] = schemes.filter(_.status == SchemeStatus.Red)
  def withdrawnSchemes: Seq[Scheme] = schemes.collect { case s if s.status == SchemeStatus.Withdrawn => s.scheme}
  def schemesForSiftForms: Seq[Scheme] = successfulSchemes.collect {
    case s if s.scheme.siftRequirement.contains(SiftRequirement.FORM) => s.scheme }

  val noSuccessfulSchemes: Int = successfulSchemes.size
  val noFailedSchemes: Int = failedSchemes.size
  val noWithdrawnSchemes: Int = withdrawnSchemes.size

  val hasFormRequirement: Boolean = successfulSchemes.exists(_.scheme.siftRequirement.contains(SiftRequirement.FORM))
  val hasNumericRequirement: Boolean = successfulSchemes.exists(_.scheme.siftRequirement.contains(SiftRequirement.NUMERIC_TEST))
  val hasAssessmentCentreRequirement: Boolean = true

  val haveAdditionalQuestionsBeenSubmitted: Boolean = additionalQuestionsStatus.contains(SiftAnswersStatus.SUBMITTED)

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

object PostOnlineTestsPage {
  def apply(
    userDataWithApp: CachedDataWithApp,
    phase3Results: Seq[SchemeEvaluationResult],
    allSchemes: Seq[Scheme],
    siftAnswersStatus: Option[SiftAnswersStatus],
    assessmentCentreSession: Option[Event],
    hasAnalysisExercise: Boolean
  ): PostOnlineTestsPage = {

    val currentSchemes = phase3Results.flatMap { schemeResult =>
      allSchemes.find(_.id == schemeResult.schemeId).map { scheme =>

        val (status, failedAt) = schemeResult.result match {
          case "Red" => (SchemeStatus.Red, Some("online tests"))
          case "Green" => (SchemeStatus.Green, None)
        }

        CurrentSchemeStatus(scheme, status, failedAt)
      }
    }

    PostOnlineTestsPage(userDataWithApp, currentSchemes, siftAnswersStatus, assessmentCentreSession, hasAnalysisExercise)
  }
}
