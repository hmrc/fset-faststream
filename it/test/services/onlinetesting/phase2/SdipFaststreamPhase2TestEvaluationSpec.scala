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

package services.onlinetesting.phase2

import model.ApplicationStatus._
import model.EvaluationResults._
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationRoute, ProgressStatuses, _ }

class SdipFaststreamPhase2TestEvaluationSpec extends Phase2TestEvaluationSpec {

  "phase2 evaluation process" should {
    "give pass for SdipFaststream when all schemes and sdip are green" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(GovernmentPolicy, Green.toString),
          SchemeEvaluationResult(GovernmentCommunicationService, Green.toString),
          SchemeEvaluationResult(Sdip, Green.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 80, 80, GovernmentPolicy,
        GovernmentCommunicationService
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
        GovernmentPolicy -> Green, GovernmentCommunicationService -> Green,
        Sdip -> Green)
    }

    "give amber for SdipFaststream when sdip and faststream schemes are amber" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(GovernmentPolicy, Green.toString),
          SchemeEvaluationResult(GovernmentCommunicationService, Green.toString),
          SchemeEvaluationResult(Sdip, Amber.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 40, 40, GovernmentPolicy,
        GovernmentCommunicationService
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED),
        GovernmentPolicy -> Amber, GovernmentCommunicationService -> Amber,
        Sdip -> Amber)
    }

    "give fail for SdipFaststream when sdip and faststream schemes are red" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString),
          SchemeEvaluationResult(Sdip, Red.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 10, 10, Commercial, DigitalDataTechnologyAndCyber
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED),
        Commercial -> Red, DigitalDataTechnologyAndCyber -> Red, Sdip -> Red)
    }

    "not fail SdipFastStream with failed faststream when sdip scheme is green and faststream schemes are red" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString),
          SchemeEvaluationResult(Sdip, Green.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 10, 10, Commercial, DigitalDataTechnologyAndCyber
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN),
        Commercial -> Red, DigitalDataTechnologyAndCyber -> Red, Sdip -> Green)
    }

    "give pass for SdipFaststream when sdip failed and faststream schemes passed" in new TestFixture {
      phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
        List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString),
          SchemeEvaluationResult(Sdip, Red.toString)
        ),
        "phase1-version1-res", None)
      applicationEvaluation("application-1", 80, 80, Commercial, DigitalDataTechnologyAndCyber
      )(ApplicationRoute.SdipFaststream) mustResultIn(
        PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
        Commercial -> Green, DigitalDataTechnologyAndCyber -> Green, Sdip -> Red)
    }

    "give pass when all schemes and sdip are green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString),
            SchemeEvaluationResult(Sdip, Green.toString)
          ),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, 80, Commercial, DigitalDataTechnologyAndCyber
        )(ApplicationRoute.SdipFaststream) mustResultIn(
          PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED),
          Commercial -> Green, DigitalDataTechnologyAndCyber -> Green, Sdip -> Green)
      }
    }
  }
}
