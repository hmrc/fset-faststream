/*
 * Copyright 2020 HM Revenue & Customs
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

import model.CandidateExamples._
import model.Exceptions.ApplicationNotFound
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.ApplicationStatusDetails
import model.persisted.ContactDetailsExamples._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.BaseServiceSpec
import services.scheme.SchemePreferencesService
import services.sift.ApplicationSiftService
import services.stc.StcEventServiceFixture
import services.testdata.examples.AdjustmentsExamples._
import testkit.ExtendedTimeout
import testkit.MockitoImplicits._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AdjustmentsManagementServiceSpec extends BaseServiceSpec with ExtendedTimeout {

  "confirm adjustment" should {
    "throw an exception when there is no application" in new TestFixture {
      when(mockAppRepository.find(AppId)).thenReturn(Future.successful(None))
      val ex = service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).failed.futureValue
      ex mustBe a[ApplicationNotFound]
    }

    "confirm new adjustments for non-fast pass candidate and no attempt is made to progress the candidate" in new TestFixture {
      when(mockAppRepository.findStatus(AppId)).thenReturnAsync(applicationStatusDetails)

      service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).futureValue

      verify(mockSchemePreferencesService, never()).find(AppId)
      verify(mockAppRepository).confirmAdjustments(AppId, InvigilatedETrayAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsConfirmed")
    }

    "confirm new adjustments and progress to FSAC for fast pass candidate whose fast pass has been accepted and has no siftable schemes" in
      new TestFixture {
      when(mockAppRepository.findStatus(AppId)).thenReturnAsync(
        applicationStatusDetails.copy(status = ApplicationStatus.FAST_PASS_ACCEPTED.toString)
      )

      val schemes = SelectedSchemes(List(generalist, humanResources), orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(AppId)).thenReturnAsync(schemes)

      service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).futureValue

      verify(mockAppRepository).addProgressStatusAndUpdateAppStatus(AppId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      verify(mockAppRepository).confirmAdjustments(AppId, InvigilatedETrayAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsConfirmed")
    }

    "confirm new adjustments and not progress fast pass candidate whose fast pass has been accepted, has siftable schemes" +
      "but no time adjustments specified" in new TestFixture {
      when(mockAppRepository.findStatus(AppId)).thenReturnAsync(
        applicationStatusDetails.copy(status = ApplicationStatus.FAST_PASS_ACCEPTED.toString)
      )

      val schemes = SelectedSchemes(List(commercial), orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(AppId)).thenReturnAsync(schemes)

      service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).futureValue

      verify(mockAppRepository, never()).addProgressStatusAndUpdateAppStatus(AppId, ProgressStatuses.SIFT_ENTERED)
      verify(mockApplicationSiftService, never()).sendSiftEnteredNotification(AppId) //check email was not sent
      verify(mockAppRepository).confirmAdjustments(AppId, InvigilatedETrayAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsConfirmed")
    }

    "confirm new adjustments and progress to SIFT for fast pass candidate whose fast pass has been accepted, has siftable schemes " +
      "and has time adjustments specified" ignore new TestFixture {
      when(mockAppRepository.findStatus(AppId)).thenReturnAsync(
        applicationStatusDetails.copy(status = ApplicationStatus.FAST_PASS_ACCEPTED.toString)
      )

      val schemes = SelectedSchemes(List(commercial), orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(AppId)).thenReturnAsync(schemes)

      when(mockAppRepository.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()
      when(mockApplicationSiftService.saveSiftExpiryDate(any[String])).thenReturnAsync()
      when(mockApplicationSiftService.sendSiftEnteredNotification(any[String])(any[HeaderCarrier])).thenReturnAsync()

      service.confirmAdjustment(AppId, ETrayTimeAdjustments).futureValue

      verify(mockAppRepository).addProgressStatusAndUpdateAppStatus(AppId, ProgressStatuses.SIFT_ENTERED)
      verify(mockApplicationSiftService).saveSiftExpiryDate(any[String])
      verify(mockApplicationSiftService).sendSiftEnteredNotification(AppId) //check email was sent
      verify(mockAppRepository).confirmAdjustments(AppId, ETrayTimeAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsConfirmed")
    }

    "update adjustments" in new TestFixture {
      when(mockAppRepository.findAdjustments(AppId)).thenReturn(Future.successful(Some(ETrayTimeExtensionAdjustments)))
      when(mockAppRepository.findStatus(AppId)).thenReturnAsync(applicationStatusDetails)

      service.confirmAdjustment(AppId, InvigilatedETrayAdjustments).futureValue

      verify(mockAppRepository).confirmAdjustments(AppId, InvigilatedETrayAdjustments)
      verifyDataStoreEvent("ManageAdjustmentsUpdated")
      verifyAuditEvent("AdjustmentsConfirmed")
      verifyEmailEvent("AdjustmentsChanged")
    }

    "remove adjustments"in new TestFixture {
      when(mockAppRepository.findAdjustments(AppId)).thenReturn(Future.successful(Some(ETrayTimeExtensionAdjustments)))
      when(mockAppRepository.findStatus(AppId)).thenReturnAsync(applicationStatusDetails)

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
    val mockSchemePreferencesService = mock[SchemePreferencesService]
    val mockSchemeRepository = mock[SchemeRepository]
    val mockApplicationSiftService = mock[ApplicationSiftService]

    val commercial = SchemeId("Commercial") // sift numeric scheme, evaluation required
    val finance = SchemeId("Finance") // sift numeric scheme, evaluation required
    val generalist = SchemeId("Generalist") // no sift requirement
    val humanResources = SchemeId("HumanResources") // no sift requirement
    val digitalAndTechnology = SchemeId("DigitalAndTechnology") // sift form, no evaluation

    val applicationStatusDetails = ApplicationStatusDetails(
      status = ApplicationStatus.SUBMITTED.toString,
      applicationRoute = ApplicationRoute.Faststream,
      latestProgressStatus = None,
      overrideSubmissionDeadline = None)

    when(mockCdRepository.find(UserId)).thenReturnAsync(ContactDetailsUK)
    when(mockAppRepository.find(AppId)).thenReturnAsync(Some(minCandidate(UserId)))
    when(mockAppRepository.findAdjustments(AppId)).thenReturnAsync(None)
    when(mockAppRepository.confirmAdjustments(any[String], any[Adjustments])).thenReturnAsync()
    when(mockSchemeRepository.siftableSchemeIds).thenReturn(Seq(commercial, finance, digitalAndTechnology))
    when(mockSchemeRepository.numericTestSiftRequirementSchemeIds).thenReturn(Seq(commercial, finance))

    val service = new AdjustmentsManagementService(
      mockAppRepository,
      mockCdRepository,
      mockSchemePreferencesService,
      mockSchemeRepository,
      mockApplicationSiftService,
      stcEventServiceMock
    )
  }
}
