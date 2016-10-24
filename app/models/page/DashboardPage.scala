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

import models.page.DashboardPage.Flags._
import models.{ CachedData, Progress }
import org.joda.time.LocalDate
import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import security.RoleUtils._
import security.Roles._

case class DashboardPage(firstStepVisibility: ProgressStepVisibility,
  secondStepVisibility: ProgressStepVisibility,
  thirdStepVisibility: ProgressStepVisibility,
  fourthStepVisibility: ProgressStepVisibility,
  isApplicationSubmittedAndNotWithdrawn: Boolean,
  isApplicationInProgressAndNotWithdrawn: Boolean,
  isApplicationWithdrawn: Boolean,
  isApplicationCreatedOrInProgress: Boolean,
  isUserWithNoApplication: Boolean,
  isPhase1TestsPassed: Boolean,
  isTestGroupExpired: Boolean,
  isPhase2TestGroupExpired: Boolean,
  fullName: String,
  phase1TestsPage: Option[Phase1TestsPage],
  phase2TestsPage: Option[Phase2TestsPage],
  assessmentStageStatus: AssessmentStageStatus,
  postAssessmentStageStatus: PostAssessmentStageStatus
)

object DashboardPage {

  import connectors.exchange.AllocationDetails
  import models.ApplicationData.ApplicationStatus
  import models.ApplicationData.ApplicationStatus.ApplicationStatus

  def apply(user: CachedData, allocationDetails: Option[AllocationDetails], phase1TestGroup: Option[Phase1TestsPage],
    phase2TestGroup: Option[Phase2TestsPage]
  )(implicit request: RequestHeader, lang: Lang): DashboardPage = {

    val (firstStepVisibility, secondStepVisibility, thirdStepVisibility,
      fourthStepVisibility
    ) = visibilityForUser(user)

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
      isPhase1TestsPassed(user),
      isTestGroupExpired(user),
      isPhase2TestGroupExpired(user),
      user.user.firstName + " " + user.user.lastName,
      phase1TestGroup,
      phase2TestGroup,
      getAssessmentInProgressStatus(user, allocationDetails),
      getPostAssessmentStatus(user, allocationDetails)
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

    case object ASSESSMENT_FAST_PASS_CERTIFICATE extends AssessmentStageStatus

    case object ASSESSMENT_BOOKED_CONFIRMED extends AssessmentStageStatus

    case object ASSESSMENT_CONFIRMATION_EXPIRED extends AssessmentStageStatus

    case object ASSESSMENT_PENDING_CONFIRMATION extends AssessmentStageStatus

    case object ASSESSMENT_FAILED extends AssessmentStageStatus

    case object ASSESSMENT_PASSED extends AssessmentStageStatus

    case object ASSESSMENT_NOT_ATTENDED extends AssessmentStageStatus

    case object ASSESSMENT_STATUS_UNKNOWN extends AssessmentStageStatus

    sealed trait PostAssessmentStageStatus

    case object POSTASSESSMENT_PASSED_MORE_SOON extends PostAssessmentStageStatus

    case object POSTASSESSMENT_FAILED_APPLY_AGAIN extends PostAssessmentStageStatus

    case object POSTASSESSMENT_STATUS_UNKNOWN extends PostAssessmentStageStatus

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

  private def isApplicationSubmittedAndNotWithdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    WithdrawApplicationRole.isAuthorized(user)

  private def isApplicationInProgressAndNotWithdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    CreatedOrInProgressRole.isAuthorized(user)

  private def isApplicationWithdrawn(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    WithdrawnApplicationRole.isAuthorized(user)

  private def isApplicationCreatedOrInProgress(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    EditPersonalDetailsAndContinueRole.isAuthorized(user)

  private def isUserWithNoApplication(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    ApplicationStartRole.isAuthorized(user)

  private def isTestGroupExpired(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    OnlineTestExpiredRole.isAuthorized(user)

  private def isPhase2TestGroupExpired(user: CachedData)(implicit request: RequestHeader, lang: Lang) =
    Phase2TestExpiredRole.isAuthorized(user)

  private def getAssessmentInProgressStatus(user: CachedData,
    allocationDetails: Option[AllocationDetails])
  (implicit request: RequestHeader, lang: Lang): AssessmentStageStatus = {

    if(hasReceivedFastPass(user)) {
      ASSESSMENT_FAST_PASS_CERTIFICATE
    } else if (ConfirmedAllocatedCandidateRole.isAuthorized(user)) {
      ASSESSMENT_BOOKED_CONFIRMED
    } else if (UnconfirmedAllocatedCandidateRole.isAuthorized(user)) {
      if (isConfirmationAllocationExpired(allocationDetails)) {
        ASSESSMENT_CONFIRMATION_EXPIRED
      } else {
        ASSESSMENT_PENDING_CONFIRMATION
      }
    } else if (AssessmentCentreFailedNotifiedRole.isAuthorized(user)) {
      ASSESSMENT_FAILED
    } else if (AssessmentCentrePassedNotifiedRole.isAuthorized(user)) {
      ASSESSMENT_PASSED
    } else if (AssessmentCentreFailedToAttendRole.isAuthorized(user)) {
      ASSESSMENT_NOT_ATTENDED
    } else {
      ASSESSMENT_STATUS_UNKNOWN
    }
  }

  private def getPostAssessmentStatus(user: CachedData,
    allocationDetails: Option[AllocationDetails])
  (implicit request: RequestHeader, lang: Lang): PostAssessmentStageStatus = {
    if (AssessmentCentreFailedNotifiedRole.isAuthorized(user) || AssessmentCentreFailedToAttendRole.isAuthorized(user)) {
      POSTASSESSMENT_FAILED_APPLY_AGAIN
    } else if (AssessmentCentrePassedNotifiedRole.isAuthorized(user)) {
      POSTASSESSMENT_PASSED_MORE_SOON
    } else {
      POSTASSESSMENT_STATUS_UNKNOWN
    }
  }

  // scalastyle:off cyclomatic.complexity
  private def visibilityForUser(user: CachedData)(implicit request: RequestHeader, lang: Lang):
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

      val firstStep = if (activeUserWithApp(user)) { ProgressActive } else { ProgressInactive }
      val secondStep = if (DisplayOnlineTestSectionRole.isAuthorized(user) || hasReceivedFastPass(user)) {
        ProgressActive
      } else {
        ProgressInactive
      }
      val thirdStep = if (isStatusOnlineTestFailedNotified || isTestGroupExpired(user)) { ProgressInactiveDisabled } else { ProgressInactive }
      val fourthStep = if (isStatusOnlineTestFailedNotified || isTestGroupExpired(user)) { ProgressInactiveDisabled } else { ProgressInactive }

      (firstStep, secondStep, thirdStep, fourthStep)
    }

    status(user) match {
      case Some(ApplicationStatus.WITHDRAWN) => withdrawnUserVisibility(user)
      case _ => activeUserVisibility(user)
    }
  }
  // scalastyle:on cyclomatic.complexity

  private def status(user: CachedData)(implicit request: RequestHeader, lang: Lang): Option[ApplicationStatus] =
    user.application.map(_.applicationStatus)

  private def isConfirmationAllocationExpired(allocationDetails: Option[AllocationDetails]): Boolean =
    allocationDetails match {
      case Some(AllocationDetails(_, _, _, Some(expirationDate))) if LocalDate.now().isAfter(expirationDate) => true
      case _ => false
    }

}

