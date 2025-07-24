/*
 * Copyright 2023 HM Revenue & Customs
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

package repositories

import factories.DateTimeFactory
import model.ApplicationStatus.*
import model.ProgressStatuses.ProgressStatus
import model.command.*
import model.{ApplicationStatus, FailedSdipFsTestType, ProgressStatuses, SuccessfulSdipFsTestType}
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bsonDocumentToDocument
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.util.Try

trait CommonBSONDocuments extends BaseBSONReader {

  def dateTimeFactory: DateTimeFactory

  protected def applicationStatusBSON(applicationStatus: ApplicationStatus) = {
    // TODO the progress status should be propagated up to the caller, rather than default, but that will
    // require widespread changes, and using a default in here is better than the previous implementation
    // that just set the progress status to applicationStatus.toString, which produced invalid progress statuses
    val defaultProgressStatus = ProgressStatuses.tryToGetDefaultProgressStatus(applicationStatus)

    defaultProgressStatus match {
      case Some(progressStatus) =>
        Document(
          "applicationStatus" -> applicationStatus.toBson,
          s"progress-status.${progressStatus.key}" -> true,
          s"progress-status-timestamp.${progressStatus.key}" -> offsetDateTimeToBson(dateTimeFactory.nowLocalTimeZone)
        )
      // For in progress application status we store application status in progress-status-timestamp
      case _ if applicationStatus == ApplicationStatus.IN_PROGRESS =>
        Document(
          "applicationStatus" -> applicationStatus.toBson,
          s"progress-status.${ApplicationStatus.IN_PROGRESS}" -> true,
          s"progress-status-timestamp.${ApplicationStatus.IN_PROGRESS}" -> offsetDateTimeToBson(dateTimeFactory.nowLocalTimeZone)
        )
      case _ =>
        Document("applicationStatus" -> applicationStatus.toBson)
    }
  }

  protected def applicationStatusBSON(progressStatus: ProgressStatus) = {
    Document(
      "applicationStatus" -> Codecs.toBson(progressStatus.applicationStatus),
      s"progress-status.${progressStatus.key}" -> true,
      s"progress-status-timestamp.${progressStatus.key}" -> offsetDateTimeToBson(dateTimeFactory.nowLocalTimeZone)
    )
  }

  def progressStatusOnlyBSON(progressStatus: ProgressStatus) = {
    Document(
      s"progress-status.${progressStatus.key}" -> true,
      s"progress-status-timestamp.${progressStatus.key}" -> offsetDateTimeToBson(dateTimeFactory.nowLocalTimeZone)
    )
  }

  def progressStatusGuardBSON(progressStatus: ProgressStatus) = {
    Document(
      "applicationStatus" -> progressStatus.applicationStatus.toBson,
      s"progress-status.${progressStatus.key}" -> true
    )
  }

  // scalastyle:off method.length
  def toProgressResponse(applicationId: String)(doc: Document) = {

    doc.get("progress-status").map(_.asDocument()).map { implicit root =>

      def getProgress(key: String)(implicit root: BsonDocument): Boolean = {
        Try(root.get(key).asBoolean().getValue).toOption
          .orElse(Try(root.get(key.toUpperCase()).asBoolean().getValue).toOption)
          .orElse(Try(root.get(key.toLowerCase()).asBoolean().getValue).toOption)
          .getOrElse(false)
      }

      def questionnaire(root: Document): List[String] = {
        import scala.jdk.CollectionConverters.*
        root.get("questionnaire").map { bsonValue =>
          bsonValue.asDocument().keySet().asScala.toList
        }.getOrElse(Nil)
      }

      def phase1ProgressResponse = Phase1ProgressResponse(
        phase1TestsInvited = getProgress(ProgressStatuses.PHASE1_TESTS_INVITED.key),
        phase1TestsFirstReminder = getProgress(ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER.key),
        phase1TestsSecondReminder = getProgress(ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER.key),
        phase1TestsResultsReady = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_READY.key),
        phase1TestsResultsReceived = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED.key),
        phase1TestsStarted = getProgress(ProgressStatuses.PHASE1_TESTS_STARTED.key),
        phase1TestsCompleted = getProgress(ProgressStatuses.PHASE1_TESTS_COMPLETED.key),
        phase1TestsExpired = getProgress(ProgressStatuses.PHASE1_TESTS_EXPIRED.key),
        phase1TestsPassed = getProgress(ProgressStatuses.PHASE1_TESTS_PASSED.key),
        phase1TestsSuccessNotified = getProgress(ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED.key),
        phase1TestsFailed = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED.key),
        phase1TestsFailedNotified = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED.key),
        sdipFSFailed = getProgress(FailedSdipFsTestType.progressStatus),
        sdipFSFailedNotified = getProgress(FailedSdipFsTestType.notificationProgress),
        sdipFSSuccessful = getProgress(SuccessfulSdipFsTestType.progressStatus),
        phase1TestsFailedSdipAmber = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_AMBER.key),
        phase1TestsFailedSdipGreen = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN.key)
      )

      def phase2ProgressResponse = Phase2ProgressResponse(
        phase2TestsInvited = getProgress(ProgressStatuses.PHASE2_TESTS_INVITED.key),
        phase2TestsFirstReminder = getProgress(ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER.key),
        phase2TestsSecondReminder = getProgress(ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER.key),
        phase2TestsResultsReady = getProgress(ProgressStatuses.PHASE2_TESTS_RESULTS_READY.key),
        phase2TestsResultsReceived = getProgress(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED.key),
        phase2TestsStarted = getProgress(ProgressStatuses.PHASE2_TESTS_STARTED.key),
        phase2TestsCompleted = getProgress(ProgressStatuses.PHASE2_TESTS_COMPLETED.key),
        phase2TestsExpired = getProgress(ProgressStatuses.PHASE2_TESTS_EXPIRED.key),
        phase2TestsPassed = getProgress(ProgressStatuses.PHASE2_TESTS_PASSED.key),
        phase2TestsFailed = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED.key),
        phase2TestsFailedNotified = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED.key),
        phase2TestsFailedSdipAmber = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_AMBER.key),
        phase2TestsFailedSdipGreen = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN.key)
      )

      def phase3ProgressResponse = Phase3ProgressResponse(
        phase3TestsInvited = getProgress(ProgressStatuses.PHASE3_TESTS_INVITED.toString),
        phase3TestsFirstReminder = getProgress(ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER.toString),
        phase3TestsSecondReminder = getProgress(ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER.toString),
        phase3TestsStarted = getProgress(ProgressStatuses.PHASE3_TESTS_STARTED.toString),
        phase3TestsCompleted = getProgress(ProgressStatuses.PHASE3_TESTS_COMPLETED.toString),
        phase3TestsExpired = getProgress(ProgressStatuses.PHASE3_TESTS_EXPIRED.toString),
        phase3TestsResultsReceived = getProgress(ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED.toString),
        phase3TestsPassedWithAmber = getProgress(ProgressStatuses.PHASE3_TESTS_PASSED_WITH_AMBER.toString),
        phase3TestsPassed = getProgress(ProgressStatuses.PHASE3_TESTS_PASSED.toString),
        phase3TestsPassedNotified = getProgress(ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED.key),
        phase3TestsFailed = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED.toString),
        phase3TestsFailedNotified = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED.key),
        phase3TestsFailedSdipAmber = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_AMBER.key),
        phase3TestsFailedSdipGreen = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN.key)
      )

      def siftProgressResponse = SiftProgressResponse(
        siftEntered = getProgress(ProgressStatuses.SIFT_ENTERED.key),
        siftTestInvited = getProgress(ProgressStatuses.SIFT_TEST_INVITED.key),
        siftTestStarted = getProgress(ProgressStatuses.SIFT_TEST_STARTED.key),
        siftTestCompleted = getProgress(ProgressStatuses.SIFT_TEST_COMPLETED.key),
        siftFormsCompleteNumericTestPending = getProgress(ProgressStatuses.SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING.key),
        siftTestResultsReady = getProgress(ProgressStatuses.SIFT_TEST_RESULTS_READY.key),
        siftTestResultsReceived = getProgress(ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED.key),
        siftFirstReminder = getProgress(ProgressStatuses.SIFT_FIRST_REMINDER.key),
        siftSecondReminder = getProgress(ProgressStatuses.SIFT_SECOND_REMINDER.key),
        siftReady = getProgress(ProgressStatuses.SIFT_READY.key),
        siftCompleted = getProgress(ProgressStatuses.SIFT_COMPLETED.key),
        siftExpired = getProgress(ProgressStatuses.SIFT_EXPIRED.key),
        siftExpiredNotified = getProgress(ProgressStatuses.SIFT_EXPIRED_NOTIFIED.key),
        failedAtSift = getProgress(ProgressStatuses.FAILED_AT_SIFT.key),
        failedAtSiftNotified = getProgress(ProgressStatuses.FAILED_AT_SIFT_NOTIFIED.key),
        sdipFailedAtSift = getProgress(ProgressStatuses.SDIP_FAILED_AT_SIFT.key),
        siftFaststreamFailedSdipGreen = getProgress(ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN.key)
      )

      def assessmentCentre = AssessmentCentre(
        awaitingAllocation = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION.key),
        allocationUnconfirmed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED.key),
        allocationConfirmed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED.key),
        failedToAttend = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_TO_ATTEND.key),
        scoresEntered = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED.key),
        scoresAccepted = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED.key),
        awaitingReevaluation = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION.key),
        passed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_PASSED.key),
        failed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED.key),
        failedNotified = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_NOTIFIED.key),
        failedSdipGreen = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN.key),
        failedSdipGreenNotified = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED.key)
      )

      def fsb = Fsb(
        getProgress(ProgressStatuses.FSB_AWAITING_ALLOCATION.key),
        getProgress(ProgressStatuses.FSB_ALLOCATION_CONFIRMED.key),
        getProgress(ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED.key),
        getProgress(ProgressStatuses.FSB_FAILED_TO_ATTEND.key),
        getProgress(ProgressStatuses.FSB_RESULT_ENTERED.key),
        getProgress(ProgressStatuses.FSB_PASSED.key),
        getProgress(ProgressStatuses.FSB_FAILED.key),
        getProgress(ProgressStatuses.FSB_FSAC_REEVALUATION_JOB_OFFER.key),
        getProgress(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED.key),
        getProgress(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED_NOTIFIED.key)
      )

      ProgressResponse(
        applicationId,
        personalDetails = getProgress(ProgressStatuses.PERSONAL_DETAILS.key),
        schemePreferences = getProgress(ProgressStatuses.SCHEME_PREFERENCES.key),
        locationPreferences = getProgress(ProgressStatuses.LOCATION_PREFERENCES.key),
        assistanceDetails = getProgress(ProgressStatuses.ASSISTANCE_DETAILS.key),
        preview = getProgress(ProgressStatuses.PREVIEW.key),
        questionnaire = questionnaire(root),
        submitted = getProgress(ProgressStatuses.SUBMITTED.key),
        submittedCheckPassed = getProgress(ProgressStatuses.SUBMITTED_CHECK_PASSED.key),
        submittedCheckFailed = getProgress(ProgressStatuses.SUBMITTED_CHECK_FAILED.key),
        submittedCheckFailedNotified = getProgress(ProgressStatuses.SUBMITTED_CHECK_FAILED_NOTIFIED.key),
        fastPassAccepted = getProgress(ProgressStatuses.FAST_PASS_ACCEPTED.key),
        withdrawn = getProgress(ProgressStatuses.WITHDRAWN.key),
        applicationArchived = getProgress(ProgressStatuses.APPLICATION_ARCHIVED.key),
        eligibleForJobOffer = JobOfferProgressResponse(
          eligible = getProgress(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER.key),
          eligibleNotified = getProgress(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED.key)
        ),
        phase1ProgressResponse = phase1ProgressResponse,
        phase2ProgressResponse = phase2ProgressResponse,
        phase3ProgressResponse = phase3ProgressResponse,
        siftProgressResponse = siftProgressResponse,
        assessmentCentre = assessmentCentre,
        fsb = fsb
      )
    }.getOrElse(ProgressResponse(applicationId))
  }
}
