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

import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE2_TESTS_EXPIRED, PHASE3_TESTS_EXPIRED, ProgressStatus }

sealed case class TestExpirationEvent(phase: String, expiredStatus: ProgressStatus, template: String)

object Phase1ExpirationEvent extends TestExpirationEvent("PHASE1", PHASE1_TESTS_EXPIRED, "fset_faststream_app_online_phase1_test_expired")
object Phase2ExpirationEvent extends TestExpirationEvent("PHASE2", PHASE2_TESTS_EXPIRED, "fset_faststream_app_online_phase2_test_expired")
object Phase3ExpirationEvent extends TestExpirationEvent("PHASE3", PHASE3_TESTS_EXPIRED, "fset_faststream_app_online_phase3_test_expired")
