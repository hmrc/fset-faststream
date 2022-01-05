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

package services

import java.util.UUID

import connectors.ApplicationClient
import connectors.exchange.Phase3TestGroupExamples
import models.UniqueIdentifier
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import testkit.BaseSpec

import scala.concurrent.Future

class Phase3FeedbackServiceSpec extends BaseSpec {

  val mockApplicationClient = mock[ApplicationClient]

  val service = new Phase3FeedbackService(mockApplicationClient)

  val applicationId = UniqueIdentifier.apply(UUID.randomUUID())

  "getFeedback" should {
    "return High for performance and High for suggestions" in {
      when(mockApplicationClient.getPhase3TestGroup(eqTo(applicationId))(any())).thenReturn(
        Future.successful(Phase3TestGroupExamples.phase3TestWithResults(Some(4.0), Some(4.0))))
      val result = service.getFeedback(applicationId).futureValue
      result must be(Some(("High", "High")))
    }

    "return Medium for performance and Medium for suggestions" in {
      when(mockApplicationClient.getPhase3TestGroup(eqTo(applicationId))(any())).thenReturn(
        Future.successful(Phase3TestGroupExamples.phase3TestWithResults(Some(2.5), Some(2.5))))
      val result = service.getFeedback(applicationId).futureValue
      result must be(Some(("Medium", "Medium")))
    }

    "return Low for performance and Low for suggestions" in {
      when(mockApplicationClient.getPhase3TestGroup(eqTo(applicationId))(any())).thenReturn(
        Future.successful(Phase3TestGroupExamples.phase3TestWithResults(Some(1.0), Some(1.0))))
      val result = service.getFeedback(applicationId).futureValue
      result must be(Some(("Low", "Low")))
    }

    "return Medium for performance and High for suggestions" in {
      when(mockApplicationClient.getPhase3TestGroup(eqTo(applicationId))(any())).thenReturn(
        Future.successful(Phase3TestGroupExamples.phase3TestWithResults(Some(2.5), Some(3.5))))
      val result = service.getFeedback(applicationId).futureValue
      result must be(Some(("Medium", "High")))
    }
  }
}
