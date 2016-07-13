/*
 * Copyright 2016 HM Revenue & Customs
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

import controllers.FrameworkControllerSpec.JsonPaths
import play.api.libs.json.{JsObject, JsValue}
import testkit.{CommonHttpWorkflows, WireLevelHttpSpec}

class FrameworkControllerSpec extends WireLevelHttpSpec with CommonHttpWorkflows {
  "Framework controller, when returning available frameworks to the candidate" should {

    "only return GCSE frameworks when candidate's highest qualifications are GCSEs" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = false,
      aLevelsAboveCInStem = false) {

      val response = await(wsUrl(s"/frameworks-available-to-application/$applicationId").get())

      response.status mustBe OK
      response.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)

      val firstRegion = response.json.getRegion("East")
      val firstLocation = firstRegion.getLocation("TestGCSE")
      val frameworks = firstLocation \ "frameworks"
      frameworks.as[List[String]] mustBe List(
        "Business",
        "Commercial",
        "Finance"
      )
    }

    "only return GCSE and A-Level (D+) frameworks when candidate's highest qualifications are A-Levels (D+)" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = false) {

      val response = await(wsUrl(s"/frameworks-available-to-application/$applicationId").get())

      response.status mustBe OK
      response.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      val firstRegion = response.json.getRegion("East")
      val firstLocation = firstRegion.getLocation("TestGCSE")
      val frameworks = firstLocation \ "frameworks"
      frameworks.as[List[String]] mustBe List(
        "Business",
        "Commercial",
        "Finance",
        "Project delivery"
      )
    }

    "return all frameworks when candidate's highest qualifications are A-Levels (C+ STEM)" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {

      val response = await(wsUrl(s"/frameworks-available-to-application/$applicationId").get())

      response.status mustBe OK
      response.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      val firstRegion = response.json.getRegion("East")
      val firstLocation = firstRegion.getLocation("TestGCSE")
      val frameworks = firstLocation \ "frameworks"
      frameworks.as[List[String]] mustBe List(
        "Business",
        "Commercial",
        "Digital and technology",
        "Finance",
        "Project delivery",
        "TestSchemeSTEM"
      )
    }

    // Note: Defensive programming. Should we enforce this on entry into the system, as a validation rule in the UI?
    "return all frameworks when candidate's highest qualifications are A-Levels (C+ STEM), " +
    "even if A-Levels (D+) is not set" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = false,
      aLevelsAboveCInStem = true) {

      val response = await(wsUrl(s"/frameworks-available-to-application/$applicationId").get())

      response.status mustBe OK
      response.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      val firstRegion = response.json.getRegion("East")
      val firstLocation = firstRegion.getLocation("TestGCSE")
      val frameworks = firstLocation \ "frameworks"
      frameworks.as[List[String]] mustBe List(
        "Business",
        "Commercial",
        "Digital and technology",
        "Finance",
        "Project delivery",
        "TestSchemeSTEM"
      )
    }

    "should yield 404 if application exists, but education details have not previously been submitted" in {
      val response = await(wsUrl(s"/frameworks-available-to-application/SomeBadID").get())
      response.status mustBe NOT_FOUND
    }

    "should yield 404 if a non-existent application ID is provided" in {
      val response = await(wsUrl(s"/frameworks-available-to-application/SomeBadID").get())
      response.status mustBe NOT_FOUND
    }
  }

  abstract class UserWithPersonalDetailsFixture(val aLevelsAboveD: Boolean, val aLevelsAboveCInStem: Boolean)
    extends testkit.UserFixture {
    val spec = FrameworkControllerSpec.this
    submitPersonalDetails(userId, applicationId, aLevelsAboveD, aLevelsAboveCInStem)
  }
}
object FrameworkControllerSpec {
  implicit class JsonPaths(val value: JsValue) extends AnyVal {
    def getRegion(regionName: String): JsValue =
      value.as[List[JsObject]].find(j => (j \ "name").as[String] == regionName).get

    def getLocation(locationName: String): JsValue =
      (value \ "locations").as[List[JsObject]].find(j => (j \ "name").as[String] == locationName).get
  }
}
