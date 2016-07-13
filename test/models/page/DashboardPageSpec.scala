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

import connectors.exchange.{ AssessmentCentre, AssessmentScores }
import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus._
import models.page.DashboardPage._
import models.{ OnlineTestProgress, Progress }
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.PlaySpec
import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import security.RolesSpec

class DashboardPageSpec extends PlaySpec with TableDrivenPropertyChecks {
  import DashboardPageSpec._

  implicit val request: RequestHeader = FakeRequest()
  implicit val lang: Lang = Lang.defaultLang

  // format: OFF
  val Applications = Table(
    ("applicationStatus", "step1", "step2", "step3", "step4"),
    (REGISTERED,                               ProgressInactive, ProgressInactive, ProgressInactive, ProgressInactive),
    (CREATED,                                  ProgressActive, ProgressInactive, ProgressInactive, ProgressInactive),
    (IN_PROGRESS,                              ProgressActive, ProgressInactive, ProgressInactive, ProgressInactive),
    (SUBMITTED,                                ProgressActive, ProgressInactive, ProgressInactive, ProgressInactive),
    (WITHDRAWN,                                ProgressActive, ProgressActive, ProgressActive, ProgressInactiveDisabled),
    (ONLINE_TEST_INVITED,                      ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ONLINE_TEST_STARTED,                      ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ONLINE_TEST_COMPLETED,                    ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ONLINE_TEST_EXPIRED,                      ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ALLOCATION_CONFIRMED,                     ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ALLOCATION_UNCONFIRMED,                   ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (AWAITING_ALLOCATION,                      ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ONLINE_TEST_FAILED,                       ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ONLINE_TEST_FAILED_NOTIFIED,              ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled),
    (AWAITING_ONLINE_TEST_RE_EVALUATION,       ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (FAILED_TO_ATTEND,                         ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ASSESSMENT_SCORES_ENTERED,                ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ASSESSMENT_SCORES_ACCEPTED,               ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION, ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ASSESSMENT_CENTRE_PASSED,                 ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ASSESSMENT_CENTRE_FAILED,                 ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ASSESSMENT_CENTRE_PASSED_NOTIFIED,        ProgressActive, ProgressActive, ProgressInactive, ProgressInactive),
    (ASSESSMENT_CENTRE_FAILED_NOTIFIED,        ProgressActive, ProgressActive, ProgressInactive, ProgressInactive)
  )
  // format: ON

  "The Steps visibility" should {
    "be correctly determined by applicationStatus" in {
      forAll(Applications) { (status: ApplicationStatus.Value, step1: ProgressStepVisibility, step2: ProgressStepVisibility,
        step3: ProgressStepVisibility, step4: ProgressStepVisibility) =>
        DashboardPage.fromUser(user(status)) must be(DashboardPage(step1, step2, step3, step4))
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _) => status }
      val allStatuses = ApplicationStatus.values.toList

      val statusesNotTested = allStatuses.diff(statusesTested)
      statusesNotTested must be(empty)
    }
  }

  // format: off
  val WithdrawnApplications = Table(
    ("Status before Withdraw", "step1", "step2", "step3", "step4"),
    (PersonalDetailsProgress, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled),
    (SubmittedProgress, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled),
    (AwaitingAssessmentCentreAllocation, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled),
    (OnlineTestInvited, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled),
    (OnlineTestFailed, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled),
    (AssessmentCentreInvited, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled)
  )
  // format: on

  "The steps visibility when application is withdrawn from status" should {
    "be correctly determined by previous applicationStatus" in {
      forAll(WithdrawnApplications) { (progress: Progress, step1: ProgressStepVisibility, step2: ProgressStepVisibility,
        step3: ProgressStepVisibility, step4: ProgressStepVisibility) =>
        DashboardPage.fromUser(withdrawnApplication(progress)) must be(DashboardPage(step1, step2, step3, step4))
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _) => status }
      // For any new status DashboardPage.Step1/2/3 or 4 needs to have this status
      // Add this new status to isReached method and increase this value
      statusesTested.length must be(23)
    }
  }
}

object DashboardPageSpec {
  def user(status: ApplicationStatus.Value) = {
    if (status == ApplicationStatus.REGISTERED) {
      RolesSpec.registeredUser(status)
    } else {
      RolesSpec.activeUser(status)
    }
  }

  def withdrawnApplication(currentProgress: Progress) = {
    val templateCachedData = RolesSpec.activeUser(ApplicationStatus.WITHDRAWN)
    val application = templateCachedData.application
    val updatedApplication = application.map { a =>
      a.copy(progress = currentProgress)
    }

    templateCachedData.copy(application = updatedApplication)
  }

  val EmptyProgress = Progress(false, false, false, false, false, false, false, false, false, false,
    OnlineTestProgress(false, false, false, false, false, false, false, false, false, false),
    false, AssessmentScores(false, false), AssessmentCentre(false, false))

  val OnlineTestProgressAwaitingAllocation = OnlineTestProgress(onlineTestInvited = true, onlineTestStarted = true,
    onlineTestCompleted = true, onlineTestExpired = false, onlineTestAwaitingReevaluation = false,
    onlineTestFailed = false, onlineTestFailedNotified = false, onlineTestAwaitingAllocation = true,
    onlineTestAllocationConfirmed = false, onlineTestAllocationUnconfirmed = false)

  val OnlineTestProgressFailed = OnlineTestProgressAwaitingAllocation.copy(onlineTestFailed = true)
  val OnlineTestProgressInvited = OnlineTestProgressAwaitingAllocation.copy(onlineTestInvited = true)

  val PersonalDetailsProgress = EmptyProgress.copy(personalDetails = true)
  val SubmittedProgress = PersonalDetailsProgress.copy(submitted = true)
  val AwaitingAssessmentCentreAllocation = SubmittedProgress.copy(onlineTest = OnlineTestProgressAwaitingAllocation)
  val OnlineTestFailed = SubmittedProgress.copy(onlineTest = OnlineTestProgressFailed)
  val OnlineTestInvited = SubmittedProgress.copy(onlineTest = OnlineTestProgressInvited)
  val AssessmentCentreInvited = SubmittedProgress.copy(onlineTest = OnlineTestProgressAwaitingAllocation.copy(onlineTestInvited = true))

}
