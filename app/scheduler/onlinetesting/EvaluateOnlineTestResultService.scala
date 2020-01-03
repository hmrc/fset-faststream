/*
 * Copyright 2020 HM Revenue & Customs
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

package scheduler.onlinetesting

import factories.UUIDFactory
import model.Phase
import model.exchange.passmarksettings.PassMarkSettings
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import play.api.libs.json.Format
import repositories.onlinetesting.OnlineTestEvaluationRepository
import services.onlinetesting.ApplicationStatusCalculator
import services.passmarksettings.PassMarkSettingsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EvaluateOnlineTestResultService[T <: PassMarkSettings] extends ApplicationStatusCalculator {
  this: PassMarkSettingsService[T] =>

  val evaluationRepository: OnlineTestEvaluationRepository

  val phase: Phase.Phase

  def nextCandidatesReadyForEvaluation(batchSize: Int)(implicit jsonFormat: Format[T]):
  Future[Option[(List[ApplicationReadyForEvaluation], T)]] = {
    getLatestPassMarkSettings flatMap {
      case Some(passmark) =>
        evaluationRepository.nextApplicationsReadyForEvaluation(passmark.version, batchSize) map { candidates =>
          Some(candidates -> passmark)
        }
      case _ => Future.successful(None)
    }
  }

  def savePassMarkEvaluation(application: ApplicationReadyForEvaluation,
                             schemeResults: List[SchemeEvaluationResult],
                             passMarkSettings: T) = {
    if (schemeResults.nonEmpty) {
      evaluationRepository.savePassmarkEvaluation(
        application.applicationId,
        PassmarkEvaluation(passMarkSettings.version, application.prevPhaseEvaluation.map(_.passmarkVersion),
          schemeResults, UUIDFactory.generateUUID().toString, application.prevPhaseEvaluation.map(_.resultVersion)),
        determineApplicationStatus(application.applicationRoute, application.applicationStatus, schemeResults, phase)
      )
    } else {
      Future.successful(())
    }
  }

  def getPassmarkEvaluation(applicationId: String): Future[PassmarkEvaluation] = {
    evaluationRepository.getPassMarkEvaluation(applicationId)
  }

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: T): Future[Unit]
}
