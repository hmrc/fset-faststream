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
import org.joda.time.LocalDate
import org.mockito.Matchers.{ eq => eqTo }
import play.api.test.Helpers._
import services.events.EventService
import services.onlinetesting.Phase3TestService

class LaunchpadTestsControllerSpec extends BaseControllerSpec {

  trait TestFixture {
    val mockPhase3TestService = mock[Phase3TestService]
    val mockEventService = mock[EventService]

    val sampleCandidateId = UUID.randomUUID().toString
    val sampleCustomCandidateId = "FSCND-456"
    val sampleInviteId = "FSINV-123"
    val sampleInterviewId = 123
    val sampleDeadline = LocalDate.now.plusDays(7)

    def controllerUnderTest = new LaunchpadTestsController {
      val phase3TestService = mockPhase3TestService
      val eventService = mockEventService
    }

    val sampleSetupProcessCallback = SetupProcessCallbackRequest(
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline
    )

    val sampleViewPracticeQuestionCallback = ViewPracticeQuestionCallbackRequest(
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline
    )

    val sampleQuestionCallback = QuestionCallbackRequest(
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline,
      "1"
    )

    val finalCallback = FinalCallbackRequest(
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      None,
      sampleInviteId,
      sampleDeadline
    )

    val finishedCallback = FinishedCallbackRequest(
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
    }
  }

  "view-practice-question callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.viewPracticeQuestionCallback(sampleInviteId)(fakeRequest(sampleViewPracticeQuestionCallback))
      status(response) mustBe OK
    }
  }

  "question callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.setupProcessCallback(sampleInviteId)(fakeRequest(sampleQuestionCallback))
      status(response) mustBe OK
    }
  }

  "final callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.setupProcessCallback(sampleInviteId)(fakeRequest(finalCallback))
      status(response) mustBe OK
    }
  }

  "finished callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.setupProcessCallback(sampleInviteId)(fakeRequest(finishedCallback))
      status(response) mustBe OK
    }
  }
}
