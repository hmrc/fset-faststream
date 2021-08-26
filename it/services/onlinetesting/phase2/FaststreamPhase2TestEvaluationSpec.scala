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
          List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
            SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, 80, SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          SchemeId("Commercial") -> Green, SchemeId("DigitalDataTechnologyAndCyber") -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("HousesOfParliament"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 79.999, 79.999, SchemeId("HousesOfParliament")) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED), SchemeId("HousesOfParliament") -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Generalist"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 30, 30, SchemeId("Generalist")) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED), SchemeId("Generalist") -> Green)
      }
    }

    "result in passed results when at-least one scheme is green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Red.toString),
            SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, 80, SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          SchemeId("Commercial") -> Red, SchemeId("DigitalDataTechnologyAndCyber") -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("HumanResources"), Green.toString),
            SchemeEvaluationResult(SchemeId("ProjectDelivery"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 50, 50, SchemeId("HumanResources"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          SchemeId("HumanResources") -> Green, SchemeId("ProjectDelivery") -> Amber)
      }
    }

    "result in failed results when all the schemes are red" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Green.toString),
            SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 35, 35, SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
          SchemeId("European") -> Red, SchemeId("ScienceAndEngineering") -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Red.toString),
            SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Red.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 80, 80, SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
          SchemeId("European") -> Red, SchemeId("ScienceAndEngineering") -> Red)
      }
    }

    "result in amber when no schemes are in green and at-least one scheme is in amber" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 20, 20, SchemeId("Commercial")) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED), SchemeId("Commercial") -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
            SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 20, 20, SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          SchemeId("Commercial") -> Amber, SchemeId("DigitalDataTechnologyAndCyber") -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Amber.toString),
            SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 80, 80, SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          SchemeId("European") -> Amber, SchemeId("ScienceAndEngineering") -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-4", 50, 50, SchemeId("European")) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED), SchemeId("European") -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Amber.toString),
            SchemeEvaluationResult(SchemeId("ProjectDelivery"), Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-5", 50, 50, SchemeId("European"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          SchemeId("European") -> Amber, SchemeId("ProjectDelivery") -> Amber)
      }
    }

    "result in passed results on re-evaluation of applicant with all schemes in amber when passmarks are decreased" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopmentEconomics"), Green.toString),
            SchemeEvaluationResult(SchemeId("DiplomaticServiceEuropean"), Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-1", 40, 40,
          SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithSettings(
          (SchemeId("DiplomaticAndDevelopmentEconomics"), 40, 40, 40, 40),
          (SchemeId("DiplomaticServiceEuropean"), 40, 40, 40, 40)
        ) mustResultIn(PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          SchemeId("DiplomaticAndDevelopmentEconomics") -> Green, SchemeId("DiplomaticServiceEuropean") -> Green)
      }
    }

    "result in passed results on re-evaluation of applicant with one scheme in amber when passmark are decreased" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("HumanResources"), Red.toString),
            SchemeEvaluationResult(SchemeId("ProjectDelivery"), Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-2", 50, 50, SchemeId("HumanResources"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
          SchemeId("HumanResources") -> Red, SchemeId("ProjectDelivery") -> Amber)

        applicationReEvaluationWithSettings(
          (SchemeId("ProjectDelivery"), 50, 50, 50, 50))
        mustResultIn(PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          SchemeId("HumanResources") -> Red, SchemeId("ProjectDelivery") -> Green)
      }
    }

    "result in failed results on re-evaluation of applicant in amber when failmarks are increased" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopmentEconomics"), Green.toString),
          SchemeEvaluationResult(SchemeId("DiplomaticServiceEuropean"), Green.toString)),
        "phase1-version1-res", None)

      applicationEvaluation("application-1", 40, 40,
        SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

      applicationReEvaluationWithSettings(
        (SchemeId("DiplomaticAndDevelopmentEconomics"), 41, 41, 41, 41),
        (SchemeId("DiplomaticServiceEuropean"), 41, 41, 41, 41)
      ) mustResultIn(PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
        SchemeId("DiplomaticAndDevelopmentEconomics") -> Red, SchemeId("DiplomaticServiceEuropean") -> Red)
    }

    "leave applicants in amber on re-evaluation when passmarks and failmarks are changed but within the amber range" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopmentEconomics"), Green.toString),
          SchemeEvaluationResult(SchemeId("DiplomaticServiceEuropean"), Green.toString)),
        "phase1-version1-res", None)

      applicationEvaluation("application-1", 40, 40,
        SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

      applicationReEvaluationWithSettings(
        (SchemeId("DiplomaticAndDevelopmentEconomics"), 35, 45, 35, 45),
        (SchemeId("DiplomaticServiceEuropean"), 35, 45, 35, 45)
      ) mustResultIn(PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)
    }
  }
}
