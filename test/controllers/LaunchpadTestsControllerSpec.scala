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

import connectors.launchpadgateway.exchangeobjects.in._
import model.Exceptions.CannotFindTestByCubiksId
import model.exchange.CubiksTestResultReady
import org.joda.time.DateTime
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.Helpers._
import services.events.EventService
import services.onlinetesting.{Phase1TestService, Phase2TestService, Phase3TestService}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class LaunchpadTestsControllerSpec extends BaseControllerSpec {

  trait TestFixture {
    val mockPhase3TestService = mock[Phase3TestService]
    val mockEventService = mock[EventService]

    val sampleInviteId = "FSINV-123"

    def controllerUnderTest = new LaunchpadTestsController {
      val phase3TestService = mockPhase3TestService
      val eventService = mockEventService
    }

    val sampleSetupProcessCallback = SetupProcessCallbackRequest(
      // TODO
    )

    val sampleViewPracticeQuestionCallback = ViewPracticeQuestionCallbackRequest(
      // TODO
    )

    val sampleQuestionCallback = QuestionCallbackRequest(
      // TODO
    )

    val finalCallback = FinalCallbackRequest(
      // TODO
    )

    val finishedCallback = FinishedCallbackRequest(
      // TODO
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
