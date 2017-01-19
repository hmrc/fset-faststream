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

package services.reporting.duplicatedetection

import factories.DateTimeFactory
import model.ProgressStatuses._
import model.persisted.{ ContactDetailsWithId, UserApplicationProfile }
import repositories.application.ReportingRepository
import services.BaseServiceSpec
import org.mockito.Mockito._
import ProgressStatus._
import model.AddressExamples
import repositories.contactdetails.ContactDetailsRepository
import testkit.ShortTimeout

import scala.concurrent.Future

class DuplicateDetectionServiceSpec extends BaseServiceSpec with ShortTimeout {

  "Find all" should {
    "detect no duplications if no applications" in new TestFixture {
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(Nil))

      val result = service.findAll.futureValue
      result mustBe Nil
    }

    "detect no duplications if only one application" in new TestFixture {
      val app1 = UserApplicationProfile("1", EXPORTED, "first1", "last1", dob, exportedToParity = true)
      val applications = List(app1)
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(applications))

      val result = service.findAll.futureValue
      result mustBe Nil
    }

    "detect no duplications if no exported to parity applications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob, exportedToParity = false)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", dob, exportedToParity = false)
      val app3 = UserApplicationProfile("3", SUBMITTED, "first1", "last1", differentDob, exportedToParity = false)
      val applications = List(app1, app2, app3)
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(applications))

      val result = service.findAll.futureValue
      result mustBe Nil
    }

    "detect all 'three fields' duplications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob, exportedToParity = true)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", dob, exportedToParity = false)
      val app3 = UserApplicationProfile("3", PHASE1_TESTS_FAILED, "first1", "last1", dob, exportedToParity = false)
      val app4 = UserApplicationProfile("4", SUBMITTED, "first1", "last2", differentDob, exportedToParity = false)
      val applications = List(app1, app2, app3, app4)
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(applications))

      val result = service.findAll.futureValue
      result mustBe List(DuplicateApplicationGroup(1, List(
        DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user2@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user3@email", "first1", "last1", PHASE1_TESTS_FAILED)
      )))
    }

    "detect all 'two fields' duplications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob, exportedToParity = true)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", differentDob, exportedToParity = false)
      val app3 = UserApplicationProfile("3", PHASE1_TESTS_FAILED, "first1", "last2", dob, exportedToParity = false)
      val app4 = UserApplicationProfile("4", SUBMITTED, "first2", "last1", dob, exportedToParity = false)
      val app5 = UserApplicationProfile("5", SUBMITTED, "first2", "last2", dob, exportedToParity = false)
      val applications = List(app1, app2, app3, app4, app5)
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(applications))

      val result = service.findAll.futureValue
      result mustBe List(DuplicateApplicationGroup(2, List(
        DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user2@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user3@email", "first1", "last2", PHASE1_TESTS_FAILED),
        DuplicateCandidate("user4@email", "first2", "last1", SUBMITTED)
      )))
    }

    "find and group all 'three fields' and 'two fields' duplications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob, exportedToParity = true)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", dob, exportedToParity = false)
      val app3 = UserApplicationProfile("3", PHASE1_TESTS_FAILED, "first1", "last1", differentDob, exportedToParity = false)
      val app4 = UserApplicationProfile("4", PHASE1_TESTS_FAILED, "first2", "second2", dob, exportedToParity = true)
      val app5 = UserApplicationProfile("5", PHASE1_TESTS_FAILED, "first2", "second2", differentDob, exportedToParity = false)
      val applications = List(app1, app2, app3, app4, app5)
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(applications))

      val result = service.findAll.futureValue
      result mustBe List(
        DuplicateApplicationGroup(1, List(
          DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
          DuplicateCandidate("user2@email", "first1", "last1", SUBMITTED))
        ),
        DuplicateApplicationGroup(2, List(
          DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
          DuplicateCandidate("user3@email", "first1", "last1", PHASE1_TESTS_FAILED))
        ),
        DuplicateApplicationGroup(2, List(
          DuplicateCandidate("user4@email", "first2", "second2", PHASE1_TESTS_FAILED),
          DuplicateCandidate("user5@email", "first2", "second2", PHASE1_TESTS_FAILED))
        )
      )
    }
  }

  trait TestFixture {
    val dob = DateTimeFactory.nowLocalDate.minusYears(40)
    val differentDob = dob.plusDays(30)
    val reportingRepositoryMock = mock[ReportingRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]

    when(cdRepositoryMock.findAll).thenReturn(Future.successful(List(
      ContactDetailsWithId("1", AddressExamples.FullAddress, None, "user1@email", None),
      ContactDetailsWithId("2", AddressExamples.FullAddress, None, "user2@email", None),
      ContactDetailsWithId("3", AddressExamples.FullAddress, None, "user3@email", None),
      ContactDetailsWithId("4", AddressExamples.FullAddress, None, "user4@email", None),
      ContactDetailsWithId("5", AddressExamples.FullAddress, None, "user5@email", None)
    )))

    val service = new DuplicateDetectionService {
      val reportingRepository: ReportingRepository = reportingRepositoryMock
      val cdRepository: ContactDetailsRepository = cdRepositoryMock
    }
  }
}
