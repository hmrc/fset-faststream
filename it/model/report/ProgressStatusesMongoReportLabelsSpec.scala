package model.report

import java.util.UUID

import model.ProgressStatuses
import testkit.MongoRepositorySpec

class ProgressStatusesMongoReportLabelsSpec extends MongoRepositorySpec {
  val collectionName: String = "application"

  lazy val appRepo = repositories.applicationRepository

  val reportLabels = new ProgressStatusesReportLabels {}

  import model.ProgressStatuses._
  val ProgressStatusToReportColumnLabelMap: Map[ProgressStatus, String] = Map(
    CREATED -> "registered",
    PERSONAL_DETAILS -> "personal_details_completed",
    SCHEME_PREFERENCES -> "scheme_preferences_completed",
    PARTNER_GRADUATE_PROGRAMMES -> "partner_graduate_programmes_completed",
    ASSISTANCE_DETAILS -> "assistance_details_completed",
    PREVIEW -> "preview_completed",
    SUBMITTED -> "submitted",
    WITHDRAWN -> "withdrawn",
    PHASE1_TESTS_INVITED -> "phase1_tests_invited",
    PHASE1_TESTS_FIRST_REMINDER -> "phase1_tests_first_remainder",
    PHASE1_TESTS_SECOND_REMINDER -> "phase1_tests_second_remainder",
    PHASE1_TESTS_STARTED -> "phase1_tests_started",
    PHASE1_TESTS_COMPLETED -> "phase1_tests_completed",
    PHASE1_TESTS_EXPIRED -> "phase1_tests_expired",
    PHASE1_TESTS_RESULTS_READY -> "phase1_tests_results_ready",
    PHASE1_TESTS_RESULTS_RECEIVED -> "phase1_tests_results_received",
    PHASE1_TESTS_PASSED -> "phase1_tests_passed",
    PHASE1_TESTS_FAILED -> "phase1_tests_failed",
    PHASE1_TESTS_FAILED_NOTIFIED -> "phase1_tests_failed_notified",
    PHASE2_TESTS_INVITED -> "phase2_tests_invited",
    PHASE2_TESTS_FIRST_REMINDER -> "phase2_tests_first_remainder",
    PHASE2_TESTS_SECOND_REMINDER -> "phase2_tests_second_remainder",
    PHASE2_TESTS_STARTED -> "phase2_tests_started",
    PHASE2_TESTS_COMPLETED -> "phase2_tests_completed",
    PHASE2_TESTS_EXPIRED -> "phase2_tests_expired",
    PHASE2_TESTS_RESULTS_READY -> "phase2_tests_results_ready",
    PHASE2_TESTS_RESULTS_RECEIVED -> "phase2_tests_results_received",
    PHASE2_TESTS_PASSED -> "phase2_tests_passed",
    PHASE2_TESTS_FAILED -> "phase2_tests_failed",
    FAILED_TO_ATTEND -> FAILED_TO_ATTEND.key.toLowerCase(),
    ONLINE_TEST_FAILED_NOTIFIED -> "registered",
    AWAITING_ALLOCATION -> "registered",
    ALLOCATION_CONFIRMED -> "registered",
    ALLOCATION_UNCONFIRMED -> "registered")

  "All progress status in the application" should {
    "be mapped to the report labels" in {
      ProgressStatuses.allStatuses
        .filterNot(_.key.contains("ASSESSMENT"))
        .filterNot(_.key.contains("PHASE3")) // TODO: Fix me
        .foreach { progressStatus =>
        val userId = UUID.randomUUID().toString
        val appId = appRepo.create(userId, "frameworkId").futureValue.applicationId

        //scalastyle:off
        println(s"Checking progress consistency in the report module for: $progressStatus")
        //scalastyle:on

        appRepo.addProgressStatusAndUpdateAppStatus(appId, progressStatus).futureValue
        val progress = appRepo.findProgress(appId).futureValue
        reportLabels.progressStatusNameInReports(progress) mustBe ProgressStatusToReportColumnLabelMap(progressStatus)
      }
    }
  }
}
