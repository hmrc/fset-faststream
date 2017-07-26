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

package services.passmarksettings

import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.persisted.assessmentcentre.{ AssessmentCentrePassMarkScheme, AssessmentCentrePassMarkSettings }
import repositories.{ SchemeYamlRepository, _ }

import scala.concurrent.{ ExecutionContext, Future }

object AssessmentCentrePassMarkSettingsService extends AssessmentCentrePassMarkSettingsService {
  val schemeRepository = SchemeYamlRepository
  val assessmentCentrePassMarkSettingsRepository = repositories.assessmentCentrePassMarkSettingsRepository
}

trait AssessmentCentrePassMarkSettingsService {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val schemeRepository: SchemeRepositoryImpl
  val assessmentCentrePassMarkSettingsRepository: AssessmentCentrePassMarkSettingsRepository

  def getLatestVersion: Future[AssessmentCentrePassMarkSettingsResponse] = {
    SchemeYamlRepository.schemes

    for {
      currentPassmarkSettingsOpt <- assessmentCentrePassMarkSettingsRepository.tryGetLatestVersion
    } yield {
      val allSchemes = schemeRepository.schemes.map(_.id)

      val passmarkSetForSchemes = currentPassmarkSettingsOpt.map(_.schemes).getOrElse(List())

      val schemesPresentInCurrentPassmarkSettings = passmarkSetForSchemes.map(_.schemeId)
      val schemesNotPresentInCurrentPassmarkSettings = allSchemes.diff(schemesPresentInCurrentPassmarkSettings)

      val passmarkUnsetForSchemes = schemesNotPresentInCurrentPassmarkSettings.map { schemeId =>
        AssessmentCentrePassMarkScheme(schemeId, None)
      }

      val allPassmarkSchemes = passmarkSetForSchemes ++ passmarkUnsetForSchemes

      val info = currentPassmarkSettingsOpt.map(_.info)
      AssessmentCentrePassMarkSettingsResponse(allPassmarkSchemes, info)
    }
  }

  def create(passmarks: AssessmentCentrePassMarkSettings): Future[Unit] = {
    assessmentCentrePassMarkSettingsRepository.create(passmarks)
  }
}
