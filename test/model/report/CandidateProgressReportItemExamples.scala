package model.report

import model.SchemeType
import model.report.{ ApplicationForOnlineTestPassMarkReportItem, TestResultsForOnlineTestPassMarkReportItem }

import scala.util.Random

object CandidateProgressReportItemExamples {
  lazy val SdipCandidate = CandidateProgressReportItem("459b5e72-e004-48ff-9f00-adbddf59d9c4", "a665043b-8317-4d28-bdf6-086859ac17ff",
    Some("submitted"), List(SchemeType.Sdip),
    Some("No"), Some("No"), Some("Yes"), Some("Yes"), Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("No"), Some("No"),
    Some("No"), None, Some("Sdip"))
}
