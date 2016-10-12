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

package services.onlinetesting.phase1

import model.ApplicationStatus
import model.ApplicationStatus._
import model.EvaluationResults.{ Result, _ }
import model.persisted.SchemeEvaluationResult

trait ApplicationStatusCalculator {

  def determineApplicationStatus(originalApplicationStatus: ApplicationStatus,
                                 evaluatedSchemes: List[SchemeEvaluationResult]): Option[ApplicationStatus] = {
    originalApplicationStatus match {
      case ApplicationStatus.PHASE1_TESTS =>
        val results = evaluatedSchemes.map(s => Result(s.result))

        if (results.forall(_ == Red)) {
          Some(PHASE1_TESTS_FAILED)
        } else if (results.contains(Green)) {
          Some(PHASE1_TESTS_PASSED)
        } else {
          None
        }
      case _ =>
        // Because failmark cannot be decreased,
        // and passmark cannot be increased, we are sure that
        // applications after PHASE1_TESTS (in eTray)
        // will need to be only evaluated for removing ambers.
        // Application status should not be changed by Phase1 evaluation
        // after PHASE1_TESTS.
        None
    }
  }
}
