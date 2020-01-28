/*
 * Copyright 2020 HM Revenue & Customs
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

import config.SecurityEnvironmentImpl
import connectors.SchemeClient.SchemePreferencesNotFound
import connectors.exchange.CivilServiceExperienceDetailsExamples._
import connectors.exchange.{ ApplicationResponse, SchemePreferencesExamples }
import connectors.{ ReferenceDataClient, ReferenceDataExamples, SchemeClient }
import forms.SelectedSchemesForm._
import models.ApplicationData.ApplicationStatus
import models._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.{ SilhouetteComponent, UserCacheService }
import testkit.{ BaseControllerSpec, TestableSecureActions }
import testkit.MockitoImplicits._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class SchemePreferencesControllerSpec extends BaseControllerSpec {

  val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]
  val schemeClient  = mock[SchemeClient]
  val referenceDataClient = mock[ReferenceDataClient]
  val userService = mock[UserCacheService]

  def controllerUnderTest = new SchemePreferencesController(schemeClient, referenceDataClient) with TestableSecureActions {
    override val env = mockSecurityEnvironment
    override lazy val silhouette = SilhouetteComponent.silhouette
    when(userService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any())).thenReturn(Future.successful(CachedData(
      mock[CachedUser],
      application = Some(mock[ApplicationData])
    )))
    when(mockSecurityEnvironment.userService).thenReturn(userService)
  }

  "present" should {
    "load scheme selections page for the new candidate" in {
      when(referenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)
      when(schemeClient.getSchemePreferences(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new SchemePreferencesNotFound))
      val result = controllerUnderTest.present(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("Choose your schemes")
      content must include (s"""name="scheme_0" value=''""")
      content must include (s"""name="scheme_1" value=''""")
      content must include (s"""name="scheme_2" value=''""")
      content must include (s"""name="scheme_3" value=''""")
      content must include (s"""name="scheme_4" value=''""")
    }

    "populate selected schemes for the candidate" in {
      when(referenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)
      when(schemeClient.getSchemePreferences(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(SchemePreferencesExamples.DefaultSelectedSchemes))
      val result = controllerUnderTest.present(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("Choose your schemes")
      content must include (s"""name="scheme_0" value='Finance'""")
      content must include (s"""name="scheme_1" value='International'""")
      content must include (s"""name="scheme_2" value=''""")
      content must include (s"""name="scheme_3" value=''""")
      content must include (s"""name="scheme_4" value=''""")
    }
  }

  "submit scheme preferences" should {
    "update scheme preferences details" in {
      val request = fakeRequest.withFormUrlEncodedBody("scheme_0" -> "Finance", "scheme_1" -> "International", "orderAgreed" -> "true",
        "eligible" -> "true")
      val applicationResponse = ApplicationResponse(currentUserId, ApplicationStatus.IN_PROGRESS.toString,
        ApplicationRoute.Faststream, currentUserId, ProgressResponseExamples.InProgress, Some(CivilServantExperience), None)
      val schemePreferences = SchemePreferences(List("Finance", "International"), orderAgreed = true, eligible = true)

      when(referenceDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)
      when(schemeClient.updateSchemePreferences(eqTo(schemePreferences))(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync()

      val result = controllerUnderTest.submit(request)
      print(contentAsString(result))
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.AssistanceDetailsController.present().url))
    }
  }
}
