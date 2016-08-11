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

package models.page

import models.page.DashboardPage.ProgressStepVisibility
import models.{CachedData, Progress}
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat
import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import security.RoleUtils
import security.Roles._

// format: OFF
case class DashboardPage(firstStepVisibility: ProgressStepVisibility,
                         secondStepVisibility: ProgressStepVisibility,
                         thirdStepVisibility: ProgressStepVisibility,
                         fourthStepVisibility: ProgressStepVisibility,
                         isApplicationSubmittedAndNotWithdrawn: Boolean,
                         isApplicationInProgressAndNotWithdrawn: Boolean,
                         isApplicationWithdrawn: Boolean,
                         isApplicationCreatedOrInProgress: Boolean,
                         isUserWithNoApplication: Boolean,
                         isFirstStepVisible: String,
                         isSecondStepVisible: String,
                         isThirdStepVisible: String,
                         isFourthStepVisible: String,
                         fullName: String
                        ) {
  import DashboardPage._

  def toddMMMMyyyyFormat(date: LocalDate): String = ddMMMMyyyy.print(date)

  def todMMMMyyyyhmmaFormat(date: DateTime): String = dMMMMyyyyhmma.print(date.toLocalDateTime)
    .replace("AM", "am")
    .replace("PM","pm")
}
// format: ON


object DashboardPage {

  import models.ApplicationData.ApplicationStatus
  import models.ApplicationData.ApplicationStatus.ApplicationStatus

  def apply(user: CachedData) (implicit request: RequestHeader, lang: Lang): DashboardPage = {
    val (firstStepVisibility, secondStepVisibility, thirdStepVisibility, fourthStepVisibility) = fromUser(user)
    DashboardPage(
      firstStepVisibility,
      secondStepVisibility,
      thirdStepVisibility,
      fourthStepVisibility,
      isApplicationSubmittedAndNotWithdrawn(user),
      isApplicationInProgressAndNotWithdrawn(user),
      isApplicationWithdrawn(user),
      isApplicationCreatedOrInProgress(user),
      isUserWithNoApplication(user),
      stepVisibility(firstStepVisibility),
      stepVisibility(secondStepVisibility),
      stepVisibility(thirdStepVisibility),
      stepVisibility(fourthStepVisibility),
      user.user.firstName + " " + user.user.lastName
    )
  }

  sealed trait ProgressStepVisibility
  case object ProgressActive extends ProgressStepVisibility
  case object ProgressInactive extends ProgressStepVisibility
  case object ProgressInactiveDisabled extends ProgressStepVisibility

  sealed trait Step {
    def isReached(p: Progress): Boolean
  }
  case object Step1 extends Step {
    // Step 1 is always reached
    def isReached(p: Progress): Boolean = true
  }
  case object Step2 extends Step {
    def isReached(p: Progress): Boolean = {
      val ot = p.onlineTest
      List(p.submitted, ot.onlineTestInvited, ot.onlineTestStarted, ot.onlineTestCompleted, ot.onlineTestExpired,
        ot.onlineTestFailed, ot.onlineTestFailedNotified).contains(true)
    }
  }

  case object Step3 extends Step {
    def isReached(p: Progress): Boolean = {
      val ot = p.onlineTest
      List(ot.onlineTestAwaitingAllocation, ot.onlineTestAllocationConfirmed, ot.onlineTestAllocationUnconfirmed,
        p.failedToAttend,
        p.assessmentScores.entered, p.assessmentScores.accepted).contains(true)
    }
  }
  case object Step4 extends Step {
    def isReached(p: Progress): Boolean =
      List(p.assessmentCentre.awaitingReevaluation, p.assessmentCentre.passed, p.assessmentCentre.failed).contains(true)
  }

  object Step {
    def determineStep(progress: Option[Progress]): Step = {
      val step = progress.map { p =>
        val latestStep: Step = if (Step4.isReached(p)) {
          Step4
        } else if (Step3.isReached(p)) {
          Step3
        } else if (Step2.isReached(p)) {
          Step2
        } else {
          Step1
        }

        latestStep
      }

      step.getOrElse(Step1)
    }
  }

  private def isApplicationSubmittedAndNotWithdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    WithdrawApplicationRole.isAuthorized(user)

  private def isApplicationInProgressAndNotWithdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    CreatedOrInProgressRole.isAuthorized(user)

  private def isApplicationWithdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    WithdrawnApplicationRole.isAuthorized(user)

  private def isApplicationCreatedOrInProgress(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    PersonalDetailsRole.isAuthorized(user)

  private def isUserWithNoApplication(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    ApplicationStartRole.isAuthorized(user)

  private def stepVisibility(step: ProgressStepVisibility): String = step match {
    case ProgressActive => "active"
    case ProgressInactiveDisabled => "disabled"
    case _ => ""
  }

  private def fromUser(user: CachedData)(implicit request: RequestHeader, lang: Lang):
  (ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility) = status(user) match {
    case Some(ApplicationStatus.WITHDRAWN) => withdrawn(user)
    case _ => activeApplication(user)
  }

  private def withdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang):
  (ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility) = {
    val progress = user.application.map(_.progress)
    val latestStepBeforeWithdrawn = Step.determineStep(progress)

    latestStepBeforeWithdrawn match {
      case Step1 => (ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled)
      case Step2 => (ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled)
      case Step3 => (ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled)
      case Step4 => (ProgressActive, ProgressActive, ProgressActive, ProgressInactiveDisabled)
    }
  }

  private def activeApplication(user: CachedData)(implicit request: RequestHeader, lang: Lang):
  (ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility) = {
    val firstStep = if (RoleUtils.activeUserWithApp(user)) ProgressActive else ProgressInactive
    val secondStep = if (DisplayOnlineTestSectionRole.isAuthorized(user)) ProgressActive else ProgressInactive
    val isStatusOnlineTestFailedNotified = user.application.exists(_.applicationStatus == ApplicationStatus.ONLINE_TEST_FAILED_NOTIFIED)
    val thirdStep = if (isStatusOnlineTestFailedNotified) ProgressInactiveDisabled else ProgressInactive
    val fourthStep = if (isStatusOnlineTestFailedNotified) ProgressInactiveDisabled else ProgressInactive

    (firstStep, secondStep, thirdStep, fourthStep)
  }

  private def status(user: CachedData)(implicit request: RequestHeader, lang: Lang): Option[ApplicationStatus] =
    user.application.map(_.applicationStatus)

  private val ddMMMMyyyy = DateTimeFormat.forPattern("dd MMMM yyyy")
  private val dMMMMyyyyhmma = DateTimeFormat.forPattern("d MMMM yyyy, h:mma")
}
