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

package services.sift

import model.EvaluationResults.{ Green, Withdrawn }
import model.ProgressStatuses.{ SIFT_COMPLETED, SIFT_READY }
import model.persisted.SchemeEvaluationResult
import model.{ Scheme, SchemeId, SiftRequirement }
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.sift.SiftAnswersRepository
import testkit.ScalaMockUnitSpec
import testkit.ScalaMockImplicits._

class SiftAnswersServiceSpec extends ScalaMockUnitSpec {

  val DaT = Scheme(SchemeId("DigitalAndTechnology"), "DaT", "Digital and Technology", civilServantEligible = false,
    degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.FORM),
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val HoP = Scheme(SchemeId("HousesOfParliament"), "HoP", "Houses of Parliament", civilServantEligible = false,
    degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.FORM),
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val Generalist = Scheme(SchemeId("Generalist"), "GFS", "Generalist", civilServantEligible = false,
    degree = None, siftEvaluationRequired = false, siftRequirement = None,
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val HumanResources = Scheme(SchemeId("HumanResources"), "HR", "Human Resources", civilServantEligible = false,
    degree = None, siftEvaluationRequired = false, siftRequirement = None,
    fsbType = None, schemeGuide = None, schemeQuestion = None)
  val Sdip = Scheme(SchemeId("Sdip"), "Sdip", "Sdip", civilServantEligible = false,
    degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.FORM),
    fsbType = None, schemeGuide = None, schemeQuestion = None)


  trait TestFixture {
    val AppId = "appId1"
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftAnswersRepo = mock[SiftAnswersRepository]
    val mockSchemeRepo = new SchemeRepository {
      override lazy val schemes = DaT :: HoP :: Generalist :: HumanResources :: Sdip :: Nil
    }
    val service = new SiftAnswersService {
      def appRepo = mockAppRepo
      def siftAnswersRepo = mockSiftAnswersRepo
      def schemeRepository = mockSchemeRepo
    }
  }

  "Submitting sift answers" must {
    "update sift status and progress status to ready" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(DaT.id, Green.toString),
        SchemeEvaluationResult(HoP.id, Withdrawn.toString)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).once().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }

    "update sift status and progress status to completed when no schemes are siftable" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(Generalist.id, Green.toString),
        SchemeEvaluationResult(HumanResources.id, Green.toString)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).once().returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_COMPLETED).once().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }

    "do not update sift status to SIFT_COMPLETED when sdip is the only siftable scheme and it has not yet been sifted" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(Generalist.id, Green.toString),
        SchemeEvaluationResult(HumanResources.id, Green.toString),
        SchemeEvaluationResult(Sdip.id, Green.toString)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_READY).once().returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, SIFT_COMPLETED).never().returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }
  }
}
