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

import model.ApplicationStatus.ApplicationStatus
import model.ApplicationStatus.{ PHASE3_TESTS_PASSED, READY_FOR_EXPORT }
import model.ProgressStatuses.{ PHASE3_TESTS_SUCCESS_NOTIFIED, ProgressStatus }


sealed case class SuccessTestType(appStatus: ApplicationStatus, notificationProgress: ProgressStatus,
                                  newApplicationStatus: ApplicationStatus, template: String)

object Phase3SuccessTestType
  extends SuccessTestType(PHASE3_TESTS_PASSED, PHASE3_TESTS_SUCCESS_NOTIFIED,
    READY_FOR_EXPORT, "fset_faststream_app_online_phase3_test_success")
