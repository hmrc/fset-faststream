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

package scheduler.onlinetesting

import org.mockito.Matchers.{ eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import services.onlinetesting.OnlineTestService
import testkit.ShortTimeout

import scala.concurrent.{ ExecutionContext, Future }

class SendInvitationJobSpec extends PlaySpec with MockitoSugar with ScalaFutures with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val onlineTestingServiceMock = mock[OnlineTestService]

  "send invitation job" should {

    "complete successfully when there is no application ready for online testing" in new WithApplication {
      object TestableSendInvitationJob extends SendInvitationJob {
        val onlineTestingService = onlineTestingServiceMock
      }
      when(onlineTestingServiceMock.nextApplicationReadyForOnlineTesting).thenReturn(Future.successful(None))
      TestableSendInvitationJob.tryExecute().futureValue mustBe (_: Unit)
    }

    "fail when there is an exception getting next application ready for online testing" in new WithApplication {
      object TestableSendInvitationJob extends SendInvitationJob {
        val onlineTestingService = onlineTestingServiceMock
      }
      when(onlineTestingServiceMock.nextApplicationReadyForOnlineTesting).thenReturn(Future.failed(new Exception))
      TestableSendInvitationJob.tryExecute().failed.futureValue mustBe an[Exception]
    }
  }
}
