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

import model._
import repositories.application.GeneralApplicationRepository
import repositories.{ SchemeRepository, SchemeYamlRepository }
import repositories.sift.SiftAnswersRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SiftAnswersService extends SiftAnswersService {
  val appRepo = repositories.applicationRepository
  val siftAnswersRepo: SiftAnswersRepository = repositories.siftAnswersRepository
  val schemeRepository = SchemeYamlRepository
}

trait SiftAnswersService {
  def appRepo: GeneralApplicationRepository
  def siftAnswersRepo: SiftAnswersRepository
  def schemeRepository: SchemeRepository

  def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: model.exchange.sift.SchemeSpecificAnswer): Future[Unit] = {
    siftAnswersRepo.addSchemeSpecificAnswer(applicationId, schemeId, model.persisted.sift.SchemeSpecificAnswer(answer))
  }

  def addGeneralAnswers(applicationId: String, answers: model.exchange.sift.GeneralQuestionsAnswers): Future[Unit] = {
    siftAnswersRepo.addGeneralAnswers(applicationId, model.persisted.sift.GeneralQuestionsAnswers(answers))
  }

  def findSiftAnswers(applicationId: String): Future[Option[model.exchange.sift.SiftAnswers]] = {
    siftAnswersRepo.findSiftAnswers(applicationId).map(persisted =>
      persisted.map(
        psa => model.exchange.sift.SiftAnswers(psa)
      )
    )
  }

  def findSiftAnswersStatus(applicationId: String): Future[Option[model.exchange.sift.SiftAnswersStatus.Value]] = {
    siftAnswersRepo.findSiftAnswersStatus(applicationId).map(persisted => persisted.map(
      psas => model.exchange.sift.SiftAnswersStatus.withName(psas.toString)
    ))
  }

  def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId): Future[Option[model.exchange.sift.SchemeSpecificAnswer]] = {
    siftAnswersRepo.findSchemeSpecificAnswer(applicationId, schemeId).map(persisted => persisted.map(
      pssa => model.exchange.sift.SchemeSpecificAnswer(pssa.rawText)
    ))
  }

  def findGeneralAnswers(applicationId: String): Future[Option[model.exchange.sift.GeneralQuestionsAnswers]] = {
    siftAnswersRepo.findGeneralQuestionsAnswers(applicationId).map(persisted => persisted.map(pgqa =>
      model.exchange.sift.GeneralQuestionsAnswers(pgqa)
    ))
  }

  def submitAnswers(applicationId: String): Future[Unit] = {
    for {
      currentSchemeStatus <- appRepo.getCurrentSchemeStatus(applicationId)
      schemesPassed = currentSchemeStatus.filter(_.result == EvaluationResults.Green.toString).map(_.schemeId).toSet
      schemesPassedRequiringSift = schemeRepository.schemes.filter( s =>
        schemesPassed.contains(s.id) && s.siftRequirement.contains(SiftRequirement.FORM)
      ).map(_.id).toSet
      schemesPassedNotRequiringSift = schemeRepository.schemes.filter( s =>
        schemesPassed.contains(s.id) && !s.siftEvaluationRequired
      ).map(_.id).toSet
      _ <- siftAnswersRepo.submitAnswers(applicationId, schemesPassedRequiringSift)
      _ <- appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY)
      _ <- maybeMoveToCompleted(applicationId, schemesPassed, schemesPassedNotRequiringSift)
    } yield {}
  }

  private def maybeMoveToCompleted(applicationId: String, passedSchemes: Set[SchemeId],
                                   passedSchemesNotRequiringSift: Set[SchemeId]): Future[Unit] = {
    val allPassedSchemesDoNotRequireSift = passedSchemes.size == passedSchemesNotRequiringSift.size &&
      passedSchemes == passedSchemesNotRequiringSift

    if(allPassedSchemesDoNotRequireSift) {
      appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_COMPLETED)
    } else {
      Future.successful(())
    }
  }
}
