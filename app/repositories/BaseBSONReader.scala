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

package repositories

import model.ApplicationStatus._
import model.ProgressStatuses
import model.command.{ AssessmentCentre, _ }
import reactivemongo.bson.{ BSONBoolean, BSONDocument, BSONDocumentReader }

trait BaseBSONReader {
  // scalastyle:off method.length
  implicit def toProgressResponse(applicationId: String) = bsonReader {
    (doc: BSONDocument) => {
      (doc.getAs[BSONDocument]("progress-status") map { root =>

        def getProgress(key: String) = {
          root.getAs[Boolean](key)
            .orElse(root.getAs[Boolean](key.toUpperCase))
            .orElse(root.getAs[Boolean](key.toLowerCase))
            .getOrElse(false)
        }

        def questionnaire = root.getAs[BSONDocument]("questionnaire").map { doc =>
          doc.elements.collect {
            case (name, BSONBoolean(true)) => name
          }.toList
        }.getOrElse(Nil)

        ProgressResponse(
          applicationId,
          personalDetails = getProgress(ProgressStatuses.PERSONAL_DETAILS.key),
          partnerGraduateProgrammes = getProgress(ProgressStatuses.PARTNER_GRADUATE_PROGRAMMES.key),
          schemePreferences = getProgress(ProgressStatuses.SCHEME_PREFERENCES.key),
          assistanceDetails = getProgress(ProgressStatuses.ASSISTANCE_DETAILS.key),
          preview = getProgress(ProgressStatuses.PREVIEW.key),
          questionnaire = questionnaire,
          submitted = getProgress(ProgressStatuses.SUBMITTED.key),
          withdrawn = getProgress(ProgressStatuses.WITHDRAWN.key),
          phase1ProgressResponse = Phase1ProgressResponse(
            phase1TestsInvited = getProgress(ProgressStatuses.PHASE1_TESTS_INVITED.key),
            phase1TestsFirstReminder = getProgress(ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER.key),
            phase1TestsSecondReminder = getProgress(ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER.key),
            phase1TestsResultsReady = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_READY.key),
            phase1TestsResultsReceived = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED.key),
            phase1TestsStarted = getProgress(ProgressStatuses.PHASE1_TESTS_STARTED.key),
            phase1TestsCompleted = getProgress(ProgressStatuses.PHASE1_TESTS_COMPLETED.key),
            phase1TestsExpired = getProgress(ProgressStatuses.PHASE1_TESTS_EXPIRED.key),
            phase1TestsPassed = getProgress(ProgressStatuses.PHASE1_TESTS_PASSED.key),
            phase1TestsFailed = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED.key),
            phase1TestsFailedNotified = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED.key)
          ),
          phase2ProgressResponse = Phase2ProgressResponse(
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
            phase2TestsFailedNotified = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED.key)
          ),
          phase3ProgressResponse = Phase3ProgressResponse(
            phase3TestsInvited = getProgress(ProgressStatuses.PHASE3_TESTS_INVITED.toString),
            phase3TestsFirstReminder = getProgress(ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER.toString),
            phase3TestsSecondReminder = getProgress(ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER.toString),
            phase3TestsStarted = getProgress(ProgressStatuses.PHASE3_TESTS_STARTED.toString),
            phase3TestsCompleted = getProgress(ProgressStatuses.PHASE3_TESTS_COMPLETED.toString),
            phase3TestsExpired = getProgress(ProgressStatuses.PHASE3_TESTS_EXPIRED.toString),
            phase3TestsResultsReceived = getProgress(ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED.toString),
            phase3TestsPassed = getProgress(ProgressStatuses.PHASE3_TESTS_PASSED.toString),
            phase3TestsFailed = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED.toString),
            phase3TestsFailedNotified = getProgress(ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED.key)
          ),
          failedToAttend = getProgress(FAILED_TO_ATTEND.toString),
          assessmentScores = AssessmentScores(getProgress(ASSESSMENT_SCORES_ENTERED.toString), getProgress(ASSESSMENT_SCORES_ACCEPTED.toString)),
          assessmentCentre = AssessmentCentre(
            getProgress(ProgressStatuses.AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION.key),
            getProgress(ProgressStatuses.ASSESSMENT_CENTRE_PASSED.key),
            getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED.key),
            getProgress(ProgressStatuses.ASSESSMENT_CENTRE_PASSED_NOTIFIED.key),
            getProgress(ProgressStatuses.ASSESSMENT_CENTRE_FAILED_NOTIFIED.key)
          )
        )
      }).getOrElse(ProgressResponse(applicationId))
    }
  }
  // scalastyle:on method.length

  protected def bsonReader[T](f: BSONDocument => T): BSONDocumentReader[T] = {
    new BSONDocumentReader[T] {
      def read(bson: BSONDocument) = f(bson)
    }
  }

  protected def booleanTranslator(bool: Boolean) = bool match {
    case true => "Yes"
    case false => "No"
  }
}
