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

package connectors

import config.CSRHttp
import connectors.AllocationExchangeObjects._
import connectors.ExchangeObjects._
import connectors.exchange.ProgressResponse
import forms.{ AssistanceForm, GeneralDetailsForm }
import mappings.PostCodeMapping
import models.ApplicationData.ApplicationStatus.ApplicationStatus
import models.UniqueIdentifier
import org.joda.time.LocalDate
import play.api.Play.current
import play.api.http.Status._
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ApplicationClient {

  val http: CSRHttp

  import ApplicationClient._
  import ExchangeObjects.Implicits._
  import config.FrontendAppConfig.fasttrackConfig._

  def createApplication(userId: UniqueIdentifier, frameworkId: String)(implicit hc: HeaderCarrier) = {

    http.PUT(s"${url.host}${url.base}/application/create", CreateApplicationRequest(userId, frameworkId)).map { response =>
      response.json.as[ApplicationResponse]
    }
  }

  def submitApplication(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/application/submit/$userId/$applicationId", Json.toJson("")).map {
      case x: HttpResponse if x.status == OK => ()
    }.recover {
      case _: BadRequestException => throw new CannotSubmit()
    }
  }

  def withdrawApplication(applicationId: UniqueIdentifier, reason: WithdrawApplicationRequest)(implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/application/withdraw/$applicationId", Json.toJson(reason)).map {
      case x: HttpResponse if x.status == OK => ()
    }.recover {
      case _: NotFoundException => throw new CannotWithdraw()
    }
  }

  def addMedia(userId: UniqueIdentifier, media: String)(implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/media/create", AddMedia(userId, media)).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotAddMedia()
    }
  }

  def getApplicationProgress(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/application/progress/$applicationId").map { response =>
      response.json.as[ProgressResponse]
    }
  }

  def findApplication(userId: UniqueIdentifier, frameworkId: String)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/application/find/user/$userId/framework/$frameworkId").map { response =>
      response.json.as[ApplicationResponse]
    } recover {
      case _: NotFoundException => throw new ApplicationNotFound()
    }
  }

  def updateGeneralDetails(applicationId: UniqueIdentifier, userId: UniqueIdentifier, data: GeneralDetailsForm.Data, email: String)(
    implicit
    hc: HeaderCarrier
  ) = {

    http.POST(
      s"${url.host}${url.base}/personal-details/$userId/$applicationId",
      GeneralDetailsExchange(
        data.firstName,
        data.lastName,
        data.preferredName,
        email,
        LocalDate.parse(s"${data.dateOfBirth.year}-${data.dateOfBirth.month}-${data.dateOfBirth.day}"),
        data.address,
        PostCodeMapping.formatPostcode(data.postCode),
        data.phone,
        data.aLevel.getOrElse(false),
        data.stemLevel.getOrElse(false)
      )
    ).map {
        case x: HttpResponse if x.status == CREATED => ()
      } recover {
        case _: BadRequestException => throw new CannotUpdateRecord()
      }
  }

  def findPersonalDetails(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/personal-details/$userId/$applicationId").map { response =>
      response.json.as[GeneralDetailsExchange]
    } recover {
      case e: NotFoundException => throw new PersonalDetailsNotFound()
    }
  }

  def updateAssistanceDetails(applicationId: UniqueIdentifier, userId: UniqueIdentifier, data: AssistanceForm.Data)(
    implicit
    hc: HeaderCarrier
  ) = {
    http.PUT(
      s"${url.host}${url.base}/assistance-details/$userId/$applicationId",
      data.exchange
    ).map {
        case x: HttpResponse if x.status == CREATED => ()
      } recover {
        case _: BadRequestException => throw new CannotUpdateRecord()
      }
  }

  def findAssistanceDetails(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/assistance-details/$userId/$applicationId").map { response =>
      response.json.as[AssistanceDetailsExchange]
    } recover {
      case _: NotFoundException => throw new AssistanceDetailsNotFound()
    }
  }

  def updateQuestionnaire(applicationId: UniqueIdentifier, sectionId: String, questionnaire: Questionnaire)(
    implicit
    hc: HeaderCarrier
  ) = {
    http.PUT(
      s"${url.host}${url.base}/questionnaire/$applicationId/$sectionId",
      questionnaire
    ).map {
        case x: HttpResponse if x.status == ACCEPTED => ()
      } recover {
        case _: BadRequestException => throw new CannotUpdateRecord()
      }
  }

  def updateReview(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/application/review/$applicationId",
      ReviewRequest(true)
    ).map {
        case x: HttpResponse if x.status == OK => ()
      } recover {
        case _: BadRequestException => throw new CannotUpdateRecord()
      }
  }

  def getTestAssessment(userId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[OnlineTest] = {
    http.GET(s"${url.host}${url.base}/online-test/candidate/$userId").map { response =>
      response.json.as[ExchangeObjects.OnlineTest]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def getPDFReport(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Array[Byte]] = {
    http.wS.url(s"${url.host}${url.base}/online-test/pdf-report/$applicationId").get(resp =>
      if (resp.status == 200) {
        Iteratee.consume[Array[Byte]]()
      } else {
        throw new PdfReportNotFoundException()
      }).flatMap { it => it.run }
  }

  def getAllocationDetails(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[AllocationDetails]] = {
    import AllocationExchangeObjects.Implicits._
    http.GET(s"${url.host}${url.base}/allocation-status/$appId").map { response =>
      Some(response.json.as[AllocationExchangeObjects.AllocationDetails])
    } recover {
      case _: NotFoundException => None
    }
  }

  def confirmAllocation(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(s"${url.host}${url.base}/allocation-status/confirm/$appId", "").map(_ => ())
  }

  def onlineTestUpdate(userId: UniqueIdentifier, status: ApplicationStatus)(implicit hc: HeaderCarrier): Future[Unit] = {
    val body = Json.toJson(OnlineTestStatus(status))
    http.POST(s"${url.host}${url.base}/online-test/candidate/$userId/status", body).map(_ => ())
  }

  def completeTests(token: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(s"${url.host}${url.base}/online-test/complete/$token", "").map(_ => ())
  }
}

object ApplicationClient {

  sealed class CannotUpdateRecord extends Exception

  sealed class CannotSubmit extends Exception

  sealed class PersonalDetailsNotFound extends Exception

  sealed class AssistanceDetailsNotFound extends Exception

  sealed class ApplicationNotFound extends Exception

  sealed class CannotAddMedia extends Exception

  sealed class CannotWithdraw extends Exception

  sealed class OnlineTestNotFound extends Exception

  sealed class PdfReportNotFoundException extends Exception
}
