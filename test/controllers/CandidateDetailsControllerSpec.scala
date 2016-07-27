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

import model.Exceptions.{CannotUpdateContactDetails, CannotUpdateRecord}
import model.command.UpdateGeneralDetailsExamples._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.Helpers._
import services.AuditService
import services.generaldetails.CandidateDetailsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class CandidateDetailsControllerSpec extends BaseControllerSpec {
  val mockCandidateDetailsService = mock[CandidateDetailsService]
  val mockAuditService = mock[AuditService]

  def controller = new CandidateDetailsController {
    val candidateDetailsService = mockCandidateDetailsService
    val auditService = mockAuditService
  }

  "update details" should {
    val Request = fakeRequest(GeneralDetailsInsideUK)

    "return Created when update is successful" in {
      when(mockCandidateDetailsService.update("appId", "userId", GeneralDetailsInsideUK)).thenReturn(Future.successful())
      reset(mockAuditService)

      val response = controller.updateDetails("userId", "appId")(Request)

      status(response) mustBe CREATED
      verify(mockAuditService).logEvent(eqTo("PersonalDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return Bad Request when CannotUpdateContactDetails is thrown" in {
      when(mockCandidateDetailsService.update("appId", "userId", GeneralDetailsInsideUK))
        .thenReturn(Future.failed(CannotUpdateContactDetails("userId")))
      reset(mockAuditService)

      val response = controller.updateDetails("userId", "appId")(Request)

      status(response) mustBe BAD_REQUEST
      verify(mockAuditService, never).logEvent(eqTo("PersonalDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return Bad Request when CannotUpdateRecord is thrown" in {
      when(mockCandidateDetailsService.update("appId", "userId", GeneralDetailsInsideUK))
        .thenReturn(Future.failed(CannotUpdateRecord("userId")))
      reset(mockAuditService)

      val response = controller.updateDetails("userId", "appId")(Request)

      status(response) mustBe BAD_REQUEST
      verify(mockAuditService, never).logEvent(eqTo("PersonalDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }
  }
}
