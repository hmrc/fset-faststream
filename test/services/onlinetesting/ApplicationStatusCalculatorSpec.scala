/*
 * Copyright 2021 HM Revenue & Customs
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
import model.SchemeId.{ apply => _ }
import model.persisted.SchemeEvaluationResult
import model.{ ApplicationRoute, ApplicationStatus, Phase, SchemeId }
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

    "not fail application for all faststream Reds and SDIP green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE1_TESTS,
        List(red, SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED_SDIP_GREEN)
    }

    "not fail application for all faststream Reds and SDIP amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE1_TESTS,
        List(red, SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)), Phase.PHASE1)
      newStatus mustBe Some(PHASE1_TESTS_FAILED_SDIP_AMBER)
    }
  }

  "determine SDIP with Faststream phase 2 application status" must {
    "not fail the application with all faststream Reds and SDIP green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE2_TESTS,
        List(red, SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)), Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_FAILED_SDIP_GREEN)
    }

    "not fail application for all faststream Reds and SDIP amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE2_TESTS,
        List(red, SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)), Phase.PHASE2)
      newStatus mustBe Some(PHASE2_TESTS_FAILED_SDIP_AMBER)
    }
  }

  "determine SDIP with Faststream phase 3 application status" must {
    "not fail the application with all faststream reds and SDIP green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(red, SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_FAILED_SDIP_GREEN)
    }

    "promote the application with faststream greens and reds and SDIP red" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(red, green, SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED)
    }

    "promote the application with faststream green only and SDIP green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(green, SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED)
    }

    "not fail application for all faststream reds and SDIP amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(red, SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_FAILED_SDIP_AMBER)
    }

    "put the application in PHASE3_TESTS_PASSED_WITH_AMBER when there are only greens but SDIP is amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(green, SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED_WITH_AMBER)
    }

    "put the application in PHASE3_TESTS_PASSED_WITH_AMBER when there are greens but SDIP is amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(red, green, SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED_WITH_AMBER)
    }

    "put the application in PHASE3_TESTS_PASSED_WITH_AMBER when faststream schemes are amber but SDIP is green" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(amber, amber, SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED_WITH_AMBER)
    }

    "not update the application when faststream schemes are amber and SDIP is red" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(amber, amber, SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)), Phase.PHASE3)
      newStatus mustBe None
    }

    "fail the application when faststream schemes are red and SDIP is red" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(red, red, SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_FAILED)
    }

    "promote the application when faststream schemes are green and SDIP is red" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(green, SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)), Phase.PHASE3)
      newStatus mustBe Some(PHASE3_TESTS_PASSED)
    }

    "not update the application when faststream schemes are amber and SDIP is amber" in {
      val newStatus = calc.determineApplicationStatus(ApplicationRoute.SdipFaststream, ApplicationStatus.PHASE3_TESTS,
        List(amber, SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)), Phase.PHASE3)
      newStatus mustBe None
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
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationRoute.Sdip,
        ApplicationStatus.PHASE1_TESTS, Nil, Phase.PHASE1)
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
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationRoute.Edip,
        ApplicationStatus.PHASE1_TESTS, Nil, Phase.PHASE1)
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
        List(green, green, green),Phase.PHASE2)
      newStatus mustBe None
    }

    "return exception when no results found" in {
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationRoute.Faststream,
        ApplicationStatus.PHASE2_TESTS, Nil, Phase.PHASE2)
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
      an[IllegalArgumentException] must be thrownBy calc.determineApplicationStatus(ApplicationRoute.Faststream,
        ApplicationStatus.PHASE3_TESTS, Nil, Phase.PHASE3)
    }
  }

  def red = SchemeEvaluationResult(SchemeId("Commercial"), Red.toString)
  def sdipRed = SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)
  def amber = SchemeEvaluationResult(SchemeId("Commercial"), Amber.toString)
  def green = SchemeEvaluationResult(SchemeId("Commercial"), Green.toString)
  def sdipGreen = SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)
}
