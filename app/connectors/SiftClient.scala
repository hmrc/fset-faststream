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

package connectors

import config.{CSRHttp, FrontendAppConfig}
import connectors.ApplicationClient._
import connectors.exchange.referencedata.SchemeId
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import connectors.exchange.sift.{GeneralQuestionsAnswers, SchemeSpecificAnswer, SiftAnswers}
import models.UniqueIdentifier
import play.api.http.Status._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SiftClient @Inject() (config: FrontendAppConfig, http: CSRHttp)(implicit ec: ExecutionContext) {

  val url = config.faststreamBackendConfig.url
  val apiBase: String = s"${url.host}${url.base}"

  def updateGeneralAnswers(applicationId: UniqueIdentifier, answers: GeneralQuestionsAnswers)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT[GeneralQuestionsAnswers, HttpResponse](
      s"$apiBase/sift-answers/$applicationId/general",
      answers
    ).map {
        case response if response.status == OK => ()
        case response if response.status == BAD_REQUEST => throw new CannotUpdateRecord
        case response if response.status == CONFLICT => throw new SiftAnswersSubmitted
      }
  }

  def updateSchemeSpecificAnswer(applicationId: UniqueIdentifier, schemeId: SchemeId, answer: SchemeSpecificAnswer)
                                (implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT[SchemeSpecificAnswer, HttpResponse](
      s"$apiBase/sift-answers/$applicationId/${schemeId.value}",
      answer
    ).map {
        case response if response.status == OK => ()
        case response if response.status == BAD_REQUEST => throw new CannotUpdateRecord
        case response if response.status == CONFLICT => throw new SiftAnswersSubmitted
    }
  }

  def getGeneralQuestionsAnswers(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[GeneralQuestionsAnswers]] = {
    http.GET[Option[GeneralQuestionsAnswers]](s"$apiBase/sift-answers/$applicationId/general")
  }

  def getSchemeSpecificAnswer(applicationId: UniqueIdentifier, schemeId: SchemeId)
    (implicit hc: HeaderCarrier): Future[Option[SchemeSpecificAnswer]] = {
    http.GET[Option[SchemeSpecificAnswer]](s"$apiBase/sift-answers/$applicationId/${schemeId.value}")
  }

  def getSiftAnswers(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[SiftAnswers] = {
    http.GET[SiftAnswers](s"$apiBase/sift-answers/$applicationId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new SiftAnswersNotFound()
    }
  }

  // scalastyle:off cyclomatic.complexity
  def submitSiftAnswers(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT[Array[Byte], HttpResponse](
      s"$apiBase/sift-answers/$applicationId/submit",
      Array.empty[Byte]
    ).map {
      case response if response.status == OK => ()
      case response if response.status == UNPROCESSABLE_ENTITY => throw new SiftAnswersIncomplete
      case response if response.status == CONFLICT => throw new SiftAnswersSubmitted
      case response if response.status == BAD_REQUEST => throw new SiftAnswersNotFound
      case response if response.status == FORBIDDEN => throw new SiftExpired
    }
  }
  // scalastyle:on

  def getSiftAnswersStatus(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[SiftAnswersStatus]] = {
    http.GET[Option[SiftAnswersStatus]](s"$apiBase/sift-answers/$applicationId/status")
  }
}
