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

package controllers

import model.Commands.Candidate
import model.exchange.ApplicationSifting
import model.persisted.SchemeEvaluationResult
import model.{ CandidateExamples, SchemeId }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import repositories.sifting.SiftingRepository
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class SiftingControllerSpec extends UnitWithAppSpec {
  val mockSiftingRepo = mock[SiftingRepository]

  val controller = new SiftingController {
    override val siftAppRepository: SiftingRepository = mockSiftingRepo
  }

  private val Commercial = SchemeId("Commercial")

  "find sifting eligible" should {
    "return list of candidates" in {
      when(mockSiftingRepo.findCandidatesEligibleForSifting(any[SchemeId])).thenReturn(Future.successful(CandidateExamples.NewCandidates))
      val response = controller.findSiftingEligible(Commercial.toString)(fakeRequest)
      status(response) mustBe OK
      contentAsString(response) mustBe Json.toJson[List[Candidate]](CandidateExamples.NewCandidates).toString()
    }
  }

  "submit sifting" should {
    "invoke repository to sift candidate" in {
      val appSifting = ApplicationSifting("app1", Commercial, "Pass")
      val request = fakeRequest(appSifting)
      val result = SchemeEvaluationResult(Commercial, "Green")
      when(mockSiftingRepo.siftCandidate("app1", result)).thenReturn(Future.successful(()))
      val response = controller.submitSifting(request)
      status(response) mustBe OK
      verify(mockSiftingRepo).siftCandidate("app1", result)
    }
  }
}

