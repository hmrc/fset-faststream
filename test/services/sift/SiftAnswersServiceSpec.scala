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

package services.sift

import config.MicroserviceAppConfig
import model.EvaluationResults.{Green, Withdrawn}
import model.ProgressStatuses.{SIFT_COMPLETED, SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING, SIFT_READY}
import model.command.ProgressResponseExamples
import model.exchange.sift.SiftSchemes
import model.persisted.SchemeEvaluationResult
import model.persisted.sift.{SchemeSpecificAnswer, SiftAnswers, SiftAnswersStatus}
import model.{Scheme, Schemes, SiftRequirement}
import repositories.SchemeYamlRepository
import repositories.application.GeneralApplicationRepository
import repositories.sift.SiftAnswersRepository
import testkit.ScalaMockImplicits.*
import testkit.ScalaMockUnitWithAppSpec

import scala.concurrent.ExecutionContext.Implicits.global

class SiftAnswersServiceSpec extends ScalaMockUnitWithAppSpec with Schemes {

  val CommercialScheme = Scheme(Commercial, "CFS", "Commercial", civilServantEligible = true,
    degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.NUMERIC_TEST),
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val DDTaCScheme = Scheme(Digital, "DDTaC", "Digital, Data, Technology & Cyber",
    civilServantEligible = false, degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.FORM),
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val HoPScheme = Scheme(HousesOfParliament, "HoP", "Houses of Parliament", civilServantEligible = false,
    degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.FORM),
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val OperationalDeliveryScheme = Scheme(OperationalDelivery, "OPD", "Operational Delivery", civilServantEligible = false,
    degree = None, siftEvaluationRequired = false, siftRequirement = None,
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val HumanResourcesScheme = Scheme(HumanResources, "HR", "Human Resources", civilServantEligible = false,
    degree = None, siftEvaluationRequired = false, siftRequirement = None,
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val SdipScheme = Scheme(Sdip, "Sdip", "Sdip", civilServantEligible = false,
    degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.FORM),
    fsbType = None, schemeGuide = None, schemeQuestion = None)

  trait TestFixture {
    val AppId = "appId1"
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftAnswersRepo = mock[SiftAnswersRepository]
    // Needed to create the SchemeYamlRepository. The implicit for the app is provided by ScalaMockUnitWithAppSpec
//    implicit val appConfig = app.injector.instanceOf(classOf[MicroserviceAppConfig])
    implicit val mockAppConfig: MicroserviceAppConfig = mock[MicroserviceAppConfig] // This also works
    val schemeRepo = new SchemeYamlRepository {
      override lazy val schemes = CommercialScheme :: DDTaCScheme :: HoPScheme :: OperationalDeliveryScheme ::
        HumanResourcesScheme :: SdipScheme :: Nil
    }
    val service = new SiftAnswersService(
      mockAppRepo,
      mockSiftAnswersRepo,
      schemeRepo
    )
  }

  "Submitting sift answers" must {
    "update sift status and progress status to ready" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(DDTaCScheme.id, Green.toString),
        SchemeEvaluationResult(HoPScheme.id, Withdrawn.toString)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockAppRepo.findProgress _).expects(AppId).returningAsync(ProgressResponseExamples.InSiftEntered)

      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).once().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }

    "update sift status and progress status to completed when no schemes are siftable" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(OperationalDeliveryScheme.id, Green.toString),
        SchemeEvaluationResult(HumanResourcesScheme.id, Green.toString)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockAppRepo.findProgress _).expects(AppId).returningAsync(ProgressResponseExamples.InSiftEntered)

      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).once().returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_COMPLETED).once().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }

    "do not update sift status to SIFT_COMPLETED when sdip is the only siftable scheme and it has not yet been sifted" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(OperationalDeliveryScheme.id, Green.toString),
        SchemeEvaluationResult(HumanResourcesScheme.id, Green.toString),
        SchemeEvaluationResult(SdipScheme.id, Green.toString)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockAppRepo.findProgress _).expects(AppId).returningAsync(ProgressResponseExamples.InSiftEntered)

      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).once().returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_COMPLETED).never().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }

    "update sift status to SIFT_READY when numeric test has already been completed and the results received" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(CommercialScheme.id, Green.toString), // Scheme requiring numeric test
        SchemeEvaluationResult(DDTaCScheme.id, Green.toString) // Scheme requiring form
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockAppRepo.findProgress _).expects(AppId).returningAsync(ProgressResponseExamples.InSiftTestResultsReceived)

      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).once().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }

    "update sift status to SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING when the candidate has a numeric test requirement, " +
      "which has not been done" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(CommercialScheme.id, Green.toString), // Scheme requiring numeric test
        SchemeEvaluationResult(DDTaCScheme.id, Green.toString) // Scheme requiring form
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockAppRepo.findProgress _).expects(AppId).returningAsync(ProgressResponseExamples.InSiftTestInvited)

      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).never().returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING).once().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }
  }

  "Find sift schemes" must {
    "return the schemes which have scheme answers" in new TestFixture {
      val siftAnswers = SiftAnswers(
        AppId,
        SiftAnswersStatus.SUBMITTED,
        generalAnswers = None,
        schemeAnswers = Map(
          "Commercial" -> SchemeSpecificAnswer("Test text"),
          "Finance" -> SchemeSpecificAnswer("Test text")
        )
      )

      (mockSiftAnswersRepo.findSiftAnswers _).expects(AppId).returningAsync(Some(siftAnswers))

      whenReady(service.findSiftSchemes(AppId)) { result =>
        result mustBe Some(SiftSchemes(Seq("Commercial", "Finance")))
      }
    }
  }
}
