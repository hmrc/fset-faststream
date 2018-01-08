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

package scheduler.onlinetesting

import org.mockito.Mockito._
import services.onlinetesting.OnlineTestService
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }

class SendInvitationJobSpec extends UnitWithAppSpec with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val onlineTestingServiceMock = mock[OnlineTestService]
  object TestableSendInvitationJob extends SendInvitationJob {
    override val onlineTestingService = onlineTestingServiceMock
    override val lockId: String = "1"
    override val forceLockReleaseAfter: Duration = mock[Duration]
    override implicit val ec: ExecutionContext = mock[ExecutionContext]
    override val name: String = "test"
    override val initialDelay: FiniteDuration = mock[FiniteDuration]
    override val interval: FiniteDuration = mock[FiniteDuration]
    val config = SendPhase1InvitationJobConfig
  }

  "send invitation job" should {
    "complete successfully when there is no application ready for online testing" in {
      when(onlineTestingServiceMock.nextApplicationsReadyForOnlineTesting(1)).thenReturn(Future.successful(Nil))
      TestableSendInvitationJob.tryExecute().futureValue mustBe unit
    }

    "fail when there is an exception getting next application ready for online testing" in {
      when(onlineTestingServiceMock.nextApplicationsReadyForOnlineTesting(1)).thenReturn(Future.failed(new Exception))
      TestableSendInvitationJob.tryExecute().failed.futureValue mustBe an[Exception]
    }
  }
}
