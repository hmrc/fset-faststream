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

import model.persisted.{ CubiksTest, PsiTest, PsiTestResult, TestResult }
import org.joda.time.DateTime

object Phase1TestExamples {
  val testResult = TestResult(status = "Ready", norm = "norm", tScore = Some(12.5), percentile = None, raw = None, sten = None)

  def createTestResult(tScore: Double) = TestResult("Ready", "norm", Some(tScore), None, None, None)

  def firstTest(implicit now: DateTime) = CubiksTest(16196, usedForResults = true, 2, "cubiks", "token", "http://localhost", now, 3,
    testResult = Some(testResult))

  def secondTest(implicit now: DateTime) = firstTest.copy(scheduleId = 16194)

  def thirdTest(implicit now: DateTime) = firstTest.copy(scheduleId = 16196)

  val psiTestResult = PsiTestResult(status = "Ready", tScore = 12.5, raw = 5.5)

  def firstPsiTest(implicit now: DateTime) =
    PsiTest(
      inventoryId = "inventoryId1",
      orderId = "orderId1",
      usedForResults = true,
      testUrl = "http://localhost",
      invitationDate = now,
      testResult = Some(psiTestResult)
    )

  def secondPsiTest(implicit now: DateTime) = firstPsiTest.copy(inventoryId = "inventoryId2", orderId = "orderId2")

  def thirdPsiTest(implicit now: DateTime) = firstPsiTest.copy(inventoryId = "inventoryId3", orderId = "orderId3")

  def fourthPsiTest(implicit now: DateTime) = firstPsiTest.copy(inventoryId = "inventoryId4", orderId = "orderId4")
}
