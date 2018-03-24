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

package services.fastpass

import connectors.OnlineTestEmailClient
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.PersonalDetailsExamples._
import model.persisted.ContactDetailsExamples.ContactDetailsUK
import model.persisted.SchemeEvaluationResult
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito.{ atLeast => atLeastTimes, _ }
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import services.personaldetails.PersonalDetailsService
import services.scheme.SchemePreferencesService
import services.sift.ApplicationSiftService
import services.stc.StcEventServiceFixture
import testkit.UnitSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class FastPassServiceSpec extends UnitSpec {

  "processFastPassCandidate" should {
    "process correctly an approved fast pass candidate" in new TextFixtureWithMockResponses {
      val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

      name mustBe completeGeneralDetails.firstName
      surname mustBe completeGeneralDetails.lastName

      verifyDataStoreEvents(2,
        List("FastPassApproved",
          "ApplicationReadyForExport")
      )

      verifyAuditEvents(3,
        List("FastPassUserAccepted",
          "ApplicationReadyForExport",
          "FastPassUserAcceptedEmailSent")
      )

      verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
      verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
      verify(schemesRepositoryMock).siftableSchemeIds
      verify(personalDetailsServiceMock).find(appId, userId)
      verify(cdRepositoryMock).find(userId)
      verify(emailClientMock).sendEmailWithName(
        eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (any[HeaderCarrier])
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)

    }

    "promote candidates with non-siftable schemes to FSAC" in new TextFixtureWithMockResponses {
      val schemes = SelectedSchemes(List(SchemeId("Generalist"), SchemeId("HumanResources")), orderAgreed = true, eligible = true)
      when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))

      val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

      verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
      verify(schemesRepositoryMock).siftableSchemeIds
      verify(personalDetailsServiceMock).find(appId, userId)
      verify(cdRepositoryMock).find(userId)
      verify(applicationSiftServiceMock, never()).sendSiftEnteredNotification(appId)
      verify(emailClientMock).sendEmailWithName(
        eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (any[HeaderCarrier])
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "promote candidates with siftable schemes to SIFT_ENTERED" in new TextFixtureWithMockResponses {
      val schemes = SelectedSchemes(
        List(SchemeId("Generalist"), SchemeId("HumanResources"), SchemeId("DigitalAndTechnology")), orderAgreed = true, eligible = true)
      when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))
      when(applicationSiftServiceMock.saveSiftExpiryDate(any[String], any[DateTime])).thenReturn(Future.successful(unit))
      when(applicationSiftServiceMock.progressStatusForSiftStage(any[Seq[SchemeId]])).thenReturn(ProgressStatuses.SIFT_ENTERED)

      val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

      verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_ENTERED)
      verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
      verify(schemesRepositoryMock).siftableSchemeIds
      verify(personalDetailsServiceMock).find(appId, userId)
      verify(cdRepositoryMock).find(userId)
      verify(emailClientMock).sendEmailWithName(
        eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (any[HeaderCarrier])
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "promote candidates with NUMERIC_TEST only schemes to SIFT_READY and do not send SIFT_ENTERED email" in new TextFixtureWithMockResponses {
      val schemes = SelectedSchemes(
        List(SchemeId("Finance"), SchemeId("Commercial"), SchemeId("Generalist")), orderAgreed = true, eligible = true)
      when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))
      when(applicationSiftServiceMock.progressStatusForSiftStage(any[Seq[SchemeId]])).thenReturn(ProgressStatuses.SIFT_READY)
      when(applicationSiftServiceMock.sendSiftEnteredNotification(appId)).thenReturn(Future.successful(unit))

      val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

      verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_READY)
      verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
      verify(schemesRepositoryMock).siftableSchemeIds
      verify(personalDetailsServiceMock).find(appId, userId)
      verify(cdRepositoryMock).find(userId)
      verify(applicationSiftServiceMock, never()).sendSiftEnteredNotification(appId)
      verify(emailClientMock).sendEmailWithName(
        eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (any[HeaderCarrier])
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "process correctly a rejected fast pass candidate" in new TextFixtureWithMockResponses {
      val (name, surname) = underTest.processFastPassCandidate(userId, appId, rejected, triggeredBy).futureValue

      name mustBe completeGeneralDetails.firstName
      surname mustBe completeGeneralDetails.lastName

      verifyDataStoreEvents(1,
        List("FastPassRejected")
      )

      verifyAuditEvents(1,
        List("FastPassUserRejected")
      )

      verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = false)
      verify(personalDetailsServiceMock).find(appId, userId)
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock)
      verifyZeroInteractions(appRepoMock, cdRepositoryMock, emailClientMock)

    }

    "fail to complete the process if a service fails" in new TextFixtureWithMockResponses {

      when(personalDetailsServiceMock.find(any[String], any[String])).thenReturn(personalDetailsResponse)
      when(cdRepositoryMock.find(any[String])).thenReturn(contactDetailsResponse)
      when(appRepoMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(serviceFutureResponse)
      when(csedRepositoryMock.evaluateFastPassCandidate(any[String], any[Boolean])).thenReturn(serviceError)

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

      verify(csedRepositoryMock).update(eqTo(appId), eqTo(underTest.fastPassDetails))
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
      verifyNoMoreInteractions(csedRepositoryMock, appRepoMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
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
    val schemePreferencesServiceMock = mock[SchemePreferencesService]
    val schemesRepositoryMock = mock[SchemeRepository]
    val applicationSiftServiceMock = mock[ApplicationSiftService]
    val accepted = true
    val rejected = false
    val userId = "user123"
    val appId = "app123"
    val triggeredBy = "admin123"
    val serviceFutureResponse = Future.successful(())
    val personalDetailsResponse = Future.successful(completeGeneralDetails)
    val contactDetailsResponse = Future.successful(ContactDetailsUK)
    val error = new RuntimeException("Something bad happened")
    val serviceError = Future.failed(error)
    val selectedSchemes = SelectedSchemesExamples.TwoSchemes
    val siftableSchemes = SelectedSchemesExamples.siftableSchemes.schemes

    val underTest = new FastPassService {
      val appRepo = appRepoMock
      val personalDetailsService = personalDetailsServiceMock
      val eventService = eventServiceMock
      val emailClient = emailClientMock
      val cdRepository = cdRepositoryMock
      val csedRepository = csedRepositoryMock
      val schemePreferencesService = schemePreferencesServiceMock
      val schemesRepository = schemesRepositoryMock
      val applicationSiftService = applicationSiftServiceMock
      override val fastPassDetails = CivilServiceExperienceDetails(
        applicable = true,
        fastPassReceived = Some(true),
        fastPassAccepted = Some(true),
        certificateNumber = Some("0000000")
      )
    }
  }

  trait TextFixtureWithMockResponses extends TestFixture {
    when(csedRepositoryMock.evaluateFastPassCandidate(any[String], any[Boolean])).thenReturn(serviceFutureResponse)
    when(csedRepositoryMock.update(any[String], any[CivilServiceExperienceDetails])).thenReturn(serviceFutureResponse)
    when(appRepoMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(serviceFutureResponse)
    when(personalDetailsServiceMock.find(any[String], any[String])).thenReturn(personalDetailsResponse)
    when(cdRepositoryMock.find(any[String])).thenReturn(contactDetailsResponse)
    when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(serviceFutureResponse)
    when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(selectedSchemes))
    when(schemesRepositoryMock.siftableSchemeIds).thenReturn(siftableSchemes)
    when(appRepoMock.updateCurrentSchemeStatus(any[String], any[Seq[SchemeEvaluationResult]])).thenReturn(Future.successful(unit))
    when(applicationSiftServiceMock.sendSiftEnteredNotification(appId)).thenReturn(Future.successful(unit))
  }
}
