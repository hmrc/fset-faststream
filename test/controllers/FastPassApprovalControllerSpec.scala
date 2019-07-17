/*
 * Copyright 2019 HM Revenue & Customs
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


import model.command.FastPassEvaluation
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.fastpass.FastPassService
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class FastPassApprovalControllerSpec extends UnitWithAppSpec {

  "FastPassApprovalController" should {
    "respond ok" in new TestFixture {
      when(mockFastPassService.processFastPassCandidate(any[String], any[String], any[Boolean], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(serviceResponse)
      val response = controllerUnderTest.processFastPassCandidate("userId", "appId")(fakeRequest(request))
      status(response) mustBe OK

      verify(mockFastPassService).processFastPassCandidate(eqTo("userId"), eqTo("appId"), eqTo(true), eqTo("adminId"))(
        any[HeaderCarrier](), any[RequestHeader])
      verifyNoMoreInteractions(mockFastPassService)
    }
  }


  trait TestFixture {
    implicit val hc = HeaderCarrier()
    implicit val rh: RequestHeader = FakeRequest("GET", "some/path")
    val mockFastPassService = mock[FastPassService]
    val request = FastPassEvaluation(accepted = true, "adminId")
    val serviceResponse: Future[(String, String)] = Future.successful(("George", "Foreman"))

    def controllerUnderTest = new FastPassApprovalController {
      val fastPassService = mockFastPassService
    }
  }

}
