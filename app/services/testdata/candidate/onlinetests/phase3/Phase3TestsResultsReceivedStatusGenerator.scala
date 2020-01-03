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

package services.testdata.candidate.onlinetests.phase3

import connectors.launchpadgateway.exchangeobjects.in.reviewed._
import model.ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.{ DateTime, LocalDate }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase3TestRepository
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.faker.DataFaker.Random

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object Phase3TestsResultsReceivedStatusGenerator extends Phase3TestsResultsReceivedStatusGenerator {
  val previousStatusGenerator = Phase3TestsCompletedStatusGenerator
  val appRepository = applicationRepository
  val phase3TestRepo = phase3TestRepository
}

trait Phase3TestsResultsReceivedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val phase3TestRepo: Phase3TestRepository

  def getCallbackData(receivedBeforeInHours: Int, scores: List[Double],
                      generateNullScoresForFewQuestions: Boolean = false): ReviewedCallbackRequest = {
    ReviewedCallbackRequest(DateTime.now.minusHours(receivedBeforeInHours), "cnd_0f38b92f2e04b87d27ffcdbe4348d5f6",
      "FSCND-f9cf395d-df9d-4037-9fbf-9b3aa9d86c16", 46, None, "FSINV-28d52608-95e0-4d3a-93e7-0881cd2bc78b",
      LocalDate.now().plusDays(3), ReviewSectionRequest(
        ReviewSectionTotalAverageRequest("videoInterview", "50%", 50.0), // TODO: 50.0 should be calculated
        ReviewSectionReviewersRequest(
          reviewer1 = getReviewSectionReviewersRequest("John Doe", "johnDoe@localhost", scores.headOption,
            scores.take(2).reverse.headOption,  generateNullScoresForFewQuestions),
          reviewer2 = Some(getReviewSectionReviewersRequest("John Doe2", "johnDoe2@localhost", scores.headOption,
            scores.take(2).reverse.headOption, generateNullScoresForFewQuestions)),
          reviewer3 = Some(getReviewSectionReviewersRequest("John Doe3", "johnDoe3@localhost", scores.headOption,
            scores.take(2).reverse.headOption, generateNullScoresForFewQuestions)))
      ))
  }

  def getReviewSectionReviewersRequest(name: String, email: String, criteria1Score: Option[Double] = None,
                                       criteria2Score: Option[Double] = None, generateNullScoresForFewQuestions: Boolean) = {
    ReviewSectionReviewerRequest(name, email, Some(Random.videoInterviewFeedback),
      question1 = getReviewSectionQuestionRequest(100, criteria1Score, criteria2Score, generateNullScoresForFewQuestions),
      question2 = getReviewSectionQuestionRequest(101, criteria1Score, criteria2Score, generateNullScoresForFewQuestions),
      question3 = getReviewSectionQuestionRequest(102, criteria1Score, criteria2Score, generateNullScoresForFewQuestions),
      question4 = getReviewSectionQuestionRequest(103, criteria1Score, criteria2Score),
      question5 = getReviewSectionQuestionRequest(104, criteria1Score, criteria2Score),
      question6 = getReviewSectionQuestionRequest(105, criteria1Score, criteria2Score),
      question7 = getReviewSectionQuestionRequest(106, criteria1Score, criteria2Score),
      question8 = getReviewSectionQuestionRequest(107, criteria1Score, criteria2Score)
    )
  }

  def getReviewSectionQuestionRequest(questionId: Int, criteria1Score: Option[Double] = None, criteria2Score: Option[Double] = None,
                                      setNullScores: Boolean = false) = {
    val (score1, score2) = if (setNullScores) {
      None -> None
    } else {
      Some(criteria1Score.getOrElse(Random.getVideoInterviewScore)) -> Some(criteria2Score.getOrElse(Random.getVideoInterviewScore))
    }
    ReviewSectionQuestionRequest(questionId,
      ReviewSectionCriteriaRequest("numeric", score1),
      ReviewSectionCriteriaRequest("numeric", score2)
    )
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    val receivedBeforeInHours = generatorConfig.phase3TestData.flatMap(_.receivedBeforeInHours).getOrElse(0)
    val scores = generatorConfig.phase3TestData.map(_.scores).getOrElse(Nil)
    val generateNullScoresForFewQuestions = generatorConfig.phase3TestData.flatMap(_.generateNullScoresForFewQuestions).getOrElse(false)

    val callbackData1 = getCallbackData(receivedBeforeInHours, scores, generateNullScoresForFewQuestions)
    val callbackData2 = getCallbackData(receivedBeforeInHours, scores, generateNullScoresForFewQuestions)
    val callbackData3 = getCallbackData(receivedBeforeInHours, scores, generateNullScoresForFewQuestions)
    for {
        candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
        token <- Future.successful(candidate.phase3TestGroup.get.tests.head.token)
        _ <- phase3TestRepo.appendCallback(token, ReviewedCallbackRequest.key, callbackData1)
        _ <- phase3TestRepo.appendCallback(token, ReviewedCallbackRequest.key, callbackData2)
        _ <- phase3TestRepo.appendCallback(token, ReviewedCallbackRequest.key, callbackData3)
        _ <- appRepository.addProgressStatusAndUpdateAppStatus(candidate.applicationId.get, PHASE3_TESTS_RESULTS_RECEIVED)
      } yield candidate
    }
  }
