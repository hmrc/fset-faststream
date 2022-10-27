/*
 * Copyright 2022 HM Revenue & Customs
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
import javax.inject.{ Inject, Singleton }
import models.UniqueIdentifier
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class Phase3FeedbackService @Inject() (applicationClient: ApplicationClient)(implicit val ec: ExecutionContext) {

  def getFeedback(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[(String, String)]] = {
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
      val testResultsOpt = phase3TestGroup.activeTests.headOption
      val latestReviewedOpt = testResultsOpt.flatMap(testResults =>
        testResults.callbacks.getLatestReviewed
      )
      latestReviewedOpt.map(latestReviewed => {
        val criteria1Score = getOverallScore(latestReviewed.calculateReviewCriteria1Score).toString
        val criteria2Score = getOverallScore(latestReviewed.calculateReviewCriteria2Score).toString
        (criteria1Score, criteria2Score)
      }
      )
    }
  }
}
