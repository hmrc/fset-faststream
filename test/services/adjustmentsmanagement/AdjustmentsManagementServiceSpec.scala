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

package services.adjustmentsmanagement

import model.Adjustments
import model.CandidateExamples._
import model.Exceptions.ApplicationNotFound
import model.persisted.ContactDetailsExamples._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.BaseServiceSpec
import services.stc.StcEventServiceFixture
import services.testdata.examples.AdjustmentsExamples._
import testkit.ShortTimeout

import scala.concurrent.Future

class AdjustmentsManagementServiceSpec extends BaseServiceSpec with ShortTimeout {

  "confirm adjustment" should {
    "throw an exception when there is no application" in new TestFixture {
      when(mockAppRepository.find(AppId)).thenReturn(Future.successful(None))
      val ex = service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).failed.futureValue
      ex mustBe a[ApplicationNotFound]
    }

    "confirm new adjustments" in new TestFixture {
      service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).futureValue

      verify(mockAppRepository).confirmAdjustments(AppId, InvigilatedETrayAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsConfirmed")
    }

    "update adjustments" in new TestFixture {
      when(mockAppRepository.findAdjustments(AppId)).thenReturn(Future.successful(Some(ETrayTimeExtensionAdjustments)))
      service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).futureValue

      verify(mockAppRepository).confirmAdjustments(AppId, InvigilatedETrayAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsChanged")
    }

    "remove adjustments"in new TestFixture {
      when(mockAppRepository.findAdjustments(AppId)).thenReturn(Future.successful(Some(ETrayTimeExtensionAdjustments)))
      service.confirmAdjustment(AppId, EmptyAdjustments).futureValue

      verify(mockAppRepository).confirmAdjustments(AppId, EmptyAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsChanged")
    }
  }

  trait TestFixture extends StcEventServiceFixture {
    val mockAppRepository = mock[GeneralApplicationRepository]
    val mockCdRepository = mock[ContactDetailsRepository]

    when(mockCdRepository.find(UserId)).thenReturn(Future.successful(ContactDetailsUK))
    when(mockAppRepository.find(AppId)).thenReturn(Future.successful(Some(minCandidate(UserId))))
    when(mockAppRepository.findAdjustments(AppId)).thenReturn(Future.successful(None))
    when(mockAppRepository.confirmAdjustments(any[String], any[Adjustments])).thenReturn(Future.successful(()))

    val service = new AdjustmentsManagementService {
      val appRepository = mockAppRepository
      val cdRepository = mockCdRepository
      override val eventService = eventServiceMock
    }
  }
}
