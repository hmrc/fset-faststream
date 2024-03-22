/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.{OffsetDateTime, ZoneId}

object Phase2TestProfileExamples {

  val now = OffsetDateTime.now(ZoneId.of("UTC"))

  def profile(implicit now: OffsetDateTime) = Phase2TestGroup(now, List(firstP2PsiTest, secondP2PsiTest))

  val psiTestResult = PsiTestResult(tScore = 12.5, rawScore = 5.5, None)

  def firstP2PsiTest(implicit now: OffsetDateTime) =
    PsiTest(
      inventoryId = "inventoryId5",
      orderId = "orderId5",
      assessmentId = "assessmentId5",
      reportId = "reportId5",
      normId = "normId5",
      usedForResults = true,
      testUrl = "http://localhost",
      invitationDate = now,
      testResult = Some(psiTestResult)
    )

  def secondP2PsiTest(implicit now: OffsetDateTime) = firstP2PsiTest.copy(inventoryId = "inventoryId6", orderId = "orderId6")
}
