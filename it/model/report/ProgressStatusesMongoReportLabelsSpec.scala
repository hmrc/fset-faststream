package model.report

import java.util.UUID

import factories.ITDateTimeFactoryMock
import model.{ ApplicationRoute, ProgressStatuses }
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec

class ProgressStatusesMongoReportLabelsSpec extends MongoRepositorySpec {
  val collectionName: String = CollectionNames.APPLICATION

//  lazy val appRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
  lazy val appRepo: GeneralApplicationMongoRepository =
    new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  val reportLabels: ProgressStatusesReportLabels = new ProgressStatusesReportLabels {}

  import model.ProgressStatuses._
  val ProgressStatusCustomNames: Map[ProgressStatus, String] = Map(
    CREATED -> "registered",
    PERSONAL_DETAILS -> "personal_details_completed",
    SCHEME_PREFERENCES -> "scheme_preferences_completed",
    ASSISTANCE_DETAILS -> "assistance_details_completed",
    PREVIEW -> "preview_completed",
    PHASE1_TESTS_FIRST_REMINDER -> "phase1_tests_first_reminder",
    PHASE1_TESTS_SECOND_REMINDER -> "phase1_tests_second_reminder",
    PHASE2_TESTS_FIRST_REMINDER -> "phase2_tests_first_reminder",
    PHASE2_TESTS_SECOND_REMINDER -> "phase2_tests_second_reminder",
    PHASE1_TESTS_FAILED_NOTIFIED -> "phase1_tests_failed_notified")

  "All progress status in the application" should {
    "be mapped to the report labels" in {
      ProgressStatuses.allStatuses
        // Only used in Test Data Generator
        .filter {_ != ProgressStatuses.QUESTIONNAIRE_OCCUPATION}
        .foreach { progressStatus =>
        val userId = UUID.randomUUID().toString
        val appId = appRepo.create(userId, "frameworkId", ApplicationRoute.Faststream).futureValue.applicationId

        //scalastyle:off
        println(s"Checking 'application progress' consistency in reports for: $progressStatus")
        //scalastyle:on

        appRepo.addProgressStatusAndUpdateAppStatus(appId, progressStatus).futureValue
        val progress = appRepo.findProgress(appId).futureValue
        /*
        NOTE: In case of mismatch for a newly added progress status, remember that the
        status should be added to GeneralApplicationRepository.findProgress in the progress
        response mapping.
         */
        reportLabels.progressStatusNameInReports(progress) mustBe ProgressStatusCustomNames
          .getOrElse(progressStatus, progressStatus.key.toLowerCase)
      }
    }
  }
}
