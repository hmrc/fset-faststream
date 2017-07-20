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

package services.fastpass

import connectors.OnlineTestEmailClient
import model.{ CivilServiceExperienceDetails, ProgressStatuses }
import model.command.PersonalDetailsExamples._
import model.persisted.ContactDetailsExamples.ContactDetailsUK
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import services.personaldetails.PersonalDetailsService
import services.stc.StcEventServiceFixture
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class FastPassServiceSpec extends PlaySpec with ScalaFutures with MockFactory {

  "processFastPassCandidate" should {
    "process correctly an approved fast pass candidate" in new TextFixtureWithMockResponses {
      val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

      name mustBe completePersonalDetails.firstName
      surname mustBe completePersonalDetails.lastName

      verifyDataStoreEvents(2,
        List("FastPassApproved",
          "ApplicationReadyForExport")
      )

      verifyAuditEvents(3,
        List("FastPassUserAccepted",
          "ApplicationReadyForExport",
          "FastPassUserAcceptedEmailSent")
      )

      (csedRepositoryMock.evaluateFastPassCandidate _).verify(appId, true)
      (appRepoMock.addProgressStatusAndUpdateAppStatus _).verify(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
      (personalDetailsServiceMock.find _).verify(appId, userId)
      (cdRepositoryMock.find _).verify(userId)
      (emailClientMock.sendEmailWithName _).verify(
        ContactDetailsUK.email, completePersonalDetails.preferredName, underTest.acceptedTemplate)
    }

    "process correctly a rejected fast pass candidate" in new TextFixtureWithMockResponses {
      val (name, surname) = underTest.processFastPassCandidate(userId, appId, rejected, triggeredBy).futureValue

      name mustBe completePersonalDetails.firstName
      surname mustBe completePersonalDetails.lastName

      verifyDataStoreEvents(1,
        List("FastPassRejected")
      )

      verifyAuditEvents(1,
        List("FastPassUserRejected")
      )

      (csedRepositoryMock.evaluateFastPassCandidate _).verify(appId, false)
      (personalDetailsServiceMock.find _).verify(appId, userId)
    }

    "fail to complete the process if a service fails" in new TestFixture {
      (personalDetailsServiceMock.find _).when(*, *).returns(personalDetailsResponse)
      (cdRepositoryMock.find _).when(*).returns(contactDetailsResponse)
      (appRepoMock.addProgressStatusAndUpdateAppStatus _).when(*, *).returns(serviceFutureResponse)
      (csedRepositoryMock.evaluateFastPassCandidate _).when(*, *).returns(serviceError)


      val result = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).failed.futureValue

      result mustBe error

      verifyDataStoreEvents(1,
        List("ApplicationReadyForExport")
      )

      verifyAuditEvents(1,
        List("ApplicationReadyForExport")
      )
    }
  }

  "promoteToFastPassCandidate" should {
    "force a candidate to a fast pass accepted state" in new TextFixtureWithMockResponses {
      underTest.promoteToFastPassCandidate(appId, triggeredBy).futureValue

      verifyDataStoreEvents(2,
        List("FastPassApproved",
          "ApplicationReadyForExport")
      )

      verifyAuditEvents(2,
        List("FastPassUserAccepted",
          "ApplicationReadyForExport")
      )

      (csedRepositoryMock.update _).verify(appId, underTest.fastPassDetails)
      (appRepoMock.addProgressStatusAndUpdateAppStatus _).verify(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
    }
  }

  trait TestFixture extends StcEventServiceFixture {
    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]
    val appRepoMock = mock[GeneralApplicationRepository]
    val personalDetailsServiceMock = mock[PersonalDetailsService]
    val emailClientMock = mock[OnlineTestEmailClient]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val csedRepositoryMock = mock[CivilServiceExperienceDetailsRepository]
    val accepted = true
    val rejected = false
    val userId = "user123"
    val appId = "app123"
    val triggeredBy = "admin123"
    val serviceFutureResponse = Future.successful(())
    val personalDetailsResponse = Future.successful(completePersonalDetails)
    val contactDetailsResponse = Future.successful(ContactDetailsUK)
    val error = new RuntimeException("Something bad happened")
    val serviceError = Future.failed(error)

    val underTest = new FastPassService {
      val appRepo = appRepoMock
      val personalDetailsService = personalDetailsServiceMock
      val eventService = eventServiceMock
      val emailClient = emailClientMock
      val cdRepository = cdRepositoryMock
      val csedRepository = csedRepositoryMock
      override val fastPassDetails = CivilServiceExperienceDetails(
        applicable = true,
        fastPassReceived = Some(true),
        fastPassAccepted = Some(true),
        certificateNumber = Some("0000000")
      )
    }
  }

  trait TextFixtureWithMockResponses extends TestFixture {

    (csedRepositoryMock.evaluateFastPassCandidate _).when(*, *).returns(serviceFutureResponse)
    (csedRepositoryMock.update _).when(*, *).returns(serviceFutureResponse)
    (appRepoMock.addProgressStatusAndUpdateAppStatus _).when(*, *).returns(serviceFutureResponse)
    (personalDetailsServiceMock.find _).when(*, *).returns(personalDetailsResponse)
    (cdRepositoryMock.find _).when(*).returns(contactDetailsResponse)
    (emailClientMock.sendEmailWithName _).when(*,*,*).returns(serviceFutureResponse)
  }

}
