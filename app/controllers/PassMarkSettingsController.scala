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

package controllers

import factories.UUIDFactory
import model.exchange.passmarksettings._
import org.joda.time.DateTime
import play.api.libs.json.{ Format, Json }
import play.api.mvc.Action
import services.AuditService
import services.passmarksettings._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1PassMarkSettingsController extends PassMarkSettingsController[Phase1PassMarkSettings] {
  val passMarkService = Phase1PassMarkSettingsService
  val auditService = AuditService
  val uuidFactory = UUIDFactory
  val passMarksCreatedEvent = "Phase1PassMarkSettingsCreated"
  def upgradeVersion(passMarkSettings:Phase1PassMarkSettings, newVersionUUID: String) =
    passMarkSettings.copy(version = newVersionUUID, createDate = DateTime.now())
}

object Phase2PassMarkSettingsController extends PassMarkSettingsController[Phase2PassMarkSettings] {
  val passMarkService = Phase2PassMarkSettingsService
  val auditService = AuditService
  val uuidFactory = UUIDFactory
  val passMarksCreatedEvent = "Phase2PassMarkSettingsCreated"
  def upgradeVersion(passMarkSettings:Phase2PassMarkSettings, newVersionUUID: String) =
    passMarkSettings.copy(version = newVersionUUID, createDate = DateTime.now())
}

object Phase3PassMarkSettingsController extends PassMarkSettingsController[Phase3PassMarkSettings] {
  val passMarkService = Phase3PassMarkSettingsService
  val auditService = AuditService
  val uuidFactory = UUIDFactory
  val passMarksCreatedEvent = "Phase3PassMarkSettingsCreated"
  def upgradeVersion(passMarkSettings:Phase3PassMarkSettings, newVersionUUID: String) =
    passMarkSettings.copy(version = newVersionUUID, createDate = DateTime.now())
}

object AssessmentCentrePassMarkSettingsController extends PassMarkSettingsController[AssessmentCentrePassMarkSettings] {
  val passMarkService = AssessmentCentrePassMarkSettingsService
  val auditService = AuditService
  val uuidFactory = UUIDFactory
  val passMarksCreatedEvent = "AssessmentCentrePassMarkSettingsCreated"
  def upgradeVersion(passMarkSettings:AssessmentCentrePassMarkSettings, newVersionUUID: String) =
    passMarkSettings.copy(version = newVersionUUID, createDate = DateTime.now())
}

abstract class PassMarkSettingsController[T <: PassMarkSettings]
(implicit manifest: Manifest[T], jsonFormat: Format[T]) extends BaseController {

  val passMarkService: PassMarkSettingsService[T]
  val auditService: AuditService
  val uuidFactory: UUIDFactory
  val passMarksCreatedEvent : String

  def upgradeVersion(passMarkSettings:T, newVersionUUID: String) : T

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[T] { passMarkSettings => {
        val newVersionUUID = uuidFactory.generateUUID()
        val newPassMarkSettings = upgradeVersion(passMarkSettings, newVersionUUID)
        for {
          createResult <- passMarkService.createPassMarkSettings(newPassMarkSettings)
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

  def getLatestVersion = Action.async { implicit request =>
    for {
      latestVersionOpt <- passMarkService.getLatestPassMarkSettings
    } yield {
      latestVersionOpt.map {
        passMarkSettings => Ok(Json.toJson(passMarkSettings))
      } getOrElse {
        NotFound("Pass mark settings not found")
      }
    }
  }
}
