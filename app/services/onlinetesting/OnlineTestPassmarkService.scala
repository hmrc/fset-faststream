/*
 * Copyright 2016 HM Revenue & Customs
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

package services.onlinetesting

import model.ApplicationStatuses
import model.EvaluationResults.{ RuleCategoryResult, _ }
import model.OnlineTestCommands._
import model.PersistedObjects.ApplicationIdWithUserIdAndStatus
import play.api.Logger
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.evaluation._
import services.passmarksettings.PassMarkSettingsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OnlineTestPassmarkService extends OnlineTestPassmarkService {
  val pmsRepository = passMarkSettingsRepository
  val fpRepository = frameworkPreferenceRepository
  val trRepository = testReportRepository
  val oRepository = phase1TestRepository
  val passmarkRulesEngine = OnlineTestPassmarkRulesEngine
  val passMarkSettingsService = PassMarkSettingsService
}

trait OnlineTestPassmarkService {
  val fpRepository: FrameworkPreferenceRepository
  val trRepository: TestReportRepository
  val oRepository: Phase1TestRepository
  val passmarkRulesEngine: OnlineTestPassmarkRulesEngine
  val pmsRepository: PassMarkSettingsRepository
  val passMarkSettingsService: PassMarkSettingsService

  def nextCandidateScoreReadyForEvaluation: Future[Option[CandidateScoresWithPreferencesAndPassmarkSettings]] = {
    passMarkSettingsService.tryGetLatestVersion().flatMap {
      case Some(settings) =>
        //TODO FAST STREAM FIX ME
        Future.successful(None)
        //oRepository.nextApplicationPassMarkProcessing(settings.version).flatMap {
        //  case Some(ApplicationIdWithUserIdAndStatus(appId, _, appStatus)) =>
        //    for {
        //      reportOpt <- trRepository.getReportByApplicationId(appId)
        //      pOpt <- fpRepository.tryGetPreferences(appId)
        //    } yield {
        //      for {
        //        r <- reportOpt
        //        p <- pOpt
        //      } yield CandidateScoresWithPreferencesAndPassmarkSettings(settings, p, r, appStatus)
        //    }
        //  case _ =>
        //    Future.successful(None)
        //}
      case _ =>
        Logger.error("No settings exist in PassMarkSettings")
        Future.successful(None)
    }
  }

  private def determineApplicationStatus(setting: String, result: RuleCategoryResult) = {
    type Rules = Seq[(RuleCategoryResult) => Option[Result]]

    val totalFailure = Seq(
      Some(result.location1Scheme1),
      result.location1Scheme2,
      result.location2Scheme1,
      result.location2Scheme2,
      result.alternativeScheme
    ).filter { case Some(_) => true case _ => false }
      .map(_.get)
      .forall(_ == Red)

    // We build a list with the results to be considered depending on the rule
    // First element of the sequence is rule1 location1scheme1 setting
    // Second element will contain the first, plus rule2 other scheme setting
    // Third element will contain all the previous ones, plus rule 3 any setting
    val rule1: Rules = Seq(r => Some(r.location1Scheme1))
    val rule2: Rules = setting match {
      case "otherScheme" | "any" =>
        Seq(
          _.location1Scheme2,
          _.location2Scheme1,
          _.location2Scheme2
        )
      case _ => Nil
    }
    val rule3: Rules = setting match {
      case "any" => Seq(_.alternativeScheme)
      case _ => Nil
    }

    if (totalFailure) {
      ApplicationStatuses.OnlineTestFailed
    } else {
      provideResult(
        Seq(rule1, rule2, rule3)
          .filter(_.nonEmpty)
          .flatten
          .map(f => f(result).getOrElse(Red)), result
      )
    }
  }

  def provideResult(rules: Seq[Result], ruleCategoryResult: RuleCategoryResult) = {
    val AC = ApplicationStatuses.AwaitingAllocation
    val ARE = ApplicationStatuses.AwaitingOnlineTestReevaluation

    if (rules.contains(Green)) {
      AC
    } else {
      ARE
    }
  }

  def evaluateCandidateScore(score: CandidateScoresWithPreferencesAndPassmarkSettings): Future[Unit] = {
    val result = passmarkRulesEngine.evaluate(score)
    val applicationStatus = determineApplicationStatus(score.passmarkSettings.setting, result)

    // TODO FAST STREAM FIX ME
    Future.successful(Unit)
    //oRepository.savePassMarkScore(
    //  score.scores.applicationId,
    //  score.passmarkSettings.version,
    //  result,
    //  applicationStatus
    //)
  }

  def evaluateCandidateScoreWithoutChangingApplicationStatus(score: CandidateScoresWithPreferencesAndPassmarkSettings): Future[Unit] = {
    val result = passmarkRulesEngine.evaluate(score)

    // TODO FAST STREAM FIX ME
    Future.successful(Unit)
    //oRepository.savePassMarkScoreWithoutApplicationStatusUpdate(
    //  score.scores.applicationId,
    //  score.passmarkSettings.version,
    //  result
    //)
  }
}
