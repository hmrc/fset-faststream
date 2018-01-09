/*
 * Copyright 2018 HM Revenue & Customs
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

package connectors

import config.CSRHttp
import connectors.exchange.candidatescores.AssessmentScoresAllExercises
import models.UniqueIdentifier
import play.api.http.Status._
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

trait AssessmentScoresClient {

  val http: CSRHttp

  import config.FrontendAppConfig.faststreamConfig._
  val apiBase: String = s"${url.host}${url.base}"

  def findReviewerAcceptedAssessmentScores(applicationId: UniqueIdentifier)(
    implicit
    hc: HeaderCarrier): Future[AssessmentScoresAllExercises] = {
    http.GET(s"$apiBase/assessment-scores/reviewer/accepted-scores/application/$applicationId").map {
      case response: HttpResponse if response.status == OK => response.json.as[AssessmentScoresAllExercises]
      case response: HttpResponse if response.status == NOT_FOUND =>
        throw new Exception(s"Error no assessment scores found for application id $applicationId")
    } recover {
      case _ => throw new Exception(s"Error retrieving assessment scores for application id $applicationId")
    }
  }
}

object AssessmentScoresClient extends AssessmentScoresClient {
  override val http: CSRHttp = CSRHttp
}
