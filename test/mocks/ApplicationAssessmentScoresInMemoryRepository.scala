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

package mocks

import model.CandidateScoresCommands.CandidateScoresAndFeedback
import repositories.ApplicationAssessmentScoresRepository

import scala.concurrent.Future

object ApplicationAssessmentScoresInMemoryRepository extends ApplicationAssessmentScoresInMemoryRepository

class ApplicationAssessmentScoresInMemoryRepository extends ApplicationAssessmentScoresRepository {

  def allScores: Future[Map[String, CandidateScoresAndFeedback]] = ???

  def tryFind(applicationId: String): Future[Option[CandidateScoresAndFeedback]] = ???

  def save(candidateScoresAndFeedbck: CandidateScoresAndFeedback): Future[Unit] = ???

  def nextCandidateScoresReadyForEvaluation: Future[Option[CandidateScoresAndFeedback]] = ???
}
