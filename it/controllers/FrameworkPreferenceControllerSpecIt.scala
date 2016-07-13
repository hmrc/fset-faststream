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

import java.util.UUID

import play.api.libs.json.Json
import testkit.{CommonHttpWorkflows, WireLevelHttpSpec}

class FrameworkPreferenceControllerSpecIt extends WireLevelHttpSpec with CommonHttpWorkflows {

  val defaultFirstPreference =
    s"""
    |{
    |  "region": "London",
    |  "location": "TestGCSE2",
    |  "firstFramework": "Commercial",
    |  "secondFramework": "Finance"
    |}
    """.stripMargin

  "Framework Selection controller, when returning framework selection" should {
    // Note: The majority of the GET tests are actually tested as a byproduct of the 200-response PUT tests. Those tests
    // are scenario-oriented tests, as opposed to endpoint-oriented tests. This is because endpoint-oriented tests require
    // that the N tests written for PUT (to test validation of data being submitted) are also repeated again, with little
    // change, as the GET tests (to test the data was actually stored correctly - not just validated correctly).
    // Scenario-based testing (i.e. the combined testing of 2+ endpoints in a single test) avoids this duplication.

    "return 404 if application doesn't exist" in {
      val response = await(wsUrl(s"/framework-preference/bad-application-id").get())
      response.status mustBe NOT_FOUND
    }

    "return 404 if framework preferences haven't previously been submitted" in new UserFixture {
      val response = await(wsUrl(s"/framework-preference/${UUID.randomUUID().toString}").get())
      response.status mustBe NOT_FOUND
    }
  }

  "Framework Preference controller, when saving alternative considerations" should {
    testCommonFailures("alternatives")

    "return 400 if location preferences not previously submitted" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/alternatives/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "location": true,
        |  "framework": false
        |}
        """.stripMargin))
      putResponse.status mustBe BAD_REQUEST
    }

    "return 200 with valid submission" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = true) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/alternatives/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "location": true,
        |  "framework": false
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $prefsSubmittedResponsePrefix
        |  "alternatives" : {
        |    "location": true,
        |    "framework": false
        |  }
        |}
        """.stripMargin)
    }
  }

  "Framework Preference controller, when saving first location" should {
    testLocationPreference(isFirstPreference = true)
  }

  "Framework Preference controller, when saving second location" should {
    testLocationPreference(isFirstPreference = false)
  }

  "Framework Preference controller, when saving second location intention" should {
    testCommonFailures("second/intention")

    "return 400 if location preferences not previously submitted" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/second/intention/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "secondPreferenceIntended": true
        |}
        """.stripMargin))
      putResponse.status mustBe BAD_REQUEST
    }

    "return 200 with valid submission" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = true) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/second/intention/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "secondPreferenceIntended": true
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $prefsSubmittedResponsePrefix
        |  "secondLocationIntended" : true
        |}
        """.stripMargin)
    }

    "clears the 2nd location preference when set to FALSE" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = true) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/second/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      putResponse.status mustBe OK

      // PUT
      val putResponse2 = await(wsUrl(s"/framework-preference/second/intention/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "secondPreferenceIntended": false
        |}
        """.stripMargin))
      putResponse2.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $prefsSubmittedResponsePrefix
        |  "secondLocationIntended" : false
        |}
        """.stripMargin)
    }
  }
  // scalastyle:off
  private def testLocationPreference(isFirstPreference: Boolean): Unit = {

    // The following is not ideal, but given the quantity of tests (and that they are all duplicated), reusing them
    // in this way seems like the better option.
    val pathUnderTest = if(isFirstPreference) "first" else "second"
    val responseFieldUnderTest = if(isFirstPreference) "firstLocation" else "secondLocation"

    val responsePrefix =
      if(isFirstPreference)
        ""
      else
        "\"secondLocationIntended\": true, \"firstLocation\": " + defaultFirstPreference + ", "

    "return 200 with valid submission" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Digital and technology",
        |  "secondFramework": "Finance"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $responsePrefix
        |  "$responseFieldUnderTest": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology",
        |    "secondFramework": "Finance"
        |  }
        |}
        """.stripMargin)
    }

    "return 200 when only first framework preference is provided" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Digital and technology"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $responsePrefix
        |  "$responseFieldUnderTest": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology"
        |  }
        |}
        """.stripMargin)
    }

    "return 200 when GCSE candidate applies to GCSE frameworks only" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = false,
      aLevelsAboveCInStem = false,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Business",
        |  "secondFramework": "Commercial"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
      |{
      |  $responsePrefix
      |  "$responseFieldUnderTest": {
      |    "region": "East",
      |    "location": "TestGCSE",
      |    "firstFramework": "Business",
      |    "secondFramework": "Commercial"
      |  }
      |}
    """.stripMargin)
    }

    "return 200 when A-Level (D+) candidate applies to GCSE frameworks only" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = false,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Business",
        |  "secondFramework": "Commercial"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
      |{
      |  $responsePrefix
      |  "$responseFieldUnderTest": {
      |    "region": "East",
      |    "location": "TestGCSE",
      |    "firstFramework": "Business",
      |    "secondFramework": "Commercial"
      |  }
      |}
    """.stripMargin)
    }

    "return 200 when A-Level (D+) candidate applies to GCSE and A-Level (D+) frameworks" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = false,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Project delivery",
        |  "secondFramework": "Commercial"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
      |{
      |  $responsePrefix
      |  "$responseFieldUnderTest": {
      |    "region": "East",
      |    "location": "TestGCSE",
      |    "firstFramework": "Project delivery",
      |    "secondFramework": "Commercial"
      |  }
      |}
    """.stripMargin)
    }

    "return 200 when A-Level (C+ STEM) candidate applies to GCSE frameworks only" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Business",
        |  "secondFramework": "Commercial"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
      |{
      |  $responsePrefix
      |  "$responseFieldUnderTest": {
      |    "region": "East",
      |    "location": "TestGCSE",
      |    "firstFramework": "Business",
      |    "secondFramework": "Commercial"
      |  }
      |}
    """.stripMargin)
    }

    "return 200 when A-Level (C+ STEM) candidate applies to GCSE and A-Level (C+ STEM) frameworks" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Digital and technology",
        |  "secondFramework": "Commercial"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
      |{
      |  $responsePrefix
      |  "$responseFieldUnderTest": {
      |    "region": "East",
      |    "location": "TestGCSE",
      |    "firstFramework": "Digital and technology",
      |    "secondFramework": "Commercial"
      |  }
      |}
    """.stripMargin)
    }

    "return 200 when A-Level (D+) candidate applies to A-Level (D+) frameworks only" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = false,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Project delivery"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $responsePrefix
        |  "$responseFieldUnderTest": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Project delivery"
        |  }
        |}
    """.stripMargin)
    }

    "return 200 when A-Level (C+ STEM) candidate applies to A-Level (D+) frameworks only" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Project delivery"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $responsePrefix
        |  "$responseFieldUnderTest": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Project delivery"
        |  }
        |}
    """.stripMargin)
    }

    "return 200 when A-Level (C+ STEM) candidate applies to A-Level (D+) and A-Level (C+ STEM) frameworks" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Project delivery",
        |  "secondFramework": "Digital and technology"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $responsePrefix
        |  "$responseFieldUnderTest": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Project delivery",
        |    "secondFramework": "Digital and technology"
        |  }
        |}
    """.stripMargin)
    }

    "return 200 when A-Level (C+ STEM) candidate applies to A-Level (C+ STEM) frameworks only" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true,
      submitFirstPref = !isFirstPreference) {

      // PUT
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
        |{
        |  "region": "East",
        |  "location": "TestGCSE",
        |  "firstFramework": "Digital and technology",
        |  "secondFramework": "TestSchemeSTEM"
        |}
        """.stripMargin))
      putResponse.status mustBe OK

      // GET
      val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
      getResponse.status mustBe OK
      getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
      getResponse.json mustBe Json.parse(s"""
        |{
        |  $responsePrefix
        |  "$responseFieldUnderTest": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology",
        |    "secondFramework": "TestSchemeSTEM"
        |  }
        |}
    """.stripMargin)
    }

    "return 200 when region name matches other selection" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {

      // PUT
      val response = await(wsUrl(s"/framework-preference/first/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      response.status mustBe OK

      val response2 = await(wsUrl(s"/framework-preference/second/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelD",
         |  "firstFramework": "Project delivery"
         |}
        """.stripMargin
        ))
      response2.status mustBe OK

      if (!isFirstPreference) {
        // GET
        val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse.status mustBe OK
        getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocation": {
        |    "region": "East",
        |    "location": "TestALevelD",
        |    "firstFramework": "Project delivery"
        |  },
        |  "secondLocationIntended": true
        |}
        """.stripMargin)
      }
      else {
        // PUT
        val response3 = await(wsUrl(s"/framework-preference/first/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelCSTEM",
         |  "firstFramework": "TestSchemeSTEM"
         |}
        """.stripMargin
          ))
        response3.status mustBe OK

        // GET
        val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse.status mustBe OK
        getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestALevelCSTEM",
        |    "firstFramework": "TestSchemeSTEM"
        |  },
        |  "secondLocation": {
        |    "region": "East",
        |    "location": "TestALevelD",
        |    "firstFramework": "Project delivery"
        |  },
        |  "secondLocationIntended": true
        |}
        """.stripMargin)
      }
    }

    "return 200 when frameworks match other selection" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {

      // PUT
      val response = await(wsUrl(s"/framework-preference/first/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      response.status mustBe OK

      val response2 = await(wsUrl(s"/framework-preference/second/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelD",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      response2.status mustBe OK

      if (!isFirstPreference) {
        // GET
        val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse.status mustBe OK
        getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocation": {
        |    "region": "East",
        |    "location": "TestALevelD",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocationIntended": true
        |}
        """.stripMargin)
      }
      else {
        // PUT
        val response3 = await(wsUrl(s"/framework-preference/first/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelCSTEM",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
          ))
        response3.status mustBe OK

        // GET
        val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse.status mustBe OK
        getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestALevelCSTEM",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocation": {
        |    "region": "East",
        |    "location": "TestALevelD",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocationIntended": true
        |}
        """.stripMargin)
      }
    }

    "return 400 when region does not correspond to an existing region in the backend" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "Australia",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when location does not correspond to an existing location in the backend" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "Perth",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when first framework does not correspond to an existing framework in the backend" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Party planner"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when second framework does not correspond to an existing framework in the backend" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology",
         |  "secondFramework": "Party planner"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when first framework is not available in the selected location" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelCSTEM",
         |  "firstFramework": "Commercial"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when second framework is not available in the selected location" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelCSTEM",
         |  "firstFramework": "TestSchemeSTEM",
         |  "secondFramework": "Commercial"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when GCSE candidate applies to A-Level (D+) framework" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = false,
      aLevelsAboveCInStem = false) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Project delivery",
         |  "secondFramework": "Commercial"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when GCSE candidate applies to A-Level (C+ STEM) framework" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = false,
      aLevelsAboveCInStem = false) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "TestSchemeSTEM",
         |  "secondFramework": "Commercial"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when A-Level (D+) candidate applies to A-Level (C+ STEM) framework" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = false) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "TestSchemeSTEM",
         |  "secondFramework": "Commercial"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when frameworks duplicated" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "TestSchemeSTEM",
         |  "secondFramework": "TestSchemeSTEM"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when region not provided" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "location": "TestGCSE",
         |  "firstFramework": "Finance",
         |  "secondFramework": "TestSchemeSTEM"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when location not provided" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "firstFramework": "Finance",
         |  "secondFramework": "TestSchemeSTEM"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when first framework not provided" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "secondFramework": "TestSchemeSTEM"
         |}
        """.stripMargin
        ))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when location matches other selection" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {

      // PUT
      val response = await(wsUrl(s"/framework-preference/first/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      response.status mustBe OK

      val response2 = await(wsUrl(s"/framework-preference/second/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelD",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
        ))
      response2.status mustBe OK

      if (isFirstPreference) {
        val response = await(wsUrl(s"/framework-preference/first/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
           |{
           |  "region": "East",
           |  "location": "TestALevelD",
           |  "firstFramework": "Digital and technology"
           |}
          """.stripMargin
          ))
        response.status mustBe BAD_REQUEST
      }
      else {
        val response = await(wsUrl(s"/framework-preference/second/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
           |{
           |  "region": "East",
           |  "location": "TestGCSE",
           |  "firstFramework": "Digital and technology"
           |}
          """.stripMargin
          ))
        response.status mustBe BAD_REQUEST
      }
    }

    testCommonFailures(pathUnderTest)

    if (!isFirstPreference) {
      "set the 2nd location intention flag to TRUE, when submitting 2nd location, and flag is UNSET" in new UserWithPersonalDetailsFixture(
        aLevelsAboveD = true,
        aLevelsAboveCInStem = true) {

        // PUT
        val response = await(wsUrl(s"/framework-preference/first/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
          ))
        response.status mustBe OK

        // GET
        val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse.status mustBe OK
        getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology"
        |  }
        |}
        """.stripMargin)

        // PUT
        val response2 = await(wsUrl(s"/framework-preference/second/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelD",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
          ))
        response2.status mustBe OK

        // GET
        val getResponse2 = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse2.status mustBe OK
        getResponse2.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse2.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocation": {
        |    "region": "East",
        |    "location": "TestALevelD",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocationIntended": true
        |}
        """.stripMargin)
      }

      "set the 2nd location intention flag to TRUE, when submitting 2nd location, and flag is FALSE" in new UserWithPersonalDetailsFixture(
        aLevelsAboveD = true,
        aLevelsAboveCInStem = true) {

        // PUT
        val response = await(wsUrl(s"/framework-preference/first/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestGCSE",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
          ))
        response.status mustBe OK

        val response2 = await(wsUrl(s"/framework-preference/second/intention/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
           |{
           |  "secondPreferenceIntended": false
           |}
          """.stripMargin
          ))
        response2.status mustBe OK

        // GET
        val getResponse = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse.status mustBe OK
        getResponse.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocationIntended": false
        |}
        """.stripMargin)

        // PUT
        val response3 = await(wsUrl(s"/framework-preference/second/$applicationId")
          .withHeaders(CONTENT_TYPE -> JSON)
          .put(s"""
         |{
         |  "region": "East",
         |  "location": "TestALevelD",
         |  "firstFramework": "Digital and technology"
         |}
        """.stripMargin
          ))
        response3.status mustBe OK

        // GET
        val getResponse2 = await(wsUrl(s"/framework-preference/$applicationId").get())
        getResponse2.status mustBe OK
        getResponse2.header(CONTENT_TYPE) mustBe Some(JSON_UTF8)
        getResponse2.json mustBe Json.parse(s"""
        |{
        |  "firstLocation": {
        |    "region": "East",
        |    "location": "TestGCSE",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocation": {
        |    "region": "East",
        |    "location": "TestALevelD",
        |    "firstFramework": "Digital and technology"
        |  },
        |  "secondLocationIntended": true
        |}
        """.stripMargin)
      }
    }
  }

  private def testCommonFailures(pathUnderTest: String): Unit = {
    "return 400 when malformed data is provided" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put("MALFORMED REQUEST"))
      response.status mustBe BAD_REQUEST
    }

    "return 400 when no data is provided" in new UserWithPersonalDetailsFixture(
      aLevelsAboveD = true,
      aLevelsAboveCInStem = true) {
      val response = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(""))
      response.status mustBe BAD_REQUEST
    }

    "return 400 if application doesn't exist" in {
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/${UUID.randomUUID().toString}")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(defaultFirstPreference))
      putResponse.status mustBe BAD_REQUEST
    }

    "return 400 if qualifications haven't previously been submitted" in new UserFixture {
      val putResponse = await(wsUrl(s"/framework-preference/$pathUnderTest/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(defaultFirstPreference))
      putResponse.status mustBe BAD_REQUEST
    }
  }

  abstract class UserFixture extends testkit.UserFixture {
    val spec = FrameworkPreferenceControllerSpecIt.this
  }

  abstract class UserWithPersonalDetailsFixture(val aLevelsAboveD: Boolean, val aLevelsAboveCInStem: Boolean, val submitFirstPref: Boolean = false)
    extends testkit.UserFixture {
    val spec = FrameworkPreferenceControllerSpecIt.this
    submitPersonalDetails(userId, applicationId, aLevelsAboveD, aLevelsAboveCInStem)

    val prefsSubmittedResponsePrefix = "\"firstLocation\": " + defaultFirstPreference + ", "

    if (submitFirstPref) {
      val putResponse = await(wsUrl(s"/framework-preference/first/$applicationId")
        .withHeaders(CONTENT_TYPE -> JSON)
        .put(defaultFirstPreference))
      putResponse.status mustBe OK
    }
  }
}
