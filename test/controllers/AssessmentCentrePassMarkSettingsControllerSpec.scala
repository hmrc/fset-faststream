/*
 * Copyright 2017 HM Revenue & Customs
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

import model.SchemeId
import model.persisted.assessmentcentre._
import org.joda.time.DateTime
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories._
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class AssessmentCentrePassMarkSettingsControllerSpec extends UnitWithAppSpec {
  val mockService = mock[AssessmentCentrePassMarkSettingsService]

  object TestableAssessmentCentrePassMarkSettingsController extends AssessmentCentrePassMarkSettingsController {
    val service = mockService
  }

  val AllAssessmentCentrePassMarkSchemes = List(
    AssessmentCentrePassMarkScheme(SchemeId("Business")),
    AssessmentCentrePassMarkScheme(SchemeId("Commercial")),
    AssessmentCentrePassMarkScheme(SchemeId("Digital and technology")),
    AssessmentCentrePassMarkScheme(SchemeId("Finance")),
    AssessmentCentrePassMarkScheme(SchemeId("Project delivery"))
  )

  "create a passmark settings" should {
    "save the passmark settings in the db" in {
      val settings = AssessmentCentrePassMarkSettings(
        AllAssessmentCentrePassMarkSchemes.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
        AssessmentCentrePassMarkInfo("version1", DateTime.now(), "userName")
      )
      when(mockService.create(settings)).thenReturn(Future.successful(()))


      val result = TestableAssessmentCentrePassMarkSettingsController.create()(createRequest(Json.toJson(settings).toString))

      status(result) must be(CREATED)
      verify(mockService).create(settings)
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
