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
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ Green, Red }
import model.Exceptions.{ ApplicationNotFound, LastSchemeWithdrawException, NotFoundException, PassMarkEvaluationNotFound }
import model.ProgressStatuses.ProgressStatus
import model.command.{ WithdrawApplication, WithdrawRequest, WithdrawScheme }
import model.stc.StcEventTypes._
import model.stc.{ AuditEvents, DataStoreEvents, EmailEvents }
import model.exchange.passmarksettings.{ Phase1PassMarkSettings, Phase3PassMarkSettings }
import model.persisted.{ ContactDetails, PassmarkEvaluation, SchemeEvaluationResult }
import model._
import model.exchange.SchemeEvaluationResultWithFailureDetails
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
import scheduler.fixer.FixBatch
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.stc.{ EventSink, StcEventService }
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.immutable.ListMap
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
  val phase1TestRepo = repositories.phase1TestRepository
  val phase2TestRepository = repositories.phase2TestRepository
  val phase1EvaluationRepository = faststreamPhase1EvaluationRepository
  val phase2EvaluationRepository = faststreamPhase2EvaluationRepository
  val phase3EvaluationRepository = faststreamPhase3EvaluationRepository
  val fsacRepo = assessmentCentreRepository
  val fsbRepo = fsbRepository
  val civilServiceExperienceDetailsRepo = civilServiceExperienceDetailsRepository
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
  def evaluateP3ResultService: EvaluateOnlineTestResultService[Phase3PassMarkSettings]
  def schemesRepo: SchemeRepository
  def phase1TestRepo: Phase1TestRepository
  def phase2TestRepository: Phase2TestRepository
  def phase1EvaluationRepository: Phase1EvaluationMongoRepository
  def phase2EvaluationRepository: Phase2EvaluationMongoRepository
  def phase3EvaluationRepository: Phase3EvaluationMongoRepository
  def fsacRepo: AssessmentCentreRepository
  def fsbRepo: FsbRepository
  def civilServiceExperienceDetailsRepo: CivilServiceExperienceDetailsRepository

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
    }) flatMap identity

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

  //scalastyle:off method.length cyclomatic.complexity
  private def withdrawFromScheme(applicationId: String, withdrawRequest: WithdrawScheme) = {

    def buildLatestSchemeStatus(current: Seq[SchemeEvaluationResult], withdrawal: WithdrawScheme)  = {
      calculateCurrentSchemeStatus(current, SchemeEvaluationResult(withdrawRequest.schemeId, EvaluationResults.Withdrawn.toString) :: Nil)
    }

    def maybeProgressToFSAC(schemeStatus: Seq[SchemeEvaluationResult], latestProgressStatus: Option[ProgressStatus]) = {
      val greenSchemes = schemeStatus.collect { case s if s.result == Green.toString => s.schemeId }.toSet

      // we only have generalist or human resources
      val onlyNonSiftableSchemesLeft = greenSchemes subsetOf schemesRepo.nonSiftableSchemeIds.toSet

      // only schemes with no evaluation requirement and form filled in (SIFT_READY) This set of schemes all require you
      // to fill in a form
      val onlyNoSiftEvaluationRequiredSchemesWithFormFilled = latestProgressStatus.contains(ProgressStatuses.SIFT_READY) &&
        (greenSchemes subsetOf schemesRepo.noSiftEvaluationRequiredSchemeIds.toSet)

      // can only progress to FSAC if I am a sdip faststream candidate still in the running for sdip plus at least
      // 1 fast stream scheme and that faststream scheme is a scheme that does not need evaluation and I have filled
      // in the sdip form and submitted (SIFT_READY)
      val canProgressSdipFaststreamCandidate = greenSchemes.contains(SchemeId("Sdip")) && greenSchemes.size > 1 &&
        latestProgressStatus.contains(ProgressStatuses.SIFT_READY) &&
        (greenSchemes.filterNot(s => s == SchemeId("Sdip")) subsetOf schemesRepo.noSiftEvaluationRequiredSchemeIds.toSet)

      val shouldProgressToFSAC = onlyNonSiftableSchemesLeft || onlyNoSiftEvaluationRequiredSchemesWithFormFilled ||
        canProgressSdipFaststreamCandidate

      // sdip faststream candidate who is awaiting allocation to an assessment centre and has withdrawn from
      // all fast stream schemes and only has sdip left
      val shouldRollbackSdipFaststreamCandidate = greenSchemes == Set(SchemeId("Sdip")) && schemeStatus.size > 1 &&
        latestProgressStatus.contains(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)

      if (shouldRollbackSdipFaststreamCandidate) {
        appRepository.removeProgressStatuses(applicationId,
          List(ProgressStatuses.SIFT_COMPLETED, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION))
      } else if (shouldProgressToFSAC) {
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

  def convertToFastStreamRouteWithFastpassFromOnlineTestsExpired(applicationId: String, fastPass: Int): Future[Unit] = {
    for {
      _ <- appRepository.updateApplicationRoute(applicationId, ApplicationRoute.SdipFaststream, ApplicationRoute.Faststream)
      _ <- civilServiceExperienceDetailsRepo.update(applicationId, CivilServiceExperienceDetails(
        applicable = true, Some(CivilServiceExperienceType.DiversityInternship), Some(Seq(InternshipType.SDIPCurrentYear)),
        Some(true), None, certificateNumber = Some(fastPass.toString)))
      _ <- phase1TestRepo.removeTestGroup(applicationId)
    } yield ()
  }

  private def rollbackAppAndProgressStatus(applicationId: String,
                                           applicationStatus: ApplicationStatus,
                                           statuses: List[ProgressStatuses.ProgressStatus]) = {
    for {
      _ <- appRepository.updateStatus(applicationId, applicationStatus)
      _ <- appRepository.removeProgressStatuses(applicationId, statuses)
    } yield ()
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

  def currentSchemeStatusWithFailureDetails(applicationId: String): Future[Seq[SchemeEvaluationResultWithFailureDetails]] = {

    def getOnlineTestEvaluation(repository: OnlineTestEvaluationRepository) =
      repository.getPassMarkEvaluation(applicationId).map(_.result).recoverWith { case _ => Future.successful(Nil) }

    for {
      phase1Evaluation <- getOnlineTestEvaluation(phase1EvaluationRepository)
      phase2Evaluation <- getOnlineTestEvaluation(phase2EvaluationRepository)
      phase3Evaluation <- getOnlineTestEvaluation(phase3EvaluationRepository)
      fsacEvaluation <- fsacRepo.getFsacEvaluatedSchemes(applicationId).map(_.getOrElse(Nil).toList)
      fsbEvaluation <- fsbRepo.findByApplicationId(applicationId).map(_.map(_.evaluation.result).getOrElse(Nil))
      evaluations = ListMap(
        "online tests" -> phase1Evaluation,
        "e-tray" -> phase2Evaluation,
        "video interview" -> phase3Evaluation,
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
