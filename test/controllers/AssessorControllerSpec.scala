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
import model.exchange.Assessor
import model.exchange.assessor.AssessorExamples
import model.persisted
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import persisted.assessor
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.assessoravailability.AssessorService
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import testkit.MockitoImplicits._

import scala.util.Success

class AssessorControllerSpec extends UnitWithAppSpec {
  val mockAssessorService = mock[AssessorService]

  val controller = new AssessorController {
    override val assessorService = mockAssessorService
  }

  "save assessor" should {
    "return OK when save is successful" in {
      val Request = fakeRequest(AssessorExamples.Assessor1)
      when(mockAssessorService.saveAssessor(eqTo(AssessorExamples.UserId1), eqTo(AssessorExamples.Assessor1))).thenReturn(Future.successful(()))
      val response = controller.saveAssessor(AssessorExamples.UserId1)(Request)
      status(response) mustBe OK
    }
  }

  "find assessor" should {
    "return Assessor when is successful" in {
      when(mockAssessorService.findAssessor(eqTo(AssessorExamples.UserId1))).thenReturn(Future.successful(AssessorExamples.Assessor1))
      val response = controller.findAssessor(AssessorExamples.UserId1)(fakeRequest)
      status(response) mustBe OK
      verify(mockAssessorService).findAssessor(eqTo(AssessorExamples.UserId1))
      contentAsJson(response) mustBe Json.toJson[Assessor](AssessorExamples.Assessor1)
    }

    "return Not Found when assessor cannot be found" in {
      when(mockAssessorService.findAssessor(UserId)).thenReturn(Future.failed(AssessorNotFoundException(UserId)))
      val response = controller.findAssessor(UserId)(fakeRequest)
      status(response) mustBe NOT_FOUND
      verify(mockAssessorService).findAssessor(eqTo(UserId))
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

