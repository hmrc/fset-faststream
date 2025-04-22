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

package controllers

import java.util.UUID
import connectors.launchpadgateway.exchangeobjects.in._
import connectors.launchpadgateway.exchangeobjects.in.reviewed._
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.onlinetesting.phase3.{Phase3TestCallbackService, Phase3TestService}
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, OffsetDateTime, ZoneId}
import scala.concurrent.Future

class LaunchpadTestsControllerSpec extends UnitWithAppSpec {

  trait TestFixture {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = FakeRequest("GET", "some/path")

    val mockPhase3TestService = mock[Phase3TestService]
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
    when(mockPhase3TestCallbackService.recordCallback(any[SetupProcessCallbackRequest]())
    (any[HeaderCarrier](), any[RequestHeader]())).thenReturn(Future.successful(()))
    when(mockPhase3TestCallbackService.recordCallback(any[ViewBrandedVideoCallbackRequest]())).thenReturn(Future.successful(()))
    when(mockPhase3TestCallbackService.recordCallback(any[ReviewedCallbackRequest]())
    (any[HeaderCarrier](), any[RequestHeader]())).thenReturn(Future.successful(()))

    def controllerUnderTest = new LaunchpadTestsController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockPhase3TestService,
      mockPhase3TestCallbackService
    )

    val now = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS)

    val sampleSetupProcessCallback = SetupProcessCallbackRequest(
      now,
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      customInterviewId = None,
      sampleInviteId,
      sampleDeadline
    )

    val sampleViewPracticeQuestionCallback = ViewPracticeQuestionCallbackRequest(
      now,
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      customInterviewId = None,
      sampleInviteId,
      sampleDeadline
    )

    val sampleQuestionCallback = QuestionCallbackRequest(
      now,
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      customInterviewId = None,
      sampleInviteId,
      sampleDeadline,
      "1"
    )

    val sampleFinalCallback = FinalCallbackRequest(
      now,
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      customInterviewId = None,
      sampleInviteId,
      sampleDeadline
    )

    val sampleFinishedCallback = FinishedCallbackRequest(
      now,
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      customInterviewId = None,
      sampleInviteId,
      sampleDeadline
    )

    private def generateReviewedQuestion(i: Int, score1: Option[Double], score2: Option[Double]) = {
      ReviewSectionQuestionRequest(
        i,
        ReviewSectionCriteriaRequest("numeric", score1),
        ReviewSectionCriteriaRequest("numeric", score2)
      )
    }

    val sampleReviewedCallback = ReviewedCallbackRequest(
      now,
      sampleCandidateId,
      sampleCustomCandidateId,
      sampleInterviewId,
      customInterviewId = None,
      sampleInviteId,
      sampleDeadline,
      ReviewSectionRequest(
        ReviewSectionTotalAverageRequest(
          "video_interview",
          "46%",
          46.0
        ),
        ReviewSectionReviewersRequest(
          ReviewSectionReviewerRequest(
            "John Smith",
            "john.smith@mailinator.com",
            Some("This is a comment"),
            generateReviewedQuestion(1, score1 = None, score2 = None),
            generateReviewedQuestion(2, Some(1.0), Some(2.0)),
            generateReviewedQuestion(3, Some(3.0), Some(2.0)),
            generateReviewedQuestion(4, Some(4.0), Some(2.5)),
            generateReviewedQuestion(5, Some(5.0), Some(2.5)),
            generateReviewedQuestion(6, Some(4.5), Some(1.0)),
            generateReviewedQuestion(7, Some(3.5), Some(5.0)),
            generateReviewedQuestion(8, Some(2.5), Some(2.5))
          ), reviewer2 = None, reviewer3 = None
        )
      )
    )
  }

  "setup-process callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.setupProcessCallback(sampleInviteId)(fakeRequest(sampleSetupProcessCallback))
      status(response) mustBe OK

      verify(mockPhase3TestCallbackService, times(1)).recordCallback(any[SetupProcessCallbackRequest]()
      )(any[HeaderCarrier](), any[RequestHeader]())
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
      val response = controllerUnderTest.finalCallback(sampleInviteId)(fakeRequest(sampleFinalCallback))
      status(response) mustBe OK
      verify(mockPhase3TestCallbackService, times(1))
        .recordCallback(eqTo(sampleFinalCallback))(any[HeaderCarrier](), any[RequestHeader]())
    }
  }

  "finished callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.finishedCallback(sampleInviteId)(fakeRequest(sampleFinishedCallback))
      status(response) mustBe OK
      verify(mockPhase3TestCallbackService, times(1))
        .recordCallback(eqTo(sampleFinishedCallback))(any[HeaderCarrier](), any[RequestHeader]())
    }
  }

  "reviewed callback" should {
    "respond ok" in new TestFixture {
      val response = controllerUnderTest.reviewedCallback(sampleInviteId)(fakeRequest(sampleReviewedCallback))
      status(response) mustBe OK
      verify(mockPhase3TestCallbackService, times(1))
        .recordCallback(eqTo(sampleReviewedCallback))(any[HeaderCarrier](), any[RequestHeader]())
    }
  }
}
