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

package controllers

import model.exchange.ApplicationSifting
import model.persisted.SchemeEvaluationResult
import model.{Candidate, CandidateExamples, SchemeId, Schemes}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.sift.ApplicationSiftService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class SiftingControllerSpec extends UnitWithAppSpec with Schemes {
  val mockSiftService = mock[ApplicationSiftService]

  val controller = new SiftingController(stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)), mockSiftService)

  "findApplicationsReadyForSifting" should {
    "return list of candidates" in {
      when(mockSiftService.findApplicationsReadyForSchemeSift(any[SchemeId])).thenReturn(Future.successful(CandidateExamples.NewCandidates))
      val response = controller.findApplicationsReadyForSchemeSifting(Commercial.toString)(fakeRequest)
      status(response) mustBe OK
      contentAsString(response) mustBe Json.toJson[List[Candidate]](CandidateExamples.NewCandidates).toString()
    }
  }

  "siftCandidateApplication" should {
    "invoke repository to sift candidate" in {
      val appSifting = ApplicationSifting("app1", Commercial, "Pass")
      val request = fakeRequest(appSifting)
      val result = SchemeEvaluationResult(Commercial, "Green")
      when(mockSiftService.siftApplicationForScheme("app1", result)).thenReturn(Future.successful(()))
      val response = controller.siftCandidateApplication(request)
      status(response) mustBe OK
      verify(mockSiftService).siftApplicationForScheme("app1", result)
    }
  }
}
