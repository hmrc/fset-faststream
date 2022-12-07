package services.onlinetesting.phase2

import model.ApplicationStatus._
import model.EvaluationResults._
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationRoute, ProgressStatuses, _ }

class SdipFaststreamPhase2TestEvaluationSpec extends Phase2TestEvaluationSpec {

  "phase2 evaluation process" should {
    "give pass for SdipFaststream when all schemes and sdip are green" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(SchemeId("DiplomaticServiceEuropean"), Green.toString),
          SchemeEvaluationResult(SchemeId("GovernmentCommunicationService"), Green.toString),
          SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 80, 80, SchemeId("DiplomaticServiceEuropean"),
        SchemeId("GovernmentCommunicationService")
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
        SchemeId("DiplomaticServiceEuropean") -> Green, SchemeId("GovernmentCommunicationService") -> Green,
        SchemeId("Sdip") -> Green)
    }

    "give amber for SdipFaststream when sdip and faststream schemes are amber" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(SchemeId("DiplomaticServiceEuropean"), Green.toString),
          SchemeEvaluationResult(SchemeId("GovernmentCommunicationService"), Green.toString),
          SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 40, 40, SchemeId("DiplomaticServiceEuropean"), SchemeId("GovernmentCommunicationService")
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        SchemeId("DiplomaticServiceEuropean") -> Amber, SchemeId("GovernmentCommunicationService") -> Amber,
        SchemeId("Sdip") -> Amber)
    }

    "give fail for SdipFaststream when sdip and faststream schemes are red" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
          SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString),
          SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 10, 10, SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
        SchemeId("Commercial") -> Red, SchemeId("DigitalDataTechnologyAndCyber") -> Red, SchemeId("Sdip") -> Red)
    }

    "not fail SdipFastStream with failed faststream when sdip scheme is green and faststream schemes are red" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
          SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString),
          SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 10, 10, SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN),
        SchemeId("Commercial") -> Red, SchemeId("DigitalDataTechnologyAndCyber") -> Red, SchemeId("Sdip") -> Green)
    }

    "give pass for SdipFaststream when sdip failed and faststream schemes passed" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
          SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString),
          SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 80, 80, SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalDataTechnologyAndCyber") -> Green, SchemeId("Sdip") -> Red)
    }

    "give pass when all schemes and sdip are green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
            SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString),
            SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)
          ),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, 80, SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")
        )(ApplicationRoute.SdipFaststream) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          SchemeId("Commercial") -> Green, SchemeId("DigitalDataTechnologyAndCyber") -> Green, SchemeId("Sdip") -> Green)
      }
    }
  }
}
