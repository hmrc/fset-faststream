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
import model.PassmarkPersistedObjects.AssessmentCentrePassMarkScheme
import repositories._

import scala.concurrent.{ ExecutionContext, Future }

object AssessmentCentrePassMarkSettingsService extends AssessmentCentrePassMarkSettingsService {
  val fwRepository = frameworkRepository
  val acpsRepository = assessmentCentrePassMarkSettingsRepository
}

trait AssessmentCentrePassMarkSettingsService {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val fwRepository: FrameworkRepository
  val acpsRepository: AssessmentCentrePassMarkSettingsRepository

  def getLatestVersion: Future[AssessmentCentrePassMarkSettingsResponse] = {
    for {
      schemes <- fwRepository.getFrameworkNames
      latestVersionOpt <- acpsRepository.tryGetLatestVersion
    } yield {
      val passmarkSetForSchemes = latestVersionOpt.map(_.schemes).getOrElse(List())
      val passmarkSetForSchemesNames = passmarkSetForSchemes.map(_.schemeName)
      val passmarkUnsetForSchemes = schemes.diff(passmarkSetForSchemesNames).map { s =>
        AssessmentCentrePassMarkScheme(s)
      }

      val allPassmarkSchemes = passmarkSetForSchemes ++ passmarkUnsetForSchemes

      val info = latestVersionOpt.map(_.info)
      AssessmentCentrePassMarkSettingsResponse(allPassmarkSchemes, info)
    }
  }
}
