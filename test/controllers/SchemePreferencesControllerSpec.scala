/*
 * Copyright 2018 HM Revenue & Customs
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

import model.Exceptions.{ CannotUpdateSchemePreferences, SchemePreferencesNotFound }
import model.SelectedSchemes
import model.SelectedSchemesExamples._
import org.mockito.Mockito._
import play.api.test.Helpers._
import services.AuditService
import services.scheme.SchemePreferencesService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class SchemePreferencesControllerSpec extends UnitWithAppSpec {
  val mockSchemePreferencesService = mock[SchemePreferencesService]
  val mockAuditService = mock[AuditService]

  val controller = new SchemePreferencesController {
    val schemePreferencesService = mockSchemePreferencesService
    val auditService = mockAuditService
  }

  "find preferences" should {
    "return scheme preferences" in {
      when(mockSchemePreferencesService.find(AppId)).thenReturn(Future.successful(TwoSchemes))

      val response = controller.find(AppId)(fakeRequest)

      val selectedSchemes = contentAsJson(response).as[SelectedSchemes]
      selectedSchemes mustBe TwoSchemes
    }

    "return not found when selected schemes do not exist" in {
      when(mockSchemePreferencesService.find(AppId)).thenReturn(Future.failed(SchemePreferencesNotFound(AppId)))

      val response = controller.find(AppId)(fakeRequest)

      status(response) mustBe NOT_FOUND
    }
  }

  "update preferences" should {
    val Request = fakeRequest(TwoSchemes)

    "create a new scheme preferences" in {
      when(mockSchemePreferencesService.update(AppId, TwoSchemes)).thenReturn(emptyFuture)
      val response = controller.update(AppId)(Request)
      status(response) mustBe OK
    }

    "return bad request when update fails" in {
      when(mockSchemePreferencesService.update(AppId, TwoSchemes)).thenReturn(Future.failed(CannotUpdateSchemePreferences(AppId)))
      val response = controller.update(AppId)(Request)
      status(response) mustBe BAD_REQUEST
    }
  }
}
