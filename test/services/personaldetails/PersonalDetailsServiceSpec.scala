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

package services.personaldetails

import model.command.GeneralDetailsExamples._
import model.persisted.ContactDetailsExamples._
import model.persisted.FSACIndicator
import model.persisted.PersonalDetailsExamples._
import model.{ ApplicationStatus, CivilServiceExperienceDetails }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.csv.FSACIndicatorCSVRepository
import repositories.fsacindicator.FSACIndicatorRepository
import repositories.personaldetails.PersonalDetailsRepository
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersonalDetailsServiceSpec extends UnitWithAppSpec with ShortTimeout {
  val mockPersonalDetailsRepository = mock[PersonalDetailsRepository]
  val mockContactDetailsRepository = mock[ContactDetailsRepository]
  val mockCivilServiceExperienceDetailsRepository = mock[CivilServiceExperienceDetailsRepository]
  val mockFSACIndicatorCSVRepository = mock[FSACIndicatorCSVRepository]
  val mockFSACIndicatorRepository = mock[FSACIndicatorRepository]

  val service = new PersonalDetailsService(
    mockPersonalDetailsRepository,
    mockContactDetailsRepository,
    mockCivilServiceExperienceDetailsRepository,
    mockFSACIndicatorCSVRepository,
    mockFSACIndicatorRepository
  )

  "find candidate" should {
    "return personal and contact details" in {
      when(mockPersonalDetailsRepository.find(AppId)).thenReturn(Future.successful(JohnDoe))
      when(mockContactDetailsRepository.find(UserId)).thenReturn(Future.successful(ContactDetailsUK))
      when(mockCivilServiceExperienceDetailsRepository.find(AppId)
      ).thenReturn(Future.successful(Some(CivilServiceExperienceDetails(applicable = false))))
      when(mockFSACIndicatorRepository.find(AppId)).thenReturn(Future.successful(FSACIndicator("London", "London", "1")))
      when(mockFSACIndicatorCSVRepository.find(any(), any())).thenReturn(Some(model.FSACIndicator("London", "London")))

      val response = service.find(AppId, UserId).futureValue
      response mustBe CandidateContactDetailsUK.copy(updateApplicationStatus = None)
    }

    "return personal and contact details for sdip candidates" in {
      when(mockPersonalDetailsRepository.find(AppId)).thenReturn(Future.successful(SdipJohnDoe))
      when(mockContactDetailsRepository.find(UserId)).thenReturn(Future.successful(ContactDetailsUK))
      when(mockCivilServiceExperienceDetailsRepository.find(AppId)
      ).thenReturn(Future.successful(Some(CivilServiceExperienceDetails(applicable = false))))
      when(mockFSACIndicatorRepository.find(AppId)).thenReturn(Future.successful(FSACIndicator("London", "London", "1")))
      when(mockFSACIndicatorCSVRepository.find(any(), any())).thenReturn(Some(model.FSACIndicator("London", "London")))

      val response = service.find(AppId, UserId).futureValue
      response mustBe CandidateContactDetailsUKSdip.copy(updateApplicationStatus = None)
    }
  }

  "update candidate" should {
    "update personal and contact details" in {
      when(mockPersonalDetailsRepository.update(eqTo(AppId), eqTo(UserId), eqTo(JohnDoe), any[Seq[ApplicationStatus.Value]],
        any[ApplicationStatus.Value])).thenReturn(Future.successful(()))
      when(mockContactDetailsRepository.update(UserId, ContactDetailsUK)).thenReturn(emptyFuture)
      when(mockCivilServiceExperienceDetailsRepository.update(AppId, CandidateContactDetailsUK.civilServiceExperienceDetails.get)
      ).thenReturn(emptyFuture)
      when(mockFSACIndicatorCSVRepository.find(any(), any())).thenReturn(Some(model.FSACIndicator("London", "London")))
      when(mockFSACIndicatorRepository.update(eqTo(AppId), eqTo(UserId), eqTo(FSACIndicator("London", "London", "1")))
      ).thenReturn(emptyFuture)

      when(mockFSACIndicatorCSVRepository.FSACIndicatorVersion).thenReturn("1")

      val response = service.update(AppId, UserId, CandidateContactDetailsUK)
      assertNoExceptions(response)
    }

    "throw an exception when updateApplicationStatus is not set" in {
      intercept[IllegalArgumentException] {
        service.update(AppId, UserId, CandidateContactDetailsUK.copy(updateApplicationStatus = None))
      }
    }
  }
}
