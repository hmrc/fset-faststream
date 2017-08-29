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

package services.sift

import model.EvaluationResults.{ Green, Withdrawn }
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
    fsbType = None, telephoneInterviewType = None, schemeGuide = None)
  val HoP = Scheme(SchemeId("HousesOfParliament"), "HoP", "Houses of Parliament", civilServantEligible = false,
    degree = None, siftEvaluationRequired = true, siftRequirement = Some(SiftRequirement.FORM),
    fsbType = None, telephoneInterviewType = None, schemeGuide = None)


  trait TestFixture {
    val AppId = "appId1"
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftAnswersRepo = mock[SiftAnswersRepository]
    val mockSchemeRepo = new SchemeRepository {
      override lazy val schemes = DaT :: HoP :: Nil
    }
    val service = new SiftAnswersService {
      def appRepo = mockAppRepo
      def siftAnswersRepo = mockSiftAnswersRepo
      def schemeRepository = mockSchemeRepo
    }
  }

  "Submitting sift answers" must {
    "update sift status and progress status" in new TestFixture {
      val currentSchemeStatus = Seq(
        SchemeEvaluationResult(DaT.id, Green.toString),
        SchemeEvaluationResult(HoP.id, Withdrawn.toString)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(AppId).returningAsync(currentSchemeStatus)
      (mockSiftAnswersRepo.submitAnswers _).expects(AppId, *).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects(AppId, *).returningAsync

      whenReady(service.submitAnswers(AppId)) { result =>
        result mustBe unit
      }
    }

  }

}
