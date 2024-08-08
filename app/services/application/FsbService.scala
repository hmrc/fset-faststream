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

import com.google.inject.name.Named
import common.FutureEx
import connectors.OnlineTestEmailClient

import javax.inject.{Inject, Singleton}
import model.EvaluationResults.{Amber, Green, Red, Withdrawn}
import model.ProgressStatuses._
import model._
import model.command.ApplicationForProgression
import model.exchange.{ApplicationResult, FsbScoresAndFeedback}
import model.Exceptions.SchemeWithdrawnException
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{FsbSchemeResult, SchemeEvaluationResult}
import play.api.Logging
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.{CurrentSchemeStatusHelper, SchemeRepository}
import services.application.DSSchemeIds._
import services.scheme.SchemePreferencesService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class FsbService @Inject() (applicationRepo: GeneralApplicationRepository,
                            contactDetailsRepo: ContactDetailsRepository,
                            fsbRepo: FsbRepository,
                            schemeRepo: SchemeRepository,
                            schemePreferencesService: SchemePreferencesService,
                            @Named("CSREmailClient") val emailClient: OnlineTestEmailClient //TODO:changed type
                           )(implicit ec: ExecutionContext) extends CurrentSchemeStatusHelper with Logging {

  val logPrefix = "[FsbEvaluation]"

  def nextFsbCandidateReadyForEvaluation: Future[Option[UniqueIdentifier]] = {
    fsbRepo.nextApplicationReadyForFsbEvaluation
  }

  def processApplicationsFailedAtFsb(batchSize: Int): Future[SerialUpdateResult[ApplicationForProgression]] = {
    fsbRepo.nextApplicationFailedAtFsb(batchSize).flatMap { applications =>
      logger.warn(s"FSB failure job processing appIds: ${applications.map(_.applicationId).mkString(",")} to " +
        s"${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED.toString}")
      val updates = FutureEx.traverseSerial(applications) { application =>
        FutureEx.futureToEither(application,
          applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
        )
      }
      updates.map(SerialUpdateResult.fromEither)
    }
  }

  def processApplicationFailedAtFsb(applicationId: String): Future[SerialUpdateResult[ApplicationForProgression]] = {
    fsbRepo.nextApplicationFailedAtFsb(applicationId).flatMap { applications =>
      val updates = FutureEx.traverseSerial(applications) { application =>
        FutureEx.futureToEither(application,
          applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
        )
      }
      updates.map(SerialUpdateResult.fromEither)
    }
  }

  def evaluateFsbCandidate(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    logger.debug(s"$logPrefix running for application $applicationId")

    val appId = applicationId.toString()
    for {
      fsbEvaluation <- fsbRepo.findByApplicationId(appId).map(_.map(_.evaluation.result))
      schemePreferences <- schemePreferencesService.find(applicationId.toString())
      currentSchemeStatusUnfiltered <- applicationRepo.getCurrentSchemeStatus(appId)
      currentSchemeStatus = currentSchemeStatusUnfiltered.filter(res => schemePreferences.schemes.contains(res.schemeId))
      firstPreference = firstResidualPreference(currentSchemeStatus)
      _ <- passOrFailFsb(appId, fsbEvaluation, firstPreference, currentSchemeStatus)
    } yield {
      logger.debug(s"$logPrefix written to DB... applicationId = $appId")
    }
  }

  private def getResultsForScheme(appId: String, schemeId: SchemeId, results: Seq[SchemeEvaluationResult]): SchemeEvaluationResult = {
    import DSSchemeIds._
    val r = schemeId match {
      //Code removed to replace EAC_DS with GES_DS
/*
      case DiplomaticAndDevelopmentEconomics =>
        val res = Seq(
          results.find(r => EacSchemes.contains(r.schemeId)),
          results.find(_.schemeId == DiplomaticAndDevelopment)
        ).flatten
        logger.info(s"$logPrefix [getResultsForScheme] FSB results for GES-DS: $res")
        require(res.size == 2 || res.exists(_.result == Red.toString),
          s"$DiplomaticAndDevelopmentEconomics requires EAC && FCO test results - $appId")
        res
      case GovernmentEconomicsService =>
        results.find(r => EacSchemes.contains(r.schemeId)).toSeq
 */
      case _ =>
        results.find(_.schemeId == schemeId).toSeq
    }
    if (r.forall(_.result == Green.toString)) {
      SchemeEvaluationResult(schemeId, Green.toString)
    } else if (r.exists(_.result == Red.toString)) {
      SchemeEvaluationResult(schemeId, Red.toString)
    } else {
      throw new Exception(s"Unexpected result in FSB scheme $schemeId fsbEvaluation ($r) - $appId")
    }
  }

  private def canEvaluateNextWithExistingResults(
                                                  currentSchemeStatus: Seq[SchemeEvaluationResult],
                                                  newFirstPreference: Option[SchemeEvaluationResult],
                                                  fsbEvaluation: Seq[SchemeEvaluationResult]
                                                ): Boolean = {
    def schemeWasEvaluatedBefore(id: SchemeId): Boolean = {
      currentSchemeStatus.map(_.schemeId).takeWhile(_ != newFirstPreference.get.schemeId).contains(id)
    }
    newFirstPreference.map(_.schemeId) match {
      //Code removed to replace EAC_DS with GES_DS
//      case Some(DiplomaticAndDevelopment) if fsbEvaluation.exists(_.schemeId == DiplomaticAndDevelopment) => true
//      case Some(GovernmentEconomicsService) if fsbEvaluation.exists(r => EacSchemes.contains(r.schemeId)) => true
//      case Some(DiplomaticAndDevelopmentEconomics) if schemeWasEvaluatedBefore(GovernmentEconomicsService) => true
//      case Some(DiplomaticAndDevelopmentEconomics) if schemeWasEvaluatedBefore(DiplomaticAndDevelopment) => true
      case _ =>
        logger.debug(s"$logPrefix canEvaluateNextWithExistingResults = false")
        false
    }
  }

  // scalastyle:off method.length
  protected[application] def passOrFailFsb(appId: String,
                                           fsbEvaluationOpt: Option[Seq[SchemeEvaluationResult]],
                                           firstResidualPreferenceOpt: Option[SchemeEvaluationResult],
                                           currentSchemeStatus: Seq[SchemeEvaluationResult])(implicit hc: HeaderCarrier): Future[Unit] = {

    logger.debug(s"$logPrefix fsbEvaluation = $fsbEvaluationOpt")
    logger.debug(s"$logPrefix firstResidualPreferenceOpt = $firstResidualPreferenceOpt")
    logger.debug(s"$logPrefix currentSchemeStatus = $currentSchemeStatus")

    require(fsbEvaluationOpt.isDefined, s"Evaluation for scheme must be defined to reach this stage, unexpected error for $appId")
    require(firstResidualPreferenceOpt.isDefined, s"First residual preference must be defined to reach this stage, unexpected error for $appId")

    val firstResidualInEvaluation = getResultsForScheme(appId, firstResidualPreferenceOpt.get.schemeId, fsbEvaluationOpt.get)
    logger.debug(s"$logPrefix firstResidualInEvaluation = $firstResidualInEvaluation")

    if (firstResidualInEvaluation.result == Green.toString) {
      // These futures need to be in sequence one after the other
      (for {
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, FSB_PASSED)
        _ = logger.debug(s"$logPrefix successfully added $FSB_PASSED for $appId")
      } yield for {
        // There are no notifications before going to eligible but we want audit trail to show we've passed
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ELIGIBLE_FOR_JOB_OFFER)
        _ = logger.debug(s"$logPrefix successfully added $ELIGIBLE_FOR_JOB_OFFER for $appId")
      } yield ()).flatMap(identity)
    } else {
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, FSB_FAILED).flatMap { _ =>
        logger.debug(s"$logPrefix successfully added $FSB_FAILED for $appId")
        val newCurrentSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, fsbEvaluationOpt.get ++ Seq(firstResidualInEvaluation))
        logger.debug(s"$logPrefix newCurrentSchemeStatus = $newCurrentSchemeStatus")

        val newFirstPreference = if (schemesRemainThatAreNotFailed(newCurrentSchemeStatus)) {
          firstResidualPreference(newCurrentSchemeStatus)
        } else { Option.empty[SchemeEvaluationResult] }
        logger.debug(s"$logPrefix newFirstPreference = $newFirstPreference")

        fsbRepo.updateCurrentSchemeStatus(appId, newCurrentSchemeStatus).flatMap { _ =>
//          if (canEvaluateNextWithExistingResults(currentSchemeStatus, newFirstPreference, fsbEvaluation.get)) {
//            passOrFailFsb(appId, fsbEvaluation, newFirstPreference, newCurrentSchemeStatus)
//          } else {
            // If there is a new first preference that is G or A then we do not mark all as failed
            maybeMarkAsFailedAll(appId, newFirstPreference).flatMap(_ =>
              maybeNotifyOnFailNeedNewFsb(appId, newCurrentSchemeStatus)
            )
//          }
        }
      }
    }
  }

  private def schemesRemainThatAreNotFailed(newCurrentSchemeStatus: Seq[SchemeEvaluationResult]) = {
    val notFailedSchemeIds = newCurrentSchemeStatus.filter( schemeEvaluationResult =>
      schemeEvaluationResult.result == Green.toString || schemeEvaluationResult.result == Amber.toString
    ).map(_.schemeId)
    val result = notFailedSchemeIds.nonEmpty
    logger.debug(s"$logPrefix schemesRemainThatAreNotFailed = $result")
    result
  }

  private def maybeNotifyOnFailNeedNewFsb(appId: String, newCurrentSchemeStatus: Seq[SchemeEvaluationResult])(
    implicit hc: HeaderCarrier): Future[Unit] = {
    logger.debug(s"$logPrefix checking to see if we should send failure email to candidate $appId," +
      s"newCurrentSchemeStatus=$newCurrentSchemeStatus")
    if (firstResidualPreference(newCurrentSchemeStatus).nonEmpty) {
      for {
        (candidate, contactDetails) <- retrieveCandidateDetails(appId)
        _ <- emailClient.notifyCandidateOnFinalFailure(contactDetails.email, candidate.name)
      } yield {
        logger.debug(s"$logPrefix successfully sent failure email to candidate $appId")
      }
    } else {
      logger.debug(s"$logPrefix not going to send failure email to candidate $appId")
      Future.successful(())
    }
  }

  private def retrieveCandidateDetails(applicationId: String)(implicit hc: HeaderCarrier) = {
    applicationRepo.find(applicationId).flatMap {
      case Some(app) => contactDetailsRepo.find(app.userId).map { cd => (app, cd) }
      case None => sys.error(s"Can't find application $applicationId")
    }
  }

  private def maybeMarkAsFailedAll(appId: String, newFirstResidualPreference: Option[SchemeEvaluationResult]): Future[Unit] = {
    logger.debug(s"$logPrefix maybeMarkAsFailedAll - appId=$appId, newFirstResidualPreference=$newFirstResidualPreference")
    if (newFirstResidualPreference.isEmpty) {
      for {
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ALL_FSBS_AND_FSACS_FAILED)
      } yield {
        logger.debug(s"$logPrefix maybeMarkAsFailedAll - successfully added $ALL_FSBS_AND_FSACS_FAILED for $appId")
      }
    } else {
      logger.debug(s"$logPrefix maybeMarkAsFailedAll - did not add $ALL_FSBS_AND_FSACS_FAILED for $appId")
      Future.successful(())
    }
  }

  def saveResults(schemeId: SchemeId, applicationResults: List[ApplicationResult]): Future[List[Unit]] = {
    Future.sequence(
      applicationResults.map(applicationResult => saveResult(schemeId, applicationResult))
    )
  }

  private def saveResult(schemeId: SchemeId, applicationResult: ApplicationResult): Future[Unit] = {
    for {
      css <- applicationRepo.getCurrentSchemeStatus(applicationResult.applicationId)
      _ = if (css.find(_.schemeId == schemeId).exists(_.result == Withdrawn.toString)) {
        throw SchemeWithdrawnException(s"Scheme $schemeId has been withdrawn so cannot save FSB result for ${applicationResult.applicationId}")
      }
      _ <- saveResult(applicationResult.applicationId, SchemeEvaluationResult(schemeId, applicationResult.result))
    } yield ()
  }

  def saveResult(applicationId: String, schemeEvaluationResult: SchemeEvaluationResult): Future[Unit] = {
    for {
      _ <- fsbRepo.saveResult(applicationId, schemeEvaluationResult)
      _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId, FSB_RESULT_ENTERED)
    } yield ()
  }

  def findScoresAndFeedback(applicationId: String): Future[Option[FsbScoresAndFeedback]] = {
    for {
      scoresAndFeedbackOpt <- fsbRepo.findScoresAndFeedback(applicationId)
    } yield {
      scoresAndFeedbackOpt.map( saf => FsbScoresAndFeedback(saf.overallScore, saf.feedback) )
    }
  }

  def saveScoresAndFeedback(applicationId: String, data: FsbScoresAndFeedback): Future[Unit] = {
    for {
      _ <- fsbRepo.saveScoresAndFeedback(applicationId, ScoresAndFeedback(data.overallScore, data.feedback))
    } yield ()
  }

  def findByApplicationIdsAndFsbType(applicationIds: List[String], mayBeFsbType: Option[String]): Future[List[FsbSchemeResult]] = {
    val maybeSchemeId = mayBeFsbType.flatMap { fsb =>
      Try(schemeRepo.getSchemeForFsb(fsb)).toOption
    }.map(_.id)
    val maybeSchemeIds = maybeSchemeId match {
      case None => List(None)
//      case Some(schemeId) if schemeId == DiplomaticAndDevelopmentEconomics =>
//        List(Some(DiplomaticAndDevelopment), Some(GovernmentEconomicsService))
      case Some(schemeId) => List(Some(schemeId))
    }

    Future.sequence(maybeSchemeIds.map { maybeSId =>
      findByApplicationIdsAndScheme(applicationIds, maybeSId)
    }).map(_.flatten)
  }

  def findByApplicationIdsAndScheme(applicationIds: List[String], mayBeSchemeId: Option[SchemeId]): Future[List[FsbSchemeResult]] = {
    fsbRepo.findByApplicationIds(applicationIds, mayBeSchemeId)
  }
}

object DSSchemeIds {
  // We should rename EAC_DS fsb to EAC_FCO
  val DiplomaticAndDevelopmentEconomics = SchemeId("DiplomaticAndDevelopmentEconomics") // fsb_type: EAC_DS, code: GES-DS
  val GovernmentEconomicsService = SchemeId("GovernmentEconomicsService")   // fsb_type: EAC,    code: GES
  val DiplomaticAndDevelopment = SchemeId("DiplomaticAndDevelopment")       // fsb_type: FCO,    code: DS

  val EacSchemes = List(DiplomaticAndDevelopmentEconomics, GovernmentEconomicsService)
}
