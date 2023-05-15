package services.onlinetesting.phase1

import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults._
import model.{ ApplicationStatus => _, _ }

class SdipFaststreamPhase1TestEvaluationSpec extends Phase1TestEvaluationSpec {

  "phase1 evaluation process" should {

    "give pass for SdipFaststream candidate when sdip scheme is green" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 80, 80, 80, 80, Commercial,
        DigitalDataTechnologyAndCyber, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green, Sdip -> Green)
    }

    "give amber for SdipFaststream when sdip and faststream schemes are amber" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00),
        (DigitalDataTechnologyAndCyber, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 40, 40, 40, 40, Commercial,
        DigitalDataTechnologyAndCyber, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        Commercial -> Amber, DigitalDataTechnologyAndCyber -> Amber, Sdip -> Amber)
    }

    "give fail for SdipFaststream when sdip and faststream schemes are red" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00, 30.00, 70.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 20, 20, 20, 20, Commercial,
        DigitalDataTechnologyAndCyber, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
        Commercial -> Red, DigitalDataTechnologyAndCyber -> Red, Sdip -> Red)
    }

    "not fail SdipFastStream with failed faststream when sdip scheme is green and faststream schemes are red" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 10.00)
      )
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 20, 20, 20, 20, Commercial,
        DigitalDataTechnologyAndCyber, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN),
        Commercial -> Red, DigitalDataTechnologyAndCyber -> Red, Sdip -> Green)
    }

    "give pass for SdipFaststream when sdip failed and faststream schemes passed" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Commercial, 70.00, 70.00, 70.00, 70.00, 70.00, 70.00, 70.00, 70.00),
        (Sdip, 80.00, 80.00, 80.00, 80.00, 80.00, 80.00, 80.00, 80.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 70, 70, 70, 70, Commercial,
        DigitalDataTechnologyAndCyber, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green, Sdip -> Red)
    }

    "re-evaluate sdip scheme to Red for SdipFaststream candidate after changing passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80, 80, 80, Commercial, DigitalDataTechnologyAndCyber, Sdip)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green)

      applicationReEvaluationWithOverridingPassmarks(
        (Sdip, 90.00, 90.00, 90.00, 90.00, 90.00, 90.00, 90.00, 90.00)
      ) mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green, Sdip -> Red)
    }

    "re-evaluate sdip scheme to Green for SdipFaststream candidate after changing passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80, 80, 80, Commercial, DigitalDataTechnologyAndCyber, Sdip)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green)

      applicationReEvaluationWithOverridingPassmarks( (Sdip, 80.00, 80.00, 80.00, 80.00, 80.00, 80.00, 80.00, 80.00) )
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green, Sdip -> Green)
    }

    "do not evaluate sdip scheme for SdipFaststream candidate until there are sdip passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80, 80, 80, Commercial, DigitalDataTechnologyAndCyber, Sdip)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green)

      applicationReEvaluationWithOverridingPassmarks( (Sdip, 40.00, 40.00, 40.00, 40.00, 40.00, 40.00, 40.00, 40.00) )
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green, Sdip -> Green)
    }

    "progress candidate to PHASE1_TESTS_FAILED_SDIP_GREEN with faststream schemes in RED and sdip in GREEN " +
      "when candidate is in sdipFaststream route and only sdip scheme score is passing the passmarks" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (Sdip, 30.00, 50.00, 30.00, 50.00, 30.00, 50.00, 30.00, 50.00),
        (Commercial, 75.00, 75.00, 75.00, 75.00, 75.00, 75.00, 75.00, 75.00),
        (DigitalDataTechnologyAndCyber, 75.00, 75.00, 75.00, 75.00, 75.00, 75.00, 75.00, 75.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 60, 60, 60, 60,
        Commercial, DigitalDataTechnologyAndCyber, Sdip)(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN),
        Commercial -> Red, DigitalDataTechnologyAndCyber -> Red, Sdip -> Green)
    }
  }
}
