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

import java.util.UUID

import connectors.launchpadgateway.exchangeobjects.in.reviewed._
import model.persisted.phase3tests.{ LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }

object Phase3TestProfileExamples {

  val Now =  DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)
  val Token = newToken
  val sampleCandidateId = UUID.randomUUID().toString
  val sampleCustomCandidateId = "FSCND-456"
  val sampleInviteId = "FSINV-123"
  val sampleInterviewId = 123
  val sampleDeadline = LocalDate.now.plusDays(7)
  def newToken = UUID.randomUUID.toString
  val launchPadTest = LaunchpadTest(
    interviewId = 123,
    usedForResults = true,
    token = Token,
    testUrl = "test.com",
    invitationDate = Now,
    candidateId = "CND_123456",
    customCandidateId = "FSCND_123",
    startedDateTime = None,
    completedDateTime = None,
    callbacks = LaunchpadTestCallbacks(reviewed = List())
  )

  val sampleReviewedCallback = (score: Option[Double]) => ReviewedCallbackRequest(
    DateTime.now(),
    sampleCandidateId,
    sampleCustomCandidateId,
    sampleInterviewId,
    None,
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
          generateReviewedQuestion(1, Some(0.0), Some(0.0)),
          generateReviewedQuestion(2, Some(0.0), Some(0.0)),
          generateReviewedQuestion(3, Some(0.0), Some(0.0)),
          generateReviewedQuestion(4, Some(0.0), Some(0.0)),
          generateReviewedQuestion(5, Some(0.0), Some(0.0)),
          generateReviewedQuestion(6, Some(0.0), Some(0.0)),
          generateReviewedQuestion(7, Some(0.0), Some(0.0)),
          generateReviewedQuestion(8, score, Some(0.0))
        ), None, None
      )
    )
  )

  def buildReviewCallBack(criteriaScore: Double, dateTime: DateTime = DateTime.now()) = ReviewedCallbackRequest(
    dateTime,
    sampleCandidateId,
    sampleCustomCandidateId,
    sampleInterviewId,
    None,
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
          generateReviewedQuestion(1, Some(criteriaScore), Some(criteriaScore)),
          generateReviewedQuestion(2, Some(criteriaScore), Some(criteriaScore)),
          generateReviewedQuestion(3, Some(criteriaScore), Some(criteriaScore)),
          generateReviewedQuestion(4, Some(criteriaScore), Some(criteriaScore)),
          generateReviewedQuestion(5, Some(criteriaScore), Some(criteriaScore)),
          generateReviewedQuestion(6, Some(criteriaScore), Some(criteriaScore)),
          generateReviewedQuestion(7, Some(criteriaScore), Some(criteriaScore)),
          generateReviewedQuestion(8, Some(criteriaScore), Some(criteriaScore))
        ), None, None
      )
    )
  )

  val phase3Test = Phase3TestGroup(expirationDate = DatePlus7Days, tests = List(launchPadTest))

  def phase3TestWithResult(implicit hrsBeforeLastReviewed: Int = 0) = {
    phase3TestWithResults(Some(50.0), hrsBeforeLastReviewed).activeTests
  }

  def phase3TestWithResults(videoInterviewScore: Option[Double], hrsBeforeLastReviewed: Int = 0) = {
    val launchPadTestWithResult = launchPadTest.copy(callbacks =
      LaunchpadTestCallbacks(reviewed = List(
        sampleReviewedCallback(videoInterviewScore).copy(received = DateTime.now().minusHours(hrsBeforeLastReviewed)),
        sampleReviewedCallback(videoInterviewScore).copy(received = DateTime.now()))))
    Phase3TestGroup(expirationDate = DatePlus7Days, tests = List(launchPadTestWithResult))
  }

  private def generateReviewedQuestion(i: Int, score1: Option[Double], score2: Option[Double]) = {
    ReviewSectionQuestionRequest(
      i,
      ReviewSectionCriteriaRequest(
        "numeric",
        score1
      ),
      ReviewSectionCriteriaRequest(
        "numeric",
        score2
      )
    )
  }
}
