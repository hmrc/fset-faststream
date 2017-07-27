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

package services.passmarksettings

import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.{ Scheme, SchemeId }
import model.persisted.assessmentcentre._
import org.joda.time.DateTime
import org.mockito.Mockito._
import repositories.{ AssessmentCentrePassMarkSettingsMongoRepository, FrameworkRepository, SchemeRepositoryImpl }
import testkit.{ UnitSpec, UnitWithAppSpec }

import scala.concurrent.Future

class AssessmentCentrePassMarkSettingsServiceSpec extends UnitWithAppSpec {
  val mockAssessmentCentrePassMarkSettingsRepository = mock[AssessmentCentrePassMarkSettingsMongoRepository]
  val mockSchemeRepository = mock[SchemeRepositoryImpl]

  val AllAssessmentCentrePassMarkSchemeIds = List(
    AssessmentCentrePassMarkScheme(SchemeId("Business")),
    AssessmentCentrePassMarkScheme(SchemeId("Commercial")),
    AssessmentCentrePassMarkScheme(SchemeId("Digital and technology")),
    AssessmentCentrePassMarkScheme(SchemeId("Finance")),
    AssessmentCentrePassMarkScheme(SchemeId("Project delivery"))
  )

  val AllSchemes = List(
    Scheme(SchemeId("Business"), "Business", "Business", None, false),
    Scheme(SchemeId("Commercial"), "Commercial", "Commercial", None, false),
    Scheme(SchemeId("Digital and technology"), "Digital and technology", "Digital and technology", None, false),
    Scheme(SchemeId("Finance"), "Finance", "Finance", None, false),
    Scheme(SchemeId("Project delivery"), "Project delivery", "Project delivery", None, false))

  val AllSchemes2 = List(
    Scheme(SchemeId("Business"), "Business", "Business", None, false),
    Scheme(SchemeId("Commercial"), "Commercial", "Commercial", None, false),
    Scheme(SchemeId("Digital and technology"), "Digital and technology", "Digital and technology", None, false),
    Scheme(SchemeId("Finance"), "Finance", "Finance", None, false),
    Scheme(SchemeId("Project delivery"), "Project delivery", "Project delivery", None, false),
    Scheme(SchemeId("New scheme"), "New scheme", "New scheme", None, false)
  )

  object TestableAssessmentCentrePassMarkSettingsService extends AssessmentCentrePassMarkSettingsService {
    val schemeRepository = mockSchemeRepository
    val assessmentCentrePassMarkSettingsRepository = mockAssessmentCentrePassMarkSettingsRepository
  }

  "get latest version" should {
    when(mockSchemeRepository.schemes).thenReturn(AllSchemes)

    "return empty scores with Schemes when there is no passmark settings" in {
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(None))

      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue

      result must be(AssessmentCentrePassMarkSettingsResponse(AllAssessmentCentrePassMarkSchemeIds, None))
    }

    "return the latest version of passmark settings" in {
      val settings = AssessmentCentrePassMarkSettings(
        AllAssessmentCentrePassMarkSchemeIds.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
        AssessmentCentrePassMarkInfo("version1", DateTime.now(), "userName")
      )
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(Some(settings)))

      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue

      result must be(AssessmentCentrePassMarkSettingsResponse(settings.schemes, Some(settings.info)))
    }

    "return the latest passmark settings together with new schemes even if they have not been set" in {
      val savedPassmarkSettings = AssessmentCentrePassMarkSettings(
        AllAssessmentCentrePassMarkSchemeIds.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
        AssessmentCentrePassMarkInfo("version1", DateTime.now(), "userName")
      )
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(Some(savedPassmarkSettings)))
      when(mockSchemeRepository.schemes).thenReturn(AllSchemes2)

      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue

      result must be(AssessmentCentrePassMarkSettingsResponse(
        savedPassmarkSettings.schemes :+ AssessmentCentrePassMarkScheme(SchemeId("New scheme")),
        Some(savedPassmarkSettings.info)
      ))
    }
  }
}
