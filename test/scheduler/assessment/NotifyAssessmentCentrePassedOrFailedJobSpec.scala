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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import services.applicationassessment.ApplicationAssessmentService
import testkit.ExtendedTimeout

import scala.concurrent.{ ExecutionContext, Future }

class NotifyAssessmentCentrePassedOrFailedJobSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "process next assessment centre passed or failed application" should {
    "work" in new TestFixture {
      val unit = ()
      Job.tryExecute().futureValue mustBe unit
    }
  }

  trait TestFixture extends WithApplication {
    val applicationAssessmentServiceMock = mock[ApplicationAssessmentService]
    when(applicationAssessmentServiceMock.processNextAssessmentCentrePassedOrFailedApplication).thenReturn(Future.successful(()))

    object Job extends NotifyAssessmentCentrePassedOrFailedJob {
      override val applicationAssessmentService = applicationAssessmentServiceMock
    }

    val application = OnlineTestApplicationWithCubiksUser("appId1", "userId1", 2)
  }
}
