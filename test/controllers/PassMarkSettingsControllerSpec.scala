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
import play.api.libs.json.{ Format, Json }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import services.AuditService
import services.passmarksettings.PassMarkSettingsService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class Phase1PassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  override type T = Phase1PassMarkSettings
  override type U = Phase1PassMark
  override type V = Phase1PassMarkSettingsPersistence
  override implicit val formatter = Phase1PassMarkSettings.jsonFormat
  implicit val formatter2 = Phase1PassMarkSettingsPersistence.jsonFormat
  override val argumentCaptor = ArgumentCaptor.forClass(classOf[Phase1PassMarkSettingsPersistence])
  val passMarkThresholds = Phase1PassMarkThresholds(defaultSchemeThreshold, defaultSchemeThreshold,
    defaultSchemeThreshold, defaultSchemeThreshold)
  override val passMarks = List(
    Phase1PassMark(SchemeId("Finance"), passMarkThresholds),
    Phase1PassMark(SchemeId("Commercial"), passMarkThresholds),
    Phase1PassMark(SchemeId("Generalist"), passMarkThresholds))
  override val passMarkSettings = Phase1PassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val passMarkSettingsPersistence = Phase1PassMarkSettingsPersistence(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val createdEvent = "Phase1PassMarksCreated"

  override val mockPassMarkSettingsService = mock[PassMarkSettingsService[Phase1PassMarkSettingsPersistence]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  override val controller = new PassMarkSettingsController[Phase1PassMarkSettings, Phase1PassMarkSettingsPersistence](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:Phase1PassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }

  override val jsonSchemeThresholds = """
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
  override val createUrl = controllers.routes.Phase1PassMarkSettingsController.create.url
}

class Phase2PassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  override type T = Phase2PassMarkSettings
  override type U = Phase2PassMark
  override type V = Phase2PassMarkSettingsPersistence
  override implicit val formatter = Phase2PassMarkSettings.jsonFormat
  implicit val formatter2 = Phase1PassMarkSettingsPersistence.jsonFormat
  override val argumentCaptor = ArgumentCaptor.forClass(classOf[Phase2PassMarkSettingsPersistence])
  override val passMarkThresholds = Phase2PassMarkThresholds(defaultSchemeThreshold, defaultSchemeThreshold)
  override val passMarks = List(
    Phase2PassMark(SchemeId("Finance"), passMarkThresholds),
    Phase2PassMark(SchemeId("Commercial"), passMarkThresholds),
    Phase2PassMark(SchemeId("Generalist"), passMarkThresholds))
  override val passMarkSettings = Phase2PassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val passMarkSettingsPersistence = Phase2PassMarkSettingsPersistence(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val createdEvent = "Phase2PassMarksCreated"

  override val mockPassMarkSettingsService = mock[PassMarkSettingsService[Phase2PassMarkSettingsPersistence]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  override val controller = new PassMarkSettingsController[Phase2PassMarkSettings, Phase2PassMarkSettingsPersistence](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:Phase2PassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }
  override val jsonSchemeThresholds = """
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
  override val createUrl = controllers.routes.Phase2PassMarkSettingsController.create.url
}

class Phase3PassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  override type T = Phase3PassMarkSettings
  override type U = Phase3PassMark
  override type V = Phase3PassMarkSettingsPersistence
  override implicit val formatter = Phase3PassMarkSettings.jsonFormat
  implicit val formatter2 = Phase3PassMarkSettingsPersistence.jsonFormat
  override val argumentCaptor = ArgumentCaptor.forClass(classOf[Phase3PassMarkSettingsPersistence])
  override val passMarkThresholds = Phase3PassMarkThresholds(defaultSchemeThreshold)
  override val passMarks = List(
    Phase3PassMark(SchemeId("Finance"), passMarkThresholds),
    Phase3PassMark(SchemeId("Commercial"), passMarkThresholds),
    Phase3PassMark(SchemeId("Generalist"), passMarkThresholds))
  override val passMarkSettings = Phase3PassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val passMarkSettingsPersistence = Phase3PassMarkSettingsPersistence(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val createdEvent = "Phase3PassMarksCreated"

  override val mockPassMarkSettingsService = mock[PassMarkSettingsService[Phase3PassMarkSettingsPersistence]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  override val controller = new PassMarkSettingsController[Phase3PassMarkSettings, Phase3PassMarkSettingsPersistence](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:Phase3PassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }
  override val jsonSchemeThresholds = """
                               | "schemeThresholds": {
                               |   "videoInterview": {
                               |     "failThreshold": 20.0,
                               |     "passThreshold": 80.0
                               |   }
                               | }
                             """
  override val createUrl = controllers.routes.Phase3PassMarkSettingsController.create.url
}

class AssessmentCentrePassMarkSettingsControllerSpec extends PassMarkSettingsControllerSpec {
  override type T = AssessmentCentrePassMarkSettings
  override type U = AssessmentCentrePassMark
  override type V = AssessmentCentrePassMarkSettingsPersistence
  override implicit val formatter = AssessmentCentrePassMarkSettings.jsonFormat
  implicit val formatter2 = AssessmentCentrePassMarkSettingsPersistence.jsonFormat
  override val argumentCaptor = ArgumentCaptor.forClass(classOf[AssessmentCentrePassMarkSettingsPersistence])
  val competencySchemeThreshold = PassMarkThreshold(2.0d, 3.0d)
  val overallSchemeThreshold = PassMarkThreshold(2.0d, 14.0d)
  val passMarkThresholds = AssessmentCentrePassMarkThresholds(competencySchemeThreshold, competencySchemeThreshold, competencySchemeThreshold,
    competencySchemeThreshold, overallSchemeThreshold)
  override val passMarks = List(
    AssessmentCentrePassMark(SchemeId("Finance"), passMarkThresholds),
    AssessmentCentrePassMark(SchemeId("Commercial"), passMarkThresholds),
    AssessmentCentrePassMark(SchemeId("Generalist"), passMarkThresholds))
  override val passMarkSettings = AssessmentCentrePassMarkSettings(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val passMarkSettingsPersistence = AssessmentCentrePassMarkSettingsPersistence(
    schemes = passMarks,
    version = mockVersion,
    createDate = mockCreateDate,
    createdBy = mockCreatedBy
  )
  override val createdEvent = "AssessmentCentrePassMarksCreated"

  override val mockPassMarkSettingsService = mock[PassMarkSettingsService[AssessmentCentrePassMarkSettingsPersistence]]

  val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

  override val controller = new PassMarkSettingsController[AssessmentCentrePassMarkSettings,
    AssessmentCentrePassMarkSettingsPersistence](stubCC) {
    val passMarkService = mockPassMarkSettingsService
    val auditService = mockAuditService
    val uuidFactory = mockUUIDFactory
    def upgradeVersion(passMarkSettings:AssessmentCentrePassMarkSettings, newVersionUUID: String) =
      passMarkSettings.copy(version = uuidFactory.generateUUID(), createDate = DateTime.now())
    val passMarksCreatedEvent = createdEvent
  }

  override val jsonSchemeThresholds = """
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
  override val createUrl = controllers.routes.AssessmentCentrePassMarkSettingsController.create.url
}

trait PassMarkSettingsControllerSpec extends UnitWithAppSpec {
  type T <: PassMarkSettings
  type U <: PassMark
  type V <: PassMarkSettingsPersistence
  implicit val formatter: Format[T]
  val argumentCaptor: ArgumentCaptor[V]
  val passMarkThresholds: PassMarkThresholds
  val passMarks: List[U]
  val passMarkSettings: T
  val passMarkSettingsPersistence: V
  val createdEvent: String
  val mockPassMarkSettingsService: PassMarkSettingsService[V]
  val controller: PassMarkSettingsController[T, V]
  val jsonSchemeThresholds: String
  val createUrl: String

  val defaultSchemeThreshold = PassMarkThreshold(20d, 80d)

  val mockVersion = "uuid-1"
  val mockCreateDate = new DateTime(1459504800000L)
  val mockCreatedBy = "TestUser"

  val mockUUIDFactory = mock[UUIDFactory]
  import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
  import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
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
      when(mockPassMarkSettingsService.getLatestPassMarkSettings(any[Format[V]])).thenReturn(Future.successful(None))

      val result = controller.getLatestVersion()(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "Return a complete settings object if there are saved settings" in {
      when(mockPassMarkSettingsService.getLatestPassMarkSettings(any[Format[V]])).thenReturn(
        Future.successful(Some(passMarkSettingsPersistence)))

      val result = controller.getLatestVersion()(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(passMarkSettings)
    }
  }

  "Save new settings" should {
    "Send a complete settings object to the repository with a version UUID appended" in {
      when(mockPassMarkSettingsService.createPassMarkSettings(any())(any(), any())).thenReturn(Future.successful(
        PassMarkSettingsCreateResponse(
          "uuid-1",
          new DateTime()
        )
      ))

      val result = controller.create()(createPassMarkSettingsRequest(validSettingsCreateRequestJSON))

      status(result) mustBe OK

      verify(mockPassMarkSettingsService).createPassMarkSettings(argumentCaptor.capture)(any(), any())

      val settingsParam = argumentCaptor.getValue

      settingsParam.schemes mustBe passMarkSettings.schemes
      settingsParam.createdBy mustBe passMarkSettings.createdBy
      settingsParam.version mustBe passMarkSettings.version
    }
  }
}
