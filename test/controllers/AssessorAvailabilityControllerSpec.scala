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
import services.assessoravailability.AssessorAvailabilityService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class AssessorAvailabilityControllerSpec extends UnitWithAppSpec {
  val mockAssessorAvailabilityService = mock[AssessorAvailabilityService]

  val controller = new AssessorAvailabilityController {
    override val assessorAvailabilityService = mockAssessorAvailabilityService
  }

  "save details" should {
    val Request = fakeRequest(AssessorAvailabilityInBothLondonAndNewcastle)

    "return Ok when save is successful" in {
      when(mockAssessorAvailabilityService.save(any[String], any[AssessorAvailability])).thenReturn(emptyFuture)
      val response = controller.save(UserId)(Request)
      status(response) mustBe OK
    }
  }

  "find" should {
    "return an assessor's availability" in {
      when(mockAssessorAvailabilityService.find(UserId)).thenReturnAsync(AssessorAvailabilityInBothLondonAndNewcastle)
      val response = controller.find(UserId)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.toJson[AssessorAvailability](AssessorAvailabilityInBothLondonAndNewcastle)
    }

    "return Not Found when availability cannot be found" in {
      when(mockAssessorAvailabilityService.find(UserId)).thenReturn(Future.failed(AssessorAvailabilityNotFoundException(UserId)))
      val response = controller.find(UserId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }
}

