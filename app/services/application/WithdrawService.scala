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

import model.*
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{Amber, Green, Red, Withdrawn}
import model.Exceptions.*
import model.ProgressStatuses.*
import model.command.*
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType.*
import model.exchange.sift.SiftAnswersStatus
import model.persisted.*
import model.stc.{AuditEvents, DataStoreEvents, EmailEvents}
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.*
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.allocation.CandidateAllocationService
import services.events.EventsService
import services.sift.{ApplicationSiftService, SiftAnswersService}
import services.stc.{EventSink, StcEventService}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WithdrawService @Inject() (appRepository: GeneralApplicationRepository,
                                 cdRepository: ContactDetailsRepository,
                                 siftService: ApplicationSiftService,
                                 siftAnswersService: SiftAnswersService,
                                 schemesRepo: SchemeRepository,
                                 val eventsService: EventsService,
                                 candidateAllocationService: CandidateAllocationService,
                                 val eventService: StcEventService
                                )(implicit ec: ExecutionContext) extends EventSink with CurrentSchemeStatusHelper with Logging with Schemes {

  val Candidate_Role = "Candidate"

  def withdraw(applicationId: String, withdrawRequest: WithdrawRequest)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    (for {
      isSiftExpired <- siftService.isSiftExpired(applicationId)
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      candidate <- appRepository.find(applicationId).map(_.getOrElse(throw ApplicationNotFound(applicationId)))
      contactDetails <- cdRepository.find(candidate.userId)
    } yield {
      withdrawRequest match {
        case withdrawApp: WithdrawApplication =>
          withdrawFromApplication(applicationId, withdrawApp)(candidate, contactDetails)
        case withdrawScheme: WithdrawScheme =>
          if (isSiftExpired) throw SiftExpiredException("SIFT_PHASE has expired. Unable to withdraw")
          if (withdrawableSchemes(currentSchemeStatus).size == 1) {
            throw LastSchemeWithdrawException(s"Can't withdraw $applicationId from last scheme ${withdrawScheme.schemeId.value}")
          } else {
            withdrawFromScheme(applicationId, withdrawScheme)
          }
      }
    }) flatMap identity
  }

  private def removeFromAllEvents(applicationId: String, eligibleForReallocation: Boolean)
                                 (implicit hc: HeaderCarrier): Future[Unit] = {
    candidateAllocationService.allocationsForApplication(applicationId).flatMap { allocations =>
      candidateAllocationService.unAllocateCandidates(allocations.toList, eligibleForReallocation).map(_ => ())
    }
  }

  private def withdrawableSchemes(currentSchemeStatus: Seq[SchemeEvaluationResult]): Seq[SchemeEvaluationResult] = {
    currentSchemeStatus.filterNot(s => s.result == EvaluationResults.Red.toString || s.result == EvaluationResults.Withdrawn.toString)
  }

  private def withdrawFromApplication(applicationId: String, withdrawRequest: WithdrawApplication)
                                     (candidate: Candidate, cd: ContactDetails)(implicit hc: HeaderCarrier) = {
    appRepository.withdraw(applicationId, withdrawRequest).flatMap { _ =>
      removeFromAllEvents(applicationId, eligibleForReallocation = false).map { _ =>
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

    def buildLatestSchemeStatus(current: Seq[SchemeEvaluationResult], withdrawal: WithdrawScheme) = {
      calculateCurrentSchemeStatus(current, SchemeEvaluationResult(withdrawRequest.schemeId, EvaluationResults.Withdrawn.toString) :: Nil)
    }

    def maybeProgressToSiftEntered(applicationStatus: ApplicationStatus, schemeStatus: Seq[SchemeEvaluationResult]) = {
      val amberSchemes = schemeStatus.collect { case s if s.result == Amber.toString => s.schemeId }.toSet
      val greenSchemes = schemeStatus.collect { case s if s.result == Green.toString => s.schemeId }.toSet

      val schemesRequiringSiftForm = amberSchemes.isEmpty && greenSchemes.nonEmpty &&
        greenSchemes.intersect(schemesRepo.siftableSchemeIds.toSet).nonEmpty

      val shouldProgressToSift = applicationStatus == ApplicationStatus.PHASE1_TESTS_PASSED && schemesRequiringSiftForm

      val prefix = "[withdraw - maybeProgressToSiftEntered]"
      logger.debug(s"$prefix")
      logger.debug(s"$prefix schemeStatus=$schemeStatus")
      logger.debug(s"$prefix amberSchemes=$amberSchemes")
      logger.debug(s"$prefix greenSchemes=$greenSchemes")
      logger.debug(s"$prefix schemesRequiringSiftForm=$schemesRequiringSiftForm")
      logger.debug(s"$prefix shouldProgressToSift=$shouldProgressToSift")

      if (shouldProgressToSift) {
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_ENTERED).map { _ =>
          logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.SIFT_ENTERED}")
          AuditEvents.AutoProgressedToSift(
            Map("applicationId" -> applicationId, "reason" -> "after withdraw, candidate only has sift schemes")
          ) :: Nil
        }
      } else {
        logger.info(s"Candidate $applicationId withdrawing scheme will not be moved to " +
          s"${ProgressStatuses.SIFT_ENTERED}")
        Future.successful(())
      }
    }

    def maybeProgressToFSAC(applicationStatus: ApplicationStatus, schemeStatus: Seq[SchemeEvaluationResult],
                            latestProgressStatus: Option[ProgressStatus],
                            siftAnswersStatus: Option[SiftAnswersStatus.Value]) = {
      val amberSchemes = schemeStatus.collect { case s if s.result == Amber.toString => s.schemeId }.toSet
      val greenSchemes = schemeStatus.collect { case s if s.result == Green.toString => s.schemeId }.toSet

      val onlyNonSiftableSchemesLeft = amberSchemes.isEmpty && greenSchemes.nonEmpty &&
        (greenSchemes subsetOf schemesRepo.nonSiftableSchemeIds.toSet)

      // only schemes with no evaluation requirement and form filled in. This set of schemes all require you to fill in a form
      val onlyNoSiftEvaluationRequiredSchemesWithFormFilled = siftAnswersStatus.contains(SiftAnswersStatus.SUBMITTED) &&
        (greenSchemes subsetOf schemesRepo.noSiftEvaluationRequiredSchemeIds.toSet)

      val shouldProgressToFSAC = (applicationStatus == ApplicationStatus.PHASE1_TESTS_PASSED || applicationStatus == ApplicationStatus.SIFT) &&
        (onlyNonSiftableSchemesLeft || onlyNoSiftEvaluationRequiredSchemesWithFormFilled) &&
        !latestProgressStatus.contains(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)

      val prefix = "[withdraw - maybeProgressToFSAC]"
      logger.debug(s"$prefix")
      logger.debug(s"$prefix schemeStatus=$schemeStatus")
      logger.debug(s"$prefix amberSchemes=$amberSchemes")
      logger.debug(s"$prefix greenSchemes=$greenSchemes")
      logger.debug(s"$prefix onlyNonSiftableSchemesLeft=$onlyNonSiftableSchemesLeft")
      logger.debug(s"$prefix onlyNoSiftEvaluationRequiredSchemesWithFormFilled=$onlyNoSiftEvaluationRequiredSchemesWithFormFilled")
      logger.debug(s"$prefix shouldProgressToFSAC=$shouldProgressToFSAC")

      // sdip faststream candidate who is awaiting allocation to an assessment centre or is in sift completed (not yet picked
      // up by the assessment centre invite job) and has withdrawn from all fast stream schemes (or been sifted out) and only has sdip left
      val shouldProgressSdipFaststreamCandidateToFsb = greenSchemes == Set(Scheme.SdipId) && schemeStatus.size > 1 &&
        (latestProgressStatus.contains(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION) ||
          latestProgressStatus.contains(ProgressStatuses.SIFT_COMPLETED))

      if (shouldProgressSdipFaststreamCandidateToFsb) {
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.FSB_AWAITING_ALLOCATION).map { _ =>
          logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.FSB_AWAITING_ALLOCATION}")
          AuditEvents.AutoProgressedToFSB(Map("applicationId" -> applicationId, "reason" -> "last fast stream scheme withdrawn")) :: Nil
        }
      } else if (shouldProgressToFSAC) {
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION).map { _ =>
          logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION}")
          AuditEvents.AutoProgressedToFSAC(Map("applicationId" -> applicationId, "reason" -> "last siftable scheme withdrawn")) :: Nil
        }
      } else {
        logger.info(s"Candidate $applicationId withdrawing scheme will not be moved to " +
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
                                 progressResponse: ProgressResponse, applicationStatus: ApplicationStatus,
                                 siftAnswersStatus: Option[SiftAnswersStatus.Value]) = {
      val greenSchemes = schemeStatus.collect { case s if s.result == Green.toString => s.schemeId }.toSet

      val numericTestRequired = greenSchemes.exists(s => schemesRepo.numericTestSiftRequirementSchemeIds.contains(s))
      val numericTestCompleted = progressResponse.siftProgressResponse.siftTestResultsReceived
      val numericTestRequirementSatisfied = requirementSatisfied(numericTestRequired, numericTestCompleted)

      val formMustBeFilledIn = greenSchemes.exists(s => schemesRepo.formMustBeFilledInSchemeIds.contains(s))
      val formsHaveBeenFilledIn = siftAnswersStatus.contains(SiftAnswersStatus.SUBMITTED)
      val formRequirementSatisfied = requirementSatisfied(formMustBeFilledIn, formsHaveBeenFilledIn)

      // we have at least one scheme that needs an evaluation
      val siftEvaluationNeeded = greenSchemes.exists(s => schemesRepo.siftableAndEvaluationRequiredSchemeIds.contains(s))

      val shouldProgressCandidate = applicationStatus == ApplicationStatus.SIFT &&
        progressResponse.siftProgressResponse.siftEntered &&
        !progressResponse.siftProgressResponse.siftReady && !progressResponse.siftProgressResponse.siftCompleted &&
        numericTestRequirementSatisfied && formRequirementSatisfied && siftEvaluationNeeded

      if (shouldProgressCandidate) {
        logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.SIFT_READY}")
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY).map { _ => }
      } else {
        logger.info(s"Candidate $applicationId withdrawing scheme will not be moved to ${ProgressStatuses.SIFT_READY}")
        Future.successful(())
      }
    }

    def maybeRemoveCandidateFromFsb(appStatusDetails: ApplicationStatusDetails) = {
      val isFsbCandidate = appStatusDetails.status == ApplicationStatus.FSB
      val isInFsbAllocationConfirmed = appStatusDetails.latestProgressStatus.contains(ProgressStatuses.FSB_ALLOCATION_CONFIRMED)
      val isInFsbAllocationUnconfirmed = appStatusDetails.latestProgressStatus.contains(ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED)

      (for {
        // Look for a candidate allocation record for the scheme that has been withdrawn if we are dealing with a fsb candidate
        // who is allocated to the fsb (confirmed or unconfirmed)
        candidateAllocationOpt <- if (isFsbCandidate && (isInFsbAllocationConfirmed || isInFsbAllocationUnconfirmed)) {
          candidateAllocationService.getCandidateAllocation(applicationId, withdrawRequest.schemeId)
        } else {
          Future.successful(None)
        }
      } yield {
        candidateAllocationOpt.map { candidateAllocation =>
          // If we are in this section then we have found an allocation record for the scheme that has been withdrawn
          val updated = candidateAllocation.copy(
            status = AllocationStatuses.REMOVED,
            removeReason = Some(s"Candidate withdrew scheme (${withdrawRequest.schemeId.value})")
          )

          for {
            _ <- candidateAllocationService.saveCandidateAllocations(Seq(updated))
            _ <- appRepository.removeProgressStatuses(
              applicationId,
              List(ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED, ProgressStatuses.FSB_ALLOCATION_CONFIRMED)
            )
            _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.FSB_AWAITING_ALLOCATION)
          } yield ()
        }.getOrElse(Future.successful(()))
      }).flatten
    }

    // Move the candidate to ELIGIBLE_FOR_JOB_OFFER if the candidate is in FSAC or FSB and after the scheme has been withdrawn
    // the candidate will be offered a job if the next scheme preference is green and does not need a FSB
    def maybeOfferJobForFsacOrFsbCandidate(schemeStatus: Seq[SchemeEvaluationResult],
                                           applicationStatus: ApplicationStatus,
                                           progress: ProgressResponse
                                          ) = {
      val amberOrGreenPreferences = schemeStatus.zipWithIndex.filterNot { case (result, _) =>
        result.result == Red.toString || result.result == Withdrawn.toString
      }

      // Candidate should be eligible for job offer if the 1st residual preference is green and does not need a fsb
      val firstResidualPreferenceIsEligibleForJobOffer = (amberOrGreenPreferences match {
        case Nil => None
        case list => Some(list.minBy { case (_, index) => index }._1)
      }).exists(ser => ser.result == Green.toString && !schemesRepo.schemeRequiresFsb(ser.schemeId))

      val prefix = "[withdraw - maybeOfferJob]"
      logger.debug(s"$prefix")
      logger.debug(s"$prefix firstResidualPreferenceIsEligibleForJobOffer=$firstResidualPreferenceIsEligibleForJobOffer")
      logger.debug(s"$prefix applicationStatus=$applicationStatus")
      logger.debug(s"$prefix progress.assessmentCentre.passed=${progress.assessmentCentre.passed}")

      val shouldProgressCandidate =
        (applicationStatus == ApplicationStatus.ASSESSMENT_CENTRE || applicationStatus == ApplicationStatus.FSB) &&
          firstResidualPreferenceIsEligibleForJobOffer && progress.assessmentCentre.passed
      if (shouldProgressCandidate) {
        logger.info(s"Candidate $applicationId withdrawing scheme will be moved to ${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}")
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER).map { _ => }
      } else {
        logger.info(s"Candidate $applicationId withdrawing scheme will not be moved to ${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}")
        Future.successful(())
      }
    }

    for {
      currentSchemeStatus <- appRepository.getCurrentSchemeStatus(applicationId)
      latestSchemeStatus = buildLatestSchemeStatus(currentSchemeStatus, withdrawRequest)
      appStatusDetails <- appRepository.findStatus(applicationId)

      // We attempt this before withdrawing the scheme in case there is a problem so the scheme doesn't get withdrawn
      _ <- maybeRemoveCandidateFromFsb(appStatusDetails)

      _ <- appRepository.withdrawScheme(applicationId, withdrawRequest, latestSchemeStatus)

      _ <- maybeProgressToSiftEntered(appStatusDetails.status, latestSchemeStatus)

      siftAnswersStatus <- siftAnswersService.findSiftAnswersStatus(applicationId)
      _ <- maybeProgressToFSAC(appStatusDetails.status, latestSchemeStatus, appStatusDetails.latestProgressStatus, siftAnswersStatus)

      progress <- appRepository.findProgress(applicationId)
      _ <- maybeProgressToSiftReady(latestSchemeStatus, progress, appStatusDetails.status, siftAnswersStatus)

      _ <- maybeOfferJobForFsacOrFsbCandidate(latestSchemeStatus, appStatusDetails.status, progress)
    } yield {
      DataStoreEvents.SchemeWithdrawn(applicationId, withdrawRequest.withdrawer) ::
        AuditEvents.SchemeWithdrawn(Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)) ::
        Nil
    }
  }
  //scalastyle:on
}
