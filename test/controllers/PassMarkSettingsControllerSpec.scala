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
import model.PassMarkSettingsCreateResponse
import model.SchemeId
import model.exchange.passmarksettings._
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.{ Format, Json }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import services.AuditService
import services.passmarksettings.PassMarkSettingsService
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps

class Phase1PassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  type T = Phase1PassMarkSettings
  type U = Phase1PassMark
  implicit val formatter = Phase1PassMarkSettings.jsonFormat
  val argumentCaptor = ArgumentCaptor.forClass(classOf[Phase1PassMarkSettings])
  val passMarkThresholds = Phase1PassMarkThresholds(defaultSchemeThreshold, defaultSchemeThreshold,
    defaultSchemeThreshold, defaultSchemeThreshold)
  val passMarks = List(
    Phase1PassMark(SchemeId("Finance"), passMarkThresholds),
    Phase1PassMark(SchemeId("Commercial"), passMarkThresholds),
    Phase1PassMark(SchemeId("Generalist"), passMarkThresholds))
  val passMarkSettings = Phase1PassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  val createdEvent = "Phase1PassMarksCreated"

  val mockPassMarkSettingsService = mock[PassMarkSettingsService[Phase1PassMarkSettings]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  val controller = new PassMarkSettingsController[Phase1PassMarkSettings](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:Phase1PassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }

  val jsonSchemeThresholds = """
                               | "schemeThresholds": {
                               |   "test1": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   },
                               |   "test2": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   },
                               |   "test3": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   },
                               |   "test4": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   }
                               | }
                             """
  val createUrl = controllers.routes.Phase1PassMarkSettingsController.create.url
}

class Phase2PassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  type T = Phase2PassMarkSettings
  type U = Phase2PassMark
  implicit val formatter = Phase2PassMarkSettings.jsonFormat
  val argumentCaptor = ArgumentCaptor.forClass(classOf[Phase2PassMarkSettings])
  val passMarkThresholds = Phase2PassMarkThresholds(defaultSchemeThreshold, defaultSchemeThreshold)
  val passMarks = List(
    Phase2PassMark(SchemeId("Finance"), passMarkThresholds),
    Phase2PassMark(SchemeId("Commercial"), passMarkThresholds),
    Phase2PassMark(SchemeId("Generalist"), passMarkThresholds))
  val passMarkSettings = Phase2PassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  val createdEvent = "Phase2PassMarksCreated"

  val mockPassMarkSettingsService = mock[PassMarkSettingsService[Phase2PassMarkSettings]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  val controller = new PassMarkSettingsController[Phase2PassMarkSettings](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:Phase2PassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }
  val jsonSchemeThresholds = """
                               | "schemeThresholds": {
                               |   "test1": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   },
                               |   "test2": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   }
                               | }
                             """
  val createUrl = controllers.routes.Phase2PassMarkSettingsController.create.url

}

class Phase3PassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  type T = Phase3PassMarkSettings
  type U = Phase3PassMark
  implicit val formatter = Phase3PassMarkSettings.jsonFormat
  val argumentCaptor = ArgumentCaptor.forClass(classOf[Phase3PassMarkSettings])
  val passMarkThresholds = Phase3PassMarkThresholds(defaultSchemeThreshold)
  val passMarks = List(
    Phase3PassMark(SchemeId("Finance"), passMarkThresholds),
    Phase3PassMark(SchemeId("Commercial"), passMarkThresholds),
    Phase3PassMark(SchemeId("Generalist"), passMarkThresholds))
  val passMarkSettings = Phase3PassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  val createdEvent = "Phase3PassMarksCreated"

  val mockPassMarkSettingsService = mock[PassMarkSettingsService[Phase3PassMarkSettings]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  val controller = new PassMarkSettingsController[Phase3PassMarkSettings](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:Phase3PassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }
  val jsonSchemeThresholds = """
                               | "schemeThresholds": {
                               |   "videoInterview": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   }
                               | }
                             """
  val createUrl = controllers.routes.Phase3PassMarkSettingsController.create.url

}

class AssessmentCentrePassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  type T = AssessmentCentrePassMarkSettings
  type U = AssessmentCentrePassMark
  implicit val formatter = AssessmentCentrePassMarkSettings.jsonFormat
  val argumentCaptor = ArgumentCaptor.forClass(classOf[AssessmentCentrePassMarkSettings])
  val competencySchemeThreshold = PassMarkThreshold(2.0d, 3.0d)
  val overallSchemeThreshold = PassMarkThreshold(2.0d, 14.0d)
  val passMarkThresholds = AssessmentCentrePassMarkThresholds(competencySchemeThreshold, competencySchemeThreshold, competencySchemeThreshold,
    competencySchemeThreshold, overallSchemeThreshold)
  val passMarks = List(
    AssessmentCentrePassMark(SchemeId("Finance"), passMarkThresholds),
    AssessmentCentrePassMark(SchemeId("Commercial"), passMarkThresholds),
    AssessmentCentrePassMark(SchemeId("Generalist"), passMarkThresholds))
  val passMarkSettings = AssessmentCentrePassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  val createdEvent = "AssessmentCentrePassMarksCreated"

  val mockPassMarkSettingsService = mock[PassMarkSettingsService[AssessmentCentrePassMarkSettings]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  val controller = new PassMarkSettingsController[AssessmentCentrePassMarkSettings](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:AssessmentCentrePassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }

  val jsonSchemeThresholds = """
                               | "schemeThresholds": {
                               |   "seeingTheBigPicture": {
                               |     "failThreshold": 2.0,
                               |     "passThreshold": 3.0
                               |   },
                               |   "makingEffectiveDecisions": {
                               |     "failThreshold": 2.0,
                               |     "passThreshold": 3.0
                               |   },
                               |   "communicatingAndInfluencing": {
                               |     "failThreshold": 2.0,
                               |     "passThreshold": 3.0
                               |   },
                               |   "workingTogetherDevelopingSelfAndOthers": {
                               |     "failThreshold": 2.0,
                               |     "passThreshold": 3.0
                               |   },
                               |   "overall": {
                               |     "failThreshold": 2.0,
                               |     "passThreshold": 14.0
                               |   }
                               | }
                             """
  val createUrl = controllers.routes.AssessmentCentrePassMarkSettingsController.create.url
}

trait PassMarkSettingsControllerSpec extends UnitWithAppSpec {
  type T <: PassMarkSettings
  type U <: PassMark
  implicit val formatter: Format[T]
  val argumentCaptor: ArgumentCaptor[T]
  val passMarkThresholds: PassMarkThresholds
  val passMarks: List[U]
  val passMarkSettings: T
  val createdEvent: String
  val mockPassMarkSettingsService: PassMarkSettingsService[T]
  val controller: PassMarkSettingsController[T]
  val jsonSchemeThresholds: String
  val createUrl: String

  val defaultSchemeThreshold = PassMarkThreshold(20d, 80d)

  val mockVersion = "uuid-1"
  val mockCreateDate = new DateTime(1459504800000L)
  val mockCreatedBy = "TestUser"

  val mockUUIDFactory = mock[UUIDFactory]
  val mockJsonFormat = Json.format[Phase1PassMarkSettings]
  val mockAuditService = mock[AuditService]

  when(mockUUIDFactory.generateUUID()).thenReturn("uuid-1")

  def createPassMarkSettingsRequest(jsonString: String) = {
    val json = Json.parse(jsonString)
    FakeRequest(Helpers.PUT, createUrl, FakeHeaders(), json)
      .withHeaders("Content-Type" -> "application/json")
  }

  def validSettingsCreateRequestJSON = s"""
                                          |{
                                          |    "createDate": 1459504800000,
                                          |    "createdBy": "TestUser",
                                          |    "version" : "version-0",
                                          |    "schemes": [
                                          |        {
                                          |            "schemeId": "Finance",
                                          |            $jsonSchemeThresholds
                                          |        },
                                          |        {
                                          |            "schemeId": "Commercial",
                                          |            $jsonSchemeThresholds
                                          |        },
                                          |        {
                                          |            "schemeId": "Generalist",
                                          |            $jsonSchemeThresholds
                                          |        }
                                          |    ],
                                          |    "setting": "schemes"
                                          |}
        """.stripMargin

  "Try and get latest settings" should {
    "Return 404 if there are no settings saved" in {
      when(mockPassMarkSettingsService.getLatestPassMarkSettings(any[Format[T]])).thenReturn(Future.successful(None))

      val result = controller.getLatestVersion()(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "Return a complete settings object if there are saved settings" in {
      when(mockPassMarkSettingsService.getLatestPassMarkSettings(any[Format[T]])).thenReturn(
        Future.successful(Some(passMarkSettings)))

      val result = controller.getLatestVersion()(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(passMarkSettings)
    }
  }

  "Save new settings" should {
    "Send a complete settings object to the repository with a version UUID appended" in {
      when(mockPassMarkSettingsService.createPassMarkSettings(any())(any())).thenReturn(Future.successful(
        PassMarkSettingsCreateResponse(
          "uuid-1",
          new DateTime()
        )
      ))

      val result = controller.create()(createPassMarkSettingsRequest(validSettingsCreateRequestJSON))

      status(result) mustBe OK

      verify(mockPassMarkSettingsService).createPassMarkSettings(argumentCaptor.capture)(any())

      val settingsParam = argumentCaptor.getValue

      settingsParam.schemes mustBe passMarkSettings.schemes
      settingsParam.createdBy mustBe passMarkSettings.createdBy
      settingsParam.version mustBe passMarkSettings.version
    }
  }
}
