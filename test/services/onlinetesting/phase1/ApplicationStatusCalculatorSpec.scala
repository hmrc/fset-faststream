package services.onlinetesting.phase1

import model.ApplicationStatus._
import model.EvaluationResults._
import model.SchemeType.{ apply => _, _ }
import model.persisted.SchemeEvaluationResult
import services.BaseServiceSpec

class ApplicationStatusCalculatorSpec extends BaseServiceSpec {
  val calc = new ApplicationStatusCalculator {}

  "determine application status" should {
    "promote the application when at least one Green" in {
      val newStatus = calc.determineApplicationStatus(PHASE1_TESTS, List(red, amber, green))
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "promote the application for all Greens" in {
      val newStatus = calc.determineApplicationStatus(PHASE1_TESTS, List(green, green, green))
      newStatus mustBe Some(PHASE1_TESTS_PASSED)
    }

    "fail application for all Reds" in {
      val newStatus = calc.determineApplicationStatus(PHASE1_TESTS, List(red, red, red))
      newStatus mustBe Some(PHASE1_TESTS_FAILED)
    }

    "do not update application status when amber - at least one amber and no greens" in {
      val newStatus = calc.determineApplicationStatus(PHASE1_TESTS, List(red, amber, red))
      newStatus mustBe None
    }

    "do not update application status when PHASE1_TESTS_PASSED" in {
      val newStatus = calc.determineApplicationStatus(PHASE1_TESTS_PASSED, List(green, green, green))
      newStatus mustBe None
    }

  }

  def red = SchemeEvaluationResult(Commercial, Red.toString)

  def amber = SchemeEvaluationResult(Commercial, Amber.toString)

  def green = SchemeEvaluationResult(Commercial, Green.toString)
}
