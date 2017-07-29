/*
 * Copyright 2017 HM Revenue & Customs
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
import connectors.exchange.referencedata.SchemeId
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import connectors.exchange.sift.{ GeneralQuestionsAnswers, SchemeSpecificAnswer, SiftAnswers }
import models.UniqueIdentifier
import play.api.http.Status._
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SiftClient {

  val http: CSRHttp

  import ApplicationClient._
  import config.FrontendAppConfig.faststreamConfig._
  val apiBase: String = s"${url.host}${url.base}"

  def updateGeneralAnswers(applicationId: UniqueIdentifier, answers: GeneralQuestionsAnswers)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(
      s"${url.host}${url.base}/sift-answers/$applicationId/general",
      answers
    ).map {
      case x: HttpResponse if x.status == OK => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
      case _: ConflictException => throw new SiftAnswersSubmitted
    }
  }

  def updateSchemeSpecificAnswer(applicationId: UniqueIdentifier, schemeId: SchemeId, answer: SchemeSpecificAnswer)
                                (implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(
      s"$apiBase/sift-answers/$applicationId/$schemeId",
      answer
    ).map {
      case x: HttpResponse if x.status == OK => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
      case _: ConflictException => throw new SiftAnswersSubmitted
    }
  }

  def getGeneralQuestionsAnswers(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[GeneralQuestionsAnswers]] = {
    http.GET(s"$apiBase/sift-answers/$applicationId/general").map { response =>
      Some(response.json.as[GeneralQuestionsAnswers])
    } recover {
      case _: NotFoundException => None
    }
  }

  def getSchemeSpecificAnswer(applicationId: UniqueIdentifier, schemeId: SchemeId)(implicit hc: HeaderCarrier): Future[SchemeSpecificAnswer] = {
    http.GET(s"$apiBase/sift-answers/$applicationId/$schemeId").map { response =>
      response.json.as[SchemeSpecificAnswer]
    } recover {
      case _: NotFoundException => throw new SchemeSpecificAnswerNotFound()
    }
  }

  def getSiftAnswers(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[SiftAnswers] = {
    http.GET(s"$apiBase/sift-answers/$applicationId").map { response =>
      response.json.as[SiftAnswers]
    } recover {
      case _: NotFoundException => throw new SiftAnswersNotFound()
    }
  }

  def submitSiftAnswers(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/sift-answers/$applicationId/submit",
      Array.empty[Byte]
    ).map {
      case x: HttpResponse if x.status == OK => ()
    } recover {
      case _: UnprocessableEntityException => throw new SiftAnswersIncomplete
      case _: ConflictException => throw new SiftAnswersSubmitted
      case _: BadRequestException => throw new SiftAnswersNotFound()
    }
  }

  def getSiftAnswersStatus(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[SiftAnswersStatus] = {
    http.GET(s"$apiBase/sift-answers/$applicationId/status").map { response =>
      response.json.as[SiftAnswersStatus]
    } recover {
      case _: NotFoundException => throw new SiftAnswersNotFound()
    }
  }
}

object SiftClient extends SiftClient {
  override val http: CSRHttp = CSRHttp
}
