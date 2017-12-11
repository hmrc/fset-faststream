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

package services.onlinetesting

import model.EvaluationResults.Amber
import model.persisted.{ApplicationReadyForEvaluation, SchemeEvaluationResult}
import model.{ApplicationRoute, ApplicationStatus, SchemeId, SelectedSchemes}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import repositories.application.GeneralApplicationRepository
import testkit.MockitoImplicits._
import testkit.UnitSpec

class CurrentSchemeStatusHelperSpec extends UnitSpec {
  "CurrentSchemeStatusHelper" must {
    "return no data when the application is not sdip faststream" in new TestFixture {
      val result = helper.getSdipResults(application).futureValue
      result mustBe Nil
    }

    "return data when the application is sdip faststream and there is sdip data in current scheme status" in new TestFixture {
      val data = Seq(SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString))
      when(mockAppRepo.getCurrentSchemeStatus(any[String])).thenReturnAsync(data)
      val result = helper.getSdipResults(application.copy(applicationRoute = ApplicationRoute.SdipFaststream)).futureValue
      result mustBe data
    }

    "return no data when the application is sdip faststream and there is no sdip data in current scheme status" in new TestFixture {
      when(mockAppRepo.getCurrentSchemeStatus(any[String])).thenReturnAsync(Nil)
      val result = helper.getSdipResults(application.copy(applicationRoute = ApplicationRoute.SdipFaststream)).futureValue
      result mustBe Nil
    }
  }

  trait TestFixture {
    val mockAppRepo: GeneralApplicationRepository = mock[GeneralApplicationRepository]

    val helper = new CurrentSchemeStatusHelper {
      override val generalAppRepository: GeneralApplicationRepository = mockAppRepo
    }

    val application = ApplicationReadyForEvaluation(
      "app1",
      ApplicationStatus.PHASE1_TESTS,
      ApplicationRoute.Faststream,
      isGis = false,
      activeCubiksTests = Nil,
      activeLaunchpadTest = None,
      prevPhaseEvaluation = None,
      preferences = SelectedSchemes(List(SchemeId("Commercial")), orderAgreed = true, eligible = true))
  }
}
