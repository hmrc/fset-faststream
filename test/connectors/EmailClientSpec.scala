/*
 * Copyright 2022 HM Revenue & Customs
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
import connectors.ExchangeObjects.SendFsetMailRequest
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.OK
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class EmailClientSpec extends PlaySpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {

  "EmailClientSpec" should {
    "send an email if the email client is enabled" in new TestFixture {
      when(mockEmailConfig.enabled).thenReturn(true)

      client.sendWithdrawnConfirmation(to = "test.user@mail.com", name = "test user").futureValue
      verify(mockHttpClient).POST(any[String], any[SendFsetMailRequest], any[Seq[(String, String)]])(
        any[Writes[SendFsetMailRequest]],
        any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
    }

    "not send an email if the email client is disabled" in new TestFixture {
      when(mockEmailConfig.enabled).thenReturn(false)

      client.sendWithdrawnConfirmation(to = "test.user@mail.com", name = "test user").futureValue
      verify(mockHttpClient, never).POST(any[String], any[SendFsetMailRequest], any[Seq[(String, String)]])(
        any[Writes[SendFsetMailRequest]],
        any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
    }
  }

  trait TestFixture {
    implicit def hc: HeaderCarrier = HeaderCarrier()

    val mockHttpClient: WSHttpT = mock[WSHttpT]
    val mockEmailConfig: EmailConfig = mock[EmailConfig]

    val client = new EmailClient { override val http = mockHttpClient; override val emailConfig = mockEmailConfig }

    val captor = ArgumentCaptor.forClass(classOf[SendFsetMailRequest])
    when(mockHttpClient.POST[SendFsetMailRequest, HttpResponse](any[String], captor.capture,
      any[Seq[(String, String)]])(any[Writes[SendFsetMailRequest]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(
        Future.successful(HttpResponse(OK, "")))
  }
}
