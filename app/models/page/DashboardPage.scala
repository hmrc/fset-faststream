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

import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus.ApplicationStatus
import models.page.DashboardPage.ProgressStepVisibility
import models.{ ApplicationData, CachedData, Progress }
import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import security.RoleUtils
import security.Roles.DisplayOnlineTestSectionRole

// format: OFF
case class DashboardPage(firstStepVisibility: ProgressStepVisibility,
                         secondStepVisibility: ProgressStepVisibility,
                         thirdStepVisibility: ProgressStepVisibility,
                         fourthStepVisibility: ProgressStepVisibility)

// format: ON

object DashboardPage {

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

  def fromUser(user: CachedData)(implicit request: RequestHeader, lang: Lang): DashboardPage = status(user) match {
    case Some(ApplicationStatus.WITHDRAWN) => withdrawn(user)
    case _ => activeApplication(user)
  }

  private def withdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang) = {
    val progress = user.application.map(_.progress)
    val latestStepBeforeWithdrawn = Step.determineStep(progress)

    latestStepBeforeWithdrawn match {
      case Step1 => DashboardPage(ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled)
      case Step2 => DashboardPage(ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled)
      case Step3 => DashboardPage(ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled)
      case Step4 => DashboardPage(ProgressActive, ProgressActive, ProgressActive, ProgressInactiveDisabled)
    }
  }

  private def activeApplication(user: CachedData)(implicit request: RequestHeader, lang: Lang): DashboardPage = {
    val firstStep = if (RoleUtils.activeUserWithApp(user)) ProgressActive else ProgressInactive
    val secondStep = if (DisplayOnlineTestSectionRole.isAuthorized(user)) ProgressActive else ProgressInactive
    val isStatusOnlineTestFailedNotified = user.application.exists(_.applicationStatus == ApplicationStatus.ONLINE_TEST_FAILED_NOTIFIED)
    val thirdStep = if (isStatusOnlineTestFailedNotified) ProgressInactiveDisabled else ProgressInactive
    val fourthStep = if (isStatusOnlineTestFailedNotified) ProgressInactiveDisabled else ProgressInactive

    DashboardPage(firstStep, secondStep, thirdStep, fourthStep)
  }

  private def isApplicationInStatus(application: Option[ApplicationData], status: ApplicationStatus) =
    application.exists(_.applicationStatus == status)

  private def status(user: CachedData)(implicit request: RequestHeader, lang: Lang) = user.application.map(_.applicationStatus)
}
