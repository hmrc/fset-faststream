/*
 * Copyright 2023 HM Revenue & Customs
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
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{Amber, Green, Red, Withdrawn}
import model.Exceptions.*
import model.ProgressStatuses.*
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType.*
import model.command.*
import model.exchange.SchemeEvaluationResultWithFailureDetails
import model.exchange.passmarksettings.{Phase1PassMarkSettingsPersistence, Phase2PassMarkSettingsPersistence, Phase3PassMarkSettingsPersistence}
import model.exchange.sift.SiftAnswersStatus
import model.persisted.*
import model.persisted.eventschedules.EventType
import model.stc.StcEventTypes.*
import model.stc.{AuditEvents, DataStoreEvents, EmailEvents}
import model.*
import model.AllocationStatuses.AllocationStatus
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.*
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.onlinetesting.*
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import repositories.sift.ApplicationSiftRepository
import scheduler.fixer.FixBatch
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.allocation.CandidateAllocationService
import services.application.ApplicationService.{InvalidSchemeException, NoChangeInCurrentSchemeStatusException}
import services.events.EventsService
import services.sift.{ApplicationSiftService, SiftAnswersService}
import services.stc.{EventSink, StcEventService}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.OffsetDateTime
import javax.inject.{Inject, Named, Singleton}
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object ApplicationService {

  case class NoChangeInCurrentSchemeStatusException(applicationId: String,
                                                    currentSchemeStatus: Seq[SchemeEvaluationResult],
                                                    newSchemeStatus: Seq[SchemeEvaluationResult]) extends
    Exception(s"No change in CSS after updating $applicationId. CSS before:$currentSchemeStatus, CSS after:$newSchemeStatus")

  case class InvalidSchemeException(message: String) extends Exception(message)
}

// scalastyle:off number.of.methods file.size.limit
@Singleton
class ApplicationService @Inject() (appRepository: GeneralApplicationRepository,
                                    pdRepository: PersonalDetailsRepository,
                                    cdRepository: ContactDetailsRepository,
                                    schemePrefsRepository: SchemePreferencesRepository,
                                    mediaRepo: MediaRepository,

                                    @Named("Phase1EvaluationService")
                                    evaluateP1ResultService: EvaluateOnlineTestResultService[Phase1PassMarkSettingsPersistence],
                                    @Named("Phase2EvaluationService")
                                    evaluateP2ResultService: EvaluateOnlineTestResultService[Phase2PassMarkSettingsPersistence],
                                    @Named("Phase3EvaluationService")
                                    evaluateP3ResultService: EvaluateOnlineTestResultService[Phase3PassMarkSettingsPersistence],

                                    @Named("Phase1EvaluationRepository") phase1EvaluationRepository: OnlineTestEvaluationRepository,
                                    @Named("Phase2EvaluationRepository") phase2EvaluationRepository: OnlineTestEvaluationRepository,
                                    @Named("Phase3EvaluationRepository") phase3EvaluationRepository: OnlineTestEvaluationRepository,

                                    siftService: ApplicationSiftService,
                                    siftAnswersService: SiftAnswersService,
                                    schemesRepo: SchemeRepository,
                                    phase1TestRepository: Phase1TestRepository,
                                    phase2TestRepository: Phase2TestRepository,
                                    phase3TestRepository: Phase3TestRepository,
                                    appSiftRepository: ApplicationSiftRepository,
                                    fsacRepo: AssessmentCentreRepository,
                                    val eventsService: EventsService,
                                    fsbRepo: FsbRepository,
                                    civilServiceExperienceDetailsRepo: CivilServiceExperienceDetailsRepository,
                                    candidateAllocationService: CandidateAllocationService,
                                    assistanceDetailsRepo: AssistanceDetailsRepository,
                                    assessorAssessmentScoresRepository: AssessorAssessmentScoresMongoRepository,
                                    reviewerAssessmentScoresRepository: ReviewerAssessmentScoresMongoRepository,
                                    val eventService: StcEventService
                                   )(implicit ec: ExecutionContext) extends EventSink with CurrentSchemeStatusHelper with Logging with Schemes {

  val Candidate_Role = "Candidate"

  def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]] = appRepository.getCurrentSchemeStatus(applicationId)

  def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatus): Future[Unit] = {
    appRepository.addProgressStatusAndUpdateAppStatus(applicationId, progressStatus)
  }

  private def removeFromAllEvents(applicationId: String, eligibleForReallocation: Boolean)
                                 (implicit hc: HeaderCarrier): Future[Unit] = {
    candidateAllocationService.allocationsForApplication(applicationId).flatMap { allocations =>
      candidateAllocationService.unAllocateCandidates(allocations.toList, eligibleForReallocation).map(_ => ())
    }
  }

  def considerForSdip(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      candidate <- appRepository.find(applicationId).map(_.getOrElse(throw ApplicationNotFound(applicationId)))
      contactDetails <- cdRepository.find(candidate.userId)
      _ <- appRepository.updateApplicationRoute(applicationId, ApplicationRoute.Faststream, ApplicationRoute.SdipFaststream)
      _ <- schemePrefsRepository.add(applicationId, Sdip)
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
      case _: ApplicationNotFound | _: NotFoundException => mediaCloningAndSdipAppCreation()
    }
  }

  def undoFullWithdraw(applicationId: String, newApplicationStatus: ApplicationStatus): Future[Unit] = {
    for {
      candidateOpt <- appRepository.find(applicationId)
      _ = candidateOpt.getOrElse(throw ApplicationNotFound(applicationId))
      _ = if (!candidateOpt.exists(_.applicationStatus.contains(WITHDRAWN.key))) {
        throw CandidateInIncorrectState(s"Candidate $applicationId does not have applicationStatus of WITHDRAWN")
      }
      _ <- appRepository.removeProgressStatuses(applicationId, List(ProgressStatuses.WITHDRAWN))
      _ <- appRepository.removeWithdrawReason(applicationId)
      _ <- appRepository.updateStatus(applicationId, newApplicationStatus)
    } yield ()
  }

  def updateApplicationStatus(applicationId: String, newApplicationStatus: ApplicationStatus): Future[Unit] = {
    for {
      application <- appRepository.find(applicationId)
      _ = application.getOrElse(throw ApplicationNotFound(applicationId))
      _ <- appRepository.updateStatus(applicationId, newApplicationStatus)
    } yield ()
  }

  def fixDataByRemovingETray(appId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      application <- appRepository.find(appId)
      _ = application.getOrElse(throw ApplicationNotFound(appId))
      _ <- appRepository.fixDataByRemovingETray(appId)
    } yield ()
  }

  def fixDataByRemovingVideoInterviewFailed(appId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      application <- appRepository.find(appId)
      _ = application.getOrElse(throw ApplicationNotFound(appId))
      applicationStatusDetails <- appRepository.findStatus(appId)
      _ = if (!applicationStatusDetails.latestProgressStatus.contains(ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED)) {
        throw CannotUpdateRecord(appId)
      }
      _ <- appRepository.fixDataByRemovingVideoInterviewFailed(appId)
    } yield ()
  }

  def fixDataByRemovingProgressStatus(appId: String, progressStatusToRemove: String)(implicit hc: HeaderCarrier,
                                                                                     rh: RequestHeader): Future[Unit] = {
    for {
      application <- appRepository.find(appId)
      _ = application.getOrElse(throw ApplicationNotFound(appId))
      _ <- appRepository.fixDataByRemovingProgressStatus(appId, progressStatusToRemove)
    } yield ()
  }

  def fix(toBeFixed: Seq[FixBatch])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    FutureEx.traverseSerial(toBeFixed)(fixData).map(_ => ())
  }

  def overrideSubmissionDeadline(applicationId: String, newDeadline: OffsetDateTime)(implicit hc: HeaderCarrier): Future[Unit] = {
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

  def rollbackToPhase1ResultsReceivedFromPhase1FailedNotified(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED,
      ProgressStatuses.PHASE1_TESTS_FAILED)
    rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE1_TESTS, statuses)
  }

  def rollbackToPhase2ResultsReceivedFromPhase2FailedNotified(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED,
      ProgressStatuses.PHASE2_TESTS_FAILED)
    rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE2_TESTS, statuses)
  }

  def rollbackToSubmittedFromOnlineTestsExpired(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.PHASE1_TESTS_INVITED,
      ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE1_TESTS_STARTED,
      ProgressStatuses.PHASE1_TESTS_EXPIRED
    )
    rollbackAppAndProgressStatus(applicationId, ApplicationStatus.SUBMITTED, statuses)
  }

  def rollbackToInProgressFromFastPassAccepted(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.FAST_PASS_ACCEPTED
    )
    rollbackAppAndProgressStatus(applicationId, ApplicationStatus.IN_PROGRESS, statuses)
  }

  def rollbackFastPassFromFsacToSubmitted(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.FAST_PASS_ACCEPTED,
      ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION,
      ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
      ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED
    )

    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.SUBMITTED, statuses)
      civilServiceDetails <- civilServiceExperienceDetailsRepo.find(applicationId)
      updatedCivilServiceDetails = civilServiceDetails
        .map(_.copy(fastPassAccepted = None)).getOrElse(throw UnexpectedException("Civil Service Details not found"))
      _ <- civilServiceExperienceDetailsRepo.update(applicationId, updatedCivilServiceDetails)
      allocations <- candidateAllocationService.allocationsForApplication(applicationId)
      _ <- candidateAllocationService.unAllocateCandidates(allocations.toList)
    } yield ()
  }

  private def allOnlineTestsPhases: Seq[ProgressStatus] = {
    Seq(
      ProgressStatuses.PHASE1_TESTS_INVITED,
      ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE1_TESTS_STARTED,
      ProgressStatuses.PHASE1_TESTS_COMPLETED,
      ProgressStatuses.PHASE1_TESTS_RESULTS_READY,
      ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE1_TESTS_PASSED,
      ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED,
      ProgressStatuses.PHASE1_TESTS_FAILED,
      ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_AMBER,
      ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN,
      ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED,
      ProgressStatuses.PHASE1_TESTS_EXPIRED,

      ProgressStatuses.PHASE2_TESTS_INVITED,
      ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE2_TESTS_STARTED,
      ProgressStatuses.PHASE2_TESTS_COMPLETED,
      ProgressStatuses.PHASE2_TESTS_RESULTS_READY,
      ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE2_TESTS_PASSED,
      ProgressStatuses.PHASE2_TESTS_PASSED,
      ProgressStatuses.PHASE2_TESTS_FAILED,
      ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_AMBER,
      ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN,
      ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED,
      ProgressStatuses.PHASE2_TESTS_EXPIRED,

      ProgressStatuses.PHASE3_TESTS_INVITED,
      ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE3_TESTS_STARTED,
      ProgressStatuses.PHASE3_TESTS_COMPLETED,
      ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE3_TESTS_PASSED,
      ProgressStatuses.PHASE3_TESTS_PASSED_WITH_AMBER,
      ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED,
      ProgressStatuses.PHASE3_TESTS_FAILED,
      ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_AMBER,
      ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN,
      ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED,
      ProgressStatuses.PHASE3_TESTS_EXPIRED
    )
  }

  def rollbackToSubmittedFromOnlineTestsAndAddFastpassNumber(applicationId: String, certificateNumber: String)
                                                            (implicit hc: HeaderCarrier): Future[Unit] = {
    val statuses = allOnlineTestsPhases ++ Seq(ProgressStatuses.SIFT_ENTERED, ProgressStatuses.SIFT_READY, ProgressStatuses.SIFT_COMPLETED)

    for {
      civilServiceDetails <- civilServiceExperienceDetailsRepo.find(applicationId)
      updatedCivilServiceDetails = civilServiceDetails
        .map(_.copy(fastPassReceived = Some(true), certificateNumber = Some(certificateNumber)))
        .getOrElse(throw UnexpectedException("Civil Service Details not found"))
      _ <- civilServiceExperienceDetailsRepo.update(applicationId, updatedCivilServiceDetails)
      _ <- phase1TestRepository.removeTestGroup(applicationId)
      _ <- phase2TestRepository.removeTestGroup(applicationId)
      // Note the default removeTestGroup has been overriden in p3TestRepository so we need to call this method
      // which routes the call to the default implementation. Might be sensible to rename the overriden impl
      _ <- phase3TestRepository.removePhase3TestGroup(applicationId)
      _ <- appSiftRepository.removeTestGroup(applicationId)
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, Seq.empty[SchemeEvaluationResult])
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.SUBMITTED, statuses.toList)
    } yield ()
  }

  def rollbackToSubmittedFromPhase1AfterFastpassRejectedByMistake(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      civilServiceDetailsOpt <- civilServiceExperienceDetailsRepo.find(applicationId)
      updatedCivilServiceDetails = civilServiceDetailsOpt
        .map(_.copy(fastPassAccepted = None))
        .getOrElse(throw UnexpectedException("Civil Service Details not found"))
      _ <- civilServiceExperienceDetailsRepo.update(applicationId, updatedCivilServiceDetails)
      _ <- phase1TestRepository.removeTestGroup(applicationId)
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.SUBMITTED, allOnlineTestsPhases.toList)
    } yield ()
  }

  def convertToFastStreamRouteWithFastpassFromOnlineTestsExpired(applicationId: String, fastPass: Int, sdipFaststream: Boolean): Future[Unit] = {
    val routeConversion = if (sdipFaststream) {
      appRepository.updateApplicationRoute(applicationId, ApplicationRoute.SdipFaststream, ApplicationRoute.Faststream)
    } else {
      Future.successful(())
    }

    for {
      _ <- routeConversion
      _ <- civilServiceExperienceDetailsRepo.update(applicationId,
        CivilServiceExperienceDetails(
          applicable = true,
          civilServantAndInternshipTypes = Some(Seq(CivilServantAndInternshipType.SDIP)),
          edipYear = None, sdipYear = None,
          otherInternshipName = None, otherInternshipYear = None,
          fastPassReceived = Some(true), fastPassAccepted = None, certificateNumber = Some(fastPass.toString)))
      _ <- phase1TestRepository.removeTestGroup(applicationId)
    } yield ()
  }

  def removeSdipSchemeFromFaststreamUser(applicationId: String): Future[Unit] = {
    for {
      applicationRoute <- appRepository.getApplicationRoute(applicationId)
      _ = if (applicationRoute != ApplicationRoute.Faststream) {
        throw new Exception(s"Application route for $applicationId must be faststream")
      }
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      currentSchemeStatusWithoutSdip = currentSchemeStatus.filterNot(_.schemeId == Scheme.SdipId)
      schemePreferences <- schemePrefsRepository.find(applicationId)
      schemePreferencesWithoutSdip = schemePreferences.copy(schemes = schemePreferences.schemes.filterNot(_ == Scheme.SdipId))
      _ <- schemePrefsRepository.save(applicationId, schemePreferencesWithoutSdip)
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, currentSchemeStatusWithoutSdip)
    } yield ()
  }

  def addSdipSchemePreference(applicationId: String): Future[Unit] = {
    for {
      // Look for the ApplicationRoute. We do this because if a document cannot be found this will throw an ApplicationNotFound exception
      _ <- appRepository.getApplicationRoute(applicationId)
      schemePreferences <- schemePrefsRepository.find(applicationId)
      // The operation of adding Sdip to the scheme preferences is idempotent so we filter out the scheme here in case it already exists
      schemePreferencesWithoutSdip = schemePreferences.copy(schemes = schemePreferences.schemes.filterNot(_ == Scheme.SdipId))
      updatedSchemes = schemePreferencesWithoutSdip.schemes :+ Scheme.SdipId
      updatedSchemePreferences = schemePreferences.copy(schemes = updatedSchemes)
      _ <- schemePrefsRepository.save(applicationId, updatedSchemePreferences)

      // If there is a currentSchemeStatus then add Sdip
      css <- appRepository.getCurrentSchemeStatus(applicationId)
      _ = if (css.nonEmpty) {
        // Filter out Sdip because of idempotent operation
        val cssWithoutSdip = css.filterNot(_.schemeId == Scheme.SdipId)
        val updatedCss = cssWithoutSdip :+ SchemeEvaluationResult(Scheme.SdipId, Green.toString)
        appRepository.updateCurrentSchemeStatus(applicationId, updatedCss).map( _ => ())
      }
    } yield ()
  }

  def removeSchemePreference(applicationId: String, schemeToRemove: SchemeId): Future[Unit] = {
    for {
      // Look for the ApplicationRoute. We do this because if a document cannot be found this will throw an ApplicationNotFound exception
      _ <- appRepository.getApplicationRoute(applicationId)
      schemePreferences <- schemePrefsRepository.find(applicationId)
      _ = if (!schemePreferences.schemes.contains(schemeToRemove)) {
        throw SchemeNotFoundException(s"Scheme $schemeToRemove does not exist")
      }
      updatedSchemes = schemePreferences.copy(schemes = schemePreferences.schemes.filterNot(_ == schemeToRemove)).schemes
      updatedSchemePreferences = schemePreferences.copy(schemes = updatedSchemes)
      _ <- schemePrefsRepository.save(applicationId, updatedSchemePreferences)

      // If there is a currentSchemeStatus then update it
      css <- appRepository.getCurrentSchemeStatus(applicationId)
      _ = if (css.nonEmpty) {
        val updatedCss = css.filterNot(_.schemeId == schemeToRemove)
        appRepository.updateCurrentSchemeStatus(applicationId, updatedCss).map( _ => ())
      }
    } yield ()
  }

  def findUsersEligibleForJobOfferButFsbApplicationStatus(): Future[Seq[String]] = {
    appRepository.findEligibleForJobOfferCandidatesWithFsbStatus
  }

  def fixUsersEligibleForJobOfferButFsbApplicationStatus(): Future[Seq[String]] = {
    val applicationIdsFut = findUsersEligibleForJobOfferButFsbApplicationStatus()
    applicationIdsFut.flatMap { applicationIds =>
      Future.sequence(applicationIds.map { applicationId =>
        appRepository.updateApplicationStatusOnly(applicationId, ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER)
      })
    }
    applicationIdsFut
  }

  private def liftToOption(passMarkFetch: String => Future[PassmarkEvaluation], applicationId: String): Future[Option[PassmarkEvaluation]] = {
    passMarkFetch(applicationId).map(Some(_)).recover { case _: PassMarkEvaluationNotFound => None }
  }

  // scalastyle:off cyclomatic.complexity
  def findSdipFaststreamFailedFaststreamInvitedToVideoInterview:
  Future[Seq[(Candidate, ContactDetails, String, ProgressStatus, PassmarkEvaluation, PassmarkEvaluation)]] = {

    (for {
      potentialAffectedUsers <- appRepository.findSdipFaststreamInvitedToVideoInterview
    } yield for {
      potentialAffectedUser <- potentialAffectedUsers
    } yield for {
      phase1SchemeStatusOpt <- liftToOption(evaluateP1ResultService.getPassmarkEvaluation _, potentialAffectedUser.applicationId.get)
      phase2SchemeStatusOpt <- liftToOption(evaluateP2ResultService.getPassmarkEvaluation _, potentialAffectedUser.applicationId.get)
      applicationDetails <- appRepository.findStatus(potentialAffectedUser.applicationId.get)
      contactDetails <- cdRepository.find(potentialAffectedUser.userId)
    } yield for {
      phase1SchemeStatus <- phase1SchemeStatusOpt
      phase2SchemeStatus <- phase2SchemeStatusOpt
    } yield {
      val failedAtOnlineExercises = phase1SchemeStatus.result.forall(schemeResult =>
        schemeResult.result == Red.toString ||
          (schemeResult.schemeId == Scheme.SdipId && schemeResult.result == Green.toString))
      val failedAtEtray = phase2SchemeStatus.result.forall(schemeResult =>
        schemeResult.result == Red.toString ||
          (schemeResult.schemeId == Scheme.SdipId && schemeResult.result == Green.toString))

      if (failedAtEtray || failedAtOnlineExercises) {
        val failedAtStage = if (failedAtOnlineExercises) "online exercises" else "work based scenarios"
        Some((potentialAffectedUser, contactDetails, failedAtStage, applicationDetails.latestProgressStatus.get,
          phase1SchemeStatus, phase2SchemeStatus))
      } else {
        None
      }
    }).map(Future.sequence(_)).flatMap(identity).map(_.map(_.flatten)).map(_.flatten)
  }
  // scalastyle:on

  def findSdipFaststreamFailedFaststreamInPhase1ExpiredPhase2InvitedToSift:
  Future[Seq[(Candidate, ContactDetails, ProgressStatus, PassmarkEvaluation)]] = {

    (for {
      potentialAffectedUsers <- appRepository.findSdipFaststreamExpiredPhase2InvitedToSift
    } yield for {
      potentialAffectedUser <- potentialAffectedUsers
    } yield for {
      phase1SchemeStatusOpt <- liftToOption(evaluateP1ResultService.getPassmarkEvaluation _, potentialAffectedUser.applicationId.get)
      applicationDetails <- appRepository.findStatus(potentialAffectedUser.applicationId.get)
      contactDetails <- cdRepository.find(potentialAffectedUser.userId)
    } yield for {
      phase1SchemeStatus <- phase1SchemeStatusOpt
    } yield {
      val failedAtOnlineExercises = phase1SchemeStatus.result.forall(schemeResult =>
        schemeResult.result == Red.toString ||
          (schemeResult.schemeId == Scheme.SdipId && schemeResult.result == Green.toString))

      if (failedAtOnlineExercises) {
        Some((potentialAffectedUser, contactDetails, applicationDetails.latestProgressStatus.get, phase1SchemeStatus))
      } else {
        None
      }
    }).map(Future.sequence(_)).flatMap(identity).map(_.map(_.flatten)).map(_.flatten)
  }

  def findSdipFaststreamFailedFaststreamInPhase2ExpiredPhase3InvitedToSift:
  Future[Seq[(Candidate, ContactDetails, ProgressStatus, PassmarkEvaluation)]] = {

    (for {
      potentialAffectedUsers <- appRepository.findSdipFaststreamExpiredPhase3InvitedToSift
    } yield for {
      potentialAffectedUser <- potentialAffectedUsers
    } yield for {
      phase2SchemeStatusOpt <- liftToOption(evaluateP2ResultService.getPassmarkEvaluation _, potentialAffectedUser.applicationId.get)
      applicationDetails <- appRepository.findStatus(potentialAffectedUser.applicationId.get)
      contactDetails <- cdRepository.find(potentialAffectedUser.userId)
    } yield for {
      phase2SchemeStatus <- phase2SchemeStatusOpt
    } yield {
      val failedAtOnlineExercises = phase2SchemeStatus.result.forall(schemeResult =>
        schemeResult.result == Red.toString ||
          (schemeResult.schemeId == Scheme.SdipId && schemeResult.result == Green.toString))

      if (failedAtOnlineExercises) {
        Some((potentialAffectedUser, contactDetails, applicationDetails.latestProgressStatus.get, phase2SchemeStatus))
      } else {
        None
      }
    }).map(Future.sequence(_)).flatMap(identity).map(_.map(_.flatten)).map(_.flatten)
  }

  def moveSdipFaststreamFailedFaststreamInvitedToVideoInterviewToSift(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      allAffectedUsers <- findSdipFaststreamFailedFaststreamInvitedToVideoInterview
      (candidate, _, _, latestProgressStatus, _, _) = allAffectedUsers.find(_._1.applicationId.get == applicationId).getOrElse(
        throw new Exception("Application not found in affected users")
      )
      _ = if (!latestProgressStatus.startsWith("PHASE2") && !latestProgressStatus.startsWith("PHASE3")) {
        throw new Exception("User must be in a Phase2 or Phase3 progress status")
      }
      _ = if (!latestProgressStatus.startsWith("PHASE3_TESTS_COMPLETED") ) {
        throw new Exception("User must be in a PHASE3_TESTS_COMPLETED")
      }
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, SIFT_ENTERED)
      siftExpiryDate <- siftService.fetchSiftExpiryDate(candidate.applicationId.get)
      _ <- siftService.sendSiftEnteredNotification(candidate.applicationId.get, siftExpiryDate).map(_ => ())
    } yield ()
  }

  //scalastyle:off method.length
  def fixSdipFaststreamCandidateWhoExpiredInOnlineTests(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {

    def setToRedExceptSdip(results: Option[List[SchemeEvaluationResult]]): List[SchemeEvaluationResult] = {
      results.map {
        _.map { result =>
          if(result.schemeId != Scheme.SdipId) {
            result.copy(result = EvaluationResults.Red.toString)
          } else {
            result
          }
        }
      }.getOrElse(throw UnexpectedException(s"No evaluation results found"))
    }

    def updateStatuses(): Future[Unit] = {
      for {
        _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_ENTERED)
        currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
        newCurrentSchemeStatus = setToRedExceptSdip(Some(currentSchemeStatus.toList))
        _ <- appRepository.updateCurrentSchemeStatus(applicationId, newCurrentSchemeStatus)
      } yield ()
    }

    def updateEvaluationResults(prevPhaseRepo: OnlineTestRepository, nextPhaseRepo: OnlineTestRepository): Future[Unit] = {
      prevPhaseRepo.getTestGroup(applicationId).flatMap { prevPhaseTestGroupOpt =>
        prevPhaseTestGroupOpt.map { prevPhaseTestGroup =>
          val newSchemeEvaluationResults = setToRedExceptSdip(prevPhaseTestGroup.evaluation.map(_.result))
          val newPassmarkEvaluationResult = prevPhaseTestGroup.evaluation
            .map(_.copy(result = newSchemeEvaluationResults))
            .getOrElse(
              throw UnexpectedException(s"Candidate with app id $applicationId has no evaluation result for ${prevPhaseRepo.phaseName}"))
          nextPhaseRepo.upsertTestGroupEvaluationResult(applicationId, newPassmarkEvaluationResult).map(_ => ())
        }.getOrElse(throw UnexpectedException(s"Candidate with app id $applicationId has no test group for ${prevPhaseRepo.phaseName}"))
      }
    }

    def moveToSift(prevPhaseRepo: OnlineTestRepository, nextPhaseRepo: OnlineTestRepository): Future[Unit] = {
      for {
        _ <- updateEvaluationResults(prevPhaseRepo, nextPhaseRepo)
        _ <- updateStatuses()
        expiryDate <- siftService.saveSiftExpiryDate(applicationId)
        _ <- siftService.sendSiftEnteredNotification(applicationId, expiryDate)
      } yield ()
    }

    appRepository.find(applicationId).flatMap {
      _.map { candidate =>
        candidate.applicationStatus match {
          case Some("PHASE2_TESTS") =>
            moveToSift(phase1TestRepository, phase2TestRepository).map(_ => ())
          case Some("PHASE3_TESTS") =>
            moveToSift(phase2TestRepository, phase3TestRepository).map(_ => ())
          case _ => throw UnexpectedException(s"Candidate with app id $applicationId should be in either PHASE2 or PHASE3")
        }
      }.getOrElse(throw ApplicationNotFound(applicationId))
    }
  } //scalastyle:on method.length

  private def amendOneSchemeInCurrentSchemeStatus(applicationId: String, currentSchemeStatus: Seq[SchemeEvaluationResult],
                                                  schemeId: SchemeId, newResult: String) =
    appRepository.updateCurrentSchemeStatus(applicationId, currentSchemeStatus.map(result =>
      if (result.schemeId == schemeId) { result.copy(result = newResult) } else { result })
    )

  def markSiftSchemeAsRed(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    for {
      _ <- appSiftRepository.fixSchemeEvaluation(applicationId, SchemeEvaluationResult(schemeId, Red.toString))
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      _ <- amendOneSchemeInCurrentSchemeStatus(applicationId, currentSchemeStatus, schemeId, Red.toString)
    } yield ()
  }

  def markPhase3SchemeAsRed(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    phase3TestRepository.updateResult(applicationId, SchemeEvaluationResult(schemeId, Red.toString)).map(_ => ())
  }

  def markPhase3SchemeAsGreen(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    phase3TestRepository.updateResult(applicationId, SchemeEvaluationResult(schemeId, Green.toString)).map(_ => ())
  }

  def addPhase3SchemeAsGreen(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    phase3TestRepository.addResult(applicationId, SchemeEvaluationResult(schemeId, Green.toString)).map(_ => ())
  }

  def markSiftSchemeAsGreen(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    for {
      _ <- appSiftRepository.fixSchemeEvaluation(applicationId, SchemeEvaluationResult(schemeId, Green.toString))
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      _ <- amendOneSchemeInCurrentSchemeStatus(applicationId, currentSchemeStatus, schemeId, Green.toString)
    } yield ()
  }

  def markFsbSchemeAsRed(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    fsbRepo.updateResult(applicationId, SchemeEvaluationResult(schemeId, Red.toString)).map(_ => ())
  }

  def markFsbSchemeAsGreen(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    fsbRepo.updateResult(applicationId, SchemeEvaluationResult(schemeId, Green.toString)).map(_ => ())
  }

  def rollbackToSiftReadyFromAssessmentCentreAwaitingAllocation(applicationId: String): Future[Unit] = {
    for {
      _ <- appSiftRepository.fixDataByRemovingSiftEvaluation(applicationId)
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.SIFT, List(
        ASSESSMENT_CENTRE_AWAITING_ALLOCATION,
        SIFT_COMPLETED
      ))
    } yield ()
  }

  def rollbackToFsbAwaitingAllocation(applicationId: String, statuses: List[ProgressStatuses.ProgressStatus])
                                     (implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      assessmentCentreEvaluation <- fsacRepo.getFsacEvaluatedSchemes(applicationId)
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, assessmentCentreEvaluation.getOrElse(
        throw NoResultsReturned(s"No FSAC results found for $applicationId")))
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.FSB, statuses)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, FSB_AWAITING_ALLOCATION)
      _ <- fsbRepo.removeTestGroup(applicationId)
      // Mark as removed on active fsb allocations
      allocations <- candidateAllocationService.allocationsForApplication(applicationId)
      eventIds = allocations.map(_.eventId)
      allocatedEvents <- Future.sequence(eventIds.map(eventId => eventsService.getEvent(eventId)))
      allocatedFsbEventIds = allocatedEvents.filter(_.eventType == EventType.FSB).map(_.id)
      activeFsbAllocations = allocations.filter(allocation =>
        allocatedFsbEventIds.contains(allocation.eventId) &&
          List(AllocationStatuses.CONFIRMED, AllocationStatuses.UNCONFIRMED).contains(allocation.status)
      )
      _ <- candidateAllocationService.unAllocateCandidates(activeFsbAllocations.toList)
    } yield ()
  }

  def removePhase3TestGroup(applicationId: String): Future[Unit] = {
    for {
      _ <- phase3TestRepository.removePhase3TestGroup(applicationId)
    } yield ()
  }

  def removeSiftTestGroup(application: String): Future[Unit] = {
    appSiftRepository.removeTestGroup(application).map(_ => ())
  }

  def removeFsbTestGroup(applicationId: String): Future[Unit] = {
    for {
      _ <- fsbRepo.removeTestGroup(applicationId)
    } yield ()
  }

  def rollbackToAssessmentCentreConfirmed(applicationId: String, statuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = {
    def getPhase3Results: Future[Option[List[SchemeEvaluationResult]]] = {
      phase3TestRepository.getTestGroup(applicationId).map { maybeTestGroup =>
        maybeTestGroup.flatMap { phase3TestGroup =>
          phase3TestGroup.evaluation.map(_.result)
        }
      }
    }
    def fetchSiftResults: Future[Seq[SchemeEvaluationResult]] = {
      for {
        siftEvaluation <- appSiftRepository.getSiftEvaluations(applicationId).recover { case _ => Nil }
      } yield {
        siftEvaluation
      }
    }
    val exercisesToRemove = List(exercise1.toString, exercise2.toString, exercise3.toString)
    val reviewerExercisesToRemove = exercisesToRemove :+ finalFeedback.toString

    for {
      _ <- assessorAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- reviewerAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), reviewerExercisesToRemove)
      _ <- fsacRepo.removeFsacTestGroup(applicationId)
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.ASSESSMENT_CENTRE, statuses)
      phase3ResultsOpt <- getPhase3Results
      phase3Results = phase3ResultsOpt.getOrElse(throw NoResultsReturned("No phase 3 video results found"))
      siftResults <- fetchSiftResults
      css = calculateCurrentSchemeStatus(phase3Results, siftResults)
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, css)
    } yield ()
  }

  def rollbackToFsacAwaitingAllocationFromFsacFailed(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED,
      ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED,
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED,
      ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION,
      ProgressStatuses.ASSESSMENT_CENTRE_FAILED,
      ProgressStatuses.ASSESSMENT_CENTRE_FAILED_NOTIFIED
    )

    for {
      _ <- fsacRepo.removeFsacTestGroup(applicationId)
      exercisesToRemove = List(exercise1.toString, exercise2.toString, exercise3.toString, finalFeedback.toString)
      _ <- assessorAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- reviewerAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.ASSESSMENT_CENTRE, statuses)
      _ <- addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
    } yield ()
  }

  def fsacResetFastPassCandidate(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED,
      ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED,
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED
    )

    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.ASSESSMENT_CENTRE, statuses)
      _ <- addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      exercisesToRemove = AssessmentScoresSectionType.asListOfStrings
      _ <- assessorAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- reviewerAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- fsacRepo.removeFsacTestGroup(applicationId)
    } yield ()
  }

  def fsacRemoveEvaluation(applicationId: String): Future[Unit] = {
    for {
      _ <- fsacRepo.removeFsacEvaluation(applicationId)
    } yield ()
  }

  def fsacRollbackWithdraw(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.WITHDRAWN,
      ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION
    )

    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.ASSESSMENT_CENTRE, statuses)
      _ <- appRepository.removeWithdrawReason(applicationId)
    } yield ()
  }

  def rollbackToFsacAllocationConfirmedFromFsb(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED,
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED,
      ProgressStatuses.ASSESSMENT_CENTRE_PASSED,
      ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION,
      ProgressStatuses.FSB_AWAITING_ALLOCATION
    )

    for {
      _ <- fsacRepo.removeFsacTestGroup(applicationId)
      exercisesToRemove = AssessmentScoresSectionType.asListOfStrings
      _ <- assessorAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- reviewerAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.ASSESSMENT_CENTRE, statuses)
      _ <- addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED)
    } yield ()
  }

  private def updateCurrentSchemeStatus(applicationId: String, evaluation: Option[Seq[SchemeEvaluationResult]]) = {
    evaluation.map { evaluationResults =>
      for {
        _ <- appRepository.updateCurrentSchemeStatus(applicationId, evaluationResults)
      } yield ()
    }.getOrElse(throw new Exception(s"Error no evaluation results found for $applicationId"))
  }

  def rollbackToPhase2ExpiredFromSift(applicationId: String): Future[Unit] = {
    val statusesToRollback = List(ProgressStatuses.SIFT_ENTERED, ProgressStatuses.SIFT_READY)
    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE2_TESTS, statusesToRollback)
      _ <- phase2TestRepository.removeTestGroupEvaluation(applicationId)
      evaluationOpt <- phase1TestRepository.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
      _ <- siftAnswersService.removeAnswers(applicationId)
    } yield ()
  }

  def fixPhase3ExpiredCandidate(applicationId: String): Future[Unit] = {
    for {
      _ <- phase3TestRepository.updateGroupExpiryTime(applicationId, OffsetDateTime.now.plusDays(1), phase3TestRepository.phaseName)
      _ <- appRepository.removeProgressStatuses(applicationId, List(ProgressStatuses.PHASE3_TESTS_EXPIRED))
      _ <- addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.PHASE3_TESTS_COMPLETED)
      _ <- addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)
    } yield ()
  }

  def rollbackToPhase3ExpiredFromSift(applicationId: String): Future[Unit] = {
    val statusesToRollback = List(ProgressStatuses.SIFT_ENTERED, ProgressStatuses.SIFT_READY)
    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE3_TESTS, statusesToRollback)
      _ <- phase3TestRepository.removeTestGroupEvaluation(applicationId)
      evaluationOpt <- phase2TestRepository.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
      _ <- siftAnswersService.removeAnswers(applicationId)
    } yield ()
  }

  def rollbackToRetakePhase3FromSift(applicationId: String, token: String): Future[Unit] = {
    val statusesToRollback = List(
      ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER, ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE3_TESTS_STARTED, ProgressStatuses.PHASE3_TESTS_COMPLETED,
      ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED, ProgressStatuses.PHASE3_TESTS_PASSED,
      ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED,
      ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN,
      ProgressStatuses.SIFT_ENTERED, ProgressStatuses.SIFT_FIRST_REMINDER,
      ProgressStatuses.SIFT_SECOND_REMINDER, ProgressStatuses.SIFT_READY,
      ProgressStatuses.SIFT_COMPLETED, ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN,
      ProgressStatuses.FSB_AWAITING_ALLOCATION
    )
    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE3_TESTS, statusesToRollback)
      _ <- siftAnswersService.removeAnswers(applicationId)
      _ <- appSiftRepository.removeTestGroup(applicationId)
      _ <- phase3TestRepository.updateExpiryDate(applicationId, OffsetDateTime.now.plusDays(7))
      _ <- phase3TestRepository.removeTestGroupEvaluation(applicationId)
      _ <- phase3TestRepository.removeReviewedCallbacks(token)
      evaluationOpt <- phase2TestRepository.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
    } yield ()
  }

  def removePhase1TestEvaluation(applicationId: String): Future[Unit] = {
    for {
      _ <- phase1TestRepository.removeTestGroupEvaluation(applicationId)
    } yield ()
  }

  def removePhase2TestEvaluation(applicationId: String): Future[Unit] = {
    for {
      _ <- phase2TestRepository.removeTestGroupEvaluation(applicationId)
      evaluationOpt <- phase1TestRepository.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
    } yield ()
  }

  def removePhase3TestEvaluation(applicationId: String): Future[Unit] = {
    for {
      _ <- phase3TestRepository.removeTestGroupEvaluation(applicationId)
      evaluationOpt <- phase2TestRepository.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
    } yield ()
  }

  def rollbackToPhase1TestsPassedFromSift(applicationId: String): Future[Unit] = {

    val statusesToRollback = List(
      ProgressStatuses.PHASE2_TESTS_INVITED,
      ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE2_TESTS_STARTED,
      ProgressStatuses.PHASE2_TESTS_COMPLETED,
      ProgressStatuses.PHASE2_TESTS_RESULTS_READY,
      ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_AMBER,
      ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN,
      ProgressStatuses.PHASE2_TESTS_PASSED,
      ProgressStatuses.PHASE3_TESTS_INVITED,
      ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE3_TESTS_STARTED,
      ProgressStatuses.PHASE3_TESTS_COMPLETED,
      ProgressStatuses.SIFT_ENTERED,
      ProgressStatuses.SIFT_FIRST_REMINDER,
      ProgressStatuses.SIFT_SECOND_REMINDER,
      ProgressStatuses.SIFT_EXPIRED,
      ProgressStatuses.SIFT_EXPIRED_NOTIFIED,
      ProgressStatuses.SIFT_READY
    )

    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE1_TESTS_PASSED, statusesToRollback)
      _ <- siftAnswersService.removeAnswers(applicationId)
      _ <- appSiftRepository.removeTestGroup(applicationId)
      _ <- phase3TestRepository.removePhase3TestGroup(applicationId)
      _ <- phase2TestRepository.removeTestGroup(applicationId)
      evaluationOpt <- phase1TestRepository.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
    } yield ()
  }

  def enablePhase3ExpiredCandidateToBeEvaluated(applicationId: String): Future[Unit] = {
    for {
      _ <- phase3TestRepository.updateExpiryDate(applicationId, OffsetDateTime.now.plusDays(1))
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)
      _ <- appRepository.removeProgressStatuses(applicationId, List(ProgressStatuses.PHASE3_TESTS_EXPIRED))
    } yield ()
  }

  def removePhase3TestAndSetOtherToActive(removeTestToken: String, markTestAsActiveToken: String): Future[Unit] = {
    for {
      _ <- phase3TestRepository.removeTest(removeTestToken)
      _ <- phase3TestRepository.markTestAsActive(markTestAsActiveToken)
    } yield ()
  }

  def setCurrentSchemeStatusToPhase3Evaluation(applicationId: String): Future[Unit] = {
    for {
      evaluationOpt <- phase3TestRepository.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
    } yield ()
  }

  def updateCurrentSchemeStatusScheme(applicationId: String, schemeId: SchemeId, newResult: model.EvaluationResults.Result): Future[Unit] = {
    for {
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      newCurrentSchemeStatus = currentSchemeStatus.map { schemeResult =>
        if (schemeResult.schemeId == schemeId) {
          schemeResult.copy(result = newResult.toString)
        } else {
          schemeResult
        }
      }
      _ = if (currentSchemeStatus == newCurrentSchemeStatus) {
        throw NoChangeInCurrentSchemeStatusException(applicationId, currentSchemeStatus, newCurrentSchemeStatus)
      }
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, newCurrentSchemeStatus)
    } yield ()
  }

  def removeCurrentSchemeStatusScheme(applicationId: String, schemeId: SchemeId): Future[Unit] = {
    for {
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      newCurrentSchemeStatus = currentSchemeStatus.filterNot( _.schemeId == schemeId )
      _ = if (currentSchemeStatus == newCurrentSchemeStatus) {
        throw NoChangeInCurrentSchemeStatusException(applicationId, currentSchemeStatus, newCurrentSchemeStatus)
      }
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, newCurrentSchemeStatus)
    } yield ()
  }

  def setPhase3UsedForResults(applicationId: String, newUsedForResults: Boolean, token: String): Future[Unit] = {
    for {
      phase3TestGroupOpt <- phase3TestRepository.getTestGroup(applicationId)
      phase3TestGroup = phase3TestGroupOpt.getOrElse(throw NoResultsReturned(s"Unable to find PHASE3 TestGroup for $applicationId"))
      newTests = phase3TestGroup.tests.map { test =>
        if(test.token == token) { test.copy(usedForResults = newUsedForResults) }
        else { throw NoResultsReturned(s"No test found for token $token") }
      }
      newPhase3TestGroup = phase3TestGroup.copy(tests = newTests)
      _ <- phase3TestRepository.insertOrUpdateTestGroup(applicationId, newPhase3TestGroup)
    } yield ()
  }

  def setPhase2UsedForResults(applicationId: String, inventoryId: String, orderId: String, newUsedForResults: Boolean): Future[Unit] = {
    for {
      phase2TestGroupOpt <- phase2TestRepository.getTestGroup(applicationId)
      phase2TestGroup = phase2TestGroupOpt.getOrElse(throw UnexpectedException(s"Unable to find PHASE2 TestGroup for $applicationId"))

      _ = if (!phase2TestGroup.tests.exists( t => t.inventoryId == inventoryId && t.orderId == orderId)) {
        throw UnexpectedException(s"Unable to find PHASE2 test for appId=$applicationId,inventoryId=$inventoryId,orderId=$orderId")
      }

      updatedTests = phase2TestGroup.tests.map { test =>
        if (test.inventoryId == inventoryId && test.orderId == orderId) test.copy(usedForResults = newUsedForResults) else test }
      newPhase2TestGroup = phase2TestGroup.copy(tests = updatedTests)
      _ <- phase2TestRepository.insertOrUpdateTestGroup(applicationId, newPhase2TestGroup)
    } yield ()
  }

  def setPhase1UsedForResults(applicationId: String, inventoryId: String, orderId: String, newUsedForResults: Boolean): Future[Unit] = {
    for {
      phase1TestProfileOpt <- phase1TestRepository.getTestGroup(applicationId)
      phase1TestProfile = phase1TestProfileOpt.getOrElse(throw UnexpectedException(s"Unable to find PHASE1 TestProfile for $applicationId"))

      _ = if (!phase1TestProfile.tests.exists( t => t.inventoryId == inventoryId && t.orderId == orderId)) {
        throw UnexpectedException(s"Unable to find PHASE1 test for appId=$applicationId,inventoryId=$inventoryId,orderId=$orderId")
      }

      updatedTests = phase1TestProfile.tests.map { test =>
        if (test.inventoryId == inventoryId && test.orderId == orderId) test.copy(usedForResults = newUsedForResults) else test }
      newPhase1TestProfile = phase1TestProfile.copy(tests = updatedTests)
      _ <- phase1TestRepository.insertOrUpdateTestGroup(applicationId, newPhase1TestProfile)
    } yield ()
  }

  def setGis(applicationId: String, newGis: Boolean): Future[Unit] = {
    for {
      candidateOpt <- appRepository.find(applicationId)
      candidate = candidateOpt.getOrElse(throw UnexpectedException(s"Unable to find application for $applicationId"))
      assistanceDetails <- assistanceDetailsRepo.find(applicationId)
      updated = assistanceDetails.copy(guaranteedInterview = Some(newGis))
      _ <- assistanceDetailsRepo.update(applicationId, candidate.userId, updated)
    } yield ()
  }

  private def rollbackAppAndProgressStatus(applicationId: String,
                                           applicationStatus: ApplicationStatus,
                                           statuses: Seq[ProgressStatuses.ProgressStatus]): Future[Unit] = {
    for {
      _ <- appRepository.updateStatus(applicationId, applicationStatus)
      _ <- appRepository.removeProgressStatuses(applicationId, statuses.toList)
    } yield ()
  }

  private def getSdipFaststreamSchemes(applicationId: String): Future[List[SchemeId]] = for {
    phase1 <- evaluateP1ResultService.getPassmarkEvaluation(applicationId)
    _ <- evaluateP3ResultService.getPassmarkEvaluation(applicationId).recover{
      case _: PassMarkEvaluationNotFound =>
        PassmarkEvaluation(passmarkVersion = "", previousPhasePassMarkVersion = None, result = Nil,
          resultVersion = "", previousPhaseResultVersion = None)
    }
  } yield {
    phase1.result.find(_.schemeId == Sdip).toList.filter(r => r.result == Green.toString).map(_.schemeId)
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
        logger.error(s"Failed to update ${fixBatch.fix.name}", e)
        None
    }.toList
  }

  def currentSchemeStatusWithFailureDetails(applicationId: String): Future[Seq[SchemeEvaluationResultWithFailureDetails]] = {

    def getOnlineTestEvaluation(repository: OnlineTestEvaluationRepository): Future[List[SchemeEvaluationResult]] =
      repository.getPassMarkEvaluation(applicationId).map(_.result).recover { case _ => Nil }

    for {
      phase1Evaluation <- getOnlineTestEvaluation(phase1EvaluationRepository)
      phase2Evaluation <- getOnlineTestEvaluation(phase2EvaluationRepository)
      phase3Evaluation <- getOnlineTestEvaluation(phase3EvaluationRepository)
      siftEvaluation <- appSiftRepository.getSiftEvaluations(applicationId).recover { case _ => Nil }
      fsacEvaluation <- fsacRepo.getFsacEvaluatedSchemes(applicationId).map(_.getOrElse(Nil).toList)
      fsbEvaluation <- fsbRepo.findByApplicationId(applicationId).map(_.map(_.evaluation.result).getOrElse(Nil))
      evaluations = ListMap(
        "online tests" -> phase1Evaluation,
        "work based scenarios" -> phase2Evaluation,
        "video interview" -> phase3Evaluation,
        "sift stage" -> siftEvaluation,
        "assessment centre" -> fsacEvaluation,
        "final selection board" -> fsbEvaluation
      )
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      redResults = extractFirstRedResults(evaluations)
    } yield for {
      result <- currentSchemeStatus
    } yield {
      redResults.find(_.schemeId == result.schemeId).getOrElse(
        SchemeEvaluationResultWithFailureDetails(result)
      )
    }
  }

  def findStatus(applicationId: String): Future[ApplicationStatusDetails] = {
    appRepository.findStatus(applicationId)
  }

  private def extractFirstRedResults(
                                      evaluations: ListMap[String, Seq[SchemeEvaluationResult]]
                                    ): Seq[SchemeEvaluationResultWithFailureDetails] = {
    evaluations.foldLeft(List[SchemeEvaluationResultWithFailureDetails]()) { case (redsSoFar, (description, results)) =>
      redsSoFar ++
        results
          .filter(_.result == Red.toString)
          .filterNot(result => redsSoFar.exists(_.schemeId == result.schemeId)).map { result =>
          SchemeEvaluationResultWithFailureDetails(
            result, description
          )
        }
    }
  }
}
// scalastyle:on
