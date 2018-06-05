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

package services

import config.{ CubiksGatewayConfig, NumericalTestsConfig }
import connectors.{ CSREmailClient, CubiksGatewayClient, EmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.EvaluationResults.Green
import model._
import model.command.ProgressResponseExamples
import model.persisted.SchemeEvaluationResult
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.sift.ApplicationSiftRepository
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits._
import testkit.{ ExtendedTimeout, UnitSpec }

class NumericalTestServiceSpec extends UnitSpec with ExtendedTimeout {

  trait TestFixture extends StcEventServiceFixture {

    val mockAppRepo: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val mockSiftRepo: ApplicationSiftRepository = mock[ApplicationSiftRepository]
    val mockContactDetailsRepo: ContactDetailsRepository = mock[ContactDetailsRepository]
    val mockEmailClient: EmailClient = mock[EmailClient]

    val mockCubiksGatewayConfig = mock[CubiksGatewayConfig]
    val mockNumericalTestsConfig = mock[NumericalTestsConfig]
    val mockCubiksGatewayClient: CubiksGatewayClient = mock[CubiksGatewayClient]
    val mockDateTimeFactory: DateTimeFactory = mock[DateTimeFactory]

    val mockSchemeRepo = new SchemeRepository {
      override lazy val schemes: Seq[Scheme] = Seq(
        Scheme("DigitalAndTechnology", "DaT", "Digital and Technology", civilServantEligible = false, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("Commercial", "GCS", "Commercial", civilServantEligible = false, None, Some(SiftRequirement.NUMERIC_TEST),
          siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None
        )
      )
    }

    val service = new NumericalTestService {
      override def applicationRepo: GeneralApplicationRepository = mockAppRepo
      override def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      val tokenFactory: UUIDFactory = UUIDFactory
      val gatewayConfig: CubiksGatewayConfig = mockCubiksGatewayConfig
      override def testConfig: NumericalTestsConfig = mockNumericalTestsConfig
      val cubiksGatewayClient: CubiksGatewayClient = mockCubiksGatewayClient
      val dateTimeFactory: DateTimeFactory = mockDateTimeFactory
      override def schemeRepository: SchemeRepository = mockSchemeRepo
      val eventService = eventServiceMock
      override def emailClient = mockEmailClient
      override def contactDetailsRepo = mockContactDetailsRepo
    }

    val appId = "appId"
  }

  "NumericalTestService.nextApplicationWithResultsReceived" must {
    "handle no application to process" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(None)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe None

      verifyZeroInteractions(service.applicationRepo)
    }

    // the current scheme status is Commercial, which has no FORM requirement only NUMERIC_TEST requirement
    // so candidate can be progressed (moved to SIFT_READY)
    "return the candidate for progression who has no schemes that require a form to be filled" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(Some(appId))
      when(mockAppRepo.findProgress(eqTo(appId))).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe Some(appId)
    }

    "return the candidate for progression who has schemes that require a form to be filled in and has already " +
      "satisfied that requirement" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(Some(appId))
      when(mockAppRepo.findProgress(eqTo(appId))).thenReturnAsync(ProgressResponseExamples.InSiftFormsCompleteNumericTestPendingProgress)

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe Some(appId)
    }

    "not return the candidate for progression who has schemes that require a form to be filled in but has not yet filled " +
      "in those forms" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(Some(appId))
      when(mockAppRepo.findProgress(eqTo(appId))).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe None
    }
  }
}
