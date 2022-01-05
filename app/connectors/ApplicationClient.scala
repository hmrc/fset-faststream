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

import java.net.URLEncoder

import config.{ CSRHttp, FrontendAppConfig }
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.GeneralDetails._
import connectors.exchange.Questionnaire._
import connectors.exchange._
import connectors.exchange.campaignmanagement.AfterDeadlineSignupCodeUnused
import connectors.exchange.candidateevents.{ CandidateAllocationWithEvent, CandidateAllocations }
import connectors.exchange.candidatescores.CompetencyAverageResult
import connectors.exchange.sift.SiftState
import javax.inject.{ Inject, Singleton }
import models.{ Adjustments, ApplicationRoute, UniqueIdentifier }
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http._

import scala.concurrent.{ ExecutionContext, Future }

// scalastyle:off number.of.methods
@Singleton
class ApplicationClient @Inject() (config: FrontendAppConfig, http: CSRHttp)(implicit ec: ExecutionContext)
  extends TestDataGeneratorClient(config, http) {

  import ApplicationClient._

  val apiBaseUrl = config.faststreamBackendConfig.url.host + config.faststreamBackendConfig.url.base

  def afterDeadlineSignupCodeUnusedAndValid(afterDeadlineSignupCode: String)(implicit hc: HeaderCarrier)
  : Future[AfterDeadlineSignupCodeUnused] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[AfterDeadlineSignupCodeUnused](s"$apiBaseUrl/campaign-management/afterDeadlineSignupCodeUnusedAndValid",
      Seq("code" -> afterDeadlineSignupCode))
  }

  def createApplication(userId: UniqueIdentifier, frameworkId: String,
    applicationRoute: ApplicationRoute.ApplicationRoute = ApplicationRoute.Faststream)
    (implicit hc: HeaderCarrier) = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.PUT[CreateApplicationRequest, ApplicationResponse](s"$apiBaseUrl/application/create", CreateApplicationRequest(userId,
      frameworkId, applicationRoute))
  }

  def overrideSubmissionDeadline(
    applicationId: UniqueIdentifier,
    overrideRequest: OverrideSubmissionDeadlineRequest
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.PUT[OverrideSubmissionDeadlineRequest, HttpResponse](
      s"$apiBaseUrl/application/overrideSubmissionDeadline/$applicationId",
      overrideRequest).map { response =>
        if (response.status != OK) {
          throw new CannotSubmitOverriddenSubmissionDeadline()
        } else {
          ()
        }
      }
  }

  def markSignupCodeAsUsed(
    code: String,
    applicationId: UniqueIdentifier
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[HttpResponse](s"$apiBaseUrl/application/markSignupCodeAsUsed", Seq(
      "code" -> code,
      "applicationId" -> applicationId.toString
    )).map { response =>
      if (response.status != OK) {
        throw new CannotMarkSignupCodeAsUsed(applicationId.toString, code)
      } else {
        ()
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
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.PUT[WithdrawApplication, Either[UpstreamErrorResponse, Unit]](s"$apiBaseUrl/application/withdraw/$applicationId", reason).map {
      case Right(_) => ()
      case Left(notFoundEx) if notFoundEx.statusCode == NOT_FOUND => throw new CannotWithdraw()
      case Left(ex) => throw ex
    }
  }

  def withdrawScheme(applicationId: UniqueIdentifier, withdrawal: WithdrawScheme)(implicit hc: HeaderCarrier) = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.PUT[WithdrawScheme, Either[UpstreamErrorResponse, Unit]](s"$apiBaseUrl/application/$applicationId/scheme/withdraw", withdrawal)
      .map {
        case Right(_) => ()
        case Left(forbiddenEx) if forbiddenEx.statusCode == FORBIDDEN => throw new SiftExpired()
        case Left(_) => throw new CannotWithdraw()
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
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[ProgressResponse](s"$apiBaseUrl/application/progress/$applicationId")
  }

  def findApplication(userId: UniqueIdentifier, frameworkId: String)(implicit hc: HeaderCarrier): Future[ApplicationResponse] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[ApplicationResponse](s"$apiBaseUrl/application/find/user/$userId/framework/$frameworkId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new ApplicationNotFound()
    }
  }

  def findFsacEvaluationAverages(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[CompetencyAverageResult] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[CompetencyAverageResult](s"$apiBaseUrl/application/$applicationId/fsacEvaluationAverages").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND =>
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
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[GeneralDetails](s"$apiBaseUrl/personal-details/$userId/$applicationId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new PersonalDetailsNotFound()
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
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[connectors.exchange.AssistanceDetails](s"$apiBaseUrl/assistance-details/$userId/$applicationId")
      .recover {
        case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new AssistanceDetailsNotFound()
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

  def verifyInvigilatedToken(email: String, token: String)(implicit hc: HeaderCarrier): Future[InvigilatedTestUrl] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.POST[VerifyInvigilatedTokenUrlRequest, InvigilatedTestUrl](
      s"$apiBaseUrl/online-test/phase2/verifyAccessCode",
      VerifyInvigilatedTokenUrlRequest(email.toLowerCase, token))
      .recover {
        case notFoundEx: UpstreamErrorResponse if notFoundEx.statusCode == NOT_FOUND => throw new TokenEmailPairInvalidException()
        case forbidenEx: UpstreamErrorResponse if forbidenEx.statusCode == FORBIDDEN => throw new TestForTokenExpiredException()
      }
  }

  // psi code start

  def getPhase1Tests(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Seq[PsiTest]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Seq[PsiTest]](s"$apiBaseUrl/phase1-tests/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
    }
  }

  def getPhase1TestProfile2(appId: UniqueIdentifier)
                           (implicit hc: HeaderCarrier): Future[Phase1TestGroupWithNames2] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Phase1TestGroupWithNames2](s"$apiBaseUrl/online-test/psi/phase1/candidate/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
    }
  }

  def getPhase2TestProfile2(appId: UniqueIdentifier)
                           (implicit hc: HeaderCarrier): Future[Phase2TestGroupWithActiveTest2] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Phase2TestGroupWithActiveTest2](s"$apiBaseUrl/online-test/phase2/candidate/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
    }
  }

  def getPhase1TestGroupWithNames2ByOrderId(orderId: UniqueIdentifier)
                           (implicit hc: HeaderCarrier): Future[Phase1TestGroupWithNames2] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Phase1TestGroupWithNames2](s"$apiBaseUrl/online-test/phase1/candidate/orderId/$orderId")
      .recover {
        case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
      }
  }

  def getPhase2TestProfile2ByOrderId(orderId: UniqueIdentifier)
                           (implicit hc: HeaderCarrier): Future[Phase2TestGroupWithActiveTest2] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Phase2TestGroupWithActiveTest2](s"$apiBaseUrl/online-test/phase2/candidate/orderId/$orderId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
    }
  }

  def completeTestByOrderId(orderId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/psi/$orderId/complete", "").map(_ => ())
  }

  // psi code end

  def getPhase1TestProfile(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase1TestGroupWithNames] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Phase1TestGroupWithNames](s"$apiBaseUrl/online-test/phase1/candidate/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
    }
  }

  def getPhase2TestProfile(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase2TestGroupWithActiveTest] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Phase2TestGroupWithActiveTest](s"$apiBaseUrl/online-test/phase2/candidate/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
    }
  }

  def getPhase3TestGroup(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Phase3TestGroup] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Phase3TestGroup](s"$apiBaseUrl/phase3-test-group/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new OnlineTestNotFound()
    }
  }

  def getPhase3Results(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[List[SchemeEvaluationResult]]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Option[List[SchemeEvaluationResult]]](s"$apiBaseUrl/application/$appId/phase3/results")
  }

  def getSiftTestGroup(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[SiftTestGroupWithActiveTest] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[SiftTestGroupWithActiveTest](s"$apiBaseUrl/sift-test-group/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new SiftTestNotFound(s"No sift test group found for $appId")
    }
  }

  def getSiftTestGroup2(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[SiftTestGroupWithActiveTest2] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[SiftTestGroupWithActiveTest2](s"$apiBaseUrl/psi/sift-test-group/$appId").recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new SiftTestNotFound(s"No sift test group found for $appId")
    }
  }

  def getSiftState(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[SiftState]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Option[SiftState]](s"$apiBaseUrl/sift-candidate/state/$appId")
  }

  def getSiftResults(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[List[SchemeEvaluationResult]]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Option[List[SchemeEvaluationResult]]](s"$apiBaseUrl/application/$appId/sift/results")
  }

  def getCurrentSchemeStatus(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Seq[SchemeEvaluationResultWithFailureDetails]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Seq[SchemeEvaluationResultWithFailureDetails]](s"${url.host}${url.base}/application/$appId/currentSchemeStatus")
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

  def startTest(orderId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/psi/$orderId/start", "").map(_ => ())
  }

  def startSiftTest(cubiksUserId: Int)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/sift-test/$cubiksUserId/start", "").map(_ => ())
  }

  def startSiftTest(orderId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/psi/sift-test/$orderId/start", "").map(_ => ())
  }

  def completeTestByToken(token: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(s"$apiBaseUrl/cubiks/complete-by-token/$token", "").map(_ => ())
  }

  def confirmAllocation(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST(s"$apiBaseUrl/allocation-status/confirm/$appId", "").map(_ => ())
  }

  def candidateAllocationEventWithSession(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[List[CandidateAllocationWithEvent]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[List[CandidateAllocationWithEvent]](
      s"$apiBaseUrl/candidate-allocations/sessions/findByApplicationId",
      Seq("applicationId" -> appId.toString)
    )
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
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Boolean](
      s"$apiBaseUrl/application/hasAnalysisExercise", Seq("applicationId" -> applicationId.toString)
    )
  }

  def findAdjustments(appId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Option[Adjustments]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[Option[Adjustments]](s"$apiBaseUrl/adjustments/$appId")
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

object ApplicationClient {
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
