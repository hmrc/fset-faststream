/*
 * Copyright 2018 HM Revenue & Customs
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

package services.reporting

import factories.DateTimeFactory
import model.ProgressStatuses.ProgressStatus._
import model.ProgressStatuses._
import model.persisted.{ UserApplicationProfile, UserIdWithEmail }
import org.mockito.Mockito._
import repositories.application.ReportingRepository
import repositories.contactdetails.ContactDetailsRepository
import services.BaseServiceSpec
import testkit.ShortTimeout

import scala.concurrent.Future

// TODO: Resurrect as part of duplications ticket

class DuplicateDetectionServiceSpec extends BaseServiceSpec with ShortTimeout {
/*
  "Find all" should {
    "detect no duplications if no applications" in new TestFixture {
      override val allApplications = Nil
      val result = service.findAll.futureValue
      result mustBe Nil
    }

    "detect no duplications if only one application" in new TestFixture {
      val app1 = UserApplicationProfile("1", PHASE3_TESTS_PASSED, "first1", "last1", dob)
      override val allApplications = List(app1)

      val result = service.findAll.futureValue
      result mustBe Nil
    }

    "detect no duplications if no exported to parity applications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", dob)
      val app3 = UserApplicationProfile("3", SUBMITTED, "first1", "last1", differentDob)
      override val allApplications = List(app1, app2, app3)

      val result = service.findAll.futureValue
      result mustBe Nil
    }

    "detect all 'three fields' duplications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", dob)
      val app3 = UserApplicationProfile("3", PHASE1_TESTS_FAILED, "first1", "last1", dob)
      val app4 = UserApplicationProfile("4", SUBMITTED, "first1", "last2", differentDob)
      override val allApplications = List(app1, app2, app3, app4)

      val result = service.findAll.futureValue
      result mustBe List(DuplicateApplicationGroup(1, List(
        DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user2@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user3@email", "first1", "last1", PHASE1_TESTS_FAILED)
      )))
    }

    "detect all 'two fields' duplications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", differentDob)
      val app3 = UserApplicationProfile("3", PHASE1_TESTS_FAILED, "first1", "last2", dob)
      val app4 = UserApplicationProfile("4", SUBMITTED, "first2", "last1", dob)
      val app5 = UserApplicationProfile("5", SUBMITTED, "first2", "last2", dob)
      val allApplications = List(app1, app2, app3, app4, app5)
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(allApplications))

      val result = service.findAll.futureValue
      result mustBe List(DuplicateApplicationGroup(2, List(
        DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user2@email", "first1", "last1", SUBMITTED),
        DuplicateCandidate("user3@email", "first1", "last2", PHASE1_TESTS_FAILED),
        DuplicateCandidate("user4@email", "first2", "last1", SUBMITTED)
      )))
    }

    "find and group all 'three fields' and 'two fields' duplications" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob)
      val app2 = UserApplicationProfile("2", SUBMITTED, "first1", "last1", dob)
      val app3 = UserApplicationProfile("3", PHASE1_TESTS_FAILED, "first1", "last1", differentDob)
      val app4 = UserApplicationProfile("4", PHASE1_TESTS_FAILED, "first2", "second2", dob)
      val app5 = UserApplicationProfile("5", PHASE1_TESTS_FAILED, "first2", "second2", differentDob)
      val allApplications = List(app1, app2, app3, app4, app5)

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

    "find duplications even if the duplicated applications do not have an email" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, "first1", "last1", dob)
      val appWithoutEmail1 = UserApplicationProfile("6", SUBMITTED, "first1", "last1", dob)
      val appWithoutEmail2 = UserApplicationProfile("7", SUBMITTED, "first1", "diff", dob)
      val allApplications = List(app1, appWithoutEmail1, appWithoutEmail2)

      val result = service.findAll.futureValue
      result mustBe List(
        DuplicateApplicationGroup(1, List(
          DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
          DuplicateCandidate("", "first1", "last1", SUBMITTED))
        ),
        DuplicateApplicationGroup(2, List(
          DuplicateCandidate("user1@email", "first1", "last1", SUBMITTED),
          DuplicateCandidate("", "first1", "diff", SUBMITTED))
        )
      )
    }

    "detect duplications even if the first name and last name differ by white spaces or lower/upper cases" in new TestFixture {
      val app1 = UserApplicationProfile("1", SUBMITTED, " First1", "last1", dob)
      val app2 = UserApplicationProfile("2", SUBMITTED, "fIRST1   ", "    last1   ", dob)
      val app3 = UserApplicationProfile("3", PHASE1_TESTS_FAILED, " first1", "      LAST1 ", dob)
      override val allApplications = List(app1, app2, app3)

      val result = service.findAll.futureValue
      result mustBe List(DuplicateApplicationGroup(1, List(
        DuplicateCandidate("user1@email", " First1", "last1", SUBMITTED),
        DuplicateCandidate("user2@email", "fIRST1   ", "    last1   ", SUBMITTED),
        DuplicateCandidate("user3@email", " first1", "      LAST1 ", PHASE1_TESTS_FAILED)
      )))
    }
  }

  trait TestFixture {
    val dob = DateTimeFactory.nowLocalDate.minusYears(40)
    val differentDob = dob.plusDays(30)
    val reportingRepositoryMock = mock[ReportingRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]

    val allApplications: List[UserApplicationProfile]

    when(cdRepositoryMock.findEmails).thenReturn(Future.successful(List(
      UserIdWithEmail("1", "user1@email"),
      UserIdWithEmail("2", "user2@email"),
      UserIdWithEmail("3", "user3@email"),
      UserIdWithEmail("4", "user4@email"),
      UserIdWithEmail("5", "user5@email")
    )))

    lazy val service = new DuplicateDetectionService {
      when(reportingRepositoryMock.candidatesForDuplicateDetectionReport).thenReturn(Future.successful(allApplications))

      val reportingRepository: ReportingRepository = reportingRepositoryMock
      val cdRepository: ContactDetailsRepository = cdRepositoryMock
    }
  }
  */
}
