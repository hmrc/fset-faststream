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

import java.util.UUID

import connectors.exchange._
import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus._
import models.page.DashboardPage._
import models._
import models.page.DashboardPage.Flags._
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
  // scalastyle:off line.size.limit
  val Applications = Table(
    ("applicationStatus",                      "step1",          "step2",         "step3",                    "step4",                  "isApplicationSubmittedAndNotWithdrawn", "isApplicationInProgressAndNotWithdrawn", "isApplicationWithdrawn", "isApplicationCreatedOrInProgress", "isUserWithNoApplication",     "fullName",      "assessmentInProgressStatus",       "assessmentCompletedStatus"),
    (REGISTERED,                               ProgressInactive, ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   false,                                    false,                    false,                              true,                          "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (CREATED,                                  ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   true,                                     false,                    true,                               false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (IN_PROGRESS,                              ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   true,                                     false,                    true,                               false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (SUBMITTED,                                ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (PHASE1_TESTS,                             ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (WITHDRAWN,                                ProgressActive,   ProgressActive,   ProgressActive,            ProgressInactiveDisabled, false,                                   false,                                    true,                     false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ALLOCATION_CONFIRMED,                     ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (ALLOCATION_UNCONFIRMED,                   ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_PENDING_CONFIRMATION,    POSTASSESSMENT_STATUS_UNKNOWN),
    (AWAITING_ALLOCATION,                      ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (FAILED_TO_ATTEND,                         ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_NOT_ATTENDED,            POSTASSESSMENT_FAILED_APPLY_AGAIN),
    (ASSESSMENT_SCORES_ENTERED,                ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_SCORES_ACCEPTED,               ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION, ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_CENTRE_PASSED,                 ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_CENTRE_FAILED,                 ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_CENTRE_PASSED_NOTIFIED,        ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_PASSED_MORE_SOON),
    (ASSESSMENT_CENTRE_FAILED_NOTIFIED,        ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_FAILED_APPLY_AGAIN)
  )
  // scalastyle:on line.size.limit
  // format: ON

  "The Steps visibility" should {
    "be correctly determined by applicationStatus" in {
      forAll(Applications) {
        (status: ApplicationStatus.Value,
         step1: ProgressStepVisibility,
         step2: ProgressStepVisibility,
         step3: ProgressStepVisibility,
         step4: ProgressStepVisibility,
         isApplicationSubmittedAndNotWithdrawn: Boolean,
         isApplicationInProgressAndNotWithdrawn: Boolean,
         isApplicationWithdrawn: Boolean,
         isApplicationCreatedOrInProgress: Boolean,
         isUserWithNoApplication: Boolean,
         fullName: String,
         assessmentInProgressStatus: AssessmentStageStatus,
         assessmentCompletedStatus: PostAssessmentStageStatus
        ) => {
          DashboardPage(user(status), None, None) mustBe (
            DashboardPage(
              step1,
              step2,
              step3,
              step4,
              isApplicationSubmittedAndNotWithdrawn,
              isApplicationInProgressAndNotWithdrawn,
              isApplicationWithdrawn,
              isApplicationCreatedOrInProgress,
              isUserWithNoApplication,
              fullName,
              None,
              assessmentInProgressStatus,
              assessmentCompletedStatus)
          )
        }
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _, _, _, _, _, _, _, _, _) => status }
      val allStatuses = ApplicationStatus.values.toList

      val statusesNotTested = allStatuses.diff(statusesTested)
      statusesNotTested mustBe(empty)
    }
  }

  // format: off
  // scalastyle:off line.size.limit
  val WithdrawnApplications = Table(
    ("Status before Withdraw", "step1", "step2", "step3", "step4", "isApplicationSubmittedAndNotWithdrawn", "isApplicationInProgressAndNotWithdrawn", "isApplicationWithdrawn", "isApplicationCreatedOrInProgress", "isUserWithNoApplication", "fullName", "assessmentInProgressStatus", "assessmentCompletedStatus"),
    (PersonalDetailsProgress, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN),
    (SubmittedProgress, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN)
  )
  // scalastyle:on line.size.limit
  // format: on

  "The steps visibility when application is withdrawn from status" should {
    "be correctly determined by previous applicationStatus" in {

      forAll(WithdrawnApplications) {
        (progress: ProgressResponse,
         step1: ProgressStepVisibility,
         step2: ProgressStepVisibility,
         step3: ProgressStepVisibility,
         step4: ProgressStepVisibility,
         isApplicationSubmittedAndNotWithdrawn: Boolean,
         isApplicationInProgressAndNotWithdrawn: Boolean,
         isApplicationWithdrawn: Boolean,
         isApplicationCreatedOrInProgress: Boolean,
         isUserWithNoApplication: Boolean,
         fullName: String,
         assessmentInProgressStatus: AssessmentStageStatus,
         assessmentCompletedStatus: PostAssessmentStageStatus
        ) => {
          DashboardPage(withdrawnApplication(progress), None, None) mustBe(
            DashboardPage(
              step1,
              step2,
              step3,
              step4,
              isApplicationSubmittedAndNotWithdrawn,
              isApplicationInProgressAndNotWithdrawn,
              isApplicationWithdrawn,
              isApplicationCreatedOrInProgress,
              isUserWithNoApplication,
              fullName,
              None,
              assessmentInProgressStatus,
              assessmentCompletedStatus)
            )
        }
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _, _, _, _, _, _, _, _, _) => status }
      // For any new status DashboardPage.Step1/2/3 or 4 needs to have this status
      // Add this new status to isReached method and increase this value
      statusesTested.length mustBe(17)
    }
  }

  "activateByStep" should {
    "return 'active' when step is ProgressActive" in {
      activateByStep(ProgressActive) mustBe "active"
    }
    "return 'disabled' when step is ProgressActive" in {
      activateByStep(ProgressInactiveDisabled) mustBe "disabled"
    }
    "return an empty string otherwise" in {
      activateByStep(ProgressInactive) mustBe ""
    }
  }

}

object DashboardPageSpec {

  import connectors.exchange.AllocationDetails
  import org.joda.time.{DateTime, LocalDate}

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

  val EmptyProgress = ProgressResponseExamples.Initial

  val phase1TestProfile = Phase1TestProfile(expirationDate = DateTime.now,
    tests = List(Phase1Test(testType = "sjq",
      usedForResults = true,
      testUrl = "test.com",
      invitationDate = DateTime.now,
      token = UniqueIdentifier(UUID.randomUUID()),
      started = false,
      completed = false,
      resultsReadyToDownload = false
    ))
  )
  val PersonalDetailsProgress = EmptyProgress.copy(personalDetails = true)
  val SubmittedProgress = PersonalDetailsProgress.copy(submitted = true)
  private val AllocationDetails_Expired = AllocationDetails("", "", new DateTime(), Some(new LocalDate().minusDays(1)))
  private val AllocationDetails_Not_Expired = AllocationDetails("", "", new DateTime(), Some(new LocalDate().plusDays(1)))

}
