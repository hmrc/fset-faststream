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

package services.fastpass

import connectors.OnlineTestEmailClient
import model._
import model.command.PersonalDetailsExamples._
import model.command.ProgressResponse
import model.persisted.ContactDetailsExamples.ContactDetailsUK
import model.persisted.{AssistanceDetails, SchemeEvaluationResult}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito.{atLeast => atLeastTimes, _}
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import services.adjustmentsmanagement.AdjustmentsManagementService
import services.personaldetails.PersonalDetailsService
import services.scheme.SchemePreferencesService
import services.sift.ApplicationSiftService
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits._
import testkit.{ExtendedTimeout, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FastPassServiceSpec extends UnitSpec with ExtendedTimeout {

  "processFastPassCandidate" should {
    "correctly process an approved fast pass candidate with a submitted application who has numeric schemes and no adjustments" in
      new TestFixtureWithMockResponses {
      when(assistanceDetailsRepositoryMock.find(any[String])).thenReturnAsync(assistanceDetails)
      when(adjustmentsManagementServiceMock.find(any[String])).thenReturnAsync(None)

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
        eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (
        any[HeaderCarrier], any[ExecutionContext])
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "refuse to accept an approved fast pass candidate who has not submitted their application" in new TestFixtureWithMockResponses {
      when(appRepoMock.findProgress(any[String])).thenReturn(Future.successful(notSubmittedProgressResponse))

      val failedFuture = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).failed.futureValue
      failedFuture mustBe a[IllegalStateException]
      failedFuture.getMessage mustBe s"Candidate $appId cannot have their fast pass accepted/rejected because their " +
        "application has not been submitted"

      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "refuse to reject a fast pass candidate who has not submitted their application" in new TestFixtureWithMockResponses {
      when(appRepoMock.findProgress(any[String])).thenReturn(Future.successful(notSubmittedProgressResponse))

      val failedFuture = underTest.processFastPassCandidate(userId, appId, rejected, triggeredBy).failed.futureValue
      failedFuture mustBe a[IllegalStateException]
      failedFuture.getMessage mustBe s"Candidate $appId cannot have their fast pass accepted/rejected because their " +
        "application has not been submitted"

      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "promote candidates with non-siftable schemes to FSAC" in new TestFixtureWithMockResponses {
      val schemes = SelectedSchemes(List(generalist, humanResources), orderAgreed = true, eligible = true)
      when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))

      val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

      verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
      verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
      verify(schemesRepositoryMock).siftableSchemeIds
      verify(personalDetailsServiceMock).find(appId, userId)
      verify(cdRepositoryMock).find(userId)
      verify(applicationSiftServiceMock, never()).sendSiftEnteredNotification(eqTo(appId), any[OffsetDateTime])(any[HeaderCarrier])
      verify(emailClientMock).sendEmailWithName(
        eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (
        any[HeaderCarrier], any[ExecutionContext])
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "promote candidates with non-numeric siftable schemes to SIFT_ENTERED" in new TestFixtureWithMockResponses {
      val schemes = SelectedSchemes(
        List(generalist, humanResources, digitalDataTechnologyAndCyber), orderAgreed = true, eligible = true)
      when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))
      when(applicationSiftServiceMock.saveSiftExpiryDate(any[String])).thenReturn(Future.successful(OffsetDateTime.now))
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
        eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (
        any[HeaderCarrier], any[ExecutionContext])
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "progress candidates with NUMERIC_TEST only schemes to SIFT_ENTERED who need adjustments and they have been applied" in
      new TestFixtureWithMockResponses {
        val schemes = SelectedSchemes(List(commercial, finance), orderAgreed = true, eligible = true)
        when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))
        when(applicationSiftServiceMock.saveSiftExpiryDate(any[String])).thenReturn(Future.successful(OffsetDateTime.now()))
        when(applicationSiftServiceMock.progressStatusForSiftStage(any[Seq[SchemeId]])).thenReturn(ProgressStatuses.SIFT_ENTERED)

        when(assistanceDetailsRepositoryMock.find(any[String])).thenReturnAsync(
          assistanceDetails.copy(needsSupportForOnlineAssessment = Some(true))
        )

        val adjustments = Adjustments(
          adjustments = None,
          adjustmentsConfirmed = Some(true),
          etray = Some(AdjustmentDetail(timeNeeded = Some(10))),
          video = None
        )

        when(adjustmentsManagementServiceMock.find(any[String])).thenReturnAsync(Some(adjustments))

        val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

        verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
        verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
        verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_ENTERED)
        verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
        verify(schemesRepositoryMock).siftableSchemeIds
        verify(personalDetailsServiceMock).find(appId, userId)
        verify(cdRepositoryMock).find(userId)
        verify(emailClientMock).sendEmailWithName(
          eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (
          any[HeaderCarrier], any[ExecutionContext])
        verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "not progress candidate with NUMERIC_TEST only schemes to SIFT_ENTERED who needs adjustments, which have not been applied" in
      new TestFixtureWithMockResponses {
        val schemes = SelectedSchemes(List(commercial, finance), orderAgreed = true, eligible = true)
        when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))
        when(applicationSiftServiceMock.saveSiftExpiryDate(any[String])).thenReturn(Future.successful(OffsetDateTime.now))
        when(applicationSiftServiceMock.progressStatusForSiftStage(any[Seq[SchemeId]])).thenReturn(ProgressStatuses.SIFT_ENTERED)

        when(assistanceDetailsRepositoryMock.find(any[String])).thenReturnAsync(
          assistanceDetails.copy(needsSupportForOnlineAssessment = Some(true))
        )
        when(adjustmentsManagementServiceMock.find(any[String])).thenReturnAsync(None)

        val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

        verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
        verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
        verify(appRepoMock, never()).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_ENTERED)
        verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
        verify(schemesRepositoryMock).siftableSchemeIds
        verify(personalDetailsServiceMock).find(appId, userId)
        verify(cdRepositoryMock).find(userId)
        verify(emailClientMock).sendEmailWithName(
          eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (
          any[HeaderCarrier], any[ExecutionContext])
        verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "not progress candidate with NUMERIC_TEST only schemes to SIFT_ENTERED who needs adjustments, which have not been applied " +
      "but not time based ones" in new TestFixtureWithMockResponses {
        val schemes = SelectedSchemes(List(commercial, finance), orderAgreed = true, eligible = true)
        when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(schemes))
        when(applicationSiftServiceMock.saveSiftExpiryDate(any[String])).thenReturn(Future.successful(OffsetDateTime.now()))
        when(applicationSiftServiceMock.progressStatusForSiftStage(any[Seq[SchemeId]])).thenReturn(ProgressStatuses.SIFT_ENTERED)

        when(assistanceDetailsRepositoryMock.find(any[String])).thenReturnAsync(
          assistanceDetails.copy(needsSupportForOnlineAssessment = Some(true))
        )

        val adjustments = Adjustments(
          adjustments = None,
          adjustmentsConfirmed = Some(true),
          etray = Some(AdjustmentDetail(invigilatedInfo = Some("Invigilated info"))),
          video = None
        )

        when(adjustmentsManagementServiceMock.find(any[String])).thenReturnAsync(Some(adjustments))

        val (name, surname) = underTest.processFastPassCandidate(userId, appId, accepted, triggeredBy).futureValue

        verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = true)
        verify(appRepoMock).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAST_PASS_ACCEPTED)
        verify(appRepoMock, never()).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_ENTERED)
        verify(schemePreferencesServiceMock, atLeastTimes(2)).find(appId)
        verify(schemesRepositoryMock).siftableSchemeIds
        verify(personalDetailsServiceMock).find(appId, userId)
        verify(cdRepositoryMock).find(userId)
        verify(emailClientMock).sendEmailWithName(
          eqTo(ContactDetailsUK.email), eqTo(completeGeneralDetails.preferredName), eqTo(underTest.acceptedTemplate)) (
          any[HeaderCarrier], any[ExecutionContext])
        verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock, cdRepositoryMock, emailClientMock)
    }

    "process correctly a rejected fast pass candidate with a submitted application" in new TestFixtureWithMockResponses {
      val (name, surname) = underTest.processFastPassCandidate(userId, appId, rejected, triggeredBy).futureValue

      name mustBe completeGeneralDetails.firstName
      surname mustBe completeGeneralDetails.lastName

      verifyDataStoreEvents(1,
        List("FastPassRejected")
      )

      verifyAuditEvents(1,
        List("FastPassUserRejected")
      )

      verify(appRepoMock).findProgress(appId)
      verify(csedRepositoryMock).evaluateFastPassCandidate(appId, accepted = false)
      verify(personalDetailsServiceMock).find(appId, userId)
      verifyNoMoreInteractions(csedRepositoryMock, personalDetailsServiceMock)
      verifyZeroInteractions(appRepoMock, cdRepositoryMock, emailClientMock)
    }

    "fail to complete the process if a service fails" in new TestFixtureWithMockResponses {
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
    "force a candidate to a fast pass accepted state" in new TestFixtureWithMockResponses {
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
    val adjustmentsManagementServiceMock = mock[AdjustmentsManagementService]
    val assistanceDetailsRepositoryMock = mock[AssistanceDetailsRepository]

    val commercial = SchemeId("Commercial") // sift numeric scheme, evaluation required
    val finance = SchemeId("Finance") // sift numeric scheme, evaluation required
    val generalist = SchemeId("Generalist") // no sift requirement
    val humanResources = SchemeId("HumanResources") // no sift requirement
    val digitalDataTechnologyAndCyber = SchemeId("DigitalDataTechnologyAndCyber") // sift form, no evaluation

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

    val assistanceDetails = AssistanceDetails(
      hasDisability = "No",
      disabilityImpact = None,
      disabilityCategories = None,
      otherDisabilityDescription = None,
      guaranteedInterview = None,
      needsSupportForOnlineAssessment = None,
      needsSupportForOnlineAssessmentDescription = None,
      needsSupportAtVenue = None,
      needsSupportAtVenueDescription = None,
      needsSupportForPhoneInterview = None,
      needsSupportForPhoneInterviewDescription = None
    )

    val underTest = new FastPassService(
      appRepoMock,
      personalDetailsServiceMock,
      stcEventServiceMock,
      emailClientMock,
      cdRepositoryMock,
      csedRepositoryMock,
      schemePreferencesServiceMock,
      schemesRepositoryMock,
      applicationSiftServiceMock,
      adjustmentsManagementServiceMock,
      assistanceDetailsRepositoryMock

//      override val fastPassDetails = CivilServiceExperienceDetails(
//        applicable = true,
//        fastPassReceived = Some(true),
//        fastPassAccepted = Some(true),
//        certificateNumber = Some("0000000")
//      )
    )
  }

  trait TestFixtureWithMockResponses extends TestFixture {
    val notSubmittedProgressResponse = ProgressResponse(appId)
    val submittedProgressResponse = ProgressResponse(appId, submitted = true)
    val siftableSchemes = SelectedSchemesExamples.siftableSchemes.schemes
    val numericSchemes = Seq(commercial, finance)

    when(csedRepositoryMock.evaluateFastPassCandidate(any[String], any[Boolean])).thenReturn(serviceFutureResponse)
    when(csedRepositoryMock.update(any[String], any[CivilServiceExperienceDetails])).thenReturn(serviceFutureResponse)
    when(appRepoMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(serviceFutureResponse)
    when(personalDetailsServiceMock.find(any[String], any[String])).thenReturn(personalDetailsResponse)
    when(cdRepositoryMock.find(any[String])).thenReturn(contactDetailsResponse)
    when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(serviceFutureResponse)
    when(schemePreferencesServiceMock.find(any[String])).thenReturn(Future.successful(selectedSchemes))
    when(schemesRepositoryMock.siftableSchemeIds).thenReturn(siftableSchemes)
    when(schemesRepositoryMock.numericTestSiftRequirementSchemeIds).thenReturn(numericSchemes)
    when(appRepoMock.updateCurrentSchemeStatus(any[String], any[Seq[SchemeEvaluationResult]])).thenReturn(Future.successful(unit))
    when(appRepoMock.findProgress(any[String])).thenReturn(Future.successful(submittedProgressResponse))
    when(applicationSiftServiceMock.fetchSiftExpiryDate(appId)).thenReturn(Future.successful(OffsetDateTime.now))
    when(applicationSiftServiceMock.sendSiftEnteredNotification(eqTo(appId), any[OffsetDateTime])(any[HeaderCarrier]))
      .thenReturn(Future.successful(unit))
  }
}
