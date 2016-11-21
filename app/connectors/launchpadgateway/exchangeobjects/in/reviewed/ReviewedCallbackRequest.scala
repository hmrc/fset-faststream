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

package connectors.launchpadgateway.exchangeobjects.in.reviewed

import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest.LaunchpadQuestionIsUnscoredException
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.Json
import reactivemongo.bson.{BSONDocument, BSONHandler, Macros}

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

    def scoreForQuestion(questionNo: Int, question: ReviewSectionQuestionRequest) =
      question.reviewCriteria1.score.getOrElse(0.0) + question.reviewCriteria2.score.getOrElse(0.0)

    scoreForQuestion(1, latestReviewer.question1) +
    scoreForQuestion(2, latestReviewer.question2) +
    scoreForQuestion(3, latestReviewer.question3) +
    scoreForQuestion(4, latestReviewer.question4) +
    scoreForQuestion(5, latestReviewer.question5) +
    scoreForQuestion(6, latestReviewer.question6) +
    scoreForQuestion(7, latestReviewer.question7) +
    scoreForQuestion(8, latestReviewer.question8)
  }
}

object ReviewedCallbackRequest {
  val key = "reviewed"
  implicit val reviewedCallbackFormat = Json.format[ReviewedCallbackRequest]
  import repositories.BSONDateTimeHandler
  import repositories.BSONLocalDateHandler
  implicit val bsonHandler: BSONHandler[BSONDocument, ReviewedCallbackRequest] = Macros.handler[ReviewedCallbackRequest]

  case class LaunchpadQuestionIsUnscoredException(message: String) extends Exception(message)
}
