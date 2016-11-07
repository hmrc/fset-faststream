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

import model.PassmarkPersistedObjects.Implicits._
import model.PassmarkPersistedObjects._
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories._
import services.passmarksettings.AssessmentCentrePassMarkSettingsService

import scala.concurrent.Future

class AssessmentCentrePassMarkSettingsControllerSpec extends PlaySpec with MockitoSugar {
  val mockAssessmentCentrePassMarkSettingsRepository = mock[AssessmentCentrePassMarkSettingsMongoRepository]
  val mockPassmarkService = mock[AssessmentCentrePassMarkSettingsService]

  object TestableAssessmentCentrePassMarkSettingsController extends AssessmentCentrePassMarkSettingsController {
    val acpmsRepository = mockAssessmentCentrePassMarkSettingsRepository
    val passmarkService = mockPassmarkService
  }

  val AllAssessmentCentrePassMarkSchemes = List(
    AssessmentCentrePassMarkScheme("Business"),
    AssessmentCentrePassMarkScheme("Commercial"),
    AssessmentCentrePassMarkScheme("Digital and technology"),
    AssessmentCentrePassMarkScheme("Finance"),
    AssessmentCentrePassMarkScheme("Project delivery")
  )

  "create a passmark settings" should {
    "save the passmark settings in the db" in {
      val settings = AssessmentCentrePassMarkSettings(
        AllAssessmentCentrePassMarkSchemes.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
        AssessmentCentrePassMarkInfo("version1", DateTime.now(), "userName")
      )
      when(mockAssessmentCentrePassMarkSettingsRepository.create(settings)).thenReturn(Future.successful(()))

      val result = TestableAssessmentCentrePassMarkSettingsController.create()(createRequest(Json.toJson(settings).toString))

      status(result) must be(CREATED)
      verify(mockAssessmentCentrePassMarkSettingsRepository).create(settings)
    }
  }

  def createRequest(jsonString: String) = {
    val json = Json.parse(jsonString)
    FakeRequest(
      Helpers.POST,
      controllers.routes.AssessmentCentrePassMarkSettingsController.create.url, FakeHeaders(), json
    ).withHeaders("Content-Type" -> "application/json")
  }
}
