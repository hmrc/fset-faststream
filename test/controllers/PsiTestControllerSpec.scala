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

package controllers

import java.util.UUID

import config.SecurityEnvironmentImpl
import connectors.ApplicationClient
import connectors.exchange.{ Phase2TestGroupWithActiveTest2, PsiTest }
import models.UniqueIdentifier
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.SilhouetteComponent
import testkit.BaseControllerSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class PsiTestControllerSpec extends BaseControllerSpec {

  "Complete phase 2 tests" should {
    "throw an exception if no active test is found for the given order id" in new TestFixture {
      val orderId = UniqueIdentifier(UUID.randomUUID().toString)
      val p2TestGroup = Phase2TestGroupWithActiveTest2(expirationDate = DateTime.now(), activeTests = Nil)

      when(mockApplicationClient.getPhase2TestProfile2ByOrderId(any[UniqueIdentifier])(any[HeaderCarrier]))
        .thenReturn(Future.successful(p2TestGroup))

      val result = underTest.completePhase2Tests(orderId)(fakeRequest)
      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "display the phase 2 tests continue page if only the first test is complete" in new TestFixture {
      val test1OrderId = UniqueIdentifier(UUID.randomUUID().toString)
      val test2OrderId = UniqueIdentifier(UUID.randomUUID().toString)

      when(mockApplicationClient.completeTestByOrderId(any[UniqueIdentifier])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      val test1 = PsiTest(inventoryId = "inventoryId1", usedForResults = true,
        testUrl = "http://testurl.com", orderId = test1OrderId,
        invitationDate = DateTime.now(), startedDateTime = Some(DateTime.now()),
        completedDateTime = Some(DateTime.now()))
      val test2 = PsiTest(inventoryId = "inventoryId2", usedForResults = true,
        testUrl = "http://testurl.com", orderId = test2OrderId,
        invitationDate = DateTime.now(), startedDateTime = None,
        completedDateTime = None)

      val p2TestGroup = Phase2TestGroupWithActiveTest2(expirationDate = DateTime.now(), activeTests = Seq(test1, test2))

      when(mockApplicationClient.getPhase2TestProfile2ByOrderId(any[UniqueIdentifier])(any[HeaderCarrier]))
        .thenReturn(Future.successful(p2TestGroup))

      val result = underTest.completePhase2Tests(test2OrderId)(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Exercise complete")
    }

    "display the phase 2 tests complete page if both tests are complete2" in new TestFixture {
      val test1OrderId = UniqueIdentifier(UUID.randomUUID().toString)
      val test2OrderId = UniqueIdentifier(UUID.randomUUID().toString)

      val test1 = PsiTest(inventoryId = "inventoryId1", usedForResults = true,
        testUrl = "http://testurl.com", orderId = test1OrderId,
        invitationDate = DateTime.now(), startedDateTime = Some(DateTime.now()),
        completedDateTime = Some(DateTime.now()))
      val test2 = PsiTest(inventoryId = "inventoryId2", usedForResults = true,
        testUrl = "http://testurl.com", orderId = test2OrderId,
        invitationDate = DateTime.now(), startedDateTime = Some(DateTime.now()),
        completedDateTime = Some(DateTime.now()))

      val p2TestGroup = Phase2TestGroupWithActiveTest2(expirationDate = DateTime.now(), activeTests = Seq(test1, test2))

      when(mockApplicationClient.getPhase2TestProfile2ByOrderId(any[UniqueIdentifier])(any[HeaderCarrier]))
        .thenReturn(Future.successful(p2TestGroup))

      val result = underTest.completePhase2Tests(test2OrderId)(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Work based scenarios complete")
    }

  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]

    when(mockApplicationClient.completeTestByOrderId(any[UniqueIdentifier])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))

    class TestablePsiTestController extends PsiTestController(mockApplicationClient) {
      override lazy val silhouette = SilhouetteComponent.silhouette
    }

    val underTest = new TestablePsiTestController
  }
}
