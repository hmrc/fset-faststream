package services.onlinetesting.phase1

import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults._
import model.{ ApplicationStatus => _, _ }

class FaststreamPhase1TestEvaluationSpec extends Phase1TestEvaluationSpec {

  "phase1 evaluation process" should {
    "result in pass results when all schemes are green" in new TestFixture {
        applicationEvaluation("application-1", 80, 80, 80, 80,
          SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
          SchemeId("Commercial") -> Green, SchemeId("DigitalDataTechnologyAndCyber") -> Green)

        applicationEvaluation("application-2", 79.999, 78.08, 77.77, 76.66,
          SchemeId("HousesOfParliament")) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), SchemeId("HousesOfParliament") -> Green)

        applicationEvaluation("application-3", 30, 30, 30, 30,
          SchemeId("Generalist")) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), SchemeId("Generalist") -> Green)
    }

    "result in pass results when at-least one scheme is green" in new TestFixture {
      applicationEvaluation("application-1", 20.002, 20.06, 20.0, 20.0,
        SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn (
        PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Red, SchemeId("DigitalDataTechnologyAndCyber") -> Green)
    }

    "result in fail results when all the schemes are red" in new TestFixture {
      applicationEvaluation("application-1", 20, 20, 20, 20,
        SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn (
        PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
        SchemeId("DiplomaticAndDevelopmentEconomics") -> Red, SchemeId("DiplomaticServiceEuropean") -> Red)
    }

    "result in amber when all the schemes are in amber" in new TestFixture {
      applicationEvaluation("application-1", 40, 40, 40, 40,
        SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

      applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
        SchemeId("Finance")) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)
    }

    "result in amber when at-least one of the schemes is amber and none of the schemes are green" in new TestFixture {
      applicationEvaluation("application-1", 30, 80, 80, 80,
        SchemeId("Commercial"), SchemeId("European")) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        SchemeId("Commercial") -> Amber, SchemeId("European") -> Red)
    }

    "result in pass results for gis candidates" in new TestFixture {
      gisApplicationEvaluation("application-1", 25, 25,
        SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn (
        PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Amber, SchemeId("DigitalDataTechnologyAndCyber") -> Green)
    }

    "result in pass results on re-evaluation of applicant in amber when passmarks are decreased" in new TestFixture {
      {
        applicationEvaluation("application-1", 40, 40, 40, 40,
          SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean"))
          mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
            SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("DiplomaticAndDevelopmentEconomics"), 30, 30, 30, 30, 30, 30, 30, 30),
          (SchemeId("DiplomaticServiceEuropean"), 30, 30, 30, 30, 30, 30, 30, 30))
        mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
          SchemeId("DiplomaticAndDevelopmentEconomics") -> Green, SchemeId("DiplomaticServiceEuropean") -> Green)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
          SchemeId("Finance")) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("Finance"), 25.011, 25.014, 25.011, 25.014, 25.011, 25.014, 25.011, 25.014)
        ) mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), SchemeId("Finance") -> Green)
      }
    }

    "result in fail results on re-evaluation of applicant in amber when fail marks are increased" in new TestFixture {
      {
        applicationEvaluation("application-1", 40, 40, 40, 40,
          SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean"))
        mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("DiplomaticAndDevelopmentEconomics"), 41, 42, 41, 42, 41, 42, 41, 42),
          (SchemeId("DiplomaticServiceEuropean"), 41, 42, 41, 42, 41, 42, 41, 42)
        ) mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
          SchemeId("DiplomaticAndDevelopmentEconomics") -> Red, SchemeId("DiplomaticServiceEuropean") -> Red)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
          SchemeId("Finance")) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("Finance"), 26.015, 27.015, 26.015, 27.015, 26.015, 27.015, 26.015, 27.015)
        ) mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED), SchemeId("Finance") -> Red)
      }
    }

    "leave applicants in amber on re-evaluation when passmarks and failmarks are changed but within the amber range" in new TestFixture {
      {
        applicationEvaluation("application-1", 40, 40, 40, 40,
          SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean"))
        mustResultIn (PHASE1_TESTS,  Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("DiplomaticAndDevelopmentEconomics"), 38, 42, 38, 42, 38, 42, 38, 42),
          (SchemeId("DiplomaticServiceEuropean"), 38, 42, 38, 42, 38, 42, 38, 42)
        ) mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
          SchemeId("Finance")) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("Finance"), 24.015, 27.015, 24.015, 27.015, 24.015, 27.015, 24.015, 27.015)
        ) mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)
      }
    }
  }
}
