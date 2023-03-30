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

package services.onlinetesting.phase2

import config._
import model.persisted.PsiTest
import org.joda.time.DateTime
import testkit.UnitSpec

import java.time.OffsetDateTime

class Phase2TestSelectorSpec extends UnitSpec {

  "test selector" should {
    "return test1" in new TestFixture {
      val test = selector.findFirstTest1Test(List(test1, test2))
      test mustBe Some(test1)
    }

    "return test2" in new TestFixture {
      val test = selector.findFirstTest2Test(List(test1, test2))
      test mustBe Some(test2)
    }

    "return no test for test1" in new TestFixture {
      val test = selector.findFirstTest1Test(List(test2, test3))
      test mustBe None
    }

    "return no test for test2" in new TestFixture {
      val test = selector.findFirstTest2Test(List(test1, test3))
      test mustBe None
    }
  }

  trait TestFixture {
    val phase1TestsConfig = Phase1TestsConfig(
      expiryTimeInDays = 5,
      gracePeriodInSecs = 300,
      testRegistrationDelayInSecs = 1,
      tests = Map.empty[String, PsiTestIds],
      standard = List("test1", "test2", "test3", "test4"),
      gis = List("test1", "test2")
    )

    val test1InventoryId = "60b423e5-75d6-4d31-b02c-97b8686e22e6"
    val test2InventoryId = "d2b3262c-2da8-4015-8579-9bebf5c0f53a"

    val phase2PsiTest1Ids = PsiTestIds(
      inventoryId = test1InventoryId,
      assessmentId = "1de9e9f5-2400-4bfd-bde6-6577f02a7aad",
      reportId = "78982931-2F72-47E8-BF48-2232AEBA205F",
      normId = "484c3fa9-1b32-48fc-a26a-11a50ce28415"
    )

    val phase2PsiTest2Ids = PsiTestIds(
      inventoryId = test2InventoryId,
      assessmentId = "9c5bca6a-2a0c-4a36-8e5a-748e80e22b04",
      reportId = "233ef02e-2bb0-4ba1-87d5-fab3e144275c",
      normId = "af7aebe6-ac44-4574-a0cf-e9fe9bb78e81"
    )

    val test1 = PsiTest(
      inventoryId = test1InventoryId,
      orderId = "orderId1",
      usedForResults = true,
      testUrl = "",
      invitationDate = OffsetDateTime.now(),
      assessmentId = "",
      reportId = "",
      normId = ""
    )

    val test2 = PsiTest(
      inventoryId = test2InventoryId,
      orderId = "orderId2",
      usedForResults = true,
      testUrl = "",
      invitationDate = OffsetDateTime.now(),
      assessmentId = "",
      reportId = "",
      normId = ""
    )

    val test3 = PsiTest(
      inventoryId = "inventoryId3",
      orderId = "orderId3",
      usedForResults = true,
      testUrl = "",
      invitationDate = OffsetDateTime.now(),
      assessmentId = "",
      reportId = "",
      normId = ""
    )

    val phase2TestsConfig = Phase2TestsConfig(
      expiryTimeInDays = 5,
      expiryTimeInDaysForInvigilatedETray = 5,
      gracePeriodInSecs = 300,
      testRegistrationDelayInSecs = 1,

      tests = Map[String, PsiTestIds](
        "test1" -> phase2PsiTest1Ids,
        "test2" -> phase2PsiTest2Ids,
      ),
      standard = List("test1", "test2")
    )

    val numericalTestsConfig = NumericalTestsConfig(
      gracePeriodInSecs = 300,
      tests = Map.empty[String, PsiTestIds],
      standard = List("test1")
    )

    val reportConfig = ReportConfig(
      xmlReportId = 1,
      pdfReportId = 2,
      localeCode = "en-GB"
    )

    val selector = new Phase2TestSelector {
      val gatewayConfig = OnlineTestsGatewayConfig(
        url = "http://test.com",
        phase1Tests = phase1TestsConfig,
        phase2Tests = phase2TestsConfig,
        numericalTests = numericalTestsConfig,
        reportConfig = reportConfig,
        candidateAppUrl = "http://localhost:9284",
        emailDomain = "mailinator.com"
      )
    }
  }
}
