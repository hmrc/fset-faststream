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

package services.passmarksettings

import model.Commands.PassMarkSettingsCreateResponse
import model.exchange.passmarksettings.Phase1PassMarkSettings
import repositories._

import scala.concurrent.Future

object PassMarkSettingsService extends PassMarkSettingsService {
  val phase1PMSRepository = phase1PassMarkSettingsRepository
}

trait PassMarkSettingsService {
  val phase1PMSRepository: Phase1PassMarkSettingsRepository

  def getLatestPhase1PassMarkSettings: Future[Option[Phase1PassMarkSettings]] = phase1PMSRepository.getLatestVersion

  def createPhase1PassMarkSettings(phase1PassMarkSettings: Phase1PassMarkSettings):Future[PassMarkSettingsCreateResponse]
      = phase1PMSRepository.create(phase1PassMarkSettings)
}
