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

package controllers

import java.util.UUID

import connectors.ApplicationClient.OnlineTestNotFound
import connectors.exchange._
import models.ApplicationData.ApplicationStatus.WITHDRAWN
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import play.api.test.Helpers._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class TestResultsControllerSpec extends BaseControllerSpec {
  "test results controller" should {
    "display feedback report links for the first 3 phases when the candidate has been invited to all 3 phases" in new TestFixture {
      mockPhaseOneTwoThreeData(List(phase1Test1), List(phase2Test1))

      val appStatus = candWithApp.application.progress.copy(
        phase2TestProgress = Phase2TestProgress(phase2TestsInvited = true),
        phase3TestProgress = Phase3TestProgress(phase3TestsInvited = true)
      )
      val result = controller(appStatus).present()(fakeRequest)

      val content = contentAsString(result)
      status(result) mustBe OK
      checkAllResultsTitlesAndLinks(content)
    }

    "display feedback report links for the first 2 phases when the candidate has been invited to the first 2 phases" in new TestFixture {
      mockPhaseOneTwoThreeData(List(phase1Test1), List(phase2Test1))

      val appStatus = candWithApp.application.progress.copy(
        phase2TestProgress = Phase2TestProgress(phase2TestsInvited = true)
      )
      val result = controller(appStatus).present()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      checkPhase1ResultsTitleAndLinks(content)
      checkPhase2ResultsTitleAndLinks(content)
      content must not include "Phase 3 results"
      content must not include phase3ResultsReportLink
    }

    "display feedback report links for the first phase when the candidate has only been invited to the first phase" in new TestFixture {
      mockPhaseOneTwoThreeData(List(phase1Test1))

      val appStatus = candWithApp.application.progress
      val result = controller(appStatus).present()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      checkPhase1ResultsTitleAndLinks(content)
      content must not include "Phase 2 results"
      content must not include phase2Test1ResultsReportLink
      content must not include "Phase 3 results"
      content must not include phase3ResultsReportLink
    }

    "not display feedback report links for the first phase when the candidate has not completed any tests" in new TestFixture {
      mockPhaseOneTwoThreeData(List(phase1Test1NoResults))

      val appStatus = candWithApp.application.progress
      val result = controller(appStatus).present()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK

      content must include("Phase 1 results")
      content must include("<div>tests.inventoryid.name.45c7aee3-4d23-45c7-a09d-276df7db3e4c</div>")
      content must not include phase1Test1ResultsReportLink
      content must not include "Phase 2 results"
      content must not include phase2Test1ResultsReportLink
      content must not include "Phase 3 results"
      content must not include phase3ResultsReportLink
    }

    "handle OnlineTestNotFound exception" in new TestFixture {
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound()))

      val appStatus = candWithApp.application.progress
      val result = controller(appStatus).present()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK

      content must include("No data found")
      content must not include "Phase 1 results"
      content must not include "<div>tests.inventoryid.name.45c7aee3-4d23-45c7-a09d-276df7db3e4c</div>"
      content must not include phase1Test1ResultsReportLink
      content must not include "Phase 2 results"
      content must not include "<div>Case Study Assessment</div>"
      content must not include phase2Test1ResultsReportLink
      content must not include "Phase 3 results"
      content must not include phase3ResultsReportLink
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val candWithApp = currentCandidateWithApp.copy(
      application = currentCandidateWithApp.application.copy(applicationStatus = WITHDRAWN))

    def controller(appStatus: Progress) = {
      new TestResultsController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent,
        mockNotificationTypeHelper, mockApplicationClient) with TestableSecureActions {
        override val candidateWithApp: CachedDataWithApp = candWithApp
          .copy(application = candWithApp.application.copy(progress = appStatus))
      }
    }

    def mockPhaseOneTwoThreeData(phase1Tests: List[PsiTest] = Nil, phase2Tests: List[PsiTest] = Nil) = {
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Phase1TestGroupWithNames2(expirationDate = DateTime.now, activeTests = phase1Tests)))
      when(mockApplicationClient.getPhase2TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Phase2TestGroupWithActiveTest2(expirationDate = DateTime.now, activeTests = phase2Tests)))
      when(mockApplicationClient.getPhase3TestGroup(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Phase3TestGroup(expirationDate = DateTime.now, tests = Nil)))
    }

    def checkAllResultsTitlesAndLinks(content: String) = {
      checkPhase1ResultsTitleAndLinks(content)
      checkPhase2ResultsTitleAndLinks(content)
      checkPhase3ResultsTitleAndLinks(content)
    }

    def checkPhase1ResultsTitleAndLinks(content: String) = {
      content must include("Phase 1 results")
      checkPhase1ResultsLinks(content)
    }

    val phase1Test1InventoryId = "45c7aee3-4d23-45c7-a09d-276df7db3e4c"
    val phase1Test1NoResults = PsiTest(inventoryId = phase1Test1InventoryId, usedForResults = true,
      testUrl = "http://testurl.com", orderId = UniqueIdentifier(UUID.randomUUID()),
      invitationDate = DateTime.now, testResult = None)
    val phase1Test1 = phase1Test1NoResults.copy(testResult = Some(PsiTestResult(testReportUrl = Some("http://phase1Test1Url.com"))))

    val phase2Test1InventoryId = "60b423e5-75d6-4d31-b02c-97b8686e22e6"
    val phase2Test1 = PsiTest(inventoryId = phase2Test1InventoryId, usedForResults = true,
      testUrl = "http://testurl.com", orderId = UniqueIdentifier(UUID.randomUUID()),
      invitationDate = DateTime.now, testResult = Some(PsiTestResult(testReportUrl = Some("http://phase2Test1Url.com"))))


    val phase1Test1ResultsReportLink = "<a href=\"http://phase1Test1Url.com\"" +
      " target=\"_blank\" id=\"tests.inventoryid.name." + phase1Test1InventoryId + "LinkResultsReport\">Feedback report"
    def checkPhase1ResultsLinks(content: String) = {
      content must include("<div>tests.inventoryid.name." + phase1Test1InventoryId + "</div>")
      content must include(phase1Test1ResultsReportLink)
    }

    def checkPhase2ResultsTitleAndLinks(content: String) = {
      content must include("Phase 2 results")
      checkPhase2ResultsLinks(content)
    }

    val phase2Test1ResultsReportLink = "<a href=\"http://phase2Test1Url.com\"" +
      " target=\"_blank\" id=\"tests.inventoryid.name." + phase2Test1InventoryId + "LinkResultsReport\">Feedback report"
    def checkPhase2ResultsLinks(content: String) = {
      content must include("<div>tests.inventoryid.name." + phase2Test1InventoryId + "</div>")
      content must include(phase2Test1ResultsReportLink)
    }

    def checkPhase3ResultsTitleAndLinks(content: String) = {
      content must include("Phase 3 results")
      checkPhase3ResultsLink(content)
    }

    val phase3ResultsReportLink = "<a href=\"/fset-fast-stream/online-tests/phase3/feedback-report\"" +
      " target=\"_blank\" id=\"phase3ResultsReportLink\" alt=\"Phase 3 feedback report\">"
    def checkPhase3ResultsLink(content: String) = {
      content must include(phase3ResultsReportLink)
    }
  }
}
