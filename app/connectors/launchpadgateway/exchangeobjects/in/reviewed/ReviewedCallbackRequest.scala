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

package connectors.launchpadgateway.exchangeobjects.in.reviewed

import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class ReviewedCallbackRequest(
  received: DateTime,
  candidateId: String,
  customCandidateId: String,
  interviewId: Int,
  customInterviewId: Option[String],
  customInviteId: String,
  deadline: LocalDate,
  reviews: ReviewSectionRequest) {

  val reviewers = reviews.reviewers
  val latestReviewer = reviewers.reviewer3.getOrElse(reviewers.reviewer2.getOrElse(reviewers.reviewer1))

  def calculateTotalScore(): Double = {
    calculateReviewCriteria1Score + calculateReviewCriteria2Score
  }

  def calculateReviewCriteria1Score(): Double = {
    aggregateScoresForAllQuestion(question => question.reviewCriteria1)
  }

  def calculateReviewCriteria2Score(): Double = {
    aggregateScoresForAllQuestion(question => question.reviewCriteria2)
  }

  private def aggregateScoresForAllQuestion(scoreExtractor: ReviewSectionQuestionRequest => ReviewSectionCriteriaRequest) = {
    (
      BigDecimal(scoreExtractor(latestReviewer.question1).score.getOrElse(0.0)) +
        BigDecimal(scoreExtractor(latestReviewer.question2).score.getOrElse(0.0))  +
        BigDecimal(scoreExtractor(latestReviewer.question3).score.getOrElse(0.0)) +
        BigDecimal(scoreExtractor(latestReviewer.question4).score.getOrElse(0.0)) +
        BigDecimal(scoreExtractor(latestReviewer.question5).score.getOrElse(0.0)) +
        BigDecimal(scoreExtractor(latestReviewer.question6).score.getOrElse(0.0)) +
        BigDecimal(scoreExtractor(latestReviewer.question7).score.getOrElse(0.0)) +
        BigDecimal(scoreExtractor(latestReviewer.question8).score.getOrElse(0.0))
      ).toDouble
  }

  def allQuestionsReviewed: Boolean = {
    val questions = List(latestReviewer.question1, latestReviewer.question2, latestReviewer.question3, latestReviewer.question4,
      latestReviewer.question5, latestReviewer.question6, latestReviewer.question7, latestReviewer.question8)
    questions.nonEmpty && questions.forall(ques => ques.reviewCriteria1.score.isDefined && ques.reviewCriteria2.score.isDefined)
  }

}

object ReviewedCallbackRequest {
  val key = "reviewed"
  implicit val reviewedCallbackFormat = Json.format[ReviewedCallbackRequest]
  import repositories.BSONDateTimeHandler
  import repositories.BSONLocalDateHandler
  implicit val bsonHandler: BSONHandler[BSONDocument, ReviewedCallbackRequest] = Macros.handler[ReviewedCallbackRequest]

  def getLatestReviewed(reviewCallBacks: List[ReviewedCallbackRequest]): Option[ReviewedCallbackRequest] =
    reviewCallBacks.sortWith { (r1, r2) => r1.received.isAfter(r2.received) }.headOption

  case class LaunchpadQuestionIsUnscoredException(message: String) extends Exception(message)
}
