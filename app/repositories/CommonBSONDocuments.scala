/*
 * Copyright 2022 HM Revenue & Customs
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
import model.ApplicationStatus._
import model.ProgressStatuses.ProgressStatus
import model.command._
import org.mongodb.scala.bson.collection.immutable.Document
import model.{ApplicationStatus, FailedSdipFsTestType, ProgressStatuses, SuccessfulSdipFsTestType}
import uk.gov.hmrc.mongo.play.json.Codecs

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
          s"progress-status-timestamp.${progressStatus.key}" -> dateTimeToBson(dateTimeFactory.nowLocalTimeZone)
        )
      // For in progress application status we store application status in progress-status-timestamp
      case _ if applicationStatus == ApplicationStatus.IN_PROGRESS =>
        Document(
          "applicationStatus" -> applicationStatus.toBson,
          s"progress-status.${ApplicationStatus.IN_PROGRESS}" -> true,
          s"progress-status-timestamp.${ApplicationStatus.IN_PROGRESS}" -> dateTimeToBson(dateTimeFactory.nowLocalTimeZone)
        )
      case _ =>
        Document("applicationStatus" -> applicationStatus.toBson)
    }
  }

  protected def applicationStatusBSON(progressStatus: ProgressStatus) = {
    Document(
      "applicationStatus" -> Codecs.toBson(progressStatus.applicationStatus),
      s"progress-status.${progressStatus.key}" -> true,
      s"progress-status-timestamp.${progressStatus.key}" -> dateTimeToBson(dateTimeFactory.nowLocalTimeZone)
    )
  }

  def progressStatusOnlyBSON(progressStatus: ProgressStatus) = {
    Document(
      s"progress-status.${progressStatus.key}" -> true,
      s"progress-status-timestamp.${progressStatus.key}" -> dateTimeToBson(dateTimeFactory.nowLocalTimeZone)
    )
  }

  def progressStatusGuardBSON(progressStatus: ProgressStatus) = {
    Document(
      "applicationStatus" -> progressStatus.applicationStatus.toBson,
      s"progress-status.${progressStatus.key}" -> true
    )
  }

  // scalastyle:off method.length
  // TODO: mongo the new impl means you need to always pass root - can we fix this?
  def toProgressResponse(applicationId: String)(doc: Document) = {

    doc.get("progress-status").map(_.asDocument()).map { root =>

      def getProgress(key: String, root: Document): Boolean = {
        root.get(key).map ( _.asBoolean().getValue )
          .orElse(root.get(key.toUpperCase).map( _.asBoolean().getValue ))
          .orElse(root.get(key.toLowerCase).map( _.asBoolean().getValue ))
          .getOrElse(false)
      }

      def questionnaire(root: Document): List[String] = {
        import scala.collection.JavaConverters._
        root.get("questionnaire").map { bsonValue =>
          bsonValue.asDocument().keySet().asScala.toList
        }.getOrElse(Nil)
      }

      ProgressResponse(
        applicationId,
        personalDetails = getProgress(ProgressStatuses.PERSONAL_DETAILS.key, root),
        schemePreferences = getProgress(ProgressStatuses.SCHEME_PREFERENCES.key, root),
        assistanceDetails = getProgress(ProgressStatuses.ASSISTANCE_DETAILS.key, root),
        preview = getProgress(ProgressStatuses.PREVIEW.key, root),
        questionnaire = questionnaire(root),
        submitted = getProgress(ProgressStatuses.SUBMITTED.key, root),
        fastPassAccepted = getProgress(ProgressStatuses.FAST_PASS_ACCEPTED.key, root),
        withdrawn = getProgress(ProgressStatuses.WITHDRAWN.key, root),
        applicationArchived = getProgress(ProgressStatuses.APPLICATION_ARCHIVED.key, root),
        eligibleForJobOffer = JobOfferProgressResponse(
          eligible = getProgress(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER.key, root),
          eligibleNotified = getProgress(ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED.key, root)
        ),
        phase1ProgressResponse = Phase1ProgressResponse(
          phase1TestsInvited = getProgress(ProgressStatuses.PHASE1_TESTS_INVITED.key, root),
          phase1TestsFirstReminder = getProgress(ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER.key, root),
          phase1TestsSecondReminder = getProgress(ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER.key, root),
          phase1TestsResultsReady = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_READY.key, root),
          phase1TestsResultsReceived = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED.key, root),
          phase1TestsStarted = getProgress(ProgressStatuses.PHASE1_TESTS_STARTED.key, root),
          phase1TestsCompleted = getProgress(ProgressStatuses.PHASE1_TESTS_COMPLETED.key, root),
          phase1TestsExpired = getProgress(ProgressStatuses.PHASE1_TESTS_EXPIRED.key, root),
          phase1TestsPassed = getProgress(ProgressStatuses.PHASE1_TESTS_PASSED.key, root),
          phase1TestsSuccessNotified = getProgress(ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED.key, root),
          phase1TestsFailed = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED.key, root),
          phase1TestsFailedNotified = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED.key, root),
          sdipFSFailed = getProgress(FailedSdipFsTestType.progressStatus, root),
          sdipFSFailedNotified = getProgress(FailedSdipFsTestType.notificationProgress, root),
          sdipFSSuccessful = getProgress(SuccessfulSdipFsTestType.progressStatus, root),
          phase1TestsFailedSdipAmber = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_AMBER.key, root),
          phase1TestsFailedSdipGreen = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN.key, root)
        ),
        phase2ProgressResponse = Phase2ProgressResponse(
          phase2TestsInvited = getProgress(ProgressStatuses.PHASE2_TESTS_INVITED.key, root),
          phase2TestsFirstReminder = getProgress(ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER.key, root),
          phase2TestsSecondReminder = getProgress(ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER.key, root),
          phase2TestsResultsReady = getProgress(ProgressStatuses.PHASE2_TESTS_RESULTS_READY.key, root),
          phase2TestsResultsReceived = getProgress(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED.key, root),
          phase2TestsStarted = getProgress(ProgressStatuses.PHASE2_TESTS_STARTED.key, root),
          phase2TestsCompleted = getProgress(ProgressStatuses.PHASE2_TESTS_COMPLETED.key, root),
          phase2TestsExpired = getProgress(ProgressStatuses.PHASE2_TESTS_EXPIRED.key, root),
          phase2TestsPassed = getProgress(ProgressStatuses.PHASE2_TESTS_PASSED.key, root),
          phase2TestsFailed = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED.key, root),
          phase2TestsFailedNotified = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED.key, root),
          phase2TestsFailedSdipAmber = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_AMBER.key, root),
          phase2TestsFailedSdipGreen = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN.key, root)
        ),
        phase3ProgressResponse = Phase3ProgressResponse(
          phase3TestsInvited = getProgress(ProgressStatuses.PHASE3_TESTS_INVITED.toString, root),
          phase3TestsFirstReminder = getProgress(ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER.toString, root),
          phase3TestsSecondReminder = getProgress(ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER.toString, root),
          phase3TestsStarted = getProgress(ProgressStatuses.PHASE3_TESTS_STARTED.toString, root),
          phase3TestsCompleted = getProgress(ProgressStatuses.PHASE3_TESTS_COMPLETED.toString, root),
          phase3TestsExpired = getProgress(ProgressStatuses.PHASE3_TESTS_EXPIRED.toString, root),
          phase3TestsResultsReceived = getProgress(ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED.toString, root),
          phase3TestsPassedWithAmber = getProgress(ProgressStatuses.PHASE3_TESTS_PASSED_WITH_AMBER.toString, root),
          phase3TestsPassed = getProgress(ProgressStatuses.PHASE3_TESTS_PASSED.toString, root),
          phase3TestsPassedNotified = getProgress(ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED.key, root),
          phase3TestsFailed = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED.toString, root),
          phase3TestsFailedNotified = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED.key, root),
          phase3TestsFailedSdipAmber = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_AMBER.key, root),
          phase3TestsFailedSdipGreen = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN.key, root)
        ),
        siftProgressResponse = SiftProgressResponse(
          siftEntered = getProgress(ProgressStatuses.SIFT_ENTERED.key, root),
          siftTestInvited = getProgress(ProgressStatuses.SIFT_TEST_INVITED.key, root),
          siftTestStarted = getProgress(ProgressStatuses.SIFT_TEST_STARTED.key, root),
          siftTestCompleted = getProgress(ProgressStatuses.SIFT_TEST_COMPLETED.key, root),
          siftFormsCompleteNumericTestPending = getProgress(ProgressStatuses.SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING.key, root),
          siftTestResultsReady = getProgress(ProgressStatuses.SIFT_TEST_RESULTS_READY.key, root),
          siftTestResultsReceived = getProgress(ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED.key, root),
          siftFirstReminder = getProgress(ProgressStatuses.SIFT_FIRST_REMINDER.key, root),
          siftSecondReminder = getProgress(ProgressStatuses.SIFT_SECOND_REMINDER.key, root),
          siftReady = getProgress(ProgressStatuses.SIFT_READY.key, root),
          siftCompleted = getProgress(ProgressStatuses.SIFT_COMPLETED.key, root),
          siftExpired = getProgress(ProgressStatuses.SIFT_EXPIRED.key, root),
          siftExpiredNotified = getProgress(ProgressStatuses.SIFT_EXPIRED_NOTIFIED.key, root),
          failedAtSift = getProgress(ProgressStatuses.FAILED_AT_SIFT.key, root),
          failedAtSiftNotified = getProgress(ProgressStatuses.FAILED_AT_SIFT_NOTIFIED.key, root),
          sdipFailedAtSift = getProgress(ProgressStatuses.SDIP_FAILED_AT_SIFT.key, root),
          siftFaststreamFailedSdipGreen = getProgress(ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN.key, root)
        ),
        assessmentCentre = AssessmentCentre(
          awaitingAllocation = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION.key, root),
          allocationUnconfirmed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED.key, root),
          allocationConfirmed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED.key, root),
          failedToAttend = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_TO_ATTEND.key, root),
          scoresEntered = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED.key, root),
          scoresAccepted = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED.key, root),
          awaitingReevaluation = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION.key, root),
          passed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_PASSED.key, root),
          failed = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED.key, root),
          failedNotified = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_NOTIFIED.key, root),
          failedSdipGreen = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN.key, root),
          failedSdipGreenNotified = getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED.key, root)
        ),
        fsb = Fsb(
          getProgress(ProgressStatuses.FSB_AWAITING_ALLOCATION.key, root),
          getProgress(ProgressStatuses.FSB_ALLOCATION_CONFIRMED.key, root),
          getProgress(ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED.key, root),
          getProgress(ProgressStatuses.FSB_FAILED_TO_ATTEND.key, root),
          getProgress(ProgressStatuses.FSB_RESULT_ENTERED.key, root),
          getProgress(ProgressStatuses.FSB_PASSED.key, root),
          getProgress(ProgressStatuses.FSB_FAILED.key, root),
          getProgress(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED.key, root),
          getProgress(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED_NOTIFIED.key, root)
        )
      )
    }.getOrElse(ProgressResponse(applicationId))
  }
}
