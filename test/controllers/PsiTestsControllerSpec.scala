/*
 * Copyright 2020 HM Revenue & Customs
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

import model.Exceptions.CannotFindTestByOrderIdException
import model.exchange.PsiRealTimeResults
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.Helpers._
import services.NumericalTestService2
import services.onlinetesting.phase1.Phase1TestService2
import services.onlinetesting.phase2.Phase2TestService2
//import services.stc.StcEventService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class PsiTestsControllerSpec extends UnitWithAppSpec {

  val mockPhase1TestService2 = mock[Phase1TestService2]
  val mockPhase2TestService2 = mock[Phase2TestService2]
  val mockNumericalTestService2 = mock[NumericalTestService2]
//  val mockEventService = mock[StcEventService2]

  val orderId = "orderId1"

  def controllerUnderTest = new PsiTestsController(
    mockPhase1TestService2,
    mockPhase2TestService2,
    mockNumericalTestService2
  )

  "start" should {
    "mark the phase1 test as started" in {
      when(mockPhase1TestService2.markAsStarted2(eqTo(orderId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.start(orderId)(fakeRequest(""))
      status(response) mustBe OK
    }

    "mark the phase2 test as started" in {
      when(mockPhase1TestService2.markAsStarted2(eqTo(orderId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockPhase2TestService2.markAsStarted2(eqTo(orderId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.start(orderId)(fakeRequest(""))
      status(response) mustBe OK
    }

    "return test not found" in {
      when(mockPhase1TestService2.markAsStarted2(eqTo(orderId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockPhase2TestService2.markAsStarted2(eqTo(orderId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))

      val response = controllerUnderTest.start(orderId)(fakeRequest(""))
      status(response) mustBe NOT_FOUND
    }
  }

  "completeTestByOrderId" should {
    "mark the phase1 test as completed" in {
      when(mockPhase1TestService2.markAsCompleted2(eqTo(orderId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.completeTestByOrderId(orderId)(fakeRequest)
      status(response) mustBe OK
    }

    "mark the phase2 test as completed" in {
      when(mockPhase1TestService2.markAsCompleted2(eqTo(orderId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockPhase2TestService2.markAsCompleted2(eqTo(orderId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.completeTestByOrderId(orderId)(fakeRequest)
      status(response) mustBe OK
    }

    "return test not found" in {
      when(mockPhase1TestService2.markAsCompleted2(eqTo(orderId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockPhase2TestService2.markAsCompleted2(eqTo(orderId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockNumericalTestService2.markAsCompletedByOrderId(eqTo(orderId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))

      val response = controllerUnderTest.completeTestByOrderId(orderId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "realTimeResults" should {
    val testResults = PsiRealTimeResults(tScore = 10.0, rawScore = 20.0, reportUrl = None)
    "process the phase1 test results" in {
      when(mockPhase1TestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.realTimeResults(orderId)(fakeRequest(testResults))
      status(response) mustBe OK
    }

    "process the phase2 test results" in {
      when(mockPhase1TestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockPhase2TestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.realTimeResults(orderId)(fakeRequest(testResults))
      status(response) mustBe OK
    }

    "process the numeric test  results" in {
      when(mockPhase1TestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockPhase2TestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockNumericalTestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.realTimeResults(orderId)(fakeRequest(testResults))
      status(response) mustBe OK
    }

    "return test not found" in {
      when(mockPhase1TestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockPhase2TestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))
      when(mockNumericalTestService2.storeRealTimeResults(eqTo(orderId), eqTo(testResults))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByOrderIdException("")))

      val response = controllerUnderTest.realTimeResults(orderId)(fakeRequest(testResults))
      status(response) mustBe NOT_FOUND
    }
  }
}
