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

import model.Schemes
import model.exchange.{ApplicationResult, FsbEvaluationResults}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.test.Helpers._
import services.application.FsbService
import services.events.EventsService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec

class FsbTestGroupControllerSpec extends UnitWithAppSpec with Schemes {

  val mockFsbTestGroupService = mock[FsbService]
  val mockEventsService = mock[EventsService]

  val controller = new FsbTestGroupController(
    stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
    mockFsbTestGroupService,
    mockEventsService
  )

  "save fsb event evaluation result" should {
    "return Ok when save is successful" in {
      val applicationResults = List(
        ApplicationResult("applicationId1", "Pass"),
        ApplicationResult("applicationId2", "Pass")
      )
      val fsbEvaluationResults = FsbEvaluationResults(DiplomaticAndDevelopment, applicationResults)
      when(mockFsbTestGroupService.saveResults(eqTo(fsbEvaluationResults.schemeId), any[List[ApplicationResult]])).thenReturnAsync(List.empty)
      val response = controller.savePerScheme()(fakeRequest(fsbEvaluationResults))
      status(response) mustBe OK
    }
  }

  "find" should {
    "return fsb results for the given applicationIds" in {
      val applicationIds = List("appId1", "appId2")
      when(mockFsbTestGroupService.findByApplicationIdsAndFsbType(applicationIds, None)).thenReturnAsync(List())
      val response = controller.find(applicationIds, None)(fakeRequest)
      status(response) mustBe OK
    }

    "return fsb results filtered by schemes related to given fsbType" in {
      val applicationIds = List("appId1", "appId2")
      val fsbType = "FsbType"
      when(mockFsbTestGroupService.findByApplicationIdsAndFsbType(applicationIds, Some(fsbType))).thenReturnAsync(List())
      val response = controller.find(applicationIds, Some(fsbType))(fakeRequest)
      status(response) mustBe OK
    }
  }
}
