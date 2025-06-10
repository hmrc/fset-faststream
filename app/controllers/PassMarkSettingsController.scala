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

package controllers

import factories.UUIDFactory

import javax.inject.{Inject, Singleton}
import model.exchange.passmarksettings.{PassMarkSettingsPersistence, *}
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.AuditService
import services.passmarksettings.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

@Singleton
class Phase1PassMarkSettingsController @Inject()(val cc: ControllerComponents,
                                                 val passMarkService: Phase1PassMarkSettingsService,
                                                 val auditService: AuditService,
                                                 val uuidFactory: UUIDFactory
                                                ) extends PassMarkSettingsController[Phase1PassMarkSettings,
  Phase1PassMarkSettingsPersistence](cc) {

  val passMarksCreatedEvent = "Phase1PassMarkSettingsCreated"

  def upgradeVersion(passMarkSettings: Phase1PassMarkSettings, newVersionUUID: String): Phase1PassMarkSettings =
    passMarkSettings.copy(version = newVersionUUID, createDate = OffsetDateTime.now)
}

@Singleton
class Phase2PassMarkSettingsController @Inject()(val cc: ControllerComponents,
                                                 val passMarkService: Phase2PassMarkSettingsService,
                                                 val auditService: AuditService,
                                                 val uuidFactory: UUIDFactory
                                                ) extends PassMarkSettingsController[Phase2PassMarkSettings,
  Phase2PassMarkSettingsPersistence](cc) {

  val passMarksCreatedEvent = "Phase2PassMarkSettingsCreated"

  def upgradeVersion(passMarkSettings: Phase2PassMarkSettings, newVersionUUID: String): Phase2PassMarkSettings =
    passMarkSettings.copy(version = newVersionUUID, createDate = OffsetDateTime.now)
}

@Singleton
class Phase3PassMarkSettingsController @Inject()(val cc: ControllerComponents,
                                                 val passMarkService: Phase3PassMarkSettingsService,
                                                 val auditService: AuditService,
                                                 val uuidFactory: UUIDFactory
                                                ) extends PassMarkSettingsController[Phase3PassMarkSettings,
  Phase3PassMarkSettingsPersistence](cc) {

  val passMarksCreatedEvent = "Phase3PassMarkSettingsCreated"

  def upgradeVersion(passMarkSettings: Phase3PassMarkSettings, newVersionUUID: String): Phase3PassMarkSettings =
    passMarkSettings.copy(version = newVersionUUID, createDate = OffsetDateTime.now)
}

@Singleton
class AssessmentCentrePassMarkSettingsController @Inject()(val cc: ControllerComponents,
                                                           val passMarkService: AssessmentCentrePassMarkSettingsService,
                                                           val auditService: AuditService,
                                                           val uuidFactory: UUIDFactory
                                                          ) extends PassMarkSettingsController[AssessmentCentrePassMarkSettings,
  AssessmentCentrePassMarkSettingsPersistence](cc) {

  val passMarksCreatedEvent = "AssessmentCentrePassMarkSettingsCreated"

  def upgradeVersion(passMarkSettings: AssessmentCentrePassMarkSettings, newVersionUUID: String): AssessmentCentrePassMarkSettings =
    passMarkSettings.copy(version = newVersionUUID, createDate = OffsetDateTime.now)
}

abstract class PassMarkSettingsController[T <: PassMarkSettings, U <: PassMarkSettingsPersistence] @Inject()(cc: ControllerComponents)(
  implicit manifest: Manifest[T], exchangeJsonFormat: Format[T], persistentJsonFormat: Format[U]) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext
  val passMarkService: PassMarkSettingsService[U]
  val auditService: AuditService
  val uuidFactory: UUIDFactory
  val passMarksCreatedEvent: String

  def upgradeVersion(passMarkSettings: T, newVersionUUID: String): T

  def create: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[T] { passMarkSettings => {
      val newVersionUUID = uuidFactory.generateUUID()
      val newPassMarkSettings = upgradeVersion(passMarkSettings, newVersionUUID)

      for {
        createResult <- passMarkService.createPassMarkSettings(newPassMarkSettings.toPersistence.asInstanceOf[U])
      } yield {
        auditService.logEvent(passMarksCreatedEvent, Map(
          "Version" -> newVersionUUID,
          "CreatedByUserId" -> passMarkSettings.createdBy,
          "StoredCreateDate" -> passMarkSettings.createDate.toString
        ))
        Ok(Json.toJson(createResult))
      }
    }
    }
  }

  def getLatestVersion: Action[AnyContent] = Action.async {
    for {
      latestVersionOpt <- passMarkService.getLatestPassMarkSettings
    } yield {
      latestVersionOpt.map { passMarkSettings =>
        Ok(Json.toJson(passMarkSettings.toExchange.asInstanceOf[T]))
      } getOrElse {
        NotFound("Pass mark settings not found")
      }
    }
  }
}
