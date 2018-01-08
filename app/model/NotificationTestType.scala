/*
 * Copyright 2018 HM Revenue & Customs
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
import model.ApplicationStatus.{ PHASE1_TESTS_FAILED, PHASE2_TESTS_FAILED, PHASE3_TESTS_FAILED }
import model.ProgressStatuses._

sealed trait NotificationTestType {
  val appStatus: ApplicationStatus
  val notificationProgress: ProgressStatus
  val template: String
  val applicationRoutes = List.empty[ApplicationRoute]
}

sealed trait NotificationTestTypeSdipFs {
  val progressStatus: String
  val notificationProgress: String
  val template: String
  val applicationRoute: ApplicationRoute
}

sealed case class FailedTestType(appStatus: ApplicationStatus, notificationProgress: ProgressStatus,
                                 receiveStatus: ProgressStatus, template: String) extends NotificationTestType

sealed case class SuccessTestType(appStatus: ApplicationStatus, notificationProgress: ProgressStatus,
                                  newApplicationStatus: ApplicationStatus, template: String) extends NotificationTestType

sealed case class FailedTestTypeSdipFs(template: String, applicationRoute: ApplicationRoute) extends NotificationTestTypeSdipFs {
  // Using a bogus ApplicationStatus just to get the progress status key
  override val progressStatus: String = getProgressStatusForSdipFsFailed(ApplicationStatus.SUBMITTED).key
  override val notificationProgress: String = getProgressStatusForSdipFsFailedNotified(ApplicationStatus.SUBMITTED).key
}

sealed case class SuccessfulTestTypeSdipFs(template: String, applicationRoute: ApplicationRoute) extends NotificationTestTypeSdipFs {
  // Using a bogus ApplicationStatus just to get the progress status key
  override val progressStatus: String = getProgressStatusForSdipFsSuccess(ApplicationStatus.SUBMITTED).key
  override val notificationProgress: String = getProgressStatusForSdipFsPassedNotified(ApplicationStatus.SUBMITTED).key
}

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
  extends SuccessTestType(ApplicationStatus.PHASE1_TESTS_PASSED, PHASE1_TESTS_PASSED_NOTIFIED,
    ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED, "fset_faststream_app_online_phase1_test_success") {
  override val applicationRoutes = List(ApplicationRoute.Edip, ApplicationRoute.Sdip)
}

object Phase3SuccessTestType
  extends SuccessTestType(ApplicationStatus.PHASE3_TESTS_PASSED, PHASE3_TESTS_PASSED_NOTIFIED,
    ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED, "fset_faststream_app_online_phase3_test_success") {
  override val applicationRoutes = List(ApplicationRoute.Faststream)
}

object Phase3SuccessSdipFsTestType
  extends SuccessTestType(ApplicationStatus.PHASE3_TESTS_PASSED, PHASE3_TESTS_PASSED_NOTIFIED,
    ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED, "fset_faststream_app_online_phase3_test_success_sdipfs") {
  override val applicationRoutes = List(ApplicationRoute.SdipFaststream)
}

object FailedSdipFsTestType
  extends FailedTestTypeSdipFs("fset_faststream_app_online_sdip_fs_test_failed", ApplicationRoute.SdipFaststream)

object SuccessfulSdipFsTestType
  extends SuccessfulTestTypeSdipFs("fset_faststream_app_online_sdip_fs_test_success", ApplicationRoute.SdipFaststream)
