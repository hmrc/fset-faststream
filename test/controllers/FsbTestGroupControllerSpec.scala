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

import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import play.api.test.Helpers._
import services.application.FsbTestGroupService
import services.events.EventsService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class FsbTestGroupControllerSpec extends UnitWithAppSpec {

  val mockFsbTestGroupService = mock[FsbTestGroupService]
  val mockEventsService = mock[EventsService]

  val controller = new FsbTestGroupController {
    val eventsService = mockEventsService
    val service = mockFsbTestGroupService
  }

  "save fsb event evaluation result" should {
    "return Created when save is successful" in {
    }
  }

  "find" should {
    "return fsb results for the given applicationIds" in {
      val applicationIds = List("appId1", "appId2")
      when(mockFsbTestGroupService.findByApplicationIdsAndFsbType(applicationIds, None)).thenReturn(Future.successful(List()))
      val response =  controller.find(applicationIds, None)(fakeRequest)

      status(response) mustBe OK
    }

    "return fsb results filtered by schemes related to given fsbType" in {
      val applicationIds = List("appId1", "appId2")
      val fsbType = "FsbType"
      when(mockFsbTestGroupService.findByApplicationIdsAndFsbType(applicationIds, Some(fsbType))).thenReturn(Future.successful(List()))
      val response = controller.find(applicationIds, Some(fsbType))(fakeRequest)

      status(response) mustBe OK
    }

  }

}
