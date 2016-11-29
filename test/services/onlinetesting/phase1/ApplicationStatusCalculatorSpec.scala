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

import model.ProgressStatuses._
import model.EvaluationResults._
import model.{ ApplicationStatus, Phase }
import model.SchemeType.{ apply => _, _ }
import model.persisted.SchemeEvaluationResult
import services.BaseServiceSpec
import services.onlinetesting.ApplicationStatusCalculator

class ApplicationStatusCalculatorSpec extends BaseServiceSpec {
  val calc = new ApplicationStatusCalculator {}

  "determine phase1 application status" should {
    "promote the application when at least one Green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE1_TESTS, List(red, amber, green), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE1_TESTS, List(green, green, green), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE1_TESTS, List(red, red, red), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED)
    }

    "do not update application status when amber - at least one amber and no greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE1_TESTS, List(red, amber, red), Phase.PHASE1)
      newStatus mustBe None
    }

    "do not update application status when PHASE1_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE1_TESTS_PASSED, List(green, green, green), Phase.PHASE1)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationStatus.PHASE1_TESTS, Nil, Phase.PHASE1)
    }
  }

  "determine phase2 application status" should {
    "promote the application when at least one Green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE2_TESTS, List(red, amber, green), Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_PASSED)
    }

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE2_TESTS, List(green, green, green), Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE2_TESTS, List(red, red, red), Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_FAILED)
    }

    "do not update application status when amber - at least one amber and no greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE2_TESTS, List(red, amber, red), Phase.PHASE2)
      newStatus mustBe None
    }

    "do not update application status when PHASE2_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE2_TESTS_PASSED, List(green, green, green), Phase.PHASE2)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationStatus.PHASE2_TESTS, Nil, Phase.PHASE2)
    }
  }

  "determine phase3 application status" should {
    "promote the application when at least one Green and no ambers" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE3_TESTS, List(red, green), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED)
    }

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE3_TESTS, List(green, green, green), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE3_TESTS, List(red, red, red), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_FAILED)
    }

    "do not update application status when at least one amber and no greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE3_TESTS, List(red, amber, red), Phase.PHASE3)
      newStatus mustBe None
    }

    "do not update application status when there are greens and at least one amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE3_TESTS, List(red, amber, green), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED_WITH_AMBER)
    }

    "do not update application status when PHASE3_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationStatus.PHASE3_TESTS_PASSED, List(green, green, green), Phase.PHASE3)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationStatus.PHASE3_TESTS, Nil, Phase.PHASE3)
    }
  }

  def red = SchemeEvaluationResult(Commercial, Red.toString)

  def amber = SchemeEvaluationResult(Commercial, Amber.toString)

  def green = SchemeEvaluationResult(Commercial, Green.toString)
}
