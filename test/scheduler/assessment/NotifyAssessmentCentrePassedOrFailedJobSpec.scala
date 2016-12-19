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

package scheduler.assessment

import model.OnlineTestCommands.OnlineTestApplicationWithCubiksUser
import org.mockito.Mockito._
import services.applicationassessment.ApplicationAssessmentService
import testkit.{ ExtendedTimeout, UnitWithAppSpec }

import scala.concurrent.{ ExecutionContext, Future }

class NotifyAssessmentCentrePassedOrFailedJobSpec extends UnitWithAppSpec with ExtendedTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "process next assessment centre passed or failed application" should {
    "work" in new TestFixture {
      Job.tryExecute().futureValue mustBe unit
    }
  }

  trait TestFixture {
    val applicationAssessmentServiceMock = mock[ApplicationAssessmentService]
    when(applicationAssessmentServiceMock.processNextAssessmentCentrePassedOrFailedApplication).thenReturn(Future.successful(()))

    object Job extends NotifyAssessmentCentrePassedOrFailedJob {
      override val applicationAssessmentService = applicationAssessmentServiceMock
      override val config = NotifyAssessmentCentrePassedOrFailedJobConfig
    }

    val application = OnlineTestApplicationWithCubiksUser("appId1", "userId1", 2)
  }
}
