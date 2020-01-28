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

import java.util.UUID

import connectors.exchange._
import controllers.UnitSpec
import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus._
import models._
import models.page.DashboardPage.Flags._
import models.page.DashboardPage._
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import security.RolesSpec
import connectors.exchange.ProgressExamples._
import org.scalatest.Inside

class DashboardPageSpec extends UnitSpec with TableDrivenPropertyChecks with Inside {
  import DashboardPageSpec._

  implicit val request: RequestHeader = FakeRequest()
  implicit val lang: Lang = Lang.defaultLang

  // format: OFF
  // scalastyle:off line.size.limit
  val Applications = Table(
    ("applicationStatus",                      "step1",          "step2",         "step3",                    "step4",                  "isApplicationSubmittedAndNotWithdrawn",  "isApplicationWithdrawn",  "isApplicationInProgress", "isUserWithNoApplication",  "isPhase1TestsPassed", "isPhase2TestsPassed", "isTestGroupExpired", "isPhase2TestGroupExpired", "isPhase3TestGroupExpired",   "isPhase1TestFailed", "isPhase2TestFailed",  "isPhase3TestFailed",  "fullName",   "testProfile", "phase2TestProfile", "phase3TestProfile",  "assessmentInProgressStatus"),
    (REGISTERED,                               ProgressInactive, ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                    false,                     false,                     true,                       false,                  false,                false,                 false,                     false,                        false,                false,                 false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (CREATED,                                  ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                    false,                     false,                     true,                       false,                  false,                false,                 false,                     false,                        false,                false,                 false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (IN_PROGRESS,                              ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         false,                                    false,                     true,                      false,                      false,                  false,                false,                 false,                     false,                        false,                false,                 false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (SUBMITTED,                                ProgressActive,   ProgressInactive, ProgressInactive,          ProgressInactive,         true,                                     false,                     false,                     false,                      false,                  false,                false,                 false,                     false,                        false,                false,                 false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (WITHDRAWN,                                ProgressActive,   ProgressActive,   ProgressActive,            ProgressInactiveDisabled, false,                                    true,                      false,                     false,                      true,                   true,                 false,                 false,                     false,                        false,                false,                 false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (PHASE1_TESTS,                             ProgressActive,   ProgressActive,   ProgressInactive,          ProgressInactive,         true,                                     false,                     false,                     false,                      true,                   false,                false,                 false,                     false,                        false,                false,                 false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (PHASE1_TESTS_FAILED,                      ProgressActive,   ProgressInactive, ProgressInactiveDisabled,  ProgressInactiveDisabled, true,                                     false,                     false,                     false,                      false,                  false,                false,                 false,                     false,                        true,                 false,                 false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (PHASE2_TESTS_FAILED,                      ProgressActive,   ProgressInactive, ProgressInactiveDisabled,  ProgressInactiveDisabled, true,                                     false,                     false,                     false,                      false,                  false,                false,                 false,                     false,                        false,                true,                  false,                 "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN),
    (PHASE3_TESTS_FAILED,                      ProgressActive,   ProgressInactive, ProgressInactiveDisabled,  ProgressInactiveDisabled, true,                                     false,                     false,                     false,                      false,                  false,                false,                 false,                     false,                        false,                false,                 true,                  "John Biggs",  None,         None,                 None,                 ASSESSMENT_STATUS_UNKNOWN)
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
         isApplicationWithdrawn: Boolean,
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
         fullName: String,
         testProfile: Option[Phase1TestsPage2],
         phase2TestProfile: Option[Phase2TestsPage2],
         phase3TestGroup: Option[Phase3TestsPage],
         assessmentInProgressStatus: AssessmentStageStatus
        ) => {
          val expected = DashboardPage(
              step1,
              step2,
              step3,
              step4,
              isApplicationSubmittedAndNotWithdrawn,
              isApplicationWithdrawn,
              isFastPassApproved = false,
              isApplicationInProgress,
              isUserWithNoApplication,
              isPhase1TestsPassed,
              isPhase2TestsPassed,
              isTestGroupExpired,
              isPhase2TestGroupExpired,
              isPhase3TestGroupExpired,
              isPhase1TestFailed,
              isPhase2TestFailed,
              isPhase3TestFailed,
              false,
              fullName,
              testProfile,
              phase2TestProfile,
              phase3TestGroup,
              assessmentInProgressStatus
            )

          // this is rather unwieldy but makes it much easier to match up what field is wrong in the long list of booleans
          val actual = DashboardPage(user(status), None, None, None)

          actual.firstStepVisibility mustBe expected.firstStepVisibility
          actual.secondStepVisibility mustBe expected.secondStepVisibility
          actual.thirdStepVisibility mustBe expected.thirdStepVisibility
          actual.fourthStepVisibility mustBe expected.fourthStepVisibility
          actual.isApplicationSubmittedAndNotWithdrawn mustBe expected.isApplicationSubmittedAndNotWithdrawn
          actual.isApplicationWithdrawn mustBe expected.isApplicationWithdrawn
          actual.isApplicationInProgress mustBe expected.isApplicationInProgress
          actual.isUserWithNoApplication mustBe expected.isUserWithNoApplication
          actual.isPhase1TestsPassed mustBe expected.isPhase1TestsPassed
          actual.isPhase2TestsPassed mustBe expected.isPhase2TestsPassed
          actual.isTestGroupExpired mustBe expected.isTestGroupExpired
          actual.isPhase2TestGroupExpired mustBe expected.isPhase2TestGroupExpired
          actual.isPhase3TestGroupExpired mustBe expected.isPhase3TestGroupExpired
          actual.isPhase1TestFailed mustBe expected.isPhase1TestFailed
          actual.isPhase2TestFailed mustBe expected.isPhase2TestFailed
          actual.isPhase3TestFailed mustBe expected.isPhase3TestFailed
          actual.fullName mustBe expected.fullName
          actual.phase1TestsPage mustBe expected.phase1TestsPage
          actual.phase2TestsPage mustBe expected.phase2TestsPage
          actual.phase3TestsPage mustBe expected.phase3TestsPage
          actual.assessmentStageStatus mustBe expected.assessmentStageStatus
        }
      }
    }

    // TODO FIX ME - when all app statuses have been implemented
    "be tested for all statuses" ignore {
      val statusesTested = Applications.toList.map(_._1)
      val allStatuses = ApplicationStatus.values.toList

      statusesTested must contain theSameElementsAs allStatuses
    }
  }

  // format: off
  // scalastyle:off line.size.limit
 val WithdrawnApplications = Table(
    ("Status before Withdraw", "step1", "step2", "step3", "step4", "isApplicationSubmittedAndNotWithdrawn", "isApplicationWithdrawn", "isApplicationInProgress", "isUserWithNoApplication", "isPhase1TestsPassed", "isPhase2TestsPassed", "isTestGroupExpired", "isPhase2TestGroupExpired", "isPhase3TestGroupExpired", "isPhase1TestFailed", "isPhase2TestFailed", "isPhase3TestFailed", "fullName", "testProfile", "phase2TestProfile", "phase3TestGroup", "assessmentInProgressStatus"),
    (PersonalDetailsProgressResponse, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, true, false, false, false, false, false, false, false, false, false, false, "John Biggs", None, None, None, ASSESSMENT_STATUS_UNKNOWN),
    (SubmittedProgressResponse, ProgressActive, ProgressInactiveDisabled, ProgressInactiveDisabled, ProgressInactiveDisabled, false, true, false, false, false, false, false, false, false, false, false, false, "John Biggs", None, None, None, ASSESSMENT_STATUS_UNKNOWN)
  )
  // scalastyle:on line.size.limit
  // format: on
  "Withdrawn The steps visibility when application is withdrawn from status" should {
    "be correctly determined by previous applicationStatus" in {

      forAll(WithdrawnApplications) {
        (progress: ProgressResponse,
         step1: ProgressStepVisibility,
         step2: ProgressStepVisibility,
         step3: ProgressStepVisibility,
         step4: ProgressStepVisibility,
         isApplicationSubmittedAndNotWithdrawn: Boolean,
         isApplicationWithdrawn: Boolean,
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
         fullName: String,
         testProfile: Option[Phase1TestsPage2],
         phase2TestProfile: Option[Phase2TestsPage2],
         phase3TestGroup: Option[Phase3TestsPage],
         assessmentInProgressStatus: AssessmentStageStatus
        ) => {
          DashboardPage(withdrawnApplication(progress), None, None, None) mustBe
            DashboardPage(
              step1,
              step2,
              step3,
              step4,
              isApplicationSubmittedAndNotWithdrawn,
              isApplicationWithdrawn,
              false,
              isApplicationInProgress,
              isUserWithNoApplication,
              isPhase1TestsPassed,
              isPhase2TestsPassed,
              isTestGroupExpired,
              isPhase2TestGroupExpired,
              isPhase3TestGroupExpired,
              isPhase1TestFailed,
              isPhase2TestFailed,
              isPhase3TestFailed,
              false,
              fullName,
              testProfile,
              phase2TestProfile,
              phase3TestGroup,
              assessmentInProgressStatus
            )
        }
      }
    }

    "be tested for all statuses" in {
      val statusesTested = Applications.toList
      // For any new status DashboardPage.Step1/2/3 or 4 needs to have this status
      // Add this new status to isReached method and increase this value
      statusesTested.length mustBe 9
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

  def user(status: ApplicationStatus.Value) = {
    import models.ApplicationData.ApplicationStatus._
    status match {
     case REGISTERED => RolesSpec.registeredUser(status)
     case CREATED | IN_PROGRESS => RolesSpec.activeUser(status, InitialProgress)
     case SUBMITTED => RolesSpec.activeUser(status, SubmittedProgress)
     case WITHDRAWN => RolesSpec.activeUser(status)
     case PHASE1_TESTS => RolesSpec.activeUser(status, Phase1TestsPassed)
     case PHASE1_TESTS_FAILED => RolesSpec.activeUser(status, Phase1TestsFailed)
     case PHASE2_TESTS_FAILED => RolesSpec.activeUser(status, Phase2TestsFailed)
     case PHASE3_TESTS_FAILED => RolesSpec.activeUser(status, Phase3TestsFailed)
     case _ => RolesSpec.activeUser(status)
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

  val EmptyProgressResponse = ProgressResponseExamples.Initial

  val PersonalDetailsProgressResponse = EmptyProgressResponse.copy(personalDetails = true)
  val SubmittedProgressResponse = PersonalDetailsProgressResponse.copy(submitted = true)
}
