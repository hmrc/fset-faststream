/*
 * Copyright 2022 HM Revenue & Customs
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

import models.page.DashboardPage.Flags._
import models.{ CachedData, Progress }
import org.joda.time.LocalDate
import play.api.mvc.RequestHeader
import security.RoleUtils
import security.ProgressStatusRoleUtils
import security.Roles._
import play.api.i18n.Messages

case class DashboardPage(firstStepVisibility: ProgressStepVisibility,
  secondStepVisibility: ProgressStepVisibility,
  thirdStepVisibility: ProgressStepVisibility,
  fourthStepVisibility: ProgressStepVisibility,
  isApplicationSubmittedAndNotWithdrawn: Boolean,
  isApplicationWithdrawn: Boolean,
  isFastPassApproved: Boolean,
  isApplicationInProgress: Boolean,
  isUserWithNoApplication: Boolean,
  isPhase1TestsPassed: Boolean,
  isPhase2TestsPassed: Boolean,
  isTestGroupExpired: Boolean,
  isPhase2TestGroupExpired: Boolean,
  isPhase3TestGroupExpired: Boolean,
  isPhase1TestFailed: Boolean,
  isPhase2TestFailed: Boolean,
  isPhase3TestFailed: Boolean,
  shouldDisplayPhase3TestFeedbackReport: Boolean,
  fullName: String,
  phase1TestsPage: Option[Phase1TestsPage2],
  phase2TestsPage: Option[Phase2TestsPage2],
  phase3TestsPage: Option[Phase3TestsPage],
  assessmentStageStatus: AssessmentStageStatus,
    fsacGuideUrl: String
) {
}

object DashboardPage {

  import connectors.exchange.AllocationDetails
  import models.ApplicationData.ApplicationStatus
  import models.ApplicationData.ApplicationStatus.ApplicationStatus

  def apply(user: CachedData,
            phase1TestGroup: Option[Phase1TestsPage2],
            phase2TestGroup: Option[Phase2TestsPage2],
            phase3TestGroup: Option[Phase3TestsPage],
            fsacGuideUrl: String)
           (implicit request: RequestHeader, messages: Messages): DashboardPage = {

    val (firstStepVisibility, secondStepVisibility, thirdStepVisibility,
      fourthStepVisibility
    ) = visibilityForUser(user)

    DashboardPage(
      firstStepVisibility,
      secondStepVisibility,
      thirdStepVisibility,
      fourthStepVisibility,
      isApplicationSubmittedAndNotWithdrawn(user),
      isApplicationWithdrawn(user),
      RoleUtils.hasFastPassBeenApproved(user),
      isApplicationInProgress(user),
      isUserWithNoApplication(user),
      ProgressStatusRoleUtils.isPhase1TestsPassed(user),
      ProgressStatusRoleUtils.isPhase2TestsPassed(user),
      isTestGroupExpired(user),
      isPhase2TestGroupExpired(user),
      isPhase3TestGroupExpired(user),
      isPhase1TestFailed(user),
      isPhase2TestFailed(user),
      isPhase3TestFailed(user),
      shouldDisplayPhase3TestFeedbackReport(user),
      user.user.firstName + " " + user.user.lastName,
      phase1TestGroup,
      phase2TestGroup,
      phase3TestGroup,
      getAssessmentInProgressStatus(user),
      fsacGuideUrl
    )
  }

  def activateByStep(step: ProgressStepVisibility): String = step match {
    case ProgressActive => "active"
    case ProgressInactiveDisabled => "disabled"
    case _ => ""
  }

  object Flags {

    sealed trait ProgressStepVisibility

    case object ProgressActive extends ProgressStepVisibility

    case object ProgressInactive extends ProgressStepVisibility

    case object ProgressInactiveDisabled extends ProgressStepVisibility

    sealed trait AssessmentStageStatus

    case object ASSESSMENT_FAST_PASS_APPROVED extends AssessmentStageStatus

    case object ASSESSMENT_FAST_PASS_CERTIFICATE extends AssessmentStageStatus

    case object ASSESSMENT_STATUS_UNKNOWN extends AssessmentStageStatus

    sealed trait Step {
      def isReached(p: Progress): Boolean
    }

    case object Step1 extends Step {
      // Step 1 is always reached
      def isReached(p: Progress): Boolean = true
    }

    case object Step2 extends Step {
      def isReached(p: Progress): Boolean = {
        true
      }
    }

    case object Step3 extends Step {
      def isReached(p: Progress): Boolean = {
        false
      }
    }

    case object Step4 extends Step {
      def isReached(p: Progress): Boolean =
        List(p.assessmentCentre.awaitingReevaluation, p.assessmentCentre.passed, p.assessmentCentre.failed).contains(true)
    }

    object Step {
      def determineStep(progress: Option[Progress]): Step = {
        val step = progress.map { p =>
          if (Step4.isReached(p)) {
            Step4
          } else if (Step3.isReached(p)) {
            Step3
          } else if (Step2.isReached(p)) {
            Step2
          } else {
            Step1
          }
        }
        step.getOrElse(Step1)
      }
    }
  }

  private def isApplicationSubmittedAndNotWithdrawn(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    AbleToWithdrawApplicationRole.isAuthorized(user)

  private def isApplicationWithdrawn(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    WithdrawnApplicationRole.isAuthorized(user)

  private def isApplicationInProgress(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    InProgressRole.isAuthorized(user)

  private def isUserWithNoApplication(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    ApplicationStartRole.isAuthorized(user)

  private def isTestGroupExpired(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    OnlineTestExpiredRole.isAuthorized(user)

  private def isPhase1TestFailed(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    Phase1TestFailedRole.isAuthorized(user)

  private def isPhase2TestFailed(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    Phase2TestFailedRole.isAuthorized(user)

  private def isPhase2TestGroupExpired(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    Phase2TestExpiredRole.isAuthorized(user)

  private def isPhase3TestGroupExpired(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    Phase3TestExpiredRole.isAuthorized(user)

  private def isPhase3TestFailed(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    Phase3TestFailedRole.isAuthorized(user)

  private def shouldDisplayPhase3TestFeedbackReport(user: CachedData)(implicit request: RequestHeader, messages: Messages) =
    Phase3TestDisplayFeedbackRole.isAuthorized(user)

  private def getAssessmentInProgressStatus(user: CachedData)
  (implicit request: RequestHeader, messages: Messages): AssessmentStageStatus = {

    if (RoleUtils.hasFastPassBeenApproved(user)) {
      ASSESSMENT_FAST_PASS_APPROVED
    } else if (RoleUtils.hasReceivedFastPass(user)) {
      ASSESSMENT_FAST_PASS_CERTIFICATE
    } else {
      ASSESSMENT_STATUS_UNKNOWN
    }
  }

  // scalastyle:off cyclomatic.complexity
  private def visibilityForUser(user: CachedData)(implicit request: RequestHeader, messages: Messages):
  (ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility, ProgressStepVisibility) = {

    def withdrawnUserVisibility(user: CachedData) = {
      val progress = user.application.map(_.progress)
      val latestStepBeforeWithdrawn = Step.determineStep(progress)

      latestStepBeforeWithdrawn match {
        case Step1 => (ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled)
        case Step2 => (ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled)
        case Step3 => (ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled)
        case Step4 => (ProgressActive, ProgressActive, ProgressActive, ProgressInactiveDisabled)
      }
    }

    def activeUserVisibility(user: CachedData) = {
      //TODO FIX ME
      //val isStatusOnlineTestFailedNotified = user.application.exists(_.applicationStatus == ApplicationStatus.PHASE1_TESTS)
      val isStatusOnlineTestFailedNotified = false

      val firstStep = if (RoleUtils.activeUserWithActiveApp(user)) { ProgressActive } else { ProgressInactive }
      val secondStep = if (DisplayOnlineTestSectionRole.isAuthorized(user) || RoleUtils.hasReceivedFastPass(user)) {
        ProgressActive
      } else {
        ProgressInactive
      }
      val thirdStep = if (isStatusOnlineTestFailedNotified || isTestGroupExpired(user) ||
        isPhase2TestGroupExpired(user) || isPhase3TestGroupExpired(user) ||
        isPhase1TestFailed(user) || isPhase2TestFailed(user) || isPhase3TestFailed(user)) {
        ProgressInactiveDisabled
      } else {
        ProgressInactive
      }
      val fourthStep = if (isStatusOnlineTestFailedNotified || isTestGroupExpired(user) ||
        isPhase2TestGroupExpired(user) || isPhase3TestGroupExpired(user) ||
        isPhase1TestFailed(user) || isPhase2TestFailed(user) || isPhase3TestFailed(user)) {
        ProgressInactiveDisabled
      } else {
        ProgressInactive
      }

      (firstStep, secondStep, thirdStep, fourthStep)
    }

    status(user) match {
      case Some(ApplicationStatus.WITHDRAWN) => withdrawnUserVisibility(user)
      case _ => activeUserVisibility(user)
    }
  }
  // scalastyle:on cyclomatic.complexity

  private def status(user: CachedData)(implicit request: RequestHeader, messages: Messages): Option[ApplicationStatus] =
    user.application.map(_.applicationStatus)

  private def isConfirmationAllocationExpired(allocationDetails: Option[AllocationDetails]): Boolean =
    allocationDetails match {
      case Some(AllocationDetails(_, _, _, Some(expirationDate))) if LocalDate.now().isAfter(expirationDate) => true
      case _ => false
    }
}
