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

import config.TestFixtureBase
import factories.UUIDFactory
import model.Commands.PassMarkSettingsCreateResponse
import model.SchemeType._
import model.exchange.passmarksettings.{ PassMarkThreshold, SchemePassMark, SchemePassMarkSettings, SchemePassMarkThresholds }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.PassMarkSettingsRepository

import scala.concurrent.Future
import scala.language.postfixOps

class OnlineTestPassMarkSettingsControllerSpec extends PlaySpec with Results with MockitoSugar {
  "Try and get latest settings" should {
    "Return a settings objects with schemes but no thresholds if there are no settings saved" in new TestFixture {
      val passMarkSettingsRepositoryMockWithNoSettings = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryMockWithNoSettings.tryGetLatestVersion).thenReturn(Future.successful(None))

      val passMarkSettingsControllerWithNoSettings = buildPMS(passMarkSettingsRepositoryMockWithNoSettings)

      val result = passMarkSettingsControllerWithNoSettings.getLatestVersion()(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "Return a complete settings object if there are saved settings" in new TestFixture {

      val passMarkSettingsRepositoryMockWithSettings = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryMockWithSettings.tryGetLatestVersion).thenReturn(Future.successful(
        Some(
          mockSettings
        )
      ))

      val passMarkSettingsControllerWithSettings = buildPMS(passMarkSettingsRepositoryMockWithSettings)

      val result = passMarkSettingsControllerWithSettings.getLatestVersion()(FakeRequest())

      status(result) must be(200)

      contentAsJson(result) mustBe Json.toJson(mockSettings)
    }
  }

  "Save new settings" should {
    def isValid(value: SchemePassMarkSettings) = true

    "Send a complete settings object to the repository with a version UUID appended" in new TestFixture {

      val passMarkSettingsRepositoryWithExpectations = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryWithExpectations.create(any())).thenReturn(Future.successful(
        PassMarkSettingsCreateResponse(
          "uuid-1",
          new DateTime()
        )
      ))

      val passMarkSettingsController = buildPMS(passMarkSettingsRepositoryWithExpectations)

      val result = passMarkSettingsController.createPassMarkSettings()(createPassMarkSettingsRequest(validSettingsCreateRequestJSON))

      status(result) mustBe OK

      verify(passMarkSettingsRepositoryWithExpectations).create(eqTo(mockSettings))
    }
  }

  trait TestFixture extends TestFixtureBase {

    val defaultSchemeThreshold = PassMarkThreshold(20d, 80d)

    val defaultSchemeThresholds = SchemePassMarkThresholds(defaultSchemeThreshold, defaultSchemeThreshold)

    val mockSchemes = List(
      SchemePassMark(Finance, defaultSchemeThresholds),
      SchemePassMark(Commercial, defaultSchemeThresholds),
      SchemePassMark(Generalist, defaultSchemeThresholds)
    )
    val mockVersion = "uuid-1"
    val mockCreateDate = new DateTime(1459504800000L)
    val mockCreatedByUser = "TestUser"

    val mockSettings = SchemePassMarkSettings(
      schemes = mockSchemes,
      version = mockVersion,
      createDate = mockCreateDate,
      createdByUser = mockCreatedByUser,
      setting = "schemes"
    )

    val mockUUIDFactory = mock[UUIDFactory]

    when(mockUUIDFactory.generateUUID()).thenReturn("uuid-1")

    def buildPMS(mockRepository: PassMarkSettingsRepository) = new OnlineTestPassMarkSettingsController {
      val pmsRepository = mockRepository
      val auditService = mockAuditService
      val uuidFactory = mockUUIDFactory
    }

    def createPassMarkSettingsRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.OnlineTestPassMarkSettingsController.createPassMarkSettings().url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    val validSettingsCreateRequestJSON = s"""
                     |{
                     |    "createDate": 1459504800000,
                     |    "createdByUser": "TestUser",
                     |    "version" : "bbbbbbb",
                     |    "schemes": [
                     |        {
                     |            "schemeName": "Finance",
                     |            "schemeThresholds": {
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "behavioural": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        },
                     |        {
                     |            "schemeName": "Commercial",
                     |            "schemeThresholds": {
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "behavioural": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        },
                     |        {
                     |            "schemeName": "Generalist",
                     |            "schemeThresholds": {
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "behavioural": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        }
                     |    ],
                     |    "setting": "schemes"
                     |}
        """.stripMargin
  }
}
