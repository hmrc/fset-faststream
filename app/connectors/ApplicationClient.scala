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

import java.net.URLEncoder

import config.CSRHttp
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.PartnerGraduateProgrammes._
import connectors.exchange.PersonalDetails._
import connectors.exchange.Questionnaire._
import connectors.exchange._
import models.{ Adjustments, ApplicationRoute, UniqueIdentifier }
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ApplicationClient {

  val http: CSRHttp

  import ApplicationClient._
  import config.FrontendAppConfig.faststreamConfig._
  import exchange.Implicits._

  def createApplication(userId: UniqueIdentifier, frameworkId: String,
    applicationRoute: ApplicationRoute.ApplicationRoute = ApplicationRoute.Faststream)
    (implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/application/create", CreateApplicationRequest(userId,
      frameworkId, applicationRoute)).map { response =>
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

  def withdrawApplication(applicationId: UniqueIdentifier, reason: WithdrawApplication)(implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/application/withdraw/$applicationId", Json.toJson(reason)).map {
      case x: HttpResponse if x.status == OK => ()
    }.recover {
      case _: NotFoundException => throw new CannotWithdraw()
    }
  }

  def addReferral(userId: UniqueIdentifier, referral: String)(implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/media/create", AddReferral(userId, referral)).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotAddReferral()
    }
  }

  def getApplicationProgress(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/application/progress/$applicationId").map { response =>
      response.json.as[ProgressResponse]
    }
  }

  def findApplication(userId: UniqueIdentifier, frameworkId: String)(implicit hc: HeaderCarrier): Future[ApplicationResponse] = {
    http.GET(s"${url.host}${url.base}/application/find/user/$userId/framework/$frameworkId").map { response =>
      response.json.as[ApplicationResponse]
    } recover {
      case _: NotFoundException => throw new ApplicationNotFound()
    }
  }

  def updatePersonalDetails(applicationId: UniqueIdentifier, userId: UniqueIdentifier, personalDetails: PersonalDetails)
                           (implicit hc: HeaderCarrier) = {
    http.POST(
      s"${url.host}${url.base}/personal-details/$userId/$applicationId",
      personalDetails
    ).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def getPersonalDetails(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/personal-details/$userId/$applicationId").map { response =>
      response.json.as[PersonalDetails]
    } recover {
      case e: NotFoundException => throw new PersonalDetailsNotFound()
    }
  }

  def updatePartnerGraduateProgrammes(applicationId: UniqueIdentifier, pgp: PartnerGraduateProgrammes)
                                     (implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/partner-graduate-programmes/$applicationId",
      pgp
    ).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def getPartnerGraduateProgrammes(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/partner-graduate-programmes/$applicationId").map { response =>
      response.json.as[connectors.exchange.PartnerGraduateProgrammes]
    } recover {
      case _: NotFoundException => throw new PartnerGraduateProgrammesNotFound()
    }
  }

  def updateAssistanceDetails(applicationId: UniqueIdentifier, userId: UniqueIdentifier, assistanceDetails: AssistanceDetails)
                             (implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/assistance-details/$userId/$applicationId", assistanceDetails).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def getAssistanceDetails(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"${url.host}${url.base}/assistance-details/$userId/$applicationId").map { response =>
      response.json.as[connectors.exchange.AssistanceDetails]
    } recover {
      case _: NotFoundException => throw new AssistanceDetailsNotFound()
    }
  }

  def updateQuestionnaire(applicationId: UniqueIdentifier, sectionId: String, questionnaire: Questionnaire)
                         (implicit hc: HeaderCarrier) = {
    http.PUT(s"${url.host}${url.base}/questionnaire/$applicationId/$sectionId", questionnaire).map {
      case x: HttpResponse if x.status == ACCEPTED => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def updatePreview(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/application/preview/$applicationId",
      PreviewRequest(true)
    ).map {
      case x: HttpResponse if x.status == OK => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def verifyInvigilatedToken(email: String, token: String)(implicit hc: HeaderCarrier): Future[InvigilatedTestUrl] =
    http.POST(s"${url.host}${url.base}/online-test/phase2/verifyAccessCode", VerifyInvigilatedTokenUrlRequest(email.toLowerCase, token)).map {
      (resp: HttpResponse) => {
        resp.json.as[InvigilatedTestUrl]
      }
    }.recover {
      case e: NotFoundException => throw new TokenEmailPairInvalidException()
      case Upstream4xxResponse(_, FORBIDDEN, _, _) => throw new TestForTokenExpiredException()
    }

  def getPhase1TestProfile(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase1TestGroupWithNames] = {
    http.GET(s"${url.host}${url.base}/online-test/phase1/candidate/$appId").map { response =>
      response.json.as[Phase1TestGroupWithNames]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def getPhase2TestProfile(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase2TestGroupWithActiveTest] = {
    http.GET(s"${url.host}${url.base}/online-test/phase2/candidate/$appId").map { response =>
      response.json.as[Phase2TestGroupWithActiveTest]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def getPhase3TestGroup(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase3TestGroup] = {
    http.GET(s"${url.host}${url.base}/phase3-test-group/$appId").map { response =>
      response.json.as[Phase3TestGroup]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def getFinalSchemeResults(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[List[SchemeEvaluationResult]]] = {
    http.GET(s"${url.host}${url.base}/application/schemeresults/$appId").map { response =>
      Some(response.json.as[List[SchemeEvaluationResult]])
    } recover {
      case _: NotFoundException => None
    }
  }

  private def encodeUrlParam(str: String) = URLEncoder.encode(str, "UTF-8")

  def startPhase3TestByToken(launchpadInviteId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"${url.host}${url.base}/launchpad/${encodeUrlParam(launchpadInviteId)}/markAsStarted", "").map(_ => ())
  }

  def completePhase3TestByToken(launchpadInviteId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"${url.host}${url.base}/launchpad/${encodeUrlParam(launchpadInviteId)}/markAsComplete", "").map(_ => ())
  }

  def startTest(cubiksUserId: Int)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"${url.host}${url.base}/cubiks/$cubiksUserId/start", "").map(_ => ())
  }

  def completeTestByToken(token: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"${url.host}${url.base}/cubiks/complete-by-token/$token", "").map(_ => ())
  }

  def confirmAllocation(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(s"${url.host}${url.base}/allocation-status/confirm/$appId", "").map(_ => ())
  }

  def findAdjustments(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[Adjustments]] = {
    http.GET(s"${url.host}${url.base}/adjustments/$appId").map { response =>
      Some(response.json.as[Adjustments])
    } recover {
      case _: NotFoundException => None
    }
  }

  def considerForSdip(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"${url.host}${url.base}/application/consider-for-sdip/$applicationId", "").map(_ => ())
  }

  def continueAsSdip(userId: UniqueIdentifier, userIdToArchiveWith: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"${url.host}${url.base}/application/continue-as-sdip/$userId/$userIdToArchiveWith", "").map(_ => ())
  }
}

trait TestDataClient {
  this: ApplicationClient =>

  import config.FrontendAppConfig.faststreamConfig._

  def getTestDataGenerator(path: String, queryParams: Map[String, String])(implicit hc: HeaderCarrier): Future[String] = {
    val queryParamString = queryParams.toList.map { item => s"${item._1}=${item._2}" }.mkString("&")
    http.GET(s"${url.host}${url.base}/test-data-generator/$path?$queryParamString").map { response =>
      response.status match {
        case OK => response.body
        case NOT_FOUND => throw new TestDataGeneratorException("There is no such test data generation endpoint")
        case _ => throw new TestDataGeneratorException("There was an error during test data generation")
      }
    }
  }

  sealed class TestDataGeneratorException(message: String) extends Exception(message)
}

object ApplicationClient extends ApplicationClient with TestDataClient {

  override val http: CSRHttp = CSRHttp

  sealed class CannotUpdateRecord extends Exception

  sealed class CannotSubmit extends Exception

  sealed class PersonalDetailsNotFound extends Exception

  sealed class AssistanceDetailsNotFound extends Exception

  sealed class PartnerGraduateProgrammesNotFound extends Exception

  sealed class ApplicationNotFound extends Exception

  sealed class CannotAddReferral extends Exception

  sealed class CannotWithdraw extends Exception

  sealed class OnlineTestNotFound extends Exception

  sealed class PdfReportNotFoundException extends Exception

  sealed class TestForTokenExpiredException extends Exception
}
