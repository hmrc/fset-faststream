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

package services.generaldetails

import model.ApplicationStatus
import model.command.UpdateGeneralDetailsExamples._
import model.persisted.ContactDetailsExamples._
import model.persisted.PersonalDetailsExamples._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.{AuditService, BaseServiceSpec}

import scala.concurrent.Future

class CandidateDetailsServiceSpec extends BaseServiceSpec {
  val mockPdRepository = mock[PersonalDetailsRepository]
  val mockCdRepository = mock[ContactDetailsRepository]
  val mockAuditService = mock[AuditService]

  val service = new CandidateDetailsService {
    val pdRepository = mockPdRepository
    val cdRepository = mockCdRepository
    val auditService = mockAuditService
  }

  "update candidate" should {
    when(mockPdRepository.update(eqTo(AppId), eqTo(UserId), eqTo(JohnDoe), any[Seq[ApplicationStatus.Value]],
      any[ApplicationStatus.Value])).thenReturn(Future.successful(()))
    when(mockCdRepository.update(UserId, ContactDetailsUK)).thenReturn(emptyFuture)
    "update personal and contact details" in {
      val response = service.update(AppId, UserId, CandidateContactDetailsUK)

      assertNoExceptions(response)
    }

    "throw an exception when updateApplicationStatus is not set" in {
      intercept[IllegalArgumentException] {
        service.update(AppId, UserId, CandidateContactDetailsUK.copy(updateApplicationStatus = None))
      }
    }
  }

  "find candidate" should {
    "return personal and contact details" in {
      when(mockPdRepository.find(AppId)).thenReturn(Future.successful(JohnDoe))
      when(mockCdRepository.find(UserId)).thenReturn(Future.successful(ContactDetailsUK))

      val response = service.find(AppId, UserId).futureValue

      response mustBe CandidateContactDetailsUK.copy(updateApplicationStatus = None)
    }
  }
}
