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

package services.scheme

import model.SelectedSchemesExamples._
import org.mockito.Mockito._
import repositories.schemepreferences.SchemePreferencesRepository
import services.BaseServiceSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SchemePreferencesServiceSpec extends BaseServiceSpec {
  val mockSchemePreferencesRepository = mock[SchemePreferencesRepository]

  val service = new SchemePreferencesServiceImpl(mockSchemePreferencesRepository)

  "find preferences" should {
    "return selected preferences" in {
      when(mockSchemePreferencesRepository.find(AppId)).thenReturn(Future.successful(twoSchemes))
      val selectedSchemes = service.find(AppId).futureValue
      selectedSchemes mustBe twoSchemes
    }
  }

  "update preferences" should {
    "save the preferences" in {
      when(mockSchemePreferencesRepository.save(AppId, twoSchemes)).thenReturn(Future.successful(()))
      val response = service.update(AppId, twoSchemes)
      assertNoExceptions(response)
    }
  }
}
