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
import connectors.PassMarkExchangeObjects.Implicits._
import connectors.PassMarkExchangeObjects._
import factories.UUIDFactory
import model.Commands.PassMarkSettingsCreateResponse
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.{ FrameworkRepository, PassMarkSettingsRepository }

import scala.concurrent.Future
import scala.language.postfixOps

class OnlineTestPassMarkSettingsControllerSpec extends PlaySpec with Results with MockitoSugar {
  "Try and get latest settings" should {
    "Return a settings objects with schemes but no thresholds if there are no settings saved" in new TestFixture {
      val passMarkSettingsRepositoryMockWithNoSettings = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryMockWithNoSettings.tryGetLatestVersion(any())).thenReturn(Future.successful(None))

      val passMarkSettingsControllerWithNoSettings = buildPMS(passMarkSettingsRepositoryMockWithNoSettings)

      val expectedEmptySettingsResponse = SettingsResponse(
        schemes = List(
          SchemeResponse(mockSchemes.head.schemeName, None),
          SchemeResponse(mockSchemes(1).schemeName, None),
          SchemeResponse(mockSchemes(2).schemeName, None)
        ),
        None,
        None,
        "location1Scheme1"
      )

      val result = passMarkSettingsControllerWithNoSettings.getLatestVersion()(FakeRequest())

      status(result) must be(200)

      contentAsJson(result) must be(Json.toJson(expectedEmptySettingsResponse))
    }

    "Return a complete settings object if there are saved settings" in new TestFixture {

      val passMarkSettingsRepositoryMockWithSettings = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryMockWithSettings.tryGetLatestVersion(any())).thenReturn(Future.successful(
        Some(
          mockSettings
        )
      ))

      val passMarkSettingsControllerWithSettings = buildPMS(passMarkSettingsRepositoryMockWithSettings)

      val expectedSettingsResponse = SettingsResponse(
        schemes = List(
          SchemeResponse(mockSchemes.head.schemeName, Some(mockSchemes.head.schemeThresholds)),
          SchemeResponse(mockSchemes(1).schemeName, Some(mockSchemes(1).schemeThresholds)),
          SchemeResponse(mockSchemes(2).schemeName, Some(mockSchemes(2).schemeThresholds))
        ),
        Some(mockCreateDate),
        Some(mockCreatedByUser),
        "location1Scheme1"
      )

      val result = passMarkSettingsControllerWithSettings.getLatestVersion()(FakeRequest())

      status(result) must be(200)

      contentAsJson(result) must be(Json.toJson(expectedSettingsResponse))
    }
  }

  "Save new settings" should {
    def isValid(value: Settings) = true

    "Send a complete settings object to the repository with a version UUID appended" in new TestFixture {

      val passMarkSettingsRepositoryWithExpectations = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryWithExpectations.create(any(), any())).thenReturn(Future.successful(
        PassMarkSettingsCreateResponse(
          "uuid-1",
          new DateTime()
        )
      ))

      // Call controller
      val passMarkSettingsController = buildPMS(passMarkSettingsRepositoryWithExpectations)

      val result = passMarkSettingsController.createPassMarkSettings()(createPassMarkSettingsRequest(validSettingsCreateRequestJSON))

      status(result) must be(200)

      verify(passMarkSettingsRepositoryWithExpectations).create(eqTo(mockSettings), eqTo(testSchemeNames))
    }
  }

  trait TestFixture extends TestFixtureBase {

    val testSchemeNames = List(
      "TestScheme1",
      "TestScheme2",
      "TestScheme3"
    )

    val mockFrameworkRepository = mock[FrameworkRepository]

    when(mockFrameworkRepository.getFrameworkNames).thenReturn(Future.successful(testSchemeNames))

    val defaultSchemeThreshold = SchemeThreshold(20d, 80d)

    val defaultSchemeThresholds = SchemeThresholds(
      defaultSchemeThreshold, defaultSchemeThreshold, defaultSchemeThreshold, defaultSchemeThreshold, None
    )

    val mockSchemes = List(
      Scheme(testSchemeNames.head, defaultSchemeThresholds),
      Scheme(testSchemeNames(1), defaultSchemeThresholds),
      Scheme(testSchemeNames(2), defaultSchemeThresholds)
    )
    val mockVersion = "uuid-1"
    val mockCreateDate = new DateTime(1459504800000L)
    val mockCreatedByUser = "TestUser"

    val mockSettings = Settings(
      schemes = mockSchemes,
      version = mockVersion,
      createDate = mockCreateDate,
      createdByUser = mockCreatedByUser,
      setting = "location1Scheme1"
    )

    val mockUUIDFactory = mock[UUIDFactory]

    when(mockUUIDFactory.generateUUID()).thenReturn("uuid-1")

    def buildPMS(mockRepository: PassMarkSettingsRepository) = new OnlineTestPassMarkSettingsController {
      val pmsRepository = mockRepository
      val fwRepository = mockFrameworkRepository
      val auditService = mockAuditService
      val uuidFactory = mockUUIDFactory
    }

    def createPassMarkSettingsRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.OnlineTestPassMarkSettingsController.createPassMarkSettings.url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    val validSettingsCreateRequestJSON = s"""
                     |{
                     |    "createDate": 1459504800000,
                     |    "createdByUser": "TestUser",
                     |    "schemes": [
                     |        {
                     |            "schemeName": "TestScheme1",
                     |            "schemeThresholds": {
                     |                "competency": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "numerical": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "verbal": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        },
                     |        {
                     |            "schemeName": "TestScheme2",
                     |            "schemeThresholds": {
                     |                "competency": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "numerical": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "verbal": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        },
                     |        {
                     |            "schemeName": "TestScheme3",
                     |            "schemeThresholds": {
                     |                "competency": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "numerical": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "verbal": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        }
                     |    ],
                     |    "setting": "location1Scheme1"
                     |}
        """.stripMargin
  }
}
