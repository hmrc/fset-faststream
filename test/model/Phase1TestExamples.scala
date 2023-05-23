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

import model.persisted.{ PsiTest, PsiTestResult }
import org.joda.time.DateTime

object Phase1TestExamples {

  val psiTestResult = PsiTestResult(tScore = 12.5, rawScore = 5.5, None)

  def firstPsiTest(implicit now: DateTime) =
    PsiTest(
      inventoryId = "inventoryId1",
      orderId = "orderId1",
      assessmentId = "assessmentId1",
      reportId = "reportId1",
      normId = "normId1",
      usedForResults = true,
      testUrl = "http://localhost",
      invitationDate = now,
      testResult = Some(psiTestResult)
    )

  def secondPsiTest(implicit now: DateTime) = firstPsiTest.copy(inventoryId = "inventoryId2", orderId = "orderId2")

  def thirdPsiTest(implicit now: DateTime) = firstPsiTest.copy(inventoryId = "inventoryId3", orderId = "orderId3")
}
