/*
 * Copyright 2024 HM Revenue & Customs
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

package services.onlinetesting.phase1

import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults._
import model.{ ApplicationStatus => _, _ }

class SdipFaststreamPhase1TestEvaluationSpec extends Phase1TestEvaluationSpec {

  "phase1 evaluation process" should {

    "give pass for SdipFaststream candidate when sdip scheme is green" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 70.00, 30.00, 70.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 80, 80, Commercial,
        Digital, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green, Sdip -> Green)
    }

    "give amber for SdipFaststream when sdip and faststream schemes are amber" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 70.00, 30.00, 70.00),
        (Digital, 30.00, 70.00, 30.00, 70.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 40, 40, Commercial,
        Digital, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        Commercial -> Amber, Digital -> Amber, Sdip -> Amber)
    }

    "give fail for SdipFaststream when sdip and faststream schemes are red" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 70.00, 30.00, 70.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 20, 20, Commercial,
        Digital, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
        Commercial -> Red, Digital -> Red, Sdip -> Red)
    }

    "not fail SdipFastStream with failed faststream when sdip scheme is green and faststream schemes are red" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 10.00, 10.00, 10.00, 10.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 20, 20, Commercial,
        Digital, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN),
        Commercial -> Red, Digital -> Red, Sdip -> Green)
    }

    "give pass for SdipFaststream when sdip failed and faststream schemes passed" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Commercial, 70.00, 70.00, 70.00, 70.00),
        (Sdip, 80.00, 80.00, 80.00, 80.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 70, 70, Commercial,
        Digital, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green, Sdip -> Red)
    }

    "re-evaluate sdip scheme to Red for SdipFaststream candidate after changing passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80, Commercial, Digital, Sdip)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green)

      applicationReEvaluationWithOverridingPassmarks(
        (Sdip, 90.00, 90.00, 90.00, 90.00)
      ) mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green, Sdip -> Red)
    }

    "re-evaluate sdip scheme to Green for SdipFaststream candidate after changing passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80, Commercial, Digital, Sdip)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green)

      applicationReEvaluationWithOverridingPassmarks( (Sdip, 80.00, 80.00, 80.00, 80.00) )
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green, Sdip -> Green)
    }

    "do not evaluate sdip scheme for SdipFaststream candidate until there are sdip passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80, Commercial, Digital, Sdip)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green)

      applicationReEvaluationWithOverridingPassmarks( (Sdip, 40.00, 40.00, 40.00, 40.00) )
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, Digital -> Green, Sdip -> Green)
    }

    "progress candidate to PHASE1_TESTS_FAILED_SDIP_GREEN with faststream schemes in RED and sdip in GREEN " +
      "when candidate is in sdipFaststream route and only sdip scheme score is passing the passmarks" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 50.00, 30.00, 50.00),
        (Commercial, 75.00, 75.00, 75.00, 75.00),
        (Digital, 75.00, 75.00, 75.00, 75.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 60, 60,
        Commercial, Digital, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN),
        Commercial -> Red, Digital -> Red, Sdip -> Green)
    }
  }
}
