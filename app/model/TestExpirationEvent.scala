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

package model

import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE2_TESTS_EXPIRED, PHASE3_TESTS_EXPIRED, ProgressStatus }
import TestExpirationEmailTemplates._

sealed trait TestExpirationEvent {
  val phase: String
  val expiredStatus: ProgressStatus
  val template: String
  val gracePeriodInSecs: Int
}

case class Phase1ExpirationEvent(phase: String = "PHASE1",
                                expiredStatus: ProgressStatus = PHASE1_TESTS_EXPIRED,
                                template: String = phase1ExpirationTemplate,
                                gracePeriodInSecs: Int) extends TestExpirationEvent

case class Phase2ExpirationEvent(phase: String = "PHASE2",
                                expiredStatus: ProgressStatus = PHASE2_TESTS_EXPIRED,
                                template: String = phase2ExpirationTemplate,
                                gracePeriodInSecs: Int) extends TestExpirationEvent

case class Phase3ExpirationEvent(phase: String = "PHASE3",
                                expiredStatus: ProgressStatus = PHASE3_TESTS_EXPIRED,
                                template: String = phase3ExpirationTemplate,
                                gracePeriodInSecs: Int) extends TestExpirationEvent

object TestExpirationEmailTemplates {
  val phase1ExpirationTemplate = "fset_faststream_app_online_phase1_test_expired"
  val phase2ExpirationTemplate = "fset_faststream_app_online_phase2_test_expired"
  val phase3ExpirationTemplate = "fset_faststream_app_online_phase3_test_expired"
}
