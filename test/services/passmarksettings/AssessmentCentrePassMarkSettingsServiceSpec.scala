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
import model.PassmarkPersistedObjects._
import org.joda.time.DateTime
import org.mockito.Mockito._
import repositories.{ AssessmentCentrePassMarkSettingsMongoRepository, FrameworkRepository }
import testkit.UnitSpec

import scala.concurrent.Future

class AssessmentCentrePassMarkSettingsServiceSpec extends UnitSpec {
  val mockAssessmentCentrePassMarkSettingsRepository = mock[AssessmentCentrePassMarkSettingsMongoRepository]
  val mockFrameworkRepository = mock[FrameworkRepository]

  val AllAssessmentCentrePassMarkSchemes = List(
    AssessmentCentrePassMarkScheme("Business"),
    AssessmentCentrePassMarkScheme("Commercial"),
    AssessmentCentrePassMarkScheme("Digital and technology"),
    AssessmentCentrePassMarkScheme("Finance"),
    AssessmentCentrePassMarkScheme("Project delivery")
  )

  object TestableAssessmentCentrePassMarkSettingsService extends AssessmentCentrePassMarkSettingsService {
    val fwRepository = mockFrameworkRepository
    val acpsRepository = mockAssessmentCentrePassMarkSettingsRepository
  }

  "get latest version" should {
    when(mockFrameworkRepository.getFrameworkNames).thenReturn(Future.successful(List(
      "Business", "Commercial", "Digital and technology", "Finance", "Project delivery"
    )))

    "return empty scores with Schemes when there is no passmark settings" in {
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(None))

      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue

      result must be(AssessmentCentrePassMarkSettingsResponse(AllAssessmentCentrePassMarkSchemes, None))
    }

    "return the latest version of passmark settings" in {
      val settings = AssessmentCentrePassMarkSettings(
        AllAssessmentCentrePassMarkSchemes.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
        AssessmentCentrePassMarkInfo("version1", DateTime.now(), "userName")
      )
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(Some(settings)))

      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue

      result must be(AssessmentCentrePassMarkSettingsResponse(settings.schemes, Some(settings.info)))
    }

    "return the latest passmark settings together with new schemes even if they have not been set" in {
      val savedPassmarkSettings = AssessmentCentrePassMarkSettings(
        AllAssessmentCentrePassMarkSchemes.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
        AssessmentCentrePassMarkInfo("version1", DateTime.now(), "userName")
      )
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(Some(savedPassmarkSettings)))
      when(mockFrameworkRepository.getFrameworkNames).thenReturn(Future.successful(List(
        "Business", "Commercial", "Digital and technology", "Finance", "Project delivery", "New scheme"
      )))

      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue

      result must be(AssessmentCentrePassMarkSettingsResponse(
        savedPassmarkSettings.schemes :+ AssessmentCentrePassMarkScheme("New scheme"),
        Some(savedPassmarkSettings.info)
      ))
    }
  }
}
