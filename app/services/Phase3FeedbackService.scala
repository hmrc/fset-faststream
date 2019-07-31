/*
 * Copyright 2019 HM Revenue & Customs
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

package services

import connectors.ApplicationClient
import models.UniqueIdentifier
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase3FeedbackService {
  val applicationClient: ApplicationClient

  def getFeedback(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[(String, String)] = {
    object OverallScore extends Enumeration {
      type OverallScore = Value
      val Low, Medium, High = Value
    }
    import OverallScore._

    def getOverallScore(score: Double): OverallScore = {
      val HighScoreThreshold: Double = 24
      val MediumScoreThreshold: Double = 20

      score match {
        case _ if score >= HighScoreThreshold => High
        case _ if score >= MediumScoreThreshold && score < HighScoreThreshold => Medium
        case _ => Low
      }
    }


    applicationClient.getPhase3TestGroup(applicationId).map { phase3TestGroup =>
      // TODO: Find out how to retrieve the best results if there are many
      val testResults = phase3TestGroup.activeTests.reverse.head.callbacks.reviewed.reverse.head
      val criteria1Score = getOverallScore(testResults.calculateReviewCriteria1Score()).toString
      val criteria2Score = getOverallScore(testResults.calculateReviewCriteria2Score()).toString
      (criteria1Score, criteria2Score)
    }
  }
}

object Phase3FeedbackService extends Phase3FeedbackService {
  val applicationClient = ApplicationClient
}
