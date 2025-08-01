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

package controllers

import config.TestFixtureBase
import model.Exceptions.CannotUpdateAssistanceDetails
import model.command.AssistanceDetailsExchangeExamples
import model.exchange.AssistanceDetailsExchange
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.Helpers._
import services.assistancedetails.AssistanceDetailsService
import testkit.UnitWithAppSpec
import testkit.MockitoImplicits._

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

class AssistanceDetailsControllerSpec extends UnitWithAppSpec {

  "Update" should {
    "return CREATED and update the details and audit AssistanceDetailsSaved event" in new TestFixture {
      val Request = fakeRequest(AssistanceDetailsExchangeExamples.DisabilityGisAndAdjustments)
      when(mockAssistanceDetailsService.update(AppId, UserId, AssistanceDetailsExchangeExamples.DisabilityGisAndAdjustments)
      ).thenReturnAsync()
      val result = controller.update(UserId, AppId)(Request)
      status(result) mustBe CREATED
      verify(mockAuditService).logEvent(eqTo("AssistanceDetailsSaved"))(any[HeaderCarrier], any[RequestHeader], any[ExecutionContext])
    }

    "return BAD_REQUEST when there is a CannotUpdateAssistanceDetails exception" in new TestFixture {
      val details = AssistanceDetailsExchange(hasDisability = "Yes", disabilityImpact = Some("No"), disabilityCategories = Some(List("cat1")),
        otherDisabilityDescription = Some(""), guaranteedInterview = Some(false),
        needsSupportAtVenue = Some(false), needsSupportAtVenueDescription = None,
        needsSupportForPhoneInterview = None, needsSupportForPhoneInterviewDescription = None)
      val Request = fakeRequest(details)
      when(mockAssistanceDetailsService.update(AppId, UserId, details)).thenReturn(Future.failed(CannotUpdateAssistanceDetails(UserId)))
      val result = controller.update(UserId, AppId)(Request)
      status(result) mustBe BAD_REQUEST
      verify(mockAuditService, times(0)).logEvent(eqTo("AssistanceDetailsSaved"))(any[HeaderCarrier], any[RequestHeader], any[ExecutionContext])
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockAssistanceDetailsService = mock[AssistanceDetailsService]
    val controller = new AssistanceDetailsController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockAssistanceDetailsService,
      mockAuditService
    )
  }
}
