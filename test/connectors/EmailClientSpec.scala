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

package connectors

import config._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.OK
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EmailClientSpec extends PlaySpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {

  "EmailClientSpec" should {
    "send an email if the email client is enabled" in new TestFixture {
      when(mockEmailConfig.enabled).thenReturn(true)
      when(mockEmailConfig.url).thenReturn("http://localhost")
      when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse.apply(OK, "")))

      client.sendWithdrawnConfirmation(to = "test.user@mail.com", name = "test user").futureValue
      verify(mockHttp).post(any[URL])(any[HeaderCarrier])
    }

    "not send an email if the email client is disabled" in new TestFixture {
      when(mockEmailConfig.enabled).thenReturn(false)

      client.sendWithdrawnConfirmation(to = "test.user@mail.com", name = "test user").futureValue
      verify(mockHttp, never).post(any[URL])(any[HeaderCarrier])
    }
  }

  trait TestFixture {
    implicit def hc: HeaderCarrier = HeaderCarrier()

    val mockHttp: HttpClientV2 = mock[HttpClientV2]
    val mockEmailConfig: EmailConfig = mock[EmailConfig]

    val client = new EmailClient { override val http = mockHttp; override val emailConfig = mockEmailConfig }

    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

    when(mockHttp.post(any[URL])(any[HeaderCarrier])).thenReturn(mockRequestBuilder)

    when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)

    def requestBuilderExecute[A]: Future[A] = mockRequestBuilder.execute[A](any[HttpReads[A]], any[ExecutionContext])
  }
}
