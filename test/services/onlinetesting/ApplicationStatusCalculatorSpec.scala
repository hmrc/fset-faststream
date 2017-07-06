/*
 * Copyright 2017 HM Revenue & Customs
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

package services.onlinetesting

import model.EvaluationResults._
import model.ProgressStatuses._
import model.SchemeType.{ apply => _, _ }
import model.persisted.SchemeEvaluationResult
import model.{ ApplicationRoute, ApplicationStatus, Phase, SchemeType }
import services.BaseServiceSpec

class ApplicationStatusCalculatorSpec extends BaseServiceSpec {
  val calc = new ApplicationStatusCalculator {}

  "determine SDIP with Faststream phase 1 application status" must {
    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE1_TESTS,
        List(green, sdipGreen), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE1_TESTS,
        List(red, sdipRed), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED)
    }

    "fail application for all faststream Reds and SDIP green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE1_TESTS,
        List(red, SchemeEvaluationResult(SchemeType.Sdip, Green.toString)), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED)
    }
  }

  "determine SDIP phase 1 application status" must {

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Sdip, ApplicationStatus.PHASE1_TESTS,
        List(green), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Sdip, ApplicationStatus.PHASE1_TESTS,
        List(red), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED)
    }

    "not update application status when amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Sdip, ApplicationStatus.PHASE1_TESTS,
        List(amber), Phase.PHASE1)
      newStatus mustBe None
    }

    "not update application status when PHASE1_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Sdip, ApplicationStatus.PHASE1_TESTS_PASSED,
        List(green, green, green), Phase.PHASE1)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(
        ApplicationRoute.Sdip,
        ApplicationStatus.PHASE1_TESTS, Nil, Phase.PHASE1
      )
    }
  }

  "determine EDIP phase 1 application status" must {

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Edip, ApplicationStatus.PHASE1_TESTS,
        List(green), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Edip, ApplicationStatus.PHASE1_TESTS,
        List(red), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED)
    }

    "do not update application status when PHASE1_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Edip, ApplicationStatus.PHASE1_TESTS_PASSED,
        List(green, green, green), Phase.PHASE1)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(
        ApplicationRoute.Edip,
        ApplicationStatus.PHASE1_TESTS, Nil, Phase.PHASE1
      )
    }
  }

  "determine fast stream phase1 application status" must {
    "promote the application when at least one Green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE1_TESTS,
        List(red, amber, green), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE1_TESTS, List(green, green, green),
        Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE1_TESTS, List(red, red, red),
        Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED)
    }

    "do not update application status when amber - at least one amber and no greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE1_TESTS, List(red, amber, red),
        Phase.PHASE1)
      newStatus mustBe None
    }

    "do not update application status when PHASE1_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE1_TESTS_PASSED,
        List(green, green, green), Phase.PHASE1)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE1_TESTS,
        Nil, Phase.PHASE1)
    }
  }

  "determine phase2 application status" must {
    "promote the application when at least one Green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE2_TESTS, List(red, amber, green),
        Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_PASSED)
    }

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE2_TESTS, List(green, green, green),
        Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE2_TESTS, List(red, red, red),
        Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_FAILED)
    }

    "do not update application status when amber - at least one amber and no greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE2_TESTS, List(red, amber, red),
        Phase.PHASE2)
      newStatus mustBe None
    }

    "do not update application status when PHASE2_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE2_TESTS_PASSED,
        List(green, green, green), Phase.PHASE2)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(
        ApplicationRoute.Faststream,
        ApplicationStatus.PHASE2_TESTS, Nil, Phase.PHASE2
      )
    }
  }

  "determine phase3 application status" must {
    "promote the application when at least one Green and no ambers" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE3_TESTS,
        List(red, green), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED)
    }

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE3_TESTS,
        List(green, green, green), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE3_TESTS,
        List(red, red, red), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_FAILED)
    }

    "do not update application status when at least one amber and no greens" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE3_TESTS,
        List(red, amber, red), Phase.PHASE3)
      newStatus mustBe None
    }

    "do not update application status when there are greens and at least one amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE3_TESTS,
        List(red, amber, green), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED_WITH_AMBER)
    }

    "do not update application status when PHASE3_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.Faststream, ApplicationStatus.PHASE3_TESTS_PASSED,
        List(green, green, green), Phase.PHASE3)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(
        ApplicationRoute.Faststream,
        ApplicationStatus.PHASE3_TESTS, Nil, Phase.PHASE3
      )
    }
  }

  def red = SchemeEvaluationResult(Commercial, Red.toString)
  def sdipRed = SchemeEvaluationResult(Sdip, Red.toString)
  def amber = SchemeEvaluationResult(Commercial, Amber.toString)
  def green = SchemeEvaluationResult(Commercial, Green.toString)
  def sdipGreen = SchemeEvaluationResult(Sdip, Green.toString)
}
