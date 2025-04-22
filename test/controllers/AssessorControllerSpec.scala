/*
 * Copyright 2023 HM Revenue & Customs
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
import model.UniqueIdentifier
import model.exchange.assessor.AssessorAvailabilityExamples._
import model.exchange.assessor.AssessorExamples
import model.exchange.{ Assessor, AssessorAvailabilities }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.AuditService
import services.assessor.AssessorService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class AssessorControllerSpec extends UnitWithAppSpec {

  "save assessor" must {
    "return OK and log AssessorSaved audit event" +
      "when save is successful" in new TestFixture {
      when(mockAssessorService.saveAssessor(eqTo(AssessorExamples.UserId1), eqTo(AssessorExamples.Assessor1))).thenReturn(Future.successful(()))

      val request = fakeRequest(AssessorExamples.Assessor1)
      val response = controller.saveAssessor(AssessorExamples.UserId1)(request)

      status(response) mustBe OK
      val logDetails = Map("assessor" -> AssessorExamples.Assessor1.toString)
      verify(mockAuditService).logEvent(eqTo("AssessorSaved"), eqTo(logDetails))(any(), any(), any())
    }

    "return FAILED_DEPENDENCY " +
      "when there is a CannotUpdateAssessorWhenSkillsAreRemovedAndFutureAllocationExistsException" in new TestFixture {
      val Request = fakeRequest(AssessorExamples.Assessor1)
      when(mockAssessorService.saveAssessor(eqTo(AssessorExamples.UserId1), eqTo(AssessorExamples.Assessor1))).thenReturn(
        Future.failed(CannotUpdateAssessorWhenSkillsAreRemovedAndFutureAllocationExistsException("", "")))

      val response = controller.saveAssessor(AssessorExamples.UserId1)(Request)

      status(response) mustBe FAILED_DEPENDENCY
      verify(mockAuditService, never()).logEvent(any(), any())(any(), any(), any())
    }

    "return CONFLICT " +
      "when there is a OptimisticLockException" in new TestFixture {
      val Request = fakeRequest(AssessorExamples.Assessor1)
      when(mockAssessorService.saveAssessor(eqTo(AssessorExamples.UserId1), eqTo(AssessorExamples.Assessor1))).thenReturn(
        Future.failed(OptimisticLockException("")))

      val response = controller.saveAssessor(AssessorExamples.UserId1)(Request)

      status(response) mustBe CONFLICT
      verify(mockAuditService, never()).logEvent(any(), any())(any(), any(), any())
    }
  }

  "add availability" must {
    "return Ok when availability is added" in new TestFixture {
      when(mockAssessorService.saveAvailability(any[AssessorAvailabilities])).thenReturnAsync()
      val response = controller.saveAvailability()(fakeRequest(AssessorAvailabilitiesSum))
      status(response) mustBe OK
    }
  }

  "find assessor" must {
    "return Assessor when is successful" in new TestFixture {
      when(mockAssessorService.findAssessor(eqTo(AssessorExamples.UserId1))).thenReturn(Future.successful(AssessorExamples.Assessor1))
      val response = controller.findAssessor(AssessorExamples.UserId1)(fakeRequest)
      status(response) mustBe OK
      verify(mockAssessorService).findAssessor(eqTo(AssessorExamples.UserId1))
      contentAsJson(response) mustBe Json.toJson[Assessor](AssessorExamples.Assessor1)
    }

    "return Not Found when assessor cannot be found" in new TestFixture {
      when(mockAssessorService.findAssessor(UserId)).thenReturn(Future.failed(AssessorNotFoundException(UserId)))
      val response = controller.findAssessor(UserId)(fakeRequest)
      status(response) mustBe NOT_FOUND
      verify(mockAssessorService).findAssessor(eqTo(UserId))
    }
  }

  "find availability" must {
    "return an assessor's availability" in new TestFixture {
      when(mockAssessorService.findAvailability(UserId)).thenReturnAsync(AssessorAvailabilitiesSum)
      val response = controller.findAvailability(UserId)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.toJson[AssessorAvailabilities](AssessorAvailabilitiesSum)
    }

    "return Not Found when availability cannot be found" in new TestFixture {
      when(mockAssessorService.findAvailability(UserId)).thenReturn(Future.failed(AssessorNotFoundException(UserId)))
      val response = controller.findAvailability(UserId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "count submitted" must {
    "return zero if there are none submitted" in new TestFixture {
      when(mockAssessorService.countSubmittedAvailability()).thenReturnAsync(0L)
      val response = controller.countSubmittedAvailability()(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.obj("size" -> 0)
    }

    "return five if there are five submitted" in new TestFixture {
      when(mockAssessorService.countSubmittedAvailability()).thenReturnAsync(5L)
      val response = controller.countSubmittedAvailability()(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.obj("size" -> 5)
    }

    "return an internal server error" in new TestFixture {
      // Pass an Integer to generate a java.lang.ClassCastException when the code tries to create a java.lang.Long
      when(mockAssessorService.countSubmittedAvailability()).thenReturnAsync(0)
      val response = controller.countSubmittedAvailability()(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "removeAssessor" must {
    "return CONFLICT " +
      "when there is CannotRemoveAssessorWhenFutureAllocationExistsException" in new TestFixture {
      val userId = UniqueIdentifier.randomUniqueIdentifier
      when(mockAssessorService.remove(eqTo(userId))).thenReturn(
        Future.failed(CannotRemoveAssessorWhenFutureAllocationExistsException("", "")))

      val response = controller.removeAssessor(userId)(fakeRequest)

      status(response) mustBe CONFLICT
      response.futureValue
      verify(mockAuditService, never()).logEvent(any(), any())(any(), any(), any())
    }

    "return NOT_FOUND " +
      "when there is CannotRemoveAssessorWhenFutureAllocationExistsException" in new TestFixture {
      val userId = UniqueIdentifier.randomUniqueIdentifier
      when(mockAssessorService.remove(eqTo(userId))).thenReturn(Future.failed(AssessorNotFoundException("")))

      val response = controller.removeAssessor(userId)(fakeRequest)

      status(response) mustBe NOT_FOUND
      response.futureValue
      verify(mockAuditService, never()).logEvent(any(), any())(any(), any(), any())
    }

    "return OK and log AssessorRemoved audit event" +
      "when assessor is removed" in new TestFixture {
      val userId = UniqueIdentifier.randomUniqueIdentifier
      when(mockAssessorService.remove(eqTo(userId))).thenReturnAsync()
      val response = controller.removeAssessor(userId)(fakeRequest)

      status(response) mustBe OK

      response.futureValue
      verify(mockAssessorService).remove(eqTo(userId))
      val logDetails = Map("userId" -> userId.toString)
      verify(mockAuditService).logEvent(eqTo("AssessorRemoved"), eqTo(logDetails))(any(), any(), any())
    }
  }

  trait TestFixture {
    val mockAssessorService = mock[AssessorService]
    val mockAuditService = mock[AuditService]
    val controller = new AssessorController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockAssessorService,
      mockAuditService
    )
  }
}
