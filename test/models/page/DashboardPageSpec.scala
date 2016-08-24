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
    ("applicationStatus",                      "step1",          "step2",         "step3",                    "step4",                  "isApplicationSubmittedAndNotWithdrawn", "isApplicationInProgressAndNotWithdrawn", "isApplicationWithdrawn", "isApplicationCreatedOrInProgress", "isUserWithNoApplication",  "isFirstStepVisible",     "isSecondStepVisible",    "isThirdStepVisible",    "isFourthStepVisible",   "fullName",      "assessmentInProgressStatus",       "assessmentCompletedStatus"),
    (REGISTERED,                               ProgressInactive, ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   false,                                    false,                    false,                              true,                        "",                      "",                       "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (CREATED,                                  ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   true,                                     false,                    true,                               false,                       "active",                "",                       "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (IN_PROGRESS,                              ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   true,                                     false,                    true,                               false,                       "active",                "",                       "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (SUBMITTED,                                ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "",                       "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (WITHDRAWN,                                ProgressActive,   ProgressActive,   ProgressActive,            ProgressInactiveDisabled, false,                                   false,                                    true,                     false,                              false,                       "active",                "active",                 "active",                "disabled",              "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ONLINE_TEST_INVITED,                      ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ONLINE_TEST_STARTED,                      ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ONLINE_TEST_COMPLETED,                    ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ONLINE_TEST_EXPIRED,                      ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ALLOCATION_CONFIRMED,                     ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (ALLOCATION_UNCONFIRMED,                   ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_PENDING_CONFIRMATION,    POSTASSESSMENT_STATUS_UNKNOWN),
    (AWAITING_ALLOCATION,                      ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ONLINE_TEST_FAILED,                       ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ONLINE_TEST_FAILED_NOTIFIED,              ProgressActive,   ProgressActive,   ProgressInactiveDisabled,  ProgressInactiveDisabled, true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "disabled",              "disabled",              "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (AWAITING_ONLINE_TEST_RE_EVALUATION,       ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (FAILED_TO_ATTEND,                         ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_NOT_ATTENDED,            POSTASSESSMENT_FAILED_APPLY_AGAIN),
    (ASSESSMENT_SCORES_ENTERED,                ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_SCORES_ACCEPTED,               ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION, ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_CENTRE_PASSED,                 ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_CENTRE_FAILED,                 ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (ASSESSMENT_CENTRE_PASSED_NOTIFIED,        ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_PASSED_MORE_SOON),
    (ASSESSMENT_CENTRE_FAILED_NOTIFIED,        ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                       "active",                "active",                 "",                      "",                      "John Biggs",    ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_FAILED_APPLY_AGAIN)
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
         isFirstStepVisible: String,
         isSecondStepVisible: String,
         isThirdStepVisible: String,
         isFourthStepVisible: String,
         fullName: String,
         assessmentInProgressStatus: AssessmentStageStatus,
         assessmentCompletedStatus: PostAssessmentStageStatus
        ) => {
          DashboardPage(user(status), None, None) must be(
            DashboardPage(step1, step2, step3, step4,
              isApplicationSubmittedAndNotWithdrawn, isApplicationInProgressAndNotWithdrawn,
              isApplicationWithdrawn, isApplicationCreatedOrInProgress, isUserWithNoApplication,
              isFirstStepVisible, isSecondStepVisible, isThirdStepVisible, isFourthStepVisible,
              fullName, assessmentInProgressStatus, assessmentCompletedStatus)
          )
        }
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => status }
      val allStatuses = ApplicationStatus.values.toList

      val statusesNotTested = allStatuses.diff(statusesTested)
      statusesNotTested must be(empty)
    }
  }

  // format: off
  // scalastyle:off line.size.limit
  val WithdrawnApplications = Table(
    ("Status before Withdraw", "step1", "step2", "step3", "step4", "isApplicationSubmittedAndNotWithdrawn", "isApplicationInProgressAndNotWithdrawn", "isApplicationWithdrawn", "isApplicationCreatedOrInProgress", "isUserWithNoApplication",  "isFirstStepVisible", "isSecondStepVisible", "isThirdStepVisible", "isFourthStepVisible", "fullName", "assessmentInProgressStatus", "assessmentCompletedStatus"),
    (PersonalDetailsProgress, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "disabled", "disabled", "disabled", "disabled", "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN),
    (SubmittedProgress, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "active", "disabled", "disabled", "disabled", "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN),
    (AwaitingAssessmentCentreAllocation, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "active", "active", "disabled", "disabled", "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN),
    (OnlineTestInvited, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "active", "active", "disabled", "disabled", "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN),
    (OnlineTestFailed, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "active", "active", "disabled", "disabled", "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN),
    (AssessmentCentreInvited, ProgressActive, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, "active", "active", "disabled", "disabled", "John Biggs", ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN)
  )
  // scalastyle:on line.size.limit
  // format: on

  "The steps visibility when application is withdrawn from status" should {
    "be correctly determined by previous applicationStatus" in {

      forAll(WithdrawnApplications) {
        (progress: Progress,
         step1: ProgressStepVisibility,
         step2: ProgressStepVisibility,
         step3: ProgressStepVisibility,
         step4: ProgressStepVisibility,
         isApplicationSubmittedAndNotWithdrawn: Boolean,
         isApplicationInProgressAndNotWithdrawn: Boolean,
         isApplicationWithdrawn: Boolean,
         isApplicationCreatedOrInProgress: Boolean,
         isUserWithNoApplication: Boolean,
         isFirstStepVisible: String,
         isSecondStepVisible: String,
         isThirdStepVisible: String,
         isFourthStepVisible: String,
         fullName: String,
         assessmentInProgressStatus: AssessmentStageStatus,
         assessmentCompletedStatus: PostAssessmentStageStatus
        ) => {
          DashboardPage(withdrawnApplication(progress), None, None) must be(
            DashboardPage(step1, step2, step3, step4,
              isApplicationSubmittedAndNotWithdrawn, isApplicationInProgressAndNotWithdrawn,
              isApplicationWithdrawn, isApplicationCreatedOrInProgress, isUserWithNoApplication,
              isFirstStepVisible, isSecondStepVisible, isThirdStepVisible, isFourthStepVisible,
              fullName, assessmentInProgressStatus, assessmentCompletedStatus)
          )
        }
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => status }
      // For any new status DashboardPage.Step1/2/3 or 4 needs to have this status
      // Add this new status to isReached method and increase this value
      statusesTested.length must be(23)
    }
  }

  "The assessment status, when the assessment hasn't yet been confirmed" should {
    "be expired when the candidate didn't confirm it within the given date" in {
      DashboardPage(user(ALLOCATION_UNCONFIRMED), Some(AllocationDetails_Expired), None) must be(
        DashboardPage(ProgressActive, ProgressActive, ProgressInactive, ProgressInactive,
          true, false, false, false, false,
          "active", "active", "", "", "John Biggs",
          ASSESSMENT_CONFIRMATION_EXPIRED, POSTASSESSMENT_STATUS_UNKNOWN))
    }
    "be awaiting candidate confirmation when not expired" in {
      DashboardPage(user(ALLOCATION_UNCONFIRMED), Some(AllocationDetails_Not_Expired), None) must be(
        DashboardPage(ProgressActive, ProgressActive, ProgressInactive, ProgressInactive,
          true, false, false, false, false,
          "active", "active", "", "", "John Biggs",
          ASSESSMENT_PENDING_CONFIRMATION, POSTASSESSMENT_STATUS_UNKNOWN))
    }
  }

  "The assessment status, when the assessment test failed" should {
    "be failed if the relative pdf report is available" in {
      DashboardPage(user(ASSESSMENT_CENTRE_FAILED_NOTIFIED), None, Some(PdfReportAvailable)) must be(
        DashboardPage(ProgressActive, ProgressActive, ProgressInactive, ProgressInactive,
          true, false, false, false, false,
          "active", "active", "", "", "John Biggs",
          ASSESSMENT_FAILED, POSTASSESSMENT_FAILED_APPLY_AGAIN))
    }
    "and unknown if the report is not available" in {
      DashboardPage(user(ASSESSMENT_CENTRE_FAILED_NOTIFIED), None, Some(PdfReportNotAvailable)) must be(
        DashboardPage(ProgressActive, ProgressActive, ProgressInactive, ProgressInactive,
          true, false, false, false, false,
          "active", "active", "", "", "John Biggs",
          ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_FAILED_APPLY_AGAIN))
    }
  }

  "The assessment status, when the assessment test passed" should {
    "be passed if the relative pdf report is available" in {
      DashboardPage(user(ASSESSMENT_CENTRE_PASSED_NOTIFIED), None, Some(PdfReportAvailable)) must be(
        DashboardPage(ProgressActive, ProgressActive, ProgressInactive, ProgressInactive,
          true, false, false, false, false,
          "active", "active", "", "", "John Biggs",
          ASSESSMENT_PASSED, POSTASSESSMENT_PASSED_MORE_SOON))
    }
    "and unknown if the report is not available" in {
      DashboardPage(user(ASSESSMENT_CENTRE_PASSED_NOTIFIED), None, Some(PdfReportNotAvailable)) must be(
        DashboardPage(ProgressActive, ProgressActive, ProgressInactive, ProgressInactive,
          true, false, false, false, false,
          "active", "active", "", "", "John Biggs",
          ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_PASSED_MORE_SOON))
    }
  }

}

object DashboardPageSpec {

  import connectors.AllocationExchangeObjects.AllocationDetails
  import connectors.ExchangeObjects.OnlineTest
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
  private val AllocationDetails_Expired = AllocationDetails("", "", new DateTime(), Some(new LocalDate().minusDays(1)))
  private val AllocationDetails_Not_Expired = AllocationDetails("", "", new DateTime(), Some(new LocalDate().plusDays(1)))
  private val PdfReportAvailable = OnlineTest(new DateTime(), "", false, true)
  private val PdfReportNotAvailable = OnlineTest(new DateTime(), "", true, false)

}
