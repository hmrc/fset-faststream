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

package controllers

import model.Exceptions._
import model.exchange.AssessorAvailability
import model.exchange.assessoravailability.AssessorAvailabilityExamples._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.assessoravailability.AssessorService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import testkit.MockitoImplicits._

class AssessorControllerSpec extends UnitWithAppSpec {
  val mockAssessorService = mock[AssessorService]

  val controller = new AssessorController {
    override val assessorService = mockAssessorService
  }

  "save details" should {
    val Request = fakeRequest(AssessorAvailabilityInBothLondonAndNewcastle)

    "return Ok when save is successful" in {
      when(mockAssessorService.addAvailability(any[String], any[AssessorAvailability])).thenReturn(emptyFuture)
      val response = controller.addAvailability(UserId)(Request)
      status(response) mustBe OK
    }
  }

  "find availability" should {
    "return an assessor's availability" in {
      when(mockAssessorService.findAvailability(UserId)).thenReturnAsync(AssessorAvailabilityInBothLondonAndNewcastle)
      val response = controller.findAvailability(UserId)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.toJson[AssessorAvailability](AssessorAvailabilityInBothLondonAndNewcastle)
    }

    "return Not Found when availability cannot be found" in {
      when(mockAssessorService.findAvailability(UserId)).thenReturn(Future.failed(AssessorNotFoundException(UserId)))
      val response = controller.findAvailability(UserId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "count submitted" should {
    "return zero if there are none submitted" in {
      when(mockAssessorService.countSubmittedAvailability()).thenReturnAsync(0)
      val response = controller.countSubmittedAvailability()(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.obj("size" -> 0)
    }

    "return five if there are five submitted" in {
      when(mockAssessorService.countSubmittedAvailability()).thenReturnAsync(5)
      val response = controller.countSubmittedAvailability()(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.obj("size" -> 5)
    }
  }
}

