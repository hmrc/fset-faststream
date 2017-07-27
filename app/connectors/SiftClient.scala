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

import java.net.URLEncoder

import config.CSRHttp
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.PartnerGraduateProgrammes._
import connectors.exchange.GeneralDetails._
import connectors.exchange.Questionnaire._
import connectors.exchange._
import connectors.exchange.referencedata.SchemeId
import models.{ Adjustments, ApplicationRoute, UniqueIdentifier }
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SiftClient {

  val http: CSRHttp

  import ApplicationClient._
  import config.FrontendAppConfig.faststreamConfig._

  def updateSchemeSpecificAnswer(applicationId: UniqueIdentifier, schemeId: SchemeId, answer: SchemeSpecificAnswer)
                                (implicit hc: HeaderCarrier) = {
    http.POST(
      s"${url.host}${url.base}/sift-answers/$applicationId/$schemeId",
      answer
    ).map {
      case x: HttpResponse if x.status == OK => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def getSchemeSpecificAnswer(applicationId: UniqueIdentifier, schemeId: SchemeId)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/sift-answers/$applicationId/$schemeId").map { response =>
      response.json.as[connectors.exchange.SchemeSpecificAnswer]
    } recover {
      case _: NotFoundException => throw new SchemeSpecificAnswerNotFound()
    }
  }

  def getSiftAnswers(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/sift-answers/$applicationId").map { response =>
      response.json.as[connectors.exchange.SiftAnswers]
    } recover {
      case _: NotFoundException => throw new SiftAnswersNotFound()
    }
  }

}

object SiftClient extends SiftClient {
  override val http: CSRHttp = CSRHttp
}
