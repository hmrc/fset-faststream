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

import model.Exceptions.NotFoundException
import model.FlagCandidateCommands
import model.FlagCandidatePersistedObject._
import org.mockito.Mockito._
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.FlagCandidateRepository
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class FlagCandidateControllerSpec extends UnitWithAppSpec {
  val mockFlagCandidateRepository = mock[FlagCandidateRepository]

  val testableFlagCandidateController = new FlagCandidateController(
    stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
    mockFlagCandidateRepository
  )

  "Flag Candidate Controller" should {
    "Return a candidate issue" in {
      val issue = "some issue"
      val flagCandidate = FlagCandidate("appId", Some(issue))
      when(mockFlagCandidateRepository.tryGetCandidateIssue("appId")).thenReturn(Future.successful(Some(flagCandidate)))

      val response = testableFlagCandidateController.find("appId")(FakeRequest())
      asFlagCandidate(response) mustBe FlagCandidateCommands.FlagCandidate(issue)
    }

    "Return Not Found for get issue if there it does not exist" in {
      when(mockFlagCandidateRepository.tryGetCandidateIssue("appId")).thenReturn(Future.successful(None))

      val response = testableFlagCandidateController.find("appId")(FakeRequest())
      status(response) mustBe NOT_FOUND
    }

    "Save a new issue for the candidate" in {
      val flagCandidate = FlagCandidate("appId", Some("some issue"))
      when(mockFlagCandidateRepository.save(flagCandidate)).thenReturn(Future.successful(()))

      val response = testableFlagCandidateController.save("appId")(createPutRequest("appId", Json.toJson(flagCandidate).toString()))
      status(response) mustBe OK
      verify(mockFlagCandidateRepository).save(flagCandidate)
    }

    "Return NOT_FOUND when save an issue for incorrect applicationId" in {
      val flagCandidate = FlagCandidate("appId", Some("some issue"))
      when(mockFlagCandidateRepository.save(flagCandidate)).thenReturn(Future.failed(new NotFoundException()))

      val response = testableFlagCandidateController.save("appId")(createPutRequest("appId", Json.toJson(flagCandidate).toString()))
      status(response) mustBe NOT_FOUND
    }

    "Remove an issue for the candidate" in {
      when(mockFlagCandidateRepository.remove("appId")).thenReturn(Future.successful(()))

      val response = testableFlagCandidateController.remove("appId")(FakeRequest())
      status(response) mustBe NO_CONTENT
    }

    "Return NOT_FOUND when remove an issue for incorrect applicationId" in {
      when(mockFlagCandidateRepository.remove("appId")).thenReturn(Future.failed(new NotFoundException()))

      val response = testableFlagCandidateController.remove("appId")(FakeRequest())
      status(response) mustBe NOT_FOUND
    }
  }

  def asFlagCandidate(response: Future[Result]) = contentAsJson(response).as[JsValue].as[FlagCandidateCommands.FlagCandidate]

  def createPutRequest(appId: String, jsonString: String) = {
    val json = Json.parse(jsonString)
    FakeRequest(Helpers.PUT, controllers.routes.FlagCandidateController.save(appId).url, FakeHeaders(), json)
      .withHeaders("Content-Type" -> "application/json")
  }
}
