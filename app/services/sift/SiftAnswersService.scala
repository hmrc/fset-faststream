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

package services.sift

import model.{ EvaluationResults, SchemeId }
import repositories.sift.SiftAnswersRepository
import services.onlinetesting.phase3.EvaluatePhase3ResultService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SiftAnswersService extends SiftAnswersService {
  val siftAnswersRepo: SiftAnswersRepository = repositories.siftAnswersRepository
  val phase3ResultsService = EvaluatePhase3ResultService
}

trait SiftAnswersService {
  def siftAnswersRepo: SiftAnswersRepository
  def phase3ResultsService: EvaluatePhase3ResultService

  def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: model.exchange.sift.SchemeSpecificAnswer): Future[Unit] = {
    siftAnswersRepo.addSchemeSpecificAnswer(applicationId, schemeId, model.persisted.sift.SchemeSpecificAnswer(answer.rawText))
  }

  def addGeneralAnswers(applicationId: String, answers: model.exchange.sift.GeneralQuestionsAnswers): Future[Unit] = {
    siftAnswersRepo.addGeneralAnswers(applicationId, model.persisted.sift.GeneralQuestionsAnswers(answers))
  }

  def findSiftAnswers(applicationId: String): Future[Option[model.exchange.sift.SiftAnswers]] = {
    siftAnswersRepo.findSiftAnswers(applicationId).map(persisted => persisted.map(
      psa => model.exchange.sift.SiftAnswers(
        psa.applicationId,
        model.exchange.sift.SiftAnswersStatus.withName(psa.status.toString),
        psa.generalAnswers.map(model.exchange.sift.GeneralQuestionsAnswers.apply),
        psa.schemeAnswers.map {
          case (k: String, v: model.persisted.sift.SchemeSpecificAnswer) => (k, model.exchange.sift.SchemeSpecificAnswer(v.rawText))
      })))
  }

  def findSiftAnswersStatus(applicationId: String): Future[Option[model.exchange.sift.SiftAnswersStatus.SiftAnswersStatus]] = {
    siftAnswersRepo.findSiftAnswersStatus(applicationId).map(persisted => persisted.map(
      psas => model.exchange.sift.SiftAnswersStatus(psas.id)
    ))
  }

  def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId): Future[Option[model.exchange.sift.SchemeSpecificAnswer]] = {
    siftAnswersRepo.findSchemeSpecificAnswer(applicationId, schemeId).map(persisted => persisted.map(
      pssa => model.exchange.sift.SchemeSpecificAnswer(pssa.rawText)
    ))
  }

  def findGeneralAnswers(applicationId: String): Future[Option[model.exchange.sift.GeneralQuestionsAnswers]] = {
    siftAnswersRepo.findGeneralQuestionsAnswers(applicationId).map(persisted =>
      persisted.map(model.exchange.sift.GeneralQuestionsAnswers.apply)
    )
  }

  def submitAnswers(applicationId: String): Future[Unit] = {
    for {
      phase3Results <- phase3ResultsService.getPassmarkEvaluation(applicationId)
      schemesPassedPhase3 = phase3Results.result.filter(_.result == EvaluationResults.Green.toString).map(_.schemeId).toSet
      submitResult <- siftAnswersRepo.submitAnswers(applicationId, schemesPassedPhase3)
    } yield submitResult
  }
}