/*
 * Copyright 2020 HM Revenue & Customs
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

import com.github.nscala_time.time.OrderingImplicits._
import config.FrontendAppConfig
import connectors.exchange.SchemeEvaluationResultWithFailureDetails
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import connectors.exchange.referencedata.Scheme
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import connectors.exchange.sift.{SiftAnswersStatus, SiftState}
import helpers.{CachedUserWithSchemeData, Timezones}
import models.events.EventType
import models.page.DashboardPage.Flags
import models.page.DashboardPage.Flags.{ProgressActive, ProgressInactiveDisabled}
import models.{ApplicationRoute, SchemeStatus}
import org.joda.time.{DateTime, LocalTime}
import security.{ProgressStatusRoleUtils, RoleUtils}

import scala.util.Try

object PostOnlineTestsStage extends Enumeration {
  type PostOnlineTestsStage = Value
  val FAILED_TO_ATTEND, CONFIRMED_FOR_EVENT, UPLOAD_EXERCISES,
  ALLOCATED_TO_EVENT, EVENT_ATTENDED, OTHER = Value
  val ASSESSMENT_CENTRE_PASSED, ASSESSMENT_CENTRE_FAILED,
  ASSESSMENT_CENTRE_FAILED_SDIP_GREEN, FSB_FAILED = Value
}

case class PostOnlineTestsPage(
                                userDataWithSchemes: CachedUserWithSchemeData,
                                allocationsWithEvent: Seq[CandidateAllocationWithEvent],
                                additionalQuestionsStatus: Option[SiftAnswersStatus],
                                hasAnalysisExercise: Boolean,
                                schemes: List[Scheme],
                                siftState: Option[SiftState],
                                phase1DataOpt: Option[Phase1TestsPage2],
                                phase2DataOpt: Option[Phase2TestsPage2],
                                phase3DataOpt: Option[Phase3TestsPage]
) {
  import PostOnlineTestsStage._

  lazy val fsacGuideUrl: String = FrontendAppConfig.fsacGuideUrl

  val isOnlySdipGreen
    : Boolean = userDataWithSchemes.application.applicationRoute == ApplicationRoute.SdipFaststream &&
    userDataWithSchemes.currentSchemesStatus.forall(schemeStatus =>
      (schemeStatus.scheme.id != Scheme.SdipId && schemeStatus.status == SchemeStatus.Red) ||
        (schemeStatus.scheme.id == Scheme.SdipId && schemeStatus.status == SchemeStatus.Green))

  val isSdipFaststreamFailed
    : Boolean = userDataWithSchemes.application.applicationRoute == ApplicationRoute.SdipFaststream &&
    userDataWithSchemes.currentSchemesStatus.exists(
      schemeStatus =>
        schemeStatus.scheme.id == Scheme.SdipId && schemeStatus.status == SchemeStatus.Red
    )

  val isSdipFaststreamSuccessful
    : Boolean = userDataWithSchemes.application.applicationRoute == ApplicationRoute.SdipFaststream &&
    userDataWithSchemes.currentSchemesStatus.exists(
      schemeStatus =>
        schemeStatus.scheme.id == Scheme.SdipId && schemeStatus.status == SchemeStatus.Green
    )

  val sdipFaststreamAllSchemesFailed
    : Boolean = userDataWithSchemes.application.applicationRoute == ApplicationRoute.SdipFaststream &&
    userDataWithSchemes.currentSchemesStatus.forall(
      schemeStatus => schemeStatus.status == SchemeStatus.Red
    )

  val sdipFaststreamBannerPage = SdipFaststreamBannerPage(
    isOnlySdipGreen,
    isSdipFaststreamFailed,
    isSdipFaststreamSuccessful,
    sdipFaststreamAllSchemesFailed,
    userDataWithSchemes.application.applicationStatus)

  val isSiftTestStarted: Boolean =
    userDataWithSchemes.application.progress.siftProgress.siftTestStarted

  val isSiftTestCompleted: Boolean =
    userDataWithSchemes.application.progress.siftProgress.siftTestCompleted

  // scalastyle:off cyclomatic.complexity
  def fsacStage: PostOnlineTestsStage =
    userDataWithSchemes.application.progress.assessmentCentre match {
      case a if a.failedToAttend             => FAILED_TO_ATTEND
      case a if a.passed                     => ASSESSMENT_CENTRE_PASSED
      case a if a.failed || a.failedNotified => ASSESSMENT_CENTRE_FAILED
      case a if a.failedSdipGreen            => ASSESSMENT_CENTRE_FAILED_SDIP_GREEN
      case _ if hasAnalysisExercise          => EVENT_ATTENDED
      case a if a.allocationConfirmed & assessmentCentreStarted =>
        UPLOAD_EXERCISES
      case a if a.allocationConfirmed   => CONFIRMED_FOR_EVENT
      case a if a.allocationUnconfirmed => ALLOCATED_TO_EVENT
      case _                            => OTHER
    }
  // scalastyle:on

  def fsbStage: PostOnlineTestsStage =
    userDataWithSchemes.application.progress.fsb match {
      case e if e.failedToAttend        => FAILED_TO_ATTEND
      case e if e.failed                => FSB_FAILED
      case e if e.allocationConfirmed   => CONFIRMED_FOR_EVENT
      case e if e.allocationUnconfirmed => ALLOCATED_TO_EVENT
      case _                            => OTHER
    }

  def isFinalSuccess =
    ProgressStatusRoleUtils.isEligibleForJobOffer(userDataWithSchemes.toCachedData)

  def isFinalFailure =
    RoleUtils.isFastStreamFailed(userDataWithSchemes.toCachedData)

  def isFailedFaststreamGreenSdip =
    ProgressStatusRoleUtils.isFastStreamFailedGreenSdip(userDataWithSchemes.toCachedData)

  def haveAdditionalQuestionsBeenSubmitted =
    additionalQuestionsStatus.contains(SiftAnswersStatus.SUBMITTED)

  private def dateTimeToStringWithOptionalMinutes(
      localTime: LocalTime): String = {
    localTime.toString(if (localTime.toString("mm") == "00") "ha" else "h:mma")
  }

  def eventStartDateAndTime(al: Option[CandidateAllocationWithEvent]): String =
    al.map { e =>
        e.event.date.toString("EEEE d MMMM YYYY") + " at " + dateTimeToStringWithOptionalMinutes(
          e.event.sessions.head.startTime)
      }
      .getOrElse("No assessment centre")

  def eventLocation(al: Option[CandidateAllocationWithEvent]): String =
    al.map { allocWithEvent =>
      // FSAC events are virtual in 20/21 campaign so do not show the description eg. London (100 Parliament Street)
      allocWithEvent.event.eventType match {
        case models.events.EventType.FSAC => "Virtual"
        case _ => allocWithEvent.event.venue.description
      }
    }.getOrElse("")

  def eventTypeText(al: Option[CandidateAllocationWithEvent]): String =
    al.map { x =>
        x.event.eventType.displayValue
      }
      .getOrElse("")

  def eventScheme(al: Option[CandidateAllocationWithEvent]): String =
    al.map(_.event)
      .flatMap { e =>
        e.eventType match {
          case EventType.FSB =>
            schemes
              .find(_.fsbType.map(_.key).contains(e.description))
              .map(_.name)
          case _ => None
        }
      }
      .getOrElse("")

  def fsbEventScheme(al: Option[CandidateAllocationWithEvent]): Option[Scheme] =
    al.map(_.event).flatMap { e =>
      e.eventType match {
        case EventType.FSB =>
          schemes.find(_.fsbType.map(_.key).contains(e.description))
        case _ => None
      }
    }

  def allSchemesFailed: Boolean =
    userDataWithSchemes.numberOfFailedSchemesForDisplay == userDataWithSchemes.rawSchemesStatus.length

  def firstResidualPreferencePassed: Boolean = {

    val zippedData = userDataWithSchemes.rawSchemesStatus.zipWithIndex

    val firstAmber: Option[(SchemeEvaluationResultWithFailureDetails, Int)] =
      zippedData.find {
        case (evaluationResult, _) =>
          evaluationResult.result == SchemeStatus.Amber.toString
      }

    val firstGreen: Option[(SchemeEvaluationResultWithFailureDetails, Int)] =
      zippedData.find {
        case (evaluationResult, _) =>
          evaluationResult.result == SchemeStatus.Green.toString
      }

    (firstAmber, firstGreen) match {
      case (Some((_, amberIndex)), Some((_, greenIndex))) =>
        greenIndex < amberIndex
      case (None, Some(green)) => true
      case _                   => false
    }
  }
  val fsacAllocation =
    allocationsWithEvent.find(_.event.eventType == EventType.FSAC)
  val fsbAllocation = Try(
    allocationsWithEvent.filter(_.event.eventType == EventType.FSB).maxBy {
      allocation =>
        allocation.event.date.toDateTime(
          allocation.event.sessions.head.startTime)
    }).toOption

  val assessmentCentreStartDateAndTime: String = eventStartDateAndTime(
    fsacAllocation)
  val fsbStartDateAndTime: String = eventStartDateAndTime(fsbAllocation)

  val assessmentCentreStarted: Boolean =
    allocationsWithEvent.map(_.event).exists { event =>
      val eventDate = event.date
      val sessionTime = event.sessions.head.startTime
      val sessionDateTime = new DateTime(
        eventDate.year.get,
        eventDate.getMonthOfYear,
        eventDate.getDayOfMonth,
        sessionTime.getHourOfDay,
        sessionTime.getMinuteOfHour,
        sessionTime.getSecondOfMinute,
        Timezones.londonDateTimezone
      )
      val timeNow = DateTime.now.toDateTime(Timezones.londonDateTimezone)

      timeNow.isAfter(sessionDateTime)
    }

  val secondStepVisibility: Flags.ProgressStepVisibility = {
    if(userDataWithSchemes.application.isSiftExpired) {
      ProgressInactiveDisabled
    } else {
      ProgressActive
    }
  }

  val fourthStepVisibility: Flags.ProgressStepVisibility = {
    userDataWithSchemes.application.progress.assessmentCentre match {
      case a if a.failedToAttend | a.failed | userDataWithSchemes.application.isSiftExpired => ProgressInactiveDisabled
      case _  => ProgressActive
    }
  }
}
