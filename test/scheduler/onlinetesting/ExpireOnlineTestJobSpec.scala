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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import model.Phase1ExpirationEvent
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import scheduler.BasicJobConfig
import services.BaseServiceSpec
import services.onlinetesting.OnlineTestService
import testkit.ShortTimeout
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }

class ExpireOnlineTestJobSpec extends BaseServiceSpec with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val serviceMock = mock[OnlineTestService]

  object TestableExpireTestJob extends ExpireOnlineTestJob {
    val onlineTestingService = serviceMock
    val expiryTest = Phase1ExpirationEvent
    override val lockId: String = "1"
    override val forceLockReleaseAfter: Duration = mock[Duration]
    override implicit val ec: ExecutionContext = mock[ExecutionContext]
    override val name: String = "test"
    override val initialDelay: FiniteDuration = mock[FiniteDuration]
    override val interval: FiniteDuration = mock[FiniteDuration]
    val config = BasicJobConfig[ScheduledJobConfig]("", "")
  }

  "expire test phase N job" should {
    "complete successfully when service completes successfully" in {
      when(serviceMock.processNextExpiredTest(eqTo(Phase1ExpirationEvent))(any[HeaderCarrier], any[RequestHeader]))
        .thenReturn(Future.successful(()))
      TestableExpireTestJob.tryExecute().futureValue mustBe unit
    }

    "fail when the service fails" in {
      when(serviceMock.processNextExpiredTest(eqTo(Phase1ExpirationEvent))(any[HeaderCarrier], any[RequestHeader]))
        .thenReturn(Future.failed(new Exception))
      TestableExpireTestJob.tryExecute().failed.futureValue mustBe an[Exception]
    }
  }
}
