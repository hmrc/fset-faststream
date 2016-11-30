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

package services.search

import connectors.AuthProviderClient
import model.Address
import model.Commands.{ Candidate, SearchCandidate }
import model.PersistedObjects.ContactDetailsWithId
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import repositories.ContactDetailsRepository
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.BaseServiceSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class SearchForApplicantServiceSpec extends BaseServiceSpec {

  val appRepositoryMock = mock[GeneralApplicationRepository]
  val psRepositoryMock = mock[PersonalDetailsRepository]
  val cdRepositoryMock = mock[ContactDetailsRepository]
  val authProviderClientMock = mock[AuthProviderClient]

  val searchForApplicantService = new SearchForApplicantService {
    override val appRepository = appRepositoryMock
    override val psRepository = psRepositoryMock
    override val cdRepository = cdRepositoryMock
    override val authProviderClient = authProviderClientMock
  }

  implicit val headerCarrier = new HeaderCarrier()

  "find by criteria" should {
    "filter by post code" in {
      val testAddress = Address(line1 = "1 Test Street", line2 = None, line3 = None, line4 = None)
      val testEmail = "test@test.com"

      when(cdRepositoryMock.findByPostCode(any[String])).thenReturn(
        Future.successful(List(ContactDetailsWithId(userId = "123", postCode = Some("QQ1 1QQ"), address = testAddress, email = testEmail,
          phone = None)))
      )

      when(cdRepositoryMock.findByUserIds(any[List[String]])).thenReturn(
        Future.successful(List(ContactDetailsWithId(userId = "123", postCode = Some("QQ1 1QQ"), address = testAddress, email = testEmail,
          phone = None)))
      )

      when(authProviderClientMock.findByFirstName(any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      when(authProviderClientMock.findByLastName(any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      when(appRepositoryMock.findByCriteria(any[Option[String]], any[Option[String]],
        any[Option[LocalDate]], any[List[String]])
      ).thenReturn(Future.successful(List(Candidate("123", None, None, Some("Leia"), Some("Amadala"), None ,None, None, None, None, None))))

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(firstOrPreferredName = Some("Leia"),
        lastName = Some("Amadala"), dateOfBirth = None, postCode = Some("QQ1 1QQ"))).futureValue

      val expected = Candidate("123", None, Some(testEmail), Some("Leia"), Some("Amadala"),
        None, None, Some(testAddress), Some("QQ1 1QQ"), None, None
      )

      actual mustBe List(expected)
    }
  }
}
