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

import model.Exceptions.SchemePreferencesNotFound
import model.SelectedSchemesExamples._
import model.command.SchemePreferences
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import play.api.test.Helpers._
import services.AuditService
import services.scheme.SchemePreferencesService

import scala.concurrent.Future

class SchemePreferencesControllerSpec extends BaseControllerSpec {
  val mockSchemePreferencesService = mock[SchemePreferencesService]
  val mockAuditService = mock[AuditService]

  val controller = new SchemePreferencesController {
    val schemePreferencesService = mockSchemePreferencesService
    val auditService = mockAuditService
  }

  "find preferences" should {
    "return scheme preferences with all possible schemes" in {
      when(mockSchemePreferencesService.find(AppId)).thenReturn(Future.successful(TwoSchemes))

      val response = controller.find(AppId)(fakeRequest)

      val schemePreferences = contentAsJson(response).as[SchemePreferences]
      schemePreferences.selectedSchemes mustBe Some(TwoSchemes)
      schemePreferences.allSchemes.size mustBe 16
    }

    "return scheme preferences with empty selected schemes" in {
      when(mockSchemePreferencesService.find(AppId)).thenReturn(Future.failed(SchemePreferencesNotFound(AppId)))

      val response = controller.find(AppId)(fakeRequest)

      val schemePreferences = contentAsJson(response).as[SchemePreferences]
      schemePreferences.selectedSchemes mustBe None
      schemePreferences.allSchemes.size mustBe 16
    }
  }

  "update preferences" should {
    val Request = fakeRequest(TwoSchemes)

    "create a new scheme preferences" in {
      when(mockSchemePreferencesService.update(AppId, TwoSchemes)).thenReturn(Future.successful(()))
      val response = controller.update(AppId)(Request)
      status(response) mustBe OK
    }
  }
}
