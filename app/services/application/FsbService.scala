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
import connectors.{ CSREmailClient, EmailClient }
import model.EvaluationResults.{ Green, Red }
import model.ProgressStatuses._
import model._
import model.command.ApplicationForProgression
import model.exchange.{ ApplicationResult, FsbScoresAndFeedback }
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{ FsbSchemeResult, SchemeEvaluationResult }
import play.api.Logger
import repositories.application.GeneralApplicationMongoRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.{ FsbMongoRepository, FsbRepository }
import repositories.{ CurrentSchemeStatusHelper, SchemeRepository, SchemeYamlRepository }
import services.application.DSSchemeIds._
import services.scheme.SchemePreferencesService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import uk.gov.hmrc.http.HeaderCarrier

object FsbService extends FsbService {
  override val applicationRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
  override val contactDetailsRepo: ContactDetailsRepository = repositories.faststreamContactDetailsRepository
  override val fsbRepo: FsbMongoRepository = repositories.fsbRepository
  override val schemeRepo: SchemeYamlRepository.type = SchemeYamlRepository
  override val emailClient: EmailClient = CSREmailClient
  override val schemePreferencesService: SchemePreferencesService = SchemePreferencesService
}

trait FsbService extends CurrentSchemeStatusHelper {
  val applicationRepo: GeneralApplicationMongoRepository
  val contactDetailsRepo: ContactDetailsRepository
  val fsbRepo: FsbRepository
  val schemeRepo: SchemeRepository
  val emailClient: EmailClient
  val schemePreferencesService: SchemePreferencesService

  val logPrefix = "[FsbEvaluation]"

  def nextFsbCandidateReadyForEvaluation: Future[Option[UniqueIdentifier]] = {
    fsbRepo.nextApplicationReadyForFsbEvaluation
  }

  def processApplicationsFailedAtFsb(batchSize: Int): Future[SerialUpdateResult[ApplicationForProgression]] = {
    fsbRepo.nextApplicationFailedAtFsb(batchSize).flatMap { applications =>
      val updates = FutureEx.traverseSerial(applications) { application =>
        FutureEx.futureToEither(application,
          applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
        )
      }

      updates.map(SerialUpdateResult.fromEither)
    }
  }

  def evaluateFsbCandidate(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {

    Logger.debug(s"$logPrefix running for application $applicationId")

    val appId = applicationId.toString()

    for {
      fsbEvaluation <- fsbRepo.findByApplicationId(appId).map(_.map(_.evaluation.result))
      schemePreferences <- schemePreferencesService.find(applicationId.toString())
      currentSchemeStatusUnfiltered <- applicationRepo.getCurrentSchemeStatus(appId)
      currentSchemeStatus = currentSchemeStatusUnfiltered.filter(res => schemePreferences.schemes.contains(res.schemeId))
      firstPreference = firstResidualPreference(currentSchemeStatus)
      _ <- passOrFailFsb(appId, fsbEvaluation, firstPreference, currentSchemeStatus)
    } yield {
      Logger.debug(s"$logPrefix written to DB... applicationId = $appId")
    }
  }

  private def getResultsForScheme(schemeId: SchemeId, results: Seq[SchemeEvaluationResult]): SchemeEvaluationResult = {
    import DSSchemeIds._
    val r = schemeId match {
      case DiplomaticServiceEconomists =>
        val res = Seq(
          results.find(r => EacSchemes.contains(r.schemeId)),
          results.find(_.schemeId == DiplomaticService)
        ).flatten
        Logger.info(s">>>>>>> Results for GES-DS: $res")
        require(res.size == 2 || res.exists(_.result == Red.toString), s"$DiplomaticServiceEconomists require EAC && FCO test results")
        res
      case GovernmentEconomicsService =>
        results.find(r => EacSchemes.contains(r.schemeId)).toSeq
      case _ =>
        results.find(_.schemeId == schemeId).toSeq
    }
    if (r.forall(_.result == Green.toString)) {
      SchemeEvaluationResult(schemeId, Green.toString)
    } else if (r.exists(_.result == Red.toString)) {
      SchemeEvaluationResult(schemeId, Red.toString)
    } else {
      throw new Exception(s"Unexpected result in FSB scheme $schemeId fsbEvaluation ($r)")
    }
  }

  private def canEvaluateNextWithExistingResults(
    currentSchemeStatus: Seq[SchemeEvaluationResult],
    newFirstScheme: Option[SchemeEvaluationResult],
    fsbEvaluation: Seq[SchemeEvaluationResult]
  ): Boolean = {
    def schemeWasEvaluatedBefore(id: SchemeId): Boolean = {
      currentSchemeStatus.map(_.schemeId).takeWhile(_ != newFirstScheme.get).contains(id)
    }
    newFirstScheme.map(_.schemeId) match {
      case Some(DiplomaticService) if fsbEvaluation.exists(_.schemeId == DiplomaticService) => true
      case Some(GovernmentEconomicsService) if fsbEvaluation.exists(r => EacSchemes.contains(r.schemeId)) => true
      case Some(DiplomaticServiceEconomists) if schemeWasEvaluatedBefore(GovernmentEconomicsService) => true
      case Some(DiplomaticServiceEconomists) if schemeWasEvaluatedBefore(DiplomaticService) => true
      case _ => false
    }
  }

  private def passOrFailFsb(appId: String,
    fsbEvaluation: Option[Seq[SchemeEvaluationResult]],
    firstResidualPreferenceOpt: Option[SchemeEvaluationResult],
    currentSchemeStatus: Seq[SchemeEvaluationResult])(implicit hc: HeaderCarrier): Future[Unit] = {

    require(fsbEvaluation.isDefined, "Evaluation for scheme must be defined to reach this stage, unexpected error.")
    require(firstResidualPreferenceOpt.isDefined, "First residual preference must be defined to reach this stage, unexpected error.")

    val firstResidualInEvaluation = getResultsForScheme(firstResidualPreferenceOpt.get.schemeId, fsbEvaluation.get)

    if (firstResidualInEvaluation.result == Green.toString) {
      for {
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, FSB_PASSED)
        // There are no notifications before going to eligible but we want audit trail to show we've passed
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ELIGIBLE_FOR_JOB_OFFER)
      } yield ()

    } else {
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, FSB_FAILED).flatMap { _ =>
        val newCurrentSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, fsbEvaluation.get ++ Seq(firstResidualInEvaluation))
        val newFirstPreference = firstResidualPreference(newCurrentSchemeStatus)
        fsbRepo.updateCurrentSchemeStatus(appId, newCurrentSchemeStatus).flatMap { _ =>
          if (canEvaluateNextWithExistingResults(currentSchemeStatus, newFirstPreference, fsbEvaluation.get)) {
            passOrFailFsb(appId, fsbEvaluation, newFirstPreference, newCurrentSchemeStatus)
          } else {
            maybeMarkAsFailedAll(appId, newFirstPreference).flatMap(_ =>
              maybeNotifyOnFailNeedNewFsb(appId, newCurrentSchemeStatus)
            )
          }
        }
      }
    }
  }

  private def maybeNotifyOnFailNeedNewFsb(
    appId: String, newCurrentSchemeStatus: Seq[SchemeEvaluationResult])(implicit hc: HeaderCarrier): Future[Unit] = {
    if (firstResidualPreference(newCurrentSchemeStatus).nonEmpty) {
      retrieveCandidateDetails(appId).flatMap { case (app, cd) =>
        emailClient.notifyCandidateOnFinalFailure(cd.email, app.name)
      }
    } else {
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
    if (newFirstResidualPreference.isEmpty) {
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ALL_FSBS_AND_FSACS_FAILED)
    } else {
      Future.successful(())
    }
  }

  def saveResults(schemeId: SchemeId, applicationResults: List[ApplicationResult]): Future[List[Unit]] = {
    Future.sequence(
      applicationResults.map(applicationResult => saveResult(schemeId, applicationResult))
    )
  }

  def saveResult(schemeId: SchemeId, applicationResult: ApplicationResult): Future[Unit] = {
    saveResult(applicationResult.applicationId, SchemeEvaluationResult(schemeId, applicationResult.result))
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
      case Some(schemeId) if schemeId == DiplomaticServiceEconomists => List(Some(DiplomaticService), Some(GovernmentEconomicsService))
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
  val DiplomaticServiceEconomists = SchemeId("DiplomaticServiceEconomists") // EAC_DS -> GES_DS
  val GovernmentEconomicsService = SchemeId("GovernmentEconomicsService") // EAC -> GES
  val DiplomaticService = SchemeId("DiplomaticService") // FCO -> DS

  val EacSchemes = List(DiplomaticServiceEconomists, GovernmentEconomicsService)
}
