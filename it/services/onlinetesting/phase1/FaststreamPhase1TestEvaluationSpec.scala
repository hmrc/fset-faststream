package services.onlinetesting.phase1

import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults._
import model.{ ApplicationStatus => _, _ }

class FaststreamPhase1TestEvaluationSpec extends Phase1TestEvaluationSpec {

  "phase1 evaluation process" should {
    "result in pass results when all schemes are green" in new TestFixture {
        applicationEvaluation("application-1", 80, 80, 80, 80,
          Commercial, DigitalDataTechnologyAndCyber) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
          Commercial -> Green, DigitalDataTechnologyAndCyber -> Green)

        applicationEvaluation("application-2", 79.999, 78.08, 77.77, 76.66,
          HousesOfParliament) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), HousesOfParliament -> Green)

        applicationEvaluation("application-3", 30, 30, 30, 30,
          OperationalDelivery) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), OperationalDelivery -> Green)
    }

    "result in pass results when at-least one scheme is green" in new TestFixture {
      applicationEvaluation("application-1", 20.002, 20.06, 20.0, 20.0,
        Commercial, DigitalDataTechnologyAndCyber) mustResultIn (
        PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Red, DigitalDataTechnologyAndCyber -> Green)
    }

    "result in fail results when all the schemes are red" in new TestFixture {
      applicationEvaluation("application-1", 20, 20, 20, 20,
        DiplomaticAndDevelopmentEconomics, PolicyStrategyAndGovernmentAdministration) mustResultIn (
        PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
        DiplomaticAndDevelopmentEconomics -> Red, PolicyStrategyAndGovernmentAdministration -> Red)
    }

    "result in amber when all the schemes are in amber" in new TestFixture {
      applicationEvaluation("application-1", 40, 40, 40, 40,
        DiplomaticAndDevelopmentEconomics, PolicyStrategyAndGovernmentAdministration) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        DiplomaticAndDevelopmentEconomics -> Amber, PolicyStrategyAndGovernmentAdministration -> Amber)

      applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
        Finance) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), Finance -> Amber)
    }

    "result in amber when at-least one of the schemes is amber and none of the schemes are green" in new TestFixture {
      applicationEvaluation("application-1", 30, 80, 80, 80,
        Commercial, Property) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        Commercial -> Amber, Property -> Red)
    }

    "result in pass results for gis candidates" in new TestFixture {
      gisApplicationEvaluation("application-1", 25, 25,
        Commercial, DigitalDataTechnologyAndCyber) mustResultIn (
        PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        Commercial -> Amber, DigitalDataTechnologyAndCyber -> Green)
    }

    "result in pass results on re-evaluation of applicant in amber when passmarks are decreased" in new TestFixture {
      {
        applicationEvaluation("application-1", 40, 40, 40, 40,
          DiplomaticAndDevelopmentEconomics, PolicyStrategyAndGovernmentAdministration)
          mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
            DiplomaticAndDevelopmentEconomics -> Amber, PolicyStrategyAndGovernmentAdministration -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (DiplomaticAndDevelopmentEconomics, 30, 30, 30, 30, 30, 30, 30, 30),
          (PolicyStrategyAndGovernmentAdministration, 30, 30, 30, 30, 30, 30, 30, 30))
        mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
          DiplomaticAndDevelopmentEconomics -> Green, PolicyStrategyAndGovernmentAdministration -> Green)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
          Finance) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), Finance -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (Finance, 25.011, 25.014, 25.011, 25.014, 25.011, 25.014, 25.011, 25.014)
        ) mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), Finance -> Green)
      }
    }

    "result in fail results on re-evaluation of applicant in amber when fail marks are increased" in new TestFixture {
      {
        applicationEvaluation("application-1", 40, 40, 40, 40,
          DiplomaticAndDevelopmentEconomics, PolicyStrategyAndGovernmentAdministration)
        mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          DiplomaticAndDevelopmentEconomics -> Amber, PolicyStrategyAndGovernmentAdministration -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (DiplomaticAndDevelopmentEconomics, 41, 42, 41, 42, 41, 42, 41, 42),
          (PolicyStrategyAndGovernmentAdministration,         41, 42, 41, 42, 41, 42, 41, 42)
        ) mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
          DiplomaticAndDevelopmentEconomics -> Red, PolicyStrategyAndGovernmentAdministration -> Red)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
          Finance) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), Finance -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (Finance, 26.015, 27.015, 26.015, 27.015, 26.015, 27.015, 26.015, 27.015)
        ) mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED), Finance -> Red)
      }
    }

    "leave applicants in amber on re-evaluation when passmarks and failmarks are changed but within the amber range" in new TestFixture {
      {
        applicationEvaluation("application-1", 40, 40, 40, 40,
          DiplomaticAndDevelopmentEconomics, PolicyStrategyAndGovernmentAdministration)
        mustResultIn (PHASE1_TESTS,  Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          DiplomaticAndDevelopmentEconomics -> Amber, PolicyStrategyAndGovernmentAdministration -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (DiplomaticAndDevelopmentEconomics, 38, 42, 38, 42, 38, 42, 38, 42),
          (PolicyStrategyAndGovernmentAdministration,         38, 42, 38, 42, 38, 42, 38, 42)
        ) mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          DiplomaticAndDevelopmentEconomics -> Amber, PolicyStrategyAndGovernmentAdministration -> Amber)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, 25.015, 25.015,
          Finance) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), Finance -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (Finance, 24.015, 27.015, 24.015, 27.015, 24.015, 27.015, 24.015, 27.015)
        ) mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), Finance -> Amber)
      }
    }
  }
}
