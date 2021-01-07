/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model._
import model.persisted.sift.SiftAnswersStatus.SiftAnswersStatus
import play.api.Logger
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.sift.SiftAnswersRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class SiftAnswersService @Inject() (appRepo: GeneralApplicationRepository,
                                    siftAnswersRepo: SiftAnswersRepository,
                                    schemeRepository: SchemeRepository
                                   ) {
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

  def setSiftAnswersStatus(applicationId: String, status: SiftAnswersStatus): Future[Unit] = {
    for {
      _ <- siftAnswersRepo.setSiftAnswersStatus(applicationId, status)
    } yield ()
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

  // This is called when the candidate submits the form answers
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

      progressResponse <- appRepo.findProgress(applicationId)
      siftTestResultsReceived = progressResponse.siftProgressResponse.siftTestResultsReceived

      _ <- maybeMoveToReadyOrTestPending(applicationId, schemesPassed, siftTestResultsReceived)
      _ <- maybeMoveToCompleted(applicationId, schemesPassed, schemesPassedNotRequiringSift)
    } yield {}
  }

  def removeAnswers(applicationId: String): Future[Unit] = {
    siftAnswersRepo.removeSiftAnswers(applicationId)
  }

  // Maybe move the candidate to SIFT_READY to indicate he/she is ready to be sifted for form based schemes
  // or to SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING to indicate the forms have been submitted and we are waiting
  // for the numeric test to be completed
  private def maybeMoveToReadyOrTestPending(applicationId: String,
                                            schemesPassed: Set[SchemeId], siftTestResultsReceived: Boolean): Future[Unit] = {

    val hasNumericSchemes = schemeRepository.numericTestSiftRequirementSchemeIds.exists( s => schemesPassed.contains(s))

    (hasNumericSchemes, siftTestResultsReceived) match {
      case (false, _) =>
        // No numeric schemes so move candidate to SIFT_READY
        Logger.info(s"Candidate $applicationId has submitted sift forms and has no numeric schemes " +
          s"so moving to ${ProgressStatuses.SIFT_READY}")
        appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY)
      case (true, true) =>
        // Numeric schemes and the test results have been received so move candidate to SIFT_READY
        Logger.info(s"Candidate $applicationId has submitted sift forms, has numeric schemes, has " +
          s"taken the numeric test and received the results so moving to ${ProgressStatuses.SIFT_READY}")
        appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY)
      case (true, false) =>
        // Numeric schemes and the test results have not been received so move the candidate to NUMERIC_TEST_PENDING
        Logger.info(s"Candidate $applicationId has submitted sift forms, has numeric schemes but has " +
          s"not received test results so now moving to ${ProgressStatuses.SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING}")
        appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING)
      case _ =>
        // Do not move the candidate
        Logger.info(s"Candidate $applicationId is not yet in a state to move to ${ProgressStatuses.SIFT_READY}")
        Future.successful(())
    }
  }

  // Maybe move the candidate to SIFT_COMPLETED to indicate the candidate has no schemes that require a sift
  // and can be moved straight into SIFT_COMPLETED
  private def maybeMoveToCompleted(applicationId: String, passedSchemes: Set[SchemeId],
                                   passedSchemesNotRequiringSift: Set[SchemeId]): Future[Unit] = {
    val allPassedSchemesDoNotRequireSift = passedSchemes.size == passedSchemesNotRequiringSift.size &&
      passedSchemes == passedSchemesNotRequiringSift

    if(allPassedSchemesDoNotRequireSift) {
      Logger.info(s"Candidate $applicationId has submitted sift forms and has no schemes requiring a sift so " +
        s"now moving to ${ProgressStatuses.SIFT_COMPLETED}")
      appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_COMPLETED)
    } else {
      Logger.info(s"Candidate $applicationId has schemes, which require sifting so cannot " +
        s"move to ${ProgressStatuses.SIFT_COMPLETED}")
      Future.successful(())
    }
  }
}
