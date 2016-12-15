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

import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.ApplicationStatus.{ PHASE1_TESTS_FAILED, PHASE2_TESTS_FAILED, PHASE3_TESTS_FAILED, READY_FOR_EXPORT }
import model.ProgressStatuses._

sealed trait NotificationTestType {
  val appStatus: ApplicationStatus
  val notificationProgress: ProgressStatus
  val template: String
  val applicationRoutes = List.empty[ApplicationRoute]
}

sealed case class FailedTestType(appStatus: ApplicationStatus, notificationProgress: ProgressStatus,
                                 receiveStatus: ProgressStatus, template: String) extends NotificationTestType

sealed case class SuccessTestType(appStatus: ApplicationStatus, notificationProgress: ProgressStatus,
                                  newApplicationStatus: ApplicationStatus, template: String) extends NotificationTestType

object Phase1FailedTestType
  extends FailedTestType(PHASE1_TESTS_FAILED, PHASE1_TESTS_FAILED_NOTIFIED, PHASE1_TESTS_RESULTS_RECEIVED,
    "fset_faststream_app_online_phase1_test_failed")

object Phase2FailedTestType
  extends FailedTestType(PHASE2_TESTS_FAILED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_RESULTS_RECEIVED,
    "fset_faststream_app_online_phase2_test_failed")

object Phase3FailedTestType
  extends FailedTestType(PHASE3_TESTS_FAILED, PHASE3_TESTS_FAILED_NOTIFIED, PHASE3_TESTS_RESULTS_RECEIVED,
      "fset_faststream_app_online_phase3_test_failed")

object Phase1SuccessTestType
  extends SuccessTestType(ApplicationStatus.PHASE1_TESTS_PASSED, PHASE1_TESTS_SUCCESS_NOTIFIED,
    READY_FOR_EXPORT, "fset_faststream_app_online_phase1_test_success") {
  override val applicationRoutes = List(ApplicationRoute.Edip, ApplicationRoute.Sdip)
}

object Phase3SuccessTestType
  extends SuccessTestType(ApplicationStatus.PHASE3_TESTS_PASSED, PHASE3_TESTS_SUCCESS_NOTIFIED,
    READY_FOR_EXPORT, "fset_faststream_app_online_phase3_test_success")
