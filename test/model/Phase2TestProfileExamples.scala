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

import model.persisted._
import org.joda.time.{ DateTime, DateTimeZone }

object Phase2TestProfileExamples {

  val now = DateTime.now().withZone(DateTimeZone.UTC)
  val testResult = TestResult("Ready", "norm", Some(12.5), None, None, None)

  def getEtrayTest(implicit now: DateTime) = CubiksTest(16196, usedForResults = true, 2, "cubiks", "token", "http://localhost", now, 3,
    testResult = Some(testResult))

  def profile(implicit now: DateTime) = Phase2TestGroup(now, List(getEtrayTest))

  def profile2(implicit now: DateTime) = Phase2TestGroup2(now, List(firstP2PsiTest, secondP2PsiTest))

  val phase2Test = List(CubiksTest(16196, usedForResults = true, 100, "cubiks", "token1", "http://localhost", now, 2000))
  val phase2TestWithResult = phase2TestWithResults(TestResult("Ready", "norm", Some(20.5), None, None, None))

  def phase2TestWithResults(testResult: TestResult) = {
    phase2Test.map(t => t.copy(testResult = Some(testResult)))
  }

  val psiTestResult = PsiTestResult(tScore = 12.5, rawScore = 5.5, None)

  def firstP2PsiTest(implicit now: DateTime) =
    PsiTest(
      inventoryId = "inventoryId5",
      orderId = "orderId5",
      usedForResults = true,
      testUrl = "http://localhost",
      invitationDate = now,
      testResult = Some(psiTestResult)
    )

  def secondP2PsiTest(implicit now: DateTime) = firstP2PsiTest.copy(inventoryId = "inventoryId6", orderId = "orderId6")
}
