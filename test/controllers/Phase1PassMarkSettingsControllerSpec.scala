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
import model.exchange.passmarksettings._
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import services.passmarksettings.PassMarkSettingsService
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps

class Phase1PassMarkSettingsControllerSpec extends UnitWithAppSpec {
  "Try and get latest settings" should {
    "Return a settings objects with schemes but no thresholds if there are no settings saved" in new TestFixture {
      val passMarkSettingsServiceMockWithNoSettings = mock[PassMarkSettingsService[Phase1PassMarkSettings]]

      when(passMarkSettingsServiceMockWithNoSettings.getLatestPassMarkSettings).thenReturn(Future.successful(None))

      val passMarkSettingsControllerWithNoSettings = buildPMS(passMarkSettingsServiceMockWithNoSettings)

      val result = passMarkSettingsControllerWithNoSettings.getLatestVersion()(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "Return a complete settings object if there are saved settings" in new TestFixture {

      val passMarkSettingsServiceMockWithSettings = mock[PassMarkSettingsService[Phase1PassMarkSettings]]

      when(passMarkSettingsServiceMockWithSettings.getLatestPassMarkSettings).thenReturn(
        Future.successful(Some(mockSettings)))

      val passMarkSettingsControllerWithSettings = buildPMS(passMarkSettingsServiceMockWithSettings)

      val result = passMarkSettingsControllerWithSettings.getLatestVersion()(FakeRequest())

      status(result) mustBe OK

      contentAsJson(result) mustBe Json.toJson(mockSettings)
    }
  }

  "Save new settings" should {
    "Send a complete settings object to the repository with a version UUID appended" in new TestFixture {

      val passMarkSettingsServiceWithExpectations = mock[PassMarkSettingsService[Phase1PassMarkSettings]]

      when(passMarkSettingsServiceWithExpectations.createPassMarkSettings(any())(any())).thenReturn(Future.successful(
        PassMarkSettingsCreateResponse(
          "uuid-1",
          new DateTime()
        )
      ))

      val passMarkSettingsController = buildPMS(passMarkSettingsServiceWithExpectations)

      val result = passMarkSettingsController.create()(createPassMarkSettingsRequest(validSettingsCreateRequestJSON))

      status(result) mustBe OK

      val passMarkSettingCaptor = ArgumentCaptor.forClass(classOf[Phase1PassMarkSettings])

      verify(passMarkSettingsServiceWithExpectations).createPassMarkSettings(passMarkSettingCaptor.capture)(any())

      val settingsParam = passMarkSettingCaptor.getValue

      settingsParam.schemes mustBe mockSettings.schemes
      settingsParam.createdBy mustBe mockSettings.createdBy
      settingsParam.version mustBe mockSettings.version
    }
  }

  trait TestFixture extends TestFixtureBase {

    val defaultSchemeThreshold = PassMarkThreshold(20d, 80d)

    val defaultSchemeThresholds = Phase1PassMarkThresholds(defaultSchemeThreshold, defaultSchemeThreshold)

    val mockSchemes = List(
      Phase1PassMark(Finance, defaultSchemeThresholds),
      Phase1PassMark(Commercial, defaultSchemeThresholds),
      Phase1PassMark(Generalist, defaultSchemeThresholds)
    )
    val mockVersion = "uuid-1"
    val mockCreateDate = new DateTime(1459504800000L)
    val mockCreatedBy = "TestUser"

    val mockSettings = Phase1PassMarkSettings(
      schemes = mockSchemes,
      version = mockVersion,
      createDate = mockCreateDate,
      createdBy = mockCreatedBy
    )

    val mockUUIDFactory = mock[UUIDFactory]

    when(mockUUIDFactory.generateUUID()).thenReturn("uuid-1")

    def buildPMS(mockService: PassMarkSettingsService[Phase1PassMarkSettings]) = new Phase1PassMarkSettingsController {
      val passMarkService = mockService
      val auditService = mockAuditService
      val uuidFactory = mockUUIDFactory
    }

    def createPassMarkSettingsRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.Phase1PassMarkSettingsController.create().url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    val validSettingsCreateRequestJSON = s"""
                     |{
                     |    "createDate": 1459504800000,
                     |    "createdBy": "TestUser",
                     |    "version" : "version-0",
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
