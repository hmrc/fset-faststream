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

package testkit

trait CommonHttpWorkflows { self: WireLevelHttpSpec =>

  def createApplication(userId: String): String = {
    val response = await(wsUrl("/application/create")
      .withHeaders(CONTENT_TYPE -> JSON)
      .put(
        s"""
           |{
           |  "userId":"$userId",
           |  "frameworkId":"FASTTRACK-2015"
           |}
          """.stripMargin
      ))
    response.status mustBe OK
    (response.json \ "applicationId").as[String]
  }

  def submitPersonalDetails(userId: String, applicationId: String, aLevelsAboveD: Boolean, aLevelsAboveCInStem: Boolean) = {
    val response = await(wsUrl(s"/personal-details/$userId/$applicationId")
      .withHeaders(CONTENT_TYPE -> JSON)
      .post(
        s"""
           |{
           |  "firstName":"Clark",
           |  "lastName":"Kent",
           |  "preferredName":"Superman",
           |  "email":"super@man.com",
           |  "dateOfBirth":"2015-07-10",
           |  "address": {
           |      "line1":"North Pole"
           |   },
           |  "postCode":"H0H 0H0",
           |  "mobilePhone":"071234567",
           |  "recentSchool":"Super hero's academy",
           |  "aLevel": $aLevelsAboveD,
           |  "stemLevel": $aLevelsAboveCInStem
           |}
          """.stripMargin
      ))
    response.status mustBe CREATED
  }
}
