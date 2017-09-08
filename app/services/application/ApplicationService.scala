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

package services.application

import common.FutureEx
import connectors.ExchangeObjects
import model.EvaluationResults.Green
import model.Exceptions.{ ApplicationNotFound, LastSchemeWithdrawException, NotFoundException, PassMarkEvaluationNotFound }
import model.ProgressStatuses.ProgressStatus
import model.command.{ WithdrawApplication, WithdrawRequest, WithdrawScheme }
import model.stc.StcEventTypes._
import model.stc.{ AuditEvents, DataStoreEvents, EmailEvents }
import model.exchange.passmarksettings.{ Phase1PassMarkSettings, Phase3PassMarkSettings }
import model.persisted.{ ContactDetails, PassmarkEvaluation, SchemeEvaluationResult }
import model._
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import scheduler.fixer.FixBatch
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.stc.{ EventSink, StcEventService }
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object ApplicationService extends ApplicationService {
  val appRepository = applicationRepository
  val eventService = StcEventService
  val pdRepository = personalDetailsRepository
  val cdRepository = faststreamContactDetailsRepository
  val mediaRepo = mediaRepository
  val schemePrefsRepository = schemePreferencesRepository
  val evaluateP1ResultService = EvaluatePhase1ResultService
  val evaluateP3ResultService = EvaluatePhase3ResultService
  val schemesRepo = SchemeYamlRepository
}

trait ApplicationService extends EventSink with CurrentSchemeStatusHelper {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  def appRepository: GeneralApplicationRepository
  def pdRepository: PersonalDetailsRepository
  def cdRepository: ContactDetailsRepository
  def schemePrefsRepository: SchemePreferencesRepository
  def mediaRepo: MediaRepository
  def evaluateP1ResultService: EvaluateOnlineTestResultService[Phase1PassMarkSettings]
  def evaluateP3ResultService: EvaluateOnlineTestResultService[Phase3PassMarkSettings]
  def schemesRepo: SchemeRepository

  val Candidate_Role = "Candidate"

  def withdraw(applicationId: String, withdrawRequest: WithdrawRequest)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    (for {
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      candidate <- appRepository.find(applicationId).map(_.getOrElse(throw ApplicationNotFound(applicationId)))
      contactDetails <- cdRepository.find(candidate.userId)
    } yield {
      withdrawRequest match {
        case withdrawApp: WithdrawApplication => withdrawFromApplication(applicationId, withdrawApp)(candidate, contactDetails)
        case withdrawScheme: WithdrawScheme =>
          if (withdrawableSchemes(currentSchemeStatus).size == 1) {
            throw LastSchemeWithdrawException(s"Can't withdraw $applicationId from last scheme ${withdrawScheme.schemeId.value}")
          } else {
            withdrawFromScheme(applicationId, withdrawScheme)
          }
      }
    }) flatMap  identity

  }

  private def withdrawableSchemes(currentSchemeStatus: Seq[SchemeEvaluationResult]): Seq[SchemeEvaluationResult] = {
    currentSchemeStatus.filterNot(s => s.result == EvaluationResults.Red.toString || s.result == EvaluationResults.Withdrawn.toString)
  }

  private def withdrawFromApplication(applicationId: String, withdrawRequest: WithdrawApplication)
    (candidate: Candidate, cd: ContactDetails) = {
      appRepository.withdraw(applicationId, withdrawRequest).map { _ =>
        val commonEventList =
            DataStoreEvents.ApplicationWithdrawn(applicationId, withdrawRequest.withdrawer) ::
            AuditEvents.ApplicationWithdrawn(Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)) ::
            Nil
        withdrawRequest.withdrawer match {
          case Candidate_Role => commonEventList
          case _ => EmailEvents.ApplicationWithdrawn(cd.email,
            candidate.preferredName.getOrElse(candidate.firstName.getOrElse(""))) :: commonEventList
        }
      }
  }

  private def withdrawFromScheme(applicationId: String, withdrawRequest: WithdrawScheme) = {

    def buildLatestSchemeStatus(current: Seq[SchemeEvaluationResult], withdrawal: WithdrawScheme)  = {
      calculateCurrentSchemeStatus(current, SchemeEvaluationResult(withdrawRequest.schemeId, EvaluationResults.Withdrawn.toString) :: Nil)
    }

    def maybeProgressToFSAC(schemeStatus: Seq[SchemeEvaluationResult], latestProgressStatus: Option[ProgressStatus]) = {
      val greenSchemes = schemeStatus.collect { case s if s.result == Green.toString => s.schemeId }.toSet
      val shouldProgressToFSAC = latestProgressStatus.getOrElse(ProgressStatuses.CREATED) >= ProgressStatuses.SIFT_READY &&
        greenSchemes subsetOf schemesRepo.nonSiftableSchemeIds.toSet

      if (shouldProgressToFSAC) {
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION).map { _ =>
          AuditEvents.AutoProgressedToFSAC(Map("applicationId" -> applicationId, "reason" -> "last siftable scheme withdrawn")) :: Nil
        }
      } else { Future.successful(Nil) }
    }

    for {
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      latestSchemeStatus = buildLatestSchemeStatus(currentSchemeStatus, withdrawRequest)
      appStatuses <- appRepository.findStatus(applicationId)
      _ <- appRepository.withdrawScheme(applicationId, withdrawRequest, latestSchemeStatus)
      _ <- maybeProgressToFSAC(latestSchemeStatus, appStatuses.latestProgressStatus)
    } yield {
      DataStoreEvents.SchemeWithdrawn(applicationId, withdrawRequest.withdrawer) ::
        AuditEvents.SchemeWithdrawn(Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)) ::
      Nil
    }
  }

  def considerForSdip(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      candidate <- appRepository.find(applicationId).map(_.getOrElse(throw ApplicationNotFound(applicationId)))
      contactDetails <- cdRepository.find(candidate.userId)
      _ <- appRepository.updateApplicationRoute(applicationId, ApplicationRoute.Faststream, ApplicationRoute.SdipFaststream)
      _ <- schemePrefsRepository.add(applicationId, SchemeId("Sdip"))
    } yield {
      List(EmailEvents.ApplicationConvertedToSdip(contactDetails.email, candidate.name))
    }
  }

  def cloneFastStreamAsSdip(userId: String, userIdToArchiveWith: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    def mediaCloningAndSdipAppCreation() = for {
      _ <- mediaRepo.cloneAndArchive(userId, userIdToArchiveWith)
      _ <- appRepository.create(userId, ExchangeObjects.frameworkId, ApplicationRoute.Sdip)
    } yield {}

    (for {
      application <- appRepository.findByUserId(userId, ExchangeObjects.frameworkId)
      _ <- appRepository.archive(application.applicationId, userId, userIdToArchiveWith,
            ExchangeObjects.frameworkId, ApplicationRoute.Faststream)
      _ <- cdRepository.archive(userId, userIdToArchiveWith)
      _ <- mediaCloningAndSdipAppCreation()
    } yield {
    }).recoverWith {
      case (_: ApplicationNotFound | _: NotFoundException) => mediaCloningAndSdipAppCreation()
    }
  }

  def fixDataByRemovingETray(appId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    appRepository.fixDataByRemovingETray(appId)
  }

  def fixDataByRemovingVideoInterviewFailed(appId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    appRepository.fixDataByRemovingVideoInterviewFailed(appId)
  }

  def fixDataByRemovingProgressStatus(appId: String, progressStatusToRemove: String)(implicit hc: HeaderCarrier,
                                                                                     rh: RequestHeader): Future[Unit] = {
    appRepository.fixDataByRemovingProgressStatus(appId, progressStatusToRemove)
  }

  def fix(toBeFixed: Seq[FixBatch])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    FutureEx.traverseSerial(toBeFixed)(fixData).map(_ => ())
  }

  def overrideSubmissionDeadline(applicationId: String, newDeadline: DateTime)(implicit hc: HeaderCarrier): Future[Unit] = {
    appRepository.updateSubmissionDeadline(applicationId, newDeadline)
  }

  def getPassedSchemes(userId: String, frameworkId: String): Future[List[SchemeId]] = {

      val passedSchemes = (_:PassmarkEvaluation).result.filter(result => result.result == Green.toString).map(_.schemeId)

      appRepository.findByUserId(userId, frameworkId).flatMap { appResponse =>
        (appResponse.progressResponse.fastPassAccepted, appResponse.applicationRoute) match {
          case (true, _) => schemePrefsRepository.find(appResponse.applicationId).map(_.schemes)

          case (_, ApplicationRoute.Edip | ApplicationRoute.Sdip) =>
            evaluateP1ResultService.getPassmarkEvaluation(appResponse.applicationId).map(passedSchemes)

          case (_, ApplicationRoute.SdipFaststream) => getSdipFaststreamSchemes(appResponse.applicationId)

          case _ => evaluateP3ResultService.getPassmarkEvaluation(appResponse.applicationId).map(passedSchemes)
        }
      }
  }

  private def getSdipFaststreamSchemes(applicationId: String): Future[List[SchemeId]] = for {
    phase1 <- evaluateP1ResultService.getPassmarkEvaluation(applicationId)
    phase3 <- evaluateP3ResultService.getPassmarkEvaluation(applicationId).recover{
      case _: PassMarkEvaluationNotFound =>
        PassmarkEvaluation(passmarkVersion = "", previousPhasePassMarkVersion = None, result = Nil,
          resultVersion = "", previousPhaseResultVersion = None)
    }
  } yield {
    phase1.result.find(_.schemeId == SchemeId("Sdip")).toList.filter(r => r.result == Green.toString).map(_.schemeId)
  }

  private def fixData(fixType: FixBatch)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      toFix <- appRepository.getApplicationsToFix(fixType)
      fixed <- FutureEx.traverseToTry(toFix)(candidate => appRepository.fix(candidate, fixType))
    } yield toEvents(fixed, fixType)
  }

  private def toEvents(seq: Seq[Try[Option[Candidate]]], fixBatch: FixBatch): StcEvents = {
    seq.flatMap {
      case Success(Some(app)) => Some(AuditEvents.FixedProdData(Map("issue" -> fixBatch.fix.name,
        "applicationId" -> app.applicationId.getOrElse(""),
        "email" -> app.email.getOrElse(""),
        "applicationRoute" -> app.applicationRoute.getOrElse("").toString)))
      case Success(None) => None
      case Failure(e) =>
        Logger.error(s"Failed to update ${fixBatch.fix.name}", e)
        None
    }.toList
  }
}
