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

package connectors

import java.net.URLEncoder

import config.CSRHttp
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.GeneralDetails._
import connectors.exchange.Questionnaire._
import connectors.exchange._
import connectors.exchange.campaignmanagement.AfterDeadlineSignupCodeUnused
import connectors.exchange.candidateevents.{ CandidateAllocationWithEvent, CandidateAllocations }
import connectors.exchange.candidatescores.CompetencyAverageResult
import connectors.exchange.sift.SiftState
import models.events.EventType.EventType
import models.{ Adjustments, ApplicationRoute, UniqueIdentifier }
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ BadRequestException, HeaderCarrier, HttpResponse, NotFoundException, Upstream4xxResponse }

// scalastyle:off number.of.methods
trait ApplicationClient {

  val http: CSRHttp

  import ApplicationClient._
  import config.FrontendAppConfig.faststreamConfig._

  val apiBaseUrl = url.host + url.base

  def afterDeadlineSignupCodeUnusedAndValid(afterDeadlineSignupCode: String)(implicit hc: HeaderCarrier)
  : Future[AfterDeadlineSignupCodeUnused] = {
    http.GET(s"$apiBaseUrl/campaign-management/afterDeadlineSignupCodeUnusedAndValid",
      Seq("code" -> afterDeadlineSignupCode)).map { response =>
      response.json.as[AfterDeadlineSignupCodeUnused]
    }
  }

  def createApplication(userId: UniqueIdentifier, frameworkId: String,
    applicationRoute: ApplicationRoute.ApplicationRoute = ApplicationRoute.Faststream)
    (implicit hc: HeaderCarrier) = {
    http.PUT(s"$apiBaseUrl/application/create", CreateApplicationRequest(userId,
      frameworkId, applicationRoute)).map { response =>
      response.json.as[ApplicationResponse]
    }
  }

  def overrideSubmissionDeadline(
                                  application: UniqueIdentifier,
                                  overrideRequest: OverrideSubmissionDeadlineRequest
                                )(implicit hc: HeaderCarrier): Future[Unit] =
    http.PUT(s"$apiBaseUrl/application/overrideSubmissionDeadline/$application", overrideRequest).map { response =>
      if (response.status != OK) {
        throw new CannotSubmitOverriddenSubmissionDeadline()
      }
    }

  def markSignupCodeAsUsed(
    code: String,
    applicationId: UniqueIdentifier
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    http.GET(s"$apiBaseUrl/application/markSignupCodeAsUsed", Seq(
      "code" -> code,
      "applicationId" -> applicationId.toString
    )).map { response =>
      if (response.status != OK) {
        throw new CannotMarkSignupCodeAsUsed(applicationId.toString, code)
      }
    }
  }

  def submitApplication(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(s"$apiBaseUrl/application/submit/$userId/$applicationId", Json.toJson("")).map {
      case x: HttpResponse if x.status == OK => ()
    }.recover {
      case _: BadRequestException => throw new CannotSubmit()
    }
  }

  def withdrawApplication(applicationId: UniqueIdentifier, reason: WithdrawApplication)(implicit hc: HeaderCarrier) = {
    http.PUT(s"$apiBaseUrl/application/withdraw/$applicationId", Json.toJson(reason)).map {
      case x: HttpResponse if x.status == OK => ()
    }.recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == FORBIDDEN => throw new SiftExpired()
      case _: NotFoundException => throw new CannotWithdraw()
    }
  }

  def withdrawScheme(applicationId: UniqueIdentifier, withdrawal: WithdrawScheme)(implicit hc: HeaderCarrier) = {
    http.PUT(s"$apiBaseUrl/application/$applicationId/scheme/withdraw", Json.toJson(withdrawal)).map (_ => ())
      .recover {
        case e: Upstream4xxResponse if e.upstreamResponseCode == FORBIDDEN => throw new SiftExpired()
        case _: Exception => throw new CannotWithdraw()
      }
  }

  def addReferral(userId: UniqueIdentifier, referral: String)(implicit hc: HeaderCarrier) = {
    http.PUT(s"$apiBaseUrl/media/create", AddReferral(userId, referral)).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotAddReferral()
    }
  }

  def getApplicationProgress(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"$apiBaseUrl/application/progress/$applicationId").map { response =>
      response.json.as[ProgressResponse]
    }
  }

  def findApplication(userId: UniqueIdentifier, frameworkId: String)(implicit hc: HeaderCarrier): Future[ApplicationResponse] = {
    http.GET(s"$apiBaseUrl/application/find/user/$userId/framework/$frameworkId").map { response =>
      response.json.as[ApplicationResponse]
    } recover {
      case _: NotFoundException => throw new ApplicationNotFound()
    }
  }

  def findFsacEvaluationAverages(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[CompetencyAverageResult] = {
    http.GET(s"$apiBaseUrl/application/$applicationId/fsacEvaluationAverages").map { response =>
      response.json.as[CompetencyAverageResult]
    } recover {
      case _: NotFoundException =>
        val msg = s"Found no fsac evaluation averages for application id: $applicationId"
        throw new FsacEvaluatedAverageScoresNotFound(msg)
    }
  }

  def updatePersonalDetails(applicationId: UniqueIdentifier, userId: UniqueIdentifier, personalDetails: GeneralDetails)
                           (implicit hc: HeaderCarrier) = {
    http.POST(
      s"$apiBaseUrl/personal-details/$userId/$applicationId",
      personalDetails
    ).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def getPersonalDetails(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"$apiBaseUrl/personal-details/$userId/$applicationId").map { response =>
      response.json.as[GeneralDetails]
    } recover {
      case e: NotFoundException => throw new PersonalDetailsNotFound()
    }
  }

  def updateAssistanceDetails(applicationId: UniqueIdentifier, userId: UniqueIdentifier, assistanceDetails: AssistanceDetails)
                             (implicit hc: HeaderCarrier) = {
    http.PUT(s"$apiBaseUrl/assistance-details/$userId/$applicationId", assistanceDetails).map {
      case x: HttpResponse if x.status == CREATED => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def getAssistanceDetails(userId: UniqueIdentifier, applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(s"$apiBaseUrl/assistance-details/$userId/$applicationId").map { response =>
      response.json.as[connectors.exchange.AssistanceDetails]
    } recover {
      case _: NotFoundException => throw new AssistanceDetailsNotFound()
    }
  }

  def updateQuestionnaire(applicationId: UniqueIdentifier, sectionId: String, questionnaire: Questionnaire)
                         (implicit hc: HeaderCarrier) = {
    http.PUT(s"$apiBaseUrl/questionnaire/$applicationId/$sectionId", questionnaire).map {
      case x: HttpResponse if x.status == ACCEPTED => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def updatePreview(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"$apiBaseUrl/application/preview/$applicationId",
      PreviewRequest(true)
    ).map {
      case x: HttpResponse if x.status == OK => ()
    } recover {
      case _: BadRequestException => throw new CannotUpdateRecord()
    }
  }

  def verifyInvigilatedToken(email: String, token: String)(implicit hc: HeaderCarrier): Future[InvigilatedTestUrl] =
    http.POST(s"$apiBaseUrl/online-test/phase2/verifyAccessCode", VerifyInvigilatedTokenUrlRequest(email.toLowerCase, token)).map {
      (resp: HttpResponse) => {
        resp.json.as[InvigilatedTestUrl]
      }
    }.recover {
      case e: NotFoundException => throw new TokenEmailPairInvalidException()
      case Upstream4xxResponse(_, FORBIDDEN, _, _) => throw new TestForTokenExpiredException()
    }

  // psi code start

  def getPhase1TestProfile2(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase1TestGroupWithNames2] = {
    http.GET(s"$apiBaseUrl/online-test/psi/phase1/candidate/$appId").map { response =>
      response.json.as[Phase1TestGroupWithNames2]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def completeTestByOrderId(orderId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/psi/complete-by-orderId/$orderId", "").map(_ => ())
  }

  // psi code end

  def getPhase1TestProfile(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase1TestGroupWithNames] = {
    http.GET(s"$apiBaseUrl/online-test/phase1/candidate/$appId").map { response =>
      response.json.as[Phase1TestGroupWithNames]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def getPhase2TestProfile(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase2TestGroupWithActiveTest] = {
    http.GET(s"$apiBaseUrl/online-test/phase2/candidate/$appId").map { response =>
      response.json.as[Phase2TestGroupWithActiveTest]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def getPhase3TestGroup(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase3TestGroup] = {
    http.GET(s"$apiBaseUrl/phase3-test-group/$appId").map { response =>
      response.json.as[Phase3TestGroup]
    } recover {
      case _: NotFoundException => throw new OnlineTestNotFound()
    }
  }

  def getPhase3Results(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[List[SchemeEvaluationResult]]] = {
    http.GET(s"$apiBaseUrl/application/$appId/phase3/results").map { response =>
      Some(response.json.as[List[SchemeEvaluationResult]])
    } recover {
      case _: NotFoundException => None
    }
  }

  def getSiftTestGroup(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[SiftTestGroupWithActiveTest] = {
    http.GET(s"$apiBaseUrl/sift-test-group/$appId").map { response =>
      response.json.as[SiftTestGroupWithActiveTest]
    } recover {
      case _: NotFoundException => throw new SiftTestNotFound(s"No sift test group found for $appId")
    }
  }

  def getSiftState(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[SiftState]] = {
    http.GET(s"$apiBaseUrl/sift-candidate/state/$appId").map { response =>
      Some(response.json.as[SiftState])
    } recover {
      case _: NotFoundException => None
    }
  }

  def getSiftResults(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[List[SchemeEvaluationResult]]] = {
    http.GET(s"$apiBaseUrl/application/$appId/sift/results").map { response =>
      Some(response.json.as[List[SchemeEvaluationResult]])
    } recover {
      case _: NotFoundException => None
    }
  }

  def getCurrentSchemeStatus(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Seq[SchemeEvaluationResultWithFailureDetails]] = {
    http.GET(s"${url.host}${url.base}/application/$appId/currentSchemeStatus").map { response =>
      response.json.as[Seq[SchemeEvaluationResultWithFailureDetails]]
    }
  }

  private def encodeUrlParam(str: String) = URLEncoder.encode(str, "UTF-8")

  def startPhase3TestByToken(launchpadInviteId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/launchpad/${encodeUrlParam(launchpadInviteId)}/markAsStarted", "").map(_ => ())
  }

  def completePhase3TestByToken(launchpadInviteId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/launchpad/${encodeUrlParam(launchpadInviteId)}/markAsComplete", "").map(_ => ())
  }

  def startTest(cubiksUserId: Int)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/cubiks/$cubiksUserId/start", "").map(_ => ())
  }

  def startSiftTest(cubiksUserId: Int)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/sift-test/$cubiksUserId/start", "").map(_ => ())
  }

  def completeTestByToken(token: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/cubiks/complete-by-token/$token", "").map(_ => ())
  }

  def confirmAllocation(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(s"$apiBaseUrl/allocation-status/confirm/$appId", "").map(_ => ())
  }

  def candidateAllocationEventWithSession(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[List[CandidateAllocationWithEvent]] = {
    http.GET(
      s"$apiBaseUrl/candidate-allocations/sessions/findByApplicationId",
      Seq("applicationId" -> appId.toString)
    ).map( _.json.as[List[CandidateAllocationWithEvent]])
  }

  def confirmCandidateAllocation(
    eventId: UniqueIdentifier,
    sessionId: UniqueIdentifier,
    candidateAllocations: CandidateAllocations
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/candidate-allocations/confirm-allocation/events/$eventId/sessions/$sessionId",
      Json.toJson(candidateAllocations)).map(_ => ()).recover {
      case Upstream4xxResponse(_, CONFLICT, _, _) =>
        throw new OptimisticLockException(s"Candidate allocation for event $eventId has changed.")
    }
  }

  def hasAnalysisExercise(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Boolean] = {
    http.GET(
      s"$apiBaseUrl/application/hasAnalysisExercise", Seq("applicationId" -> applicationId.toString)
    ).map { response =>
      response.json.as[Boolean]
    }
  }

  def findAdjustments(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[Adjustments]] = {
    http.GET(s"$apiBaseUrl/adjustments/$appId").map { response =>
      Some(response.json.as[Adjustments])
    } recover {
      case _: NotFoundException => None
    }
  }

  def considerForSdip(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/application/consider-for-sdip/$applicationId", "").map(_ => ())
  }

  def continueAsSdip(userId: UniqueIdentifier, userIdToArchiveWith: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/application/continue-as-sdip/$userId/$userIdToArchiveWith", "").map(_ => ())
  }

  def uploadAnalysisExercise(applicationId: UniqueIdentifier,
    contentType: String, fileContents: Array[Byte])(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POSTBinary(
      s"$apiBaseUrl/application/uploadAnalysisExercise?applicationId=$applicationId&contentType=$contentType", fileContents
    ).map { _=> () }.recover {
      case response: Upstream4xxResponse if response.upstreamResponseCode == CONFLICT =>
          throw new CandidateAlreadyHasAnAnalysisExerciseException
      }
  }
}
// scalastyle:on

trait TestDataClient {
  val http: CSRHttp

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

object TestDataClient extends TestDataClient {
  override val http: CSRHttp = CSRHttp
}

object ApplicationClient extends ApplicationClient with TestDataClient {

  override val http: CSRHttp = CSRHttp

  sealed class CannotUpdateRecord extends Exception

  sealed class CannotSubmit extends Exception

  sealed class CannotSubmitOverriddenSubmissionDeadline extends Exception

  sealed class CannotMarkSignupCodeAsUsed(applicationId: String, code: String) extends Exception

  sealed class PersonalDetailsNotFound extends Exception

  sealed class AssistanceDetailsNotFound extends Exception

  sealed class ApplicationNotFound extends Exception

  sealed class CannotAddReferral extends Exception

  sealed class CannotWithdraw extends Exception

  sealed class OnlineTestNotFound extends Exception

  sealed class PdfReportNotFoundException extends Exception

  sealed class SiftAnswersNotFound extends Exception

  sealed class SiftExpired extends Exception

  sealed class SchemeSpecificAnswerNotFound extends Exception

  sealed class SiftAnswersIncomplete extends Exception

  sealed class SiftAnswersSubmitted extends Exception

  sealed class SiftTestNotFound(m: String) extends Exception(m)

  sealed class TestForTokenExpiredException extends Exception

  sealed class CandidateAlreadyHasAnAnalysisExerciseException extends Exception

  sealed class OptimisticLockException(m: String) extends Exception(m)

  sealed class FsacEvaluatedAverageScoresNotFound(m: String) extends Exception(m)
}
