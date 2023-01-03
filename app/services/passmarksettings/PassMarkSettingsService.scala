/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model.PassMarkSettingsCreateResponse
import model.exchange.passmarksettings._
import play.api.libs.json.Format
import repositories.passmarksettings._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class Phase1PassMarkSettingsService @Inject() (val passMarkSettingsRepo: Phase1PassMarkSettingsMongoRepository)
  extends PassMarkSettingsService[Phase1PassMarkSettings] {

  override def createPassMarkSettings(passMarkSettings: Phase1PassMarkSettings)(
    implicit jsonFormat: Format[Phase1PassMarkSettings]): Future[PassMarkSettingsCreateResponse] = {
    for {
      latestPassMarkSettingsOpt <- getLatestPassMarkSettings
      merged = Phase1PassMarkSettings.merge(latestPassMarkSettingsOpt, passMarkSettings)
      response <- super.createPassMarkSettings(merged)
    } yield response
  }
}

@Singleton
class Phase2PassMarkSettingsService @Inject() (val passMarkSettingsRepo: Phase2PassMarkSettingsMongoRepository)
  extends PassMarkSettingsService[Phase2PassMarkSettings] {
}

@Singleton
class Phase3PassMarkSettingsService @Inject() (val passMarkSettingsRepo: Phase3PassMarkSettingsMongoRepository)
  extends PassMarkSettingsService[Phase3PassMarkSettings] {
}

@Singleton
class AssessmentCentrePassMarkSettingsService @Inject() (val passMarkSettingsRepo: AssessmentCentrePassMarkSettingsMongoRepository
                                                         ) extends PassMarkSettingsService[AssessmentCentrePassMarkSettings] {
}

trait PassMarkSettingsService[T <: PassMarkSettings] {
  val passMarkSettingsRepo: PassMarkSettingsRepository[T]

  def getLatestPassMarkSettings(implicit jsonFormat: Format[T]): Future[Option[T]] = passMarkSettingsRepo.getLatestVersion
  def createPassMarkSettings(passMarkSettings: T)(implicit jsonFormat: Format[T]):Future[PassMarkSettingsCreateResponse]
  = passMarkSettingsRepo.create(passMarkSettings)
}
