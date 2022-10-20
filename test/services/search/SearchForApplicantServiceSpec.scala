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

package services.search

import connectors.AuthProviderClient
import model.persisted.ContactDetailsWithId
import model.{ Address, Candidate, SearchCandidate }
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.BaseServiceSpec
import testkit.ShortTimeout
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SearchForApplicantServiceSpec extends BaseServiceSpec with ShortTimeout {

  "find by criteria" should {
    "search by first name only" in new TestFixture {
      when(authProviderClientMock.findByFirstName(any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(firstOrPreferredName = Some("Leia"),
        lastName = None, dateOfBirth = None, postCode = None)).futureValue

      actual mustBe List(expected)
    }

    "search by last name only" in new TestFixture {
      when(authProviderClientMock.findByLastName(any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(firstOrPreferredName = None,
        lastName = Some("Amadala"), dateOfBirth = None, postCode = None)).futureValue

      actual mustBe List(expected)
    }

    "search by first name and last name" in new TestFixture {
      when(authProviderClientMock.findByFirstNameAndLastName(any[String], any[String], any[List[String]])
      (any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(firstOrPreferredName = Some("Leia"),
        lastName = Some("Amadala"), dateOfBirth = None, postCode = None)).futureValue

      actual mustBe List(expected)
    }

    "search by date of birth only" in new TestFixture {
      when(appRepositoryMock.findByCriteria(any[Option[String]], any[Option[String]],
        any[Option[LocalDate]], any[List[String]])
      ).thenReturn(Future.successful(List(Candidate("123", None, None, None, Some("Leia"), Some("Amadala"), None,
        Some(new LocalDate("1990-11-25")), None, None, None, None, None))))

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(firstOrPreferredName = None,
        lastName = None, dateOfBirth = Some(new LocalDate("1990-11-25")), postCode = None)).futureValue

      val expectedWithDateOfBirth = expected.copy(dateOfBirth = Some(new LocalDate("1990-11-25")))
      actual mustBe List(expectedWithDateOfBirth)
    }

    "filter by post code" in new TestFixture {
      when(authProviderClientMock.findByFirstNameAndLastName(any[String], any[String], any[List[String]])
      (any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(firstOrPreferredName = Some("Leia"),
        lastName = Some("Amadala"), dateOfBirth = None, postCode = Some("QQ1 1QQ"))).futureValue

      actual mustBe List(expected)
    }
  }

  trait TestFixture {
    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val authProviderClientMock = mock[AuthProviderClient]

    val searchForApplicantService = new SearchForApplicantService(
      appRepositoryMock,
      cdRepositoryMock,
      authProviderClientMock
    )

    implicit val headerCarrier = HeaderCarrier()

    val testAddress = Address(line1 = "1 Test Street", line2 = None, line3 = None, line4 = None)
    val testEmail = "test@test.com"
    val expected = Candidate("123", None, None, Some(testEmail), Some("Leia"), Some("Amadala"),
      None, None, Some(testAddress), Some("QQ1 1QQ"), None, None, None
    )

    when(cdRepositoryMock.findByPostCode(any[String])).thenReturn(
      Future.successful(
        List(ContactDetailsWithId(userId = "123", postCode = Some("QQ1 1QQ"), outsideUk = false, address = testAddress,
          email = testEmail, phone = None))
      )
    )

    when(cdRepositoryMock.findByUserIds(any[List[String]])).thenReturn(
      Future.successful(
        List(ContactDetailsWithId(userId = "123", postCode = Some("QQ1 1QQ"), outsideUk = false, address = testAddress,
          email = testEmail, phone = None))
      )
    )

    when(appRepositoryMock.findByCriteria(any[Option[String]], any[Option[String]],
      any[Option[LocalDate]], any[List[String]])
    ).thenReturn(Future.successful(List(Candidate("123", None, None, None, Some("Leia"), Some("Amadala"), None, None,
      None, None, None, None, None))))
  }
}
