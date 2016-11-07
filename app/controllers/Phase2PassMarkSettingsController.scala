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

package controllers

import factories.UUIDFactory
import model.Commands.Implicits._
import model.exchange.passmarksettings.Phase2PassMarkSettings
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.Action
import services.AuditService
import services.passmarksettings.{ PassMarkSettingsService, Phase2PassMarkSettingsService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object Phase2PassMarkSettingsController extends Phase2PassMarkSettingsController {
  val passMarkService = Phase2PassMarkSettingsService
  val auditService = AuditService
  val uuidFactory = UUIDFactory
}

trait Phase2PassMarkSettingsController extends BaseController {

  val passMarkService: PassMarkSettingsService[Phase2PassMarkSettings]
  val auditService: AuditService
  val uuidFactory: UUIDFactory

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[Phase2PassMarkSettings] { passMarkSettings => {
        val newVersionUUID = uuidFactory.generateUUID()
        val newPassMarkSettings = passMarkSettings.copy(version = newVersionUUID, createDate = DateTime.now())
        for {
          createResult <- passMarkService.createPassMarkSettings(newPassMarkSettings)
        } yield {
          auditService.logEvent("PassMarkSettingsCreated", Map(
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
