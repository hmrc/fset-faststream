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

package model

// TODO: The next step is to refactor the whole application so everything references this object for statuses.
// These are all possible statuses
object ApplicationStatuses {
  val Created = "CREATED"
  val Withdrawn = "WITHDRAWN"
  val InProgress = "IN_PROGRESS"
  val Submitted = "SUBMITTED"
  val OnlineTestInvited = "ONLINE_TEST_INVITED"
  val OnlineTestStarted = "ONLINE_TEST_STARTED"
  val OnlineTestExpired = "ONLINE_TEST_EXPIRED"
  val OnlineTestCompleted = "ONLINE_TEST_COMPLETED"
  val OnlineTestFailed = "ONLINE_TEST_FAILED"
  val OnlineTestFailedNotified = "ONLINE_TEST_FAILED_NOTIFIED"
  val AwaitingOnlineTestReevaluation = "AWAITING_ONLINE_TEST_RE_EVALUATION"
  val AwaitingAllocation = "AWAITING_ALLOCATION"
  val FailedToAttend = "FAILED_TO_ATTEND"
  val AllocationUnconfirmed = "ALLOCATION_UNCONFIRMED"
  val AllocationConfirmed = "ALLOCATION_CONFIRMED"
  val AssessmentScoresEntered = "ASSESSMENT_SCORES_ENTERED"
  val AssessmentScoresAccepted = "ASSESSMENT_SCORES_ACCEPTED"
  val AwaitingAssessmentCentreReevaluation = "AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION"
  val AssessmentCentrePassed = "ASSESSMENT_CENTRE_PASSED"
  val AssessmentCentrePassedNotified = "ASSESSMENT_CENTRE_PASSED_NOTIFIED"
  val AssessmentCentreFailed = "ASSESSMENT_CENTRE_FAILED"
  val AssessmentCentreFailedNotified = "ASSESSMENT_CENTRE_FAILED_NOTIFIED"
}
