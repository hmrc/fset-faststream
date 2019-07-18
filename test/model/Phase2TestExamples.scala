/*
 * Copyright 2019 HM Revenue & Customs
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

import model.Phase1TestExamples.firstPsiTest
import model.persisted.CubiksTest
import model.persisted.TestResult
import org.joda.time.DateTime

object Phase2TestExamples {
  val testResult = TestResult("Ready", "norm", tScore = Some(12.5), percentile = None, raw = None, sten = None)

  def firstTest(implicit now: DateTime) = CubiksTest(17815, usedForResults = true, 2, "cubiks", "token", "http://localhost", now, 3,
    testResult = Some(testResult))

  def fifthPsiTest(implicit now: DateTime) = firstPsiTest.copy(inventoryId = "inventoryId5", orderId = "orderId5")

  def sixthPsiTest(implicit now: DateTime) = firstPsiTest.copy(inventoryId = "inventoryId6", orderId = "orderId6")
}
