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
    ("applicationStatus",                      "step1",          "step2",         "step3",                    "step4",                  "isApplicationSubmittedAndNotWithdrawn", "isApplicationInProgressAndNotWithdrawn", "isApplicationWithdrawn", "isApplicationCreatedOrInProgress", "isUserWithNoApplication",  "isPhase1TestsPassed", "isTestGroupExpired", "isPhase2TestGroupExpired",    "fullName",   "testProfile", "testProfile",  "assessmentInProgressStatus",       "assessmentCompletedStatus"),
    (REGISTERED,                               ProgressInactive, ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   false,                                    false,                    false,                              true,                       false,                 false,                 false,                        "John Biggs",  None,         None,           ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (CREATED,                                  ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   true,                                     false,                    true,                               false,                      true,                 false,                 false,                        "John Biggs",  None,         None,           ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (IN_PROGRESS,                              ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                   true,                                     false,                    true,                               false,                      true,                 false,                 false,                        "John Biggs",  None,         None,           ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (SUBMITTED,                                ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                      true,                 false,                 false,                        "John Biggs",  None,         None,           ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (WITHDRAWN,                                ProgressActive,   ProgressActive,   ProgressActive,            ProgressInactiveDisabled, false,                                   false,                                    true,                     false,                              false,                      true,                 false,                 false,                        "John Biggs",  None,         None,           ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    (PHASE1_TESTS,                             ProgressActive,   ProgressActive,   ProgressInactiveDisabled,  ProgressInactiveDisabled,  true,                                   false,                                    false,                    false,                              false,                      true,                 true,                  false,                        "John Biggs",  None,         None,           ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN)
    //(ALLOCATION_CONFIRMED,                     ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    //(ALLOCATION_UNCONFIRMED,                   ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_PENDING_CONFIRMATION,    POSTASSESSMENT_STATUS_UNKNOWN),
    //(AWAITING_ALLOCATION,                      ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    //(FAILED_TO_ATTEND,                         ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_NOT_ATTENDED,            POSTASSESSMENT_FAILED_APPLY_AGAIN),
    //(ASSESSMENT_SCORES_ENTERED,                ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    //(ASSESSMENT_SCORES_ACCEPTED,               ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    //(AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION, ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_BOOKED_CONFIRMED,        POSTASSESSMENT_STATUS_UNKNOWN),
    //(ASSESSMENT_CENTRE_PASSED,                 ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    //(ASSESSMENT_CENTRE_FAILED,                 ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_STATUS_UNKNOWN),
    //(ASSESSMENT_CENTRE_PASSED_NOTIFIED,        ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_PASSED_MORE_SOON),
    //(ASSESSMENT_CENTRE_FAILED_NOTIFIED,        ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                    false,                                    false,                    false,                              false,                         "John Biggs", None,   ASSESSMENT_STATUS_UNKNOWN,          POSTASSESSMENT_FAILED_APPLY_AGAIN)
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
         isPhase1TestsPassed: Boolean,
         isTestGroupExpired: Boolean,
         isPhase2TestGroupExpired: Boolean,
         fullName: String,
         testProfile: Option[Phase1TestsPage],
         phase2TestProfile: Option[Phase2TestsPage],
         assessmentInProgressStatus: AssessmentStageStatus,
         assessmentCompletedStatus: PostAssessmentStageStatus
        ) => {
          DashboardPage(user(status), None, None, None) mustBe
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
              isPhase1TestsPassed,
              isTestGroupExpired,
              isPhase2TestGroupExpired,
              fullName,
              testProfile,
              phase2TestProfile,
              assessmentInProgressStatus,
              assessmentCompletedStatus
            )
        }
      }
    }

    // TODO FIX ME - when all app statuses have been implemented
    "be tested for all statuses" ignore {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _ , _, _, _, _, _, _, _, _, _, _, _, _, _) => status }
      val allStatuses = ApplicationStatus.values.toList

      val statusesNotTested = allStatuses.diff(statusesTested)
      statusesNotTested mustBe(empty)
    }
  }

  // format: off
  // scalastyle:off line.size.limit
  val WithdrawnApplications = Table(
    ("Status before Withdraw", "step1", "step2", "step3", "step4", "isApplicationSubmittedAndNotWithdrawn", "isApplicationInProgressAndNotWithdrawn", "isApplicationWithdrawn", "isApplicationCreatedOrInProgress", "isUserWithNoApplication", "isPhase1TestsPassed", "isTestGroupExpired", "isPhase2TestGroupExpired","fullName", "testProfile", "phase2TestProfile", "assessmentInProgressStatus", "assessmentCompletedStatus"),
    (PersonalDetailsProgress, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, false, false, false, "John Biggs", None, None, ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN),
    (SubmittedProgress, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, false, true, false, false, false, false, false, "John Biggs", None, None, ASSESSMENT_STATUS_UNKNOWN, POSTASSESSMENT_STATUS_UNKNOWN)
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
         isPhase1TestsPassed: Boolean,
         isTestGroupExpired: Boolean,
         isPhase2TestGroupExpired: Boolean,
         fullName: String,
         testProfile: Option[Phase1TestsPage],
         phase2TestProfile: Option[Phase2TestsPage],
         assessmentInProgressStatus: AssessmentStageStatus,
         assessmentCompletedStatus: PostAssessmentStageStatus
        ) => {
          DashboardPage(withdrawnApplication(progress), None, None, None) mustBe
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
              isPhase1TestsPassed,
              isTestGroupExpired,
              isPhase2TestGroupExpired,
              fullName,
              testProfile,
              phase2TestProfile,
              assessmentInProgressStatus,
              assessmentCompletedStatus
            )
        }
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList.map { case (status, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => status }
      // For any new status DashboardPage.Step1/2/3 or 4 needs to have this status
      // Add this new status to isReached method and increase this value
      statusesTested.length mustBe(6)
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

  val phase1TestProfile = Phase1TestGroup(expirationDate = DateTime.now,
    tests = List(CubiksTest(usedForResults = true,
      testUrl = "test.com",
      invitationDate = DateTime.now,
      token = UniqueIdentifier(UUID.randomUUID()),
      cubiksUserId = 123,
      startedDateTime = Some(DateTime.now),
      completedDateTime= Some(DateTime.now),
      resultsReadyToDownload = false
    ))
  )
  val PersonalDetailsProgress = EmptyProgress.copy(personalDetails = true)
  val SubmittedProgress = PersonalDetailsProgress.copy(submitted = true)
  private val AllocationDetails_Expired = AllocationDetails("", "", new DateTime(), Some(new LocalDate().minusDays(1)))
  private val AllocationDetails_Not_Expired = AllocationDetails("", "", new DateTime(), Some(new LocalDate().plusDays(1)))

}
