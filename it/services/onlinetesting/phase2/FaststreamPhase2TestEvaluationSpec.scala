package services.onlinetesting.phase2

import model.ApplicationStatus._
import model.EvaluationResults._
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ProgressStatuses, _ }

class FaststreamPhase2TestEvaluationSpec extends Phase2TestEvaluationSpec {

  "phase2 evaluation process" should {
    "result in passed results when all schemes are green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, 80, Commercial, DigitalDataTechnologyAndCyber) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          Commercial -> Green, DigitalDataTechnologyAndCyber -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(HousesOfParliament, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 79.999, 79.999, HousesOfParliament) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED), HousesOfParliament -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(OperationalDelivery, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 30, 30, OperationalDelivery) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED), OperationalDelivery -> Green)
      }
    }

    "result in passed results when at-least one scheme is green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Red.toString),
            SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, 80, Commercial, DigitalDataTechnologyAndCyber) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          Commercial -> Red, DigitalDataTechnologyAndCyber -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(HumanResources, Green.toString),
            SchemeEvaluationResult(ProjectDelivery, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 50, 50, HumanResources, ProjectDelivery) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          HumanResources -> Green, ProjectDelivery -> Amber)
      }
    }

    "result in failed results when all the schemes are red" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Property, Green.toString),
            SchemeEvaluationResult(ScienceAndEngineering, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 35, 35, Property, ScienceAndEngineering) mustResultIn(
          PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
          Property -> Red, ScienceAndEngineering -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Property, Red.toString),
            SchemeEvaluationResult(ScienceAndEngineering, Red.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 80, 80, Property, ScienceAndEngineering) mustResultIn(
          PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
          Property -> Red, ScienceAndEngineering -> Red)
      }
    }

    "result in amber when no schemes are in green and at-least one scheme is in amber" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 20, 20, Commercial) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED), Commercial -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 20, 20, Commercial, DigitalDataTechnologyAndCyber) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          Commercial -> Amber, DigitalDataTechnologyAndCyber -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Property, Amber.toString),
            SchemeEvaluationResult(ScienceAndEngineering, Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 80, 80, Property, ScienceAndEngineering) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          Property -> Amber, ScienceAndEngineering -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Property, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-4", 50, 50, Property) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED), Property -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Property, Amber.toString),
            SchemeEvaluationResult(ProjectDelivery, Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-5", 50, 50, Property, ProjectDelivery) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          Property -> Amber, ProjectDelivery -> Amber)
      }
    }

    "result in passed results on re-evaluation of applicant with all schemes in amber when passmarks are decreased" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Green.toString),
            SchemeEvaluationResult(GovernmentPolicy, Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-1", 40, 40,
          DiplomaticAndDevelopmentEconomics, GovernmentPolicy) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          DiplomaticAndDevelopmentEconomics -> Amber, GovernmentPolicy -> Amber)

        applicationReEvaluationWithSettings(
          (DiplomaticAndDevelopmentEconomics,         40, 40, 40, 40),
          (GovernmentPolicy, 40, 40, 40, 40)
        ) mustResultIn(PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          DiplomaticAndDevelopmentEconomics -> Green, GovernmentPolicy -> Green)
      }
    }

    "result in passed results on re-evaluation of applicant with one scheme in amber when passmark are decreased" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(HumanResources, Red.toString),
            SchemeEvaluationResult(ProjectDelivery, Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-2", 50, 50, HumanResources, ProjectDelivery) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          HumanResources -> Red, ProjectDelivery -> Amber)

        applicationReEvaluationWithSettings(
          (ProjectDelivery, 50, 50, 50, 50))
        mustResultIn(PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          HumanResources -> Red, ProjectDelivery -> Green)
      }
    }

    "result in failed results on re-evaluation of applicant in amber when failmarks are increased" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Green.toString),
          SchemeEvaluationResult(GovernmentPolicy, Green.toString)),
        "phase1-version1-res", None)

      applicationEvaluation("application-1", 40, 40,
        DiplomaticAndDevelopmentEconomics, GovernmentPolicy) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        DiplomaticAndDevelopmentEconomics -> Amber, GovernmentPolicy -> Amber)

      applicationReEvaluationWithSettings(
        (DiplomaticAndDevelopmentEconomics,         41, 41, 41, 41),
        (GovernmentPolicy, 41, 41, 41, 41)
      ) mustResultIn(PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
        DiplomaticAndDevelopmentEconomics -> Red, GovernmentPolicy -> Red)
    }

    "leave applicants in amber on re-evaluation when passmarks and failmarks are changed but within the amber range" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Green.toString),
          SchemeEvaluationResult(GovernmentPolicy, Green.toString)),
        "phase1-version1-res", None)

      applicationEvaluation("application-1", 40, 40,
        DiplomaticAndDevelopmentEconomics, GovernmentPolicy) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        DiplomaticAndDevelopmentEconomics -> Amber, GovernmentPolicy -> Amber)

      applicationReEvaluationWithSettings(
        (DiplomaticAndDevelopmentEconomics,         35, 45, 35, 45),
        (GovernmentPolicy, 35, 45, 35, 45)
      ) mustResultIn(PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        DiplomaticAndDevelopmentEconomics -> Amber, GovernmentPolicy -> Amber)
    }
  }
}
