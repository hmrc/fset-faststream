/*
 * Copyright 2018 HM Revenue & Customs
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
import model.EvaluationResults.{ Green, Red }
import model.Exceptions._
import model.ProgressStatuses._
import model.command._
import model.stc.StcEventTypes._
import model.stc.{ AuditEvents, DataStoreEvents, EmailEvents }
import model.exchange.passmarksettings.{ Phase1PassMarkSettings, Phase2PassMarkSettings, Phase3PassMarkSettings }
import model.persisted.{ ContactDetails, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ProgressStatuses, _ }
import model.exchange.SchemeEvaluationResultWithFailureDetails
import model.exchange.sift.SiftAnswersStatus
import model.persisted.eventschedules.EventType
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.onlinetesting._
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import repositories.sift.ApplicationSiftRepository
import scheduler.fixer.FixBatch
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.allocation.CandidateAllocationService
import services.application.ApplicationService.NoChangeInCurrentSchemeStatusException
import services.events.EventsService
import services.stc.{ EventSink, StcEventService }
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import services.onlinetesting.phase2.EvaluatePhase2ResultService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import services.sift.{ ApplicationSiftService, SiftAnswersService }

import scala.collection.immutable.ListMap
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import uk.gov.hmrc.http.HeaderCarrier

object ApplicationService extends ApplicationService with CurrentSchemeStatusHelper {
  val appRepository = applicationRepository
  val eventService = StcEventService
  val pdRepository = personalDetailsRepository
  val cdRepository = faststreamContactDetailsRepository
  val mediaRepo = mediaRepository
  val schemePrefsRepository = schemePreferencesRepository
  val evaluateP1ResultService = EvaluatePhase1ResultService
  val evaluateP2ResultService = EvaluatePhase2ResultService
  val evaluateP3ResultService = EvaluatePhase3ResultService
  val siftService = ApplicationSiftService
  val siftAnswersService = SiftAnswersService
  val schemesRepo = SchemeYamlRepository
  val phase1TestRepo = repositories.phase1TestRepository
  val phase2TestRepository = repositories.phase2TestRepository
  val phase3TestRepository = repositories.phase3TestRepository
  val phase1EvaluationRepository = faststreamPhase1EvaluationRepository
  val phase2EvaluationRepository = faststreamPhase2EvaluationRepository
  val phase3EvaluationRepository = faststreamPhase3EvaluationRepository
  val appSiftRepository = applicationSiftRepository
  val fsacRepo = assessmentCentreRepository
  val eventsService = EventsService
  val fsbRepo = fsbRepository
  val civilServiceExperienceDetailsRepo = civilServiceExperienceDetailsRepository
  val assessorAssessmentScoresRepository = repositories.assessorAssessmentScoresRepository
  val reviewerAssessmentScoresRepository = repositories.reviewerAssessmentScoresRepository
  val candidateAllocationService = CandidateAllocationService

  case class NoChangeInCurrentSchemeStatusException(applicationId: String,
    currentSchemeStatus: Seq[SchemeEvaluationResult],
    newSchemeStatus: Seq[SchemeEvaluationResult]) extends Exception(s"$applicationId / $currentSchemeStatus / $newSchemeStatus")
}

// scalastyle:off number.of.methods
trait ApplicationService extends EventSink with CurrentSchemeStatusHelper {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  def appRepository: GeneralApplicationRepository
  def pdRepository: PersonalDetailsRepository
  def cdRepository: ContactDetailsRepository
  def schemePrefsRepository: SchemePreferencesRepository
  def mediaRepo: MediaRepository
  def evaluateP1ResultService: EvaluateOnlineTestResultService[Phase1PassMarkSettings]
  def evaluateP2ResultService: EvaluateOnlineTestResultService[Phase2PassMarkSettings]
  def evaluateP3ResultService: EvaluateOnlineTestResultService[Phase3PassMarkSettings]
  def siftService: ApplicationSiftService
  def siftAnswersService: SiftAnswersService
  def schemesRepo: SchemeRepository
  def phase1TestRepo: Phase1TestRepository
  def phase2TestRepository: Phase2TestRepository
  def phase3TestRepository: Phase3TestRepository
  def phase1EvaluationRepository: Phase1EvaluationMongoRepository
  def phase2EvaluationRepository: Phase2EvaluationMongoRepository
  def phase3EvaluationRepository: Phase3EvaluationMongoRepository
  def appSiftRepository: ApplicationSiftRepository
  def fsacRepo: AssessmentCentreRepository
  def eventsService: EventsService
  def fsbRepo: FsbRepository
  def civilServiceExperienceDetailsRepo: CivilServiceExperienceDetailsRepository
  def assessorAssessmentScoresRepository: AssessorAssessmentScoresMongoRepository
  def reviewerAssessmentScoresRepository: ReviewerAssessmentScoresMongoRepository
  def candidateAllocationService: CandidateAllocationService

  val Candidate_Role = "Candidate"

  def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]] = appRepository.getCurrentSchemeStatus(applicationId)

  def withdraw(applicationId: String, withdrawRequest: WithdrawRequest)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    (for {
      isSiftExpired <- siftService.isSiftExpired(applicationId)
      _ = if(isSiftExpired) throw SiftExpiredException("SIFT_PHASE has expired. Unable to withdraw")
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
    }) flatMap identity
  }

  def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatus): Future[Unit] = {
    appRepository.addProgressStatusAndUpdateAppStatus(applicationId, progressStatus)
  }

  def removeFromAllEvents(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    candidateAllocationService.allocationsForApplication(applicationId).flatMap { allocations =>
      candidateAllocationService.unAllocateCandidates(allocations.toList).map(_ => ())
    }
  }

  private def withdrawableSchemes(currentSchemeStatus: Seq[SchemeEvaluationResult]): Seq[SchemeEvaluationResult] = {
    currentSchemeStatus.filterNot(s => s.result == EvaluationResults.Red.toString || s.result == EvaluationResults.Withdrawn.toString)
  }

  private def withdrawFromApplication(applicationId: String, withdrawRequest: WithdrawApplication)
    (candidate: Candidate, cd: ContactDetails)(implicit hc: HeaderCarrier) = {
      appRepository.withdraw(applicationId, withdrawRequest).flatMap { _ =>
        removeFromAllEvents(applicationId).map { _ =>
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
  }

  //scalastyle:off method.length cyclomatic.complexity
  private def withdrawFromScheme(applicationId: String, withdrawRequest: WithdrawScheme) = {

    def buildLatestSchemeStatus(current: Seq[SchemeEvaluationResult], withdrawal: WithdrawScheme)  = {
      calculateCurrentSchemeStatus(current, SchemeEvaluationResult(withdrawRequest.schemeId, EvaluationResults.Withdrawn.toString) :: Nil)
    }

    def maybeProgressToFSAC(schemeStatus: Seq[SchemeEvaluationResult], latestProgressStatus: Option[ProgressStatus],
      siftAnswersStatus: Option[SiftAnswersStatus.Value]) = {
      val greenSchemes = schemeStatus.collect { case s if s.result == Green.toString => s.schemeId }.toSet

      // we only have generalist or human resources
      val onlyNonSiftableSchemesLeft = greenSchemes subsetOf schemesRepo.nonSiftableSchemeIds.toSet

      // only schemes with no evaluation requirement and form filled in. This set of schemes all require you to fill in a form
      val onlyNoSiftEvaluationRequiredSchemesWithFormFilled = siftAnswersStatus.contains(SiftAnswersStatus.SUBMITTED) &&
        (greenSchemes subsetOf schemesRepo.noSiftEvaluationRequiredSchemeIds.toSet)

      val shouldProgressToFSAC = onlyNonSiftableSchemesLeft || onlyNoSiftEvaluationRequiredSchemesWithFormFilled &&
        !latestProgressStatus.contains(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)

      // sdip faststream candidate who is awaiting allocation to an assessment centre or is in sift completed (not yet picked
      // up by the assessment centre invite job) and has withdrawn from all fast stream schemes (or been sifted out) and only has sdip left
      val shouldProgressSdipFaststreamCandidateToFsb = greenSchemes == Set(Scheme.SdipId) && schemeStatus.size > 1 &&
        (latestProgressStatus.contains(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION) ||
          latestProgressStatus.contains(ProgressStatuses.SIFT_COMPLETED))

      if (shouldProgressSdipFaststreamCandidateToFsb) {
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.FSB_AWAITING_ALLOCATION).map { _ =>
          Logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.FSB_AWAITING_ALLOCATION}")
          AuditEvents.AutoProgressedToFSB(Map("applicationId" -> applicationId, "reason" -> "last fast stream scheme withdrawn")) :: Nil
        }
      } else if (shouldProgressToFSAC) {
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION).map { _ =>
          Logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION}")
          AuditEvents.AutoProgressedToFSAC(Map("applicationId" -> applicationId, "reason" -> "last siftable scheme withdrawn")) :: Nil
        }
      } else {
        Logger.info(s"Candidate $applicationId withdrawing scheme will not be moved to " +
          s"${ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION}")
        Future.successful(())
      }
    }

    def requirementSatisfied(req1: Boolean, req2: Boolean) = (req1, req2) match {
      case (false, _) => true // eg no numeric test needed/form to be filled
      case (true, true) => true // eg numeric test needed/form to be filled and the test has been done/form has been submitted
      case _ => false // Everything else
    }

    // Move the candidate to SIFT_READY if the candidate is in SIFT_ENTERED and after withdrawal, the numeric test requirements are
    // satisfied, the form requirements are satisfied and the candidate still has at least one scheme that needs an evaluation
    def maybeProgressToSiftReady(schemeStatus: Seq[SchemeEvaluationResult],
      progressResponse: ProgressResponse, applicationStatus: ApplicationStatus, siftAnswersStatus: Option[SiftAnswersStatus.Value]) = {
      val greenSchemes = schemeStatus.collect { case s if s.result == Green.toString => s.schemeId }.toSet

      val numericTestRequired = greenSchemes.exists( s => schemesRepo.numericTestSiftRequirementSchemeIds.contains(s) )
      val numericTestCompleted = progressResponse.siftProgressResponse.siftTestResultsReceived
      val numericTestRequirementSatisfied = requirementSatisfied(numericTestRequired, numericTestCompleted)

      val formMustBeFilledIn = greenSchemes.exists( s => schemesRepo.formMustBeFilledInSchemeIds.contains(s) )
      val formsHaveBeenFilledIn = siftAnswersStatus.contains(SiftAnswersStatus.SUBMITTED)
      val formRequirementSatisfied = requirementSatisfied(formMustBeFilledIn, formsHaveBeenFilledIn)

      // we have at least one scheme that needs an evaluation
      val siftEvaluationNeeded = greenSchemes.exists( s => schemesRepo.siftableAndEvaluationRequiredSchemeIds.contains(s) )

      val shouldProgressCandidate = applicationStatus == ApplicationStatus.SIFT &&
        progressResponse.siftProgressResponse.siftEntered &&
        !progressResponse.siftProgressResponse.siftReady && !progressResponse.siftProgressResponse.siftCompleted &&
        numericTestRequirementSatisfied && formRequirementSatisfied && siftEvaluationNeeded

      if (shouldProgressCandidate) {
        Logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.SIFT_READY}")
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY).map { _ => }
      } else {
        Logger.info(s"Candidate $applicationId withdrawing scheme will not be moved to ${ProgressStatuses.SIFT_READY}")
        Future.successful(()) }
    }

    for {
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      latestSchemeStatus = buildLatestSchemeStatus(currentSchemeStatus, withdrawRequest)
      appStatuses <- appRepository.findStatus(applicationId)
      _ <- appRepository.withdrawScheme(applicationId, withdrawRequest, latestSchemeStatus)

      siftAnswersStatus <- siftAnswersService.findSiftAnswersStatus(applicationId)
      _ <- maybeProgressToFSAC(latestSchemeStatus, appStatuses.latestProgressStatus, siftAnswersStatus)

      progressResponse <- appRepository.findProgress(applicationId)
      _ <- maybeProgressToSiftReady(latestSchemeStatus, progressResponse,
        ApplicationStatus.withName(appStatuses.status), siftAnswersStatus)
    } yield {
      DataStoreEvents.SchemeWithdrawn(applicationId, withdrawRequest.withdrawer) ::
        AuditEvents.SchemeWithdrawn(Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)) ::
      Nil
    }
  }
  //scalastyle:on

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

  def undoFullWithdraw(applicationId: String, newApplicationStatus: ApplicationStatus): Future[Unit] = {
    for {
      application <- appRepository.find(applicationId)
      _ = application.map(_.applicationStatus.exists(_ == WITHDRAWN)).getOrElse(throw ApplicationNotFound(applicationId))
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

  def rollbackCandidateToPhase2CompletedFromPhase2Failed(applicationId: String): Future[Unit] = {
    val statuses = List(
      ProgressStatuses.PHASE2_TESTS_FAILED,
      ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED,
      ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE2_TESTS_RESULTS_READY
    )

    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE2_TESTS, statuses)
      phase1TestProfileOpt <- phase1TestRepository.getTestGroup(applicationId)

      phase1Result = phase1TestProfileOpt.flatMap(_.evaluation.map(_.result))
        .getOrElse(throw new Exception(s"Unable to find PHASE1 testGroup/results for $applicationId"))

      _ <- appRepository.updateCurrentSchemeStatus(applicationId, phase1Result)
      phase2TestGroupOpt <- phase2TestRepository.getTestGroup(applicationId)

      phase2TestGroup = phase2TestGroupOpt.getOrElse(throw new Exception(s"Unable to find PHASE2 testGroup for $applicationId"))
      cubiksTests = phase2TestGroup.tests.map { ct =>
        if(ct.usedForResults) {
          ct.copy(resultsReadyToDownload = false, testResult = None,
            reportId = None, reportLinkURL = None, reportStatus = None)
        } else {
          ct
        }
      }
      newTestGroup = phase2TestGroup.copy(tests = cubiksTests)
      _ <- phase2TestRepository.saveTestGroup(applicationId, newTestGroup)
    } yield ()
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
      _ <- phase1TestRepo.removeTestGroup(applicationId)
      _ <- phase2TestRepository.removeTestGroup(applicationId)
      _ <- phase3TestRepository.removeTestGroup(applicationId)
      _ <- appSiftRepository.removeTestGroup(applicationId)
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, Seq.empty[SchemeEvaluationResult])
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.SUBMITTED, statuses.toList)
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
      _ <- civilServiceExperienceDetailsRepo.update(applicationId, CivilServiceExperienceDetails(
        applicable = true, Some(CivilServiceExperienceType.DiversityInternship), Some(Seq(InternshipType.SDIPCurrentYear)),
        Some(true), None, certificateNumber = Some(fastPass.toString)))
      _ <- phase1TestRepo.removeTestGroup(applicationId)
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

  def findUsersEligibleForJobOfferButFsbApplicationStatus(): Future[Seq[String]] = {
    appRepository.findEligibleForJobOfferCandidatesWithFsbStatus
  }

  def fixUsersEligibleForJobOfferButFsbApplicationStatus(): Future[Int] = {
    val applicationIdsFut = findUsersEligibleForJobOfferButFsbApplicationStatus()
    applicationIdsFut.flatMap { applicationIds =>
      Future.sequence(applicationIds.map { applicationId =>
        appRepository.updateApplicationStatusOnly(applicationId, ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER)
      })
    }.flatMap(_ => applicationIdsFut.map(_.length))
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
        val failedAtStage = if (failedAtOnlineExercises) "online exercises" else "e-tray"
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
      _ <- siftService.sendSiftEnteredNotification(candidate.applicationId.get).map(_ => ())
    } yield ()
  }

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

    appRepository.find(applicationId).map {
      _.map { candidate =>
        candidate.applicationStatus match {
          case Some("PHASE2_TESTS") =>
            updateEvaluationResults(phase1TestRepo, phase2TestRepository)
              .flatMap(_ => updateStatuses().flatMap(_ => siftService.sendSiftEnteredNotification(applicationId).map(_ => ())))
          case Some("PHASE3_TESTS") =>
            updateEvaluationResults(phase2TestRepository, phase3TestRepository)
              .flatMap(_ => updateStatuses().flatMap(_ => siftService.sendSiftEnteredNotification(applicationId).map(_ => ())))
          case _ => throw UnexpectedException(s"Candidate with app id $applicationId should be in either PHASE2 or PHASE3")
        }
      }
    }
  }

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
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, assessmentCentreEvaluation.get)
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

  def removeSiftTestGroup(application: String): Future[Unit] = {
    appSiftRepository.removeTestGroup(application).map(_ => ())
  }

  def rollbackToAssessmentCentreConfirmed(applicationId: String, statuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = {
    import model.command.AssessmentScoresCommands.AssessmentScoresSectionType._
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
    val exercisesToRemove = List(analysisExercise.toString, groupExercise.toString, leadershipExercise.toString)
    val reviewerExercisesToRemove = exercisesToRemove :+ finalFeedback.toString

    for {
      _ <- assessorAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- reviewerAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), reviewerExercisesToRemove)
      _ <- fsacRepo.removeFsacEvaluation(applicationId)
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.ASSESSMENT_CENTRE, statuses)
      phase3ResultsOpt <- getPhase3Results
      phase3Results = phase3ResultsOpt.getOrElse(throw new RuntimeException("No phase 3 video results found"))
      siftResults <- fetchSiftResults
      css = calculateCurrentSchemeStatus(phase3Results, siftResults)
      _ <- appRepository.updateCurrentSchemeStatus(applicationId, css)
    } yield ()
  }

  def rollbackToFsacAwaitingAllocationFromFsacFailed(applicationId: String): Future[Unit] = {
    val exercisesToRemove = List("analysisExercise", "groupExercise", "leadershipExercise", "finalFeedback")
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
      _ <- fsacRepo.removeFsacEvaluation(applicationId)
      _ <- assessorAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- reviewerAssessmentScoresRepository.resetExercise(UniqueIdentifier(applicationId), exercisesToRemove)
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.ASSESSMENT_CENTRE, statuses)
      _ <- addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
    } yield ()
  }

  def rollbackToFsacAllocationConfirmedFromFsb(applicationId: String): Future[Unit] = {
    val exercisesToRemove = List("analysisExercise", "groupExercise", "leadershipExercise", "finalFeedback")
    val statuses = List(
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED,
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED,
      ProgressStatuses.ASSESSMENT_CENTRE_PASSED,
      ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION,
      ProgressStatuses.FSB_AWAITING_ALLOCATION
    )

    for {
      _ <- fsacRepo.removeFsacEvaluation(applicationId)
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
      ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED, ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED,
      ProgressStatuses.SIFT_ENTERED, ProgressStatuses.SIFT_FIRST_REMINDER,
      ProgressStatuses.SIFT_SECOND_REMINDER, ProgressStatuses.SIFT_READY)
    for {
      _ <- rollbackAppAndProgressStatus(applicationId, ApplicationStatus.PHASE3_TESTS, statusesToRollback)
      _ <- siftAnswersService.removeAnswers(applicationId)
      _ <- appSiftRepository.removeTestGroup(applicationId)
      _ <- phase3TestRepository.updateExpiryDate(applicationId, new DateTime().plusDays(1))
      _ <- phase3TestRepository.removeTestGroupEvaluation(applicationId)
      _ <- phase3TestRepository.removeReviewedCallbacks(token)
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
      evaluationOpt <- phase1TestRepo.findEvaluation(applicationId)
      _ <- updateCurrentSchemeStatus(applicationId, evaluationOpt)
    } yield ()
  }

  def enablePhase3CandidateToBeEvaluated(applicationId: String): Future[Unit] = {
    for {
      _ <- phase3TestRepository.updateExpiryDate(applicationId, new DateTime().plusDays(1))
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)
      _ <- appRepository.removeProgressStatuses(applicationId, List(ProgressStatuses.PHASE3_TESTS_EXPIRED))
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

  def setUsedForResults(applicationId: String, newUsedForResults: Boolean, token: String): Future[Unit] = {
    for {
      phase3TestGroupOpt <- phase3TestRepository.getTestGroup(applicationId)
      phase3TestGroup = phase3TestGroupOpt.getOrElse(throw UnexpectedException(s"Unable to find PHASE3 TestGroup for $applicationId"))
      newTests = phase3TestGroup.tests.map { test =>
        if(test.token == token) test.copy(usedForResults = newUsedForResults) else test }
      newPhase3TestGroup = phase3TestGroup.copy(tests = newTests)
      _ <- phase3TestRepository.insertOrUpdateTestGroup(applicationId, newPhase3TestGroup)
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
        "e-tray" -> phase2Evaluation,
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
