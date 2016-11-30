/*
 * Copyright 2016 HM Revenue & Customs
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

import model.Exceptions.CannotFindTestByCubiksId
import model.exchange.CubiksTestResultReady
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.Helpers._
import services.events.EventService
import services.onlinetesting.{ Phase1TestService, Phase2TestService }
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class CubiksTestControllerSpec extends UnitWithAppSpec {

  val mockPhase1TestService = mock[Phase1TestService]
  val mockPhase2TestService = mock[Phase2TestService]
  val mockEventService = mock[EventService]

  def controllerUnderTest = new CubiksTestsController {
    val phase1TestService = mockPhase1TestService
    val eventService = mockEventService
    val phase2TestService = mockPhase2TestService
  }

  "start" should {
    "mark the phase1 test as started" in {
      val cubiksUserId = 1
      when(mockPhase1TestService.markAsStarted(eqTo(cubiksUserId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.start(cubiksUserId)(fakeRequest(""))
      status(response) mustBe OK
    }

    "mark the phase2 test as started" in {
      val cubiksUserId = 1
      when(mockPhase1TestService.markAsStarted(eqTo(cubiksUserId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsStarted(eqTo(cubiksUserId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.start(cubiksUserId)(fakeRequest(""))
      status(response) mustBe OK
    }

    "return test not found" in {
      val cubiksUserId = 1
      when(mockPhase1TestService.markAsStarted(eqTo(cubiksUserId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsStarted(eqTo(cubiksUserId), any[DateTime])(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))

      val response = controllerUnderTest.start(cubiksUserId)(fakeRequest(""))
      status(response) mustBe NOT_FOUND
    }
  }

  "complete" should {
    "mark the phase1 test as completed" in {
      val cubiksUserId = 1
      when(mockPhase1TestService.markAsCompleted(eqTo(cubiksUserId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.complete(cubiksUserId)(fakeRequest(""))
      status(response) mustBe OK
    }

    "mark the phase2 test as completed" in {
      val cubiksUserId = 1
      when(mockPhase1TestService.markAsCompleted(eqTo(cubiksUserId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsCompleted(eqTo(cubiksUserId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.complete(cubiksUserId)(fakeRequest(""))
      status(response) mustBe OK
    }

    "return test not found" in {
      val cubiksUserId = 1
      when(mockPhase1TestService.markAsCompleted(eqTo(cubiksUserId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsCompleted(eqTo(cubiksUserId))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))

      val response = controllerUnderTest.complete(cubiksUserId)(fakeRequest(""))
      status(response) mustBe NOT_FOUND
    }
  }

  "completeTestByToken" should {
    "mark the phase1 test as completed" in {
      val token = "1"
      when(mockPhase1TestService.markAsCompleted(eqTo(token))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.completeTestByToken(token)(fakeRequest)
      status(response) mustBe OK
    }

    "mark the phase2 test as completed" in {
      val token = "1"
      when(mockPhase1TestService.markAsCompleted(eqTo(token))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsCompleted(eqTo(token))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.completeTestByToken(token)(fakeRequest)
      status(response) mustBe OK
    }

    "return test not found" in {
      val token = "1"
      when(mockPhase1TestService.markAsCompleted(eqTo(token))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsCompleted(eqTo(token))(any[HeaderCarrier], any[RequestHeader])
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))

      val response = controllerUnderTest.completeTestByToken(token)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "markResultsReady" should {
    "mark the phase1 test results as ready" in {
      val cubiksUserId = 1
      val cubiksTestResult = CubiksTestResultReady(Some(1), "Ready", Some(""))
      when(mockPhase1TestService.markAsReportReadyToDownload(eqTo(cubiksUserId), eqTo(cubiksTestResult))
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.markResultsReady(cubiksUserId)(fakeRequest(cubiksTestResult))
      status(response) mustBe OK
    }

    "mark the phase2 test as completed" in {
      val cubiksUserId = 1
      val cubiksTestResult = CubiksTestResultReady(Some(1), "Ready", Some(""))
      when(mockPhase1TestService.markAsReportReadyToDownload(eqTo(cubiksUserId), eqTo(cubiksTestResult))
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsReportReadyToDownload(eqTo(cubiksUserId), eqTo(cubiksTestResult))
      ).thenReturn(Future.successful(()))

      val response = controllerUnderTest.markResultsReady(cubiksUserId)(fakeRequest(cubiksTestResult))
      status(response) mustBe OK
    }

    "return test not found" in {
      val cubiksUserId = 1
      val cubiksTestResult = CubiksTestResultReady(Some(1), "Ready", Some(""))
      when(mockPhase1TestService.markAsReportReadyToDownload(eqTo(cubiksUserId), eqTo(cubiksTestResult))
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))
      when(mockPhase2TestService.markAsReportReadyToDownload(eqTo(cubiksUserId), eqTo(cubiksTestResult))
      ).thenReturn(Future.failed(CannotFindTestByCubiksId("")))

      val response = controllerUnderTest.markResultsReady(cubiksUserId)(fakeRequest(cubiksTestResult))
      status(response) mustBe NOT_FOUND
    }
  }
}
