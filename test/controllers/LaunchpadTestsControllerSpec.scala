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

import java.util.UUID

import connectors.launchpadgateway.exchangeobjects.in._
import org.joda.time.{ DateTime, LocalDate }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.events.EventService
import services.onlinetesting.{ Phase3TestCallbackService, Phase3TestService }
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class LaunchpadTestsControllerSpec extends UnitWithAppSpec {

  trait TestFixture {
    implicit val hc = new HeaderCarrier()
    implicit val rh: RequestHeader = FakeRequest("GET", "some/path")

    val mockPhase3TestService = mock[Phase3TestService]
    val mockEventService = mock[EventService]
    val mockPhase3TestCallbackService = mock[Phase3TestCallbackService]

    val sampleCandidateId = UUID.randomUUID().toString
    val sampleCustomCandidateId = "FSCND-456"
    val sampleInviteId = "FSINV-123"
    val sampleInterviewId = 123
    val sampleDeadline = LocalDate.now.plusDays(7)

    when(mockPhase3TestCallbackService.recordCallback(any[QuestionCallbackRequest]())).thenReturn(Future.successful(()))
    when(mockPhase3TestCallbackService.recordCallback(any[FinishedCallbackRequest]())
    (any[HeaderCarrier](), any[RequestHeader])).thenReturn(Future.successful(()))
    when(mockPhase3TestCallbackService.recordCallback(any[FinalCallbackRequest]())
    (any[HeaderCarrier](), any[RequestHeader])).thenReturn(Future.successful(()))
    when(mockPhase3TestCallbackService.recordCallback(any[ViewPracticeQuestionCallbackRequest]())).thenReturn(Future.successful(()))
    when(mockPhase3TestCallbackService.recordCallback(any[SetupProcessCallbackRequest]())).thenReturn(Future.successful(()))
    when(mockPhase3TestCallbackService.recordCallback(any[ViewBrandedVideoCallbackRequest]())).thenReturn(Future.successful(()))

    def controllerUnderTest = new LaunchpadTestsController {
      val phase3TestService = mockPhase3TestService
      val phase3TestCallbackService = mockPhase3TestCallbackService
      val eventService = mockEventService
    }

    val sampleSetupProcessCallback = SetupProcessCallbackRequest(
      DateTime.now(),
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline
    )

    val sampleViewPracticeQuestionCallback = ViewPracticeQuestionCallbackRequest(
      DateTime.now(),
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline
    )

    val sampleQuestionCallback = QuestionCallbackRequest(
      DateTime.now(),
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline,
      "1"
    )

    val finalCallback = FinalCallbackRequest(
      DateTime.now(),
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline
    )

    val finishedCallback = FinishedCallbackRequest(
      DateTime.now(),
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline
    )
  }

  "setup-process callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.setupProcessCallback(sampleInviteId)(fakeRequest(sampleSetupProcessCallback))
      status(response) mustBe OK

      verify(mockPhase3TestCallbackService, times(1)).recordCallback(any[SetupProcessCallbackRequest]())
    }
  }

  "view-practice-question callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.viewPracticeQuestionCallback(sampleInviteId)(fakeRequest(sampleViewPracticeQuestionCallback))
      status(response) mustBe OK

      verify(mockPhase3TestCallbackService, times(1)).recordCallback(any[ViewPracticeQuestionCallbackRequest]())
    }
  }

  "question callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.questionCallback(sampleInviteId)(fakeRequest(sampleQuestionCallback))
      status(response) mustBe OK

      verify(mockPhase3TestCallbackService, times(1)).recordCallback(any[QuestionCallbackRequest]())
    }
  }

  "final callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.finalCallback(sampleInviteId)(fakeRequest(finalCallback))
      status(response) mustBe OK

      verify(mockPhase3TestCallbackService, times(1)).recordCallback(eqTo(finalCallback))(any[HeaderCarrier](), any[RequestHeader]())
    }
  }

  "finished callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.finishedCallback(sampleInviteId)(fakeRequest(finishedCallback))
      status(response) mustBe OK

      verify(mockPhase3TestCallbackService, times(1)).recordCallback(eqTo(finishedCallback))(any[HeaderCarrier](), any[RequestHeader]())
    }
  }
}
