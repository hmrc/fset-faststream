/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers

import java.nio.file.{Files, Path}

import config.{FrontendAppConfig, SecurityEnvironment}
import connectors.ApplicationClient.{ApplicationNotFound, CandidateAlreadyHasAnAnalysisExerciseException, OnlineTestNotFound, _}
import connectors.exchange._
import connectors.exchange.candidateevents.CandidateAllocations
import connectors.{ApplicationClient, ReferenceDataClient, SchemeClient, SiftClient}
import helpers.NotificationType._
import helpers.{CachedUserWithSchemeData, NotificationTypeHelper}
import javax.inject.{Inject, Singleton}
import models.ApplicationData.ApplicationStatus
import models._
import models.page._
import play.api.Logger
import play.api.mvc._
import security.ProgressStatusRoleUtils._
import security.RoleUtils._
import security.Roles._
import security.{Roles, SilhouetteComponent}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient,
  refDataClient: ReferenceDataClient,
  siftClient: SiftClient,
  schemeClient: SchemeClient
)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) with CampaignAwareController {

  val appRouteConfigMap: Map[ApplicationRoute.Value, ApplicationRouteState] = config.applicationRoutesFrontend
  val Withdrawer = "Candidate"

  import notificationTypeHelper._

  private lazy val validMSWordContentTypes = List(
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  )

  private lazy val maxAnalysisExerciseFileSizeInBytes = 4096 * 1024
  private lazy val minAnalysisExerciseFileSizeInBytes = 1024

  def present(implicit displaySdipEligibilityInfo: Boolean = false): Action[AnyContent] = CSRSecureAction(ActiveUserRole) {
    implicit request =>
      implicit cachedData =>
        for {
        page <- cachedData.application.map { implicit application =>
          cachedData match {
            case _ if !isSiftEntered && !isSiftReady && !isPhase3TestsPassed && !isAllocatedToAssessmentCentre => {
              dashboardWithOnlineTests.recoverWith(dashboardWithoutOnlineTests)
            }
            case _ => displayPostOnlineTestsDashboard
          }
        }.getOrElse {
          dashboardWithoutApplication
        }
      } yield page
  }

  def resume: Action[AnyContent] = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      Future.successful(Redirect(Roles.userJourneySequence.find(_._1.isAuthorized(user)).map(_._2).getOrElse(routes.HomeController.present())))
  }

  def create: Action[AnyContent] = CSRSecureAction(ApplicationStartRole) { implicit request =>
    implicit cachedData =>
      for {
        response <- applicationClient.findApplication(cachedData.user.userID, FrameworkId).recoverWith {
          case _: ApplicationNotFound => applicationClient.createApplication(cachedData.user.userID, FrameworkId)
        }
        if canApplicationBeSubmitted(response.overriddenSubmissionDeadline)(response.applicationRoute)
      } yield {
        Redirect(routes.PersonalDetailsController.presentAndContinue())
      }
  }

  def confirmAssessmentCentreAllocation(allocationVersion: String, eventId: UniqueIdentifier, sessionId: UniqueIdentifier): Action[AnyContent] =
    CSRSecureAction(ActiveUserRole) { implicit request =>
    implicit cachedData =>
      val alloc = CandidateAllocations.createConfirmed(Some(allocationVersion), cachedData.application.get.applicationId.toString)
      applicationClient.confirmCandidateAllocation(eventId, sessionId, alloc).map { _ =>
        Redirect(routes.HomeController.present()).flashing(success("assessmentCentre.event.confirm.success"))
      }.recover { case _: OptimisticLockException =>
        Redirect(routes.HomeController.present()).flashing(danger("assessmentCentre.event.confirm.optimistic.lock"))
      }
  }

  // If the candidate is fast pass then there will be no P1 data
  private def getPhase1DataIfCandidateIsNotFastPass(implicit application: ApplicationData, cachedData: CachedData,
                                                    request: Request[_], hc: HeaderCarrier) = {
    if (hasFastPassBeenApproved(cachedData)) {
      Future.successful(None)
    } else {
      applicationClient.getPhase1TestProfile2(application.applicationId).map(Some(_))
    }
  }

  private def displayPostOnlineTestsDashboard(implicit application: ApplicationData, cachedData: CachedData,
    request: Request[_], hc: HeaderCarrier) = {
    for {
      allSchemes <- refDataClient.allSchemes()
      schemeStatus <- applicationClient.getCurrentSchemeStatus(application.applicationId)
      siftAnswersStatus <- siftClient.getSiftAnswersStatus(application.applicationId)
      allocationWithEvents <- applicationClient.candidateAllocationEventWithSession(application.applicationId)
      hasWrittenAnalysisExercise <- applicationClient.hasAnalysisExercise(application.applicationId)
      phase3Evaluation <- applicationClient.getPhase3Results(application.applicationId)
      siftEvaluation <- applicationClient.getSiftResults(application.applicationId)
      schemePreferences <- schemeClient.getSchemePreferences(application.applicationId)
      siftState <- applicationClient.getSiftState(application.applicationId)
      phase1TestsWithNames <- getPhase1DataIfCandidateIsNotFastPass
      phase2TestsWithNames <- getPhase2Test
      phase3Tests <- getPhase3Test
    } yield {
      val phase1DataOpt = phase1TestsWithNames.map(Phase1TestsPage(_))
      val phase2DataOpt = phase2TestsWithNames.map(Phase2TestsPage2(_, None))
      val phase3DataOpt = phase3Tests.map(Phase3TestsPage(_, None))
      val page = PostOnlineTestsPage(
        CachedUserWithSchemeData(cachedData.user, application, schemePreferences, allSchemes, phase3Evaluation, siftEvaluation, schemeStatus),
        allocationWithEvents,
        siftAnswersStatus,
        hasWrittenAnalysisExercise,
        allSchemes,
        siftState,
        phase1DataOpt,
        phase2DataOpt,
        phase3DataOpt,
        config.fsacGuideUrl
      )
      Ok(views.html.home.postOnlineTestsDashboard(page))
    }
  }

  protected def getAllBytesInFile(path: Path): Array[Byte] = Files.readAllBytes(path)

  def submitAnalysisExercise(): Action[AnyContent] = CSRSecureAppAction(AssessmentCentreRole) { implicit request =>
    implicit cachedData =>
      val applicationId = cachedData.application.applicationId

      request.asInstanceOf[Request[AnyContent]].body.asMultipartFormData.flatMap { multiPartRequest =>
        multiPartRequest.file("analysisExerciseFile").map {
          case document if document.ref.path.toFile.length() > maxAnalysisExerciseFileSizeInBytes =>
            logger.warn(s"File upload rejected as too large for applicationId $applicationId (Size: ${document.ref.path.toFile.length()})")
            Future.successful(Redirect(routes.HomeController.present()).flashing(
              danger("assessmentCentre.analysisExercise.upload.tooBig")))
          case document if document.ref.path.toFile.length() < minAnalysisExerciseFileSizeInBytes =>
            logger.warn(s"File upload rejected as too small for applicationId $applicationId (Size: ${document.ref.path.toFile.length()})")
            Future.successful(Redirect(routes.HomeController.present()).flashing(
              danger("assessmentCentre.analysisExercise.upload.tooSmall")))
          case document =>
            document.contentType match {
              case Some(contentType) if validMSWordContentTypes.contains(contentType) =>
                logger.warn(s"File upload accepted for applicationId $applicationId (Size: ${document.ref.path.toFile.length()})")
                applicationClient.uploadAnalysisExercise(applicationId, contentType,
                  getAllBytesInFile(document.ref.path)).map { result =>
                  Redirect(routes.HomeController.present()).flashing(
                    success("assessmentCentre.analysisExercise.upload.success"))
                }.recover {
                  case _: CandidateAlreadyHasAnAnalysisExerciseException =>
                    logger.warn(s"A duplicate written exercise submission was attempted " +
                      s"(applicationId = $applicationId)")
                    Redirect(routes.HomeController.present()).flashing(
                      danger("assessmentCentre.analysisExercise.upload.error"))
                }
              case Some(contentType) =>
                logger.warn(s"File upload rejected as wrong content type for applicationId $applicationId" +
                  s" (Size: ${document.ref.path.toFile.length()})")
                Future.successful(
                  Redirect(routes.HomeController.present()).flashing(danger("assessmentCentre.analysisExercise.upload.wrongContentType"))
                )
            }
        }
      }.getOrElse {
        logger.info(s"A malformed file request was submitted as a written exercise " +
          s"(applicationId = $applicationId)")
        Future.successful(Redirect(routes.HomeController.present()).flashing(danger("assessmentCentre.analysisExercise.upload.error")))
      }
  }

  private def dashboardWithOnlineTests(implicit application: ApplicationData,
    displaySdipEligibilityInfo: Boolean,
    cachedData: CachedData, request: Request[_]) = {
    for {
      adjustmentsOpt <- getAdjustments
      assistanceDetailsOpt <- getAssistanceDetails
      phase1TestsWithNames <- applicationClient.getPhase1TestProfile2(application.applicationId)
      phase2TestsWithNames <- getPhase2Test
      phase3Tests <- getPhase3Test
      updatedData <- secEnv.userService.refreshCachedUser(cachedData.user.userID)(hc, request)
    } yield {
      val dashboardPage = DashboardPage(
        updatedData,
        Some(Phase1TestsPage(phase1TestsWithNames)),
        phase2TestsWithNames.map(Phase2TestsPage2(_, adjustmentsOpt)),
        phase3Tests.map(Phase3TestsPage(_, adjustmentsOpt)),
        config.fsacGuideUrl
      )
      Ok(
        views.html.home.dashboard(
          updatedData, dashboardPage, assistanceDetailsOpt,
          adjustmentsOpt, submitApplicationsEnabled = true, displaySdipEligibilityInfo
        )
      )
    }
  }

  private def dashboardWithoutOnlineTests(implicit application: ApplicationData,
    displaySdipEligibilityInfo: Boolean,
    cachedData: CachedData,
    request: Request[_]): PartialFunction[Throwable, Future[Result]] = {
    case e: OnlineTestNotFound =>
      val applicationSubmitted = !cachedData.application.forall { app =>
        app.applicationStatus == ApplicationStatus.CREATED || app.applicationStatus == ApplicationStatus.IN_PROGRESS
      }
      val isDashboardEnabled = canApplicationBeSubmitted(application.overriddenSubmissionDeadline)(application.applicationRoute) ||
        applicationSubmitted
      val dashboardPage = DashboardPage(cachedData, None, None, None, config.fsacGuideUrl)
      Future.successful(Ok(views.html.home.dashboard(cachedData, dashboardPage,
        submitApplicationsEnabled = isDashboardEnabled,
        displaySdipEligibilityInfo = displaySdipEligibilityInfo)))
  }

  private def dashboardWithoutApplication(implicit cachedData: CachedData,
    displaySdipEligibilityInfo: Boolean,
    request: Request[_]) = {
    val dashboardPage = DashboardPage(cachedData, None, None, None, config.fsacGuideUrl)
    Future.successful(
      Ok(views.html.home.dashboard(cachedData, dashboardPage,
        submitApplicationsEnabled = canApplicationBeSubmitted(None),
        displaySdipEligibilityInfo = displaySdipEligibilityInfo))
    )
  }

  private def getPhase2Test(implicit application: ApplicationData, hc: HeaderCarrier) = if (application.isPhase2) {
    applicationClient.getPhase2TestProfile2(application.applicationId).map(Some(_))
  } else {
    Future.successful(None)
  }

  private def getPhase3Test(implicit application: ApplicationData, hc: HeaderCarrier) = if (application.isPhase3) {
    applicationClient.getPhase3TestGroup(application.applicationId).map(Some(_))
  } else {
    Future.successful(None)
  }

  private def getAdjustments(implicit application: ApplicationData, hc: HeaderCarrier) =
    if (application.progress.assistanceDetails) {
      applicationClient.findAdjustments(application.applicationId)
    } else {
      Future.successful(None)
    }

  private def getAssistanceDetails(implicit application: ApplicationData,
    hc: HeaderCarrier, cachedData: CachedData) =
    if (application.progress.assistanceDetails) {
      applicationClient.getAssistanceDetails(cachedData.user.userID, application.applicationId).map(a => Some(a))
    } else {
      Future.successful(None)
    }
}
