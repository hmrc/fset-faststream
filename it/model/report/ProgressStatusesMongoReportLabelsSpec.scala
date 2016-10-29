package model.report

import java.util.UUID

import model.{ ApplicationRoute, ProgressStatuses }
import testkit.MongoRepositorySpec

class ProgressStatusesMongoReportLabelsSpec extends MongoRepositorySpec {
  val collectionName: String = "application"

  lazy val appRepo = repositories.applicationRepository

  val reportLabels = new ProgressStatusesReportLabels {}

  import model.ProgressStatuses._
  val ProgressStatusCustomNames: Map[ProgressStatus, String] = Map(
    CREATED -> "registered",
    PERSONAL_DETAILS -> "personal_details_completed",
    SCHEME_PREFERENCES -> "scheme_preferences_completed",
    PARTNER_GRADUATE_PROGRAMMES -> "partner_graduate_programmes_completed",
    ASSISTANCE_DETAILS -> "assistance_details_completed",
    PREVIEW -> "preview_completed",
    PHASE1_TESTS_INVITED -> "phase1_tests_invited",
    PHASE1_TESTS_FIRST_REMINDER -> "phase1_tests_first_remainder",
    PHASE1_TESTS_SECOND_REMINDER -> "phase1_tests_second_remainder",
    PHASE2_TESTS_FIRST_REMINDER -> "phase2_tests_first_remainder",
    PHASE2_TESTS_SECOND_REMINDER -> "phase2_tests_second_remainder",
    ONLINE_TEST_FAILED_NOTIFIED -> "registered",
    AWAITING_ALLOCATION -> "registered",
    ALLOCATION_CONFIRMED -> "registered",
    ALLOCATION_UNCONFIRMED -> "registered")

  "All progress status in the application" should {
    "be mapped to the report labels" in {
      ProgressStatuses.allStatuses
        .filterNot(_.key.contains("ASSESSMENT"))
        .foreach { progressStatus =>
        val userId = UUID.randomUUID().toString
        val appId = appRepo.create(userId, "frameworkId", ApplicationRoute.Faststream).futureValue.applicationId

        //scalastyle:off
        println(s"Checking progress consistency in the report module for: $progressStatus")
        //scalastyle:on

        appRepo.addProgressStatusAndUpdateAppStatus(appId, progressStatus).futureValue
        val progress = appRepo.findProgress(appId).futureValue
        reportLabels.progressStatusNameInReports(progress) mustBe ProgressStatusCustomNames
          .getOrElse(progressStatus, progressStatus.key.toLowerCase)
      }
    }
  }
}
