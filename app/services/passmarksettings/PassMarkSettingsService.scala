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

import javax.inject.{Inject, Singleton}
import model.PassMarkSettingsCreateResponse
import model.exchange.passmarksettings._
import play.api.libs.json.Format
import repositories.passmarksettings._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Phase1PassMarkSettingsService @Inject() (val passMarkSettingsRepo: Phase1PassMarkSettingsMongoRepository)(implicit ec: ExecutionContext)
  extends PassMarkSettingsService[Phase1PassMarkSettingsPersistence] {

  override def createPassMarkSettings(passMarkSettingsPersistence: Phase1PassMarkSettingsPersistence)(
    implicit jsonFormat: Format[Phase1PassMarkSettingsPersistence], ec: ExecutionContext): Future[PassMarkSettingsCreateResponse] = {
    for {
      latestPassMarkSettingsOpt <- getLatestPassMarkSettings
      merged = Phase1PassMarkSettings.merge(latestPassMarkSettingsOpt, passMarkSettingsPersistence)
      response <- super.createPassMarkSettings(merged)
    } yield response
  }
}

@Singleton
class Phase2PassMarkSettingsService @Inject() (val passMarkSettingsRepo: Phase2PassMarkSettingsMongoRepository)
  extends PassMarkSettingsService[Phase2PassMarkSettingsPersistence] {
}

@Singleton
class Phase3PassMarkSettingsService @Inject() (val passMarkSettingsRepo: Phase3PassMarkSettingsMongoRepository)
  extends PassMarkSettingsService[Phase3PassMarkSettingsPersistence] {
}

@Singleton
class AssessmentCentrePassMarkSettingsService @Inject() (val passMarkSettingsRepo: AssessmentCentrePassMarkSettingsMongoRepository)(
  implicit ec: ExecutionContext) extends PassMarkSettingsService[AssessmentCentrePassMarkSettingsPersistence] {
}

trait PassMarkSettingsService[T <: PassMarkSettingsPersistence] {
  val passMarkSettingsRepo: PassMarkSettingsRepository[T]

  def getLatestPassMarkSettings(implicit jsonFormat: Format[T]): Future[Option[T]] = passMarkSettingsRepo.getLatestVersion
  def createPassMarkSettings(passMarkSettings: T)(implicit jsonFormat: Format[T], ec: ExecutionContext): Future[PassMarkSettingsCreateResponse]
  = passMarkSettingsRepo.create(passMarkSettings)
}
