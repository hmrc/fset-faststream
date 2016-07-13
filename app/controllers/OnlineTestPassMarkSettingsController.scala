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

import connectors.PassMarkExchangeObjects.Implicits._
import connectors.PassMarkExchangeObjects._
import factories.UUIDFactory
import model.Commands.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories._
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object OnlineTestPassMarkSettingsController extends OnlineTestPassMarkSettingsController {
  val pmsRepository = passMarkSettingsRepository
  val fwRepository = frameworkRepository
  val auditService = AuditService
  val uuidFactory = UUIDFactory
}

trait OnlineTestPassMarkSettingsController extends BaseController {

  val pmsRepository: PassMarkSettingsRepository
  val fwRepository: FrameworkRepository
  val auditService: AuditService
  val uuidFactory: UUIDFactory

  def createPassMarkSettings = Action.async(parse.json) { implicit request =>
    withJsonBody[SettingsCreateRequest] { passMarkSettingsRequest =>
      {
        val newVersionUUID = uuidFactory.generateUUID()

        val builtSettingsObject = Settings(
          schemes = passMarkSettingsRequest.schemes,
          version = newVersionUUID,
          createDate = passMarkSettingsRequest.createDate,
          createdByUser = passMarkSettingsRequest.createdByUser,
          setting = passMarkSettingsRequest.setting
        )

        for {
          names <- fwRepository.getFrameworkNames
          createResult <- pmsRepository.create(builtSettingsObject, names)
        } yield {
          auditService.logEvent("PassMarkSettingsCreated", Map(
            "Version" -> newVersionUUID,
            "CreatedByUserId" -> passMarkSettingsRequest.createdByUser,
            "StoredCreateDate" -> passMarkSettingsRequest.createDate.toString
          ))
          Ok(Json.toJson(createResult))
        }
      }
    }
  }

  def getLatestVersion = Action.async { implicit request =>
    for {
      schemeNames <- fwRepository.getFrameworkNames
      latestVersionOpt <- pmsRepository.tryGetLatestVersion(schemeNames)
    } yield {
      latestVersionOpt.map(latestVersion => {
        val responseSchemes = latestVersion.schemes.map(scheme => SchemeResponse(scheme.schemeName, Some(scheme.schemeThresholds)))

        val exchangeObject = SettingsResponse(
          schemes = responseSchemes,
          createDate = Some(latestVersion.createDate),
          createdByUser = Some(latestVersion.createdByUser),
          setting = latestVersion.setting
        )

        Ok(Json.toJson(exchangeObject))
      }).getOrElse({
        val emptyPassMarkSchemes = schemeNames.map(schemeName => SchemeResponse(schemeName, None))

        val emptySettingsExchangeObject = SettingsResponse(emptyPassMarkSchemes, None, None, "location1Scheme1")

        Ok(Json.toJson(emptySettingsExchangeObject))
      })
    }
  }
}
