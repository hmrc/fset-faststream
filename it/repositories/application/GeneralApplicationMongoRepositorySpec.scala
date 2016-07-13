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

package repositories.application

import factories.UUIDFactory
import model.Commands.Report
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class GeneralApplicationMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  import ImplicitBSONHandlers._

  val collectionName = "application"

  def repository = new GeneralApplicationMongoRepository(GBTimeZoneService)

  "General Application repository" should {
    "Get overall report for an application with all fields" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createApplicationWithAllFields(userId, appId, "FastTrack-2015")

      val result = repository.overallReport("FastTrack-2015").futureValue

      result must not be empty
      result.head must be(Report(
        appId, Some("registered"), Some("Location1"), Some("Commercial"), Some("Digital and technology"),
        Some("Location2"), Some("Business"), Some("Finance"),
        Some("Yes"), Some("Yes"), Some("Yes"), Some("Yes"),
        Some("No"), Some("No"), Some("No"),
        Some("this candidate has changed the email")
      ))
    }

    "Get overall report for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack-2015")

      val result = repository.overallReport("FastTrack-2015").futureValue

      result must not be empty
      result.head must be(Report(appId, Some("registered"),
        None, None, None, None, None, None, None, None, None, None, None, None, None, None)
      )

    }
  }

  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "framework-preferences" -> BSONDocument(
        "firstLocation" -> BSONDocument(
          "region" -> "Region1",
          "location" -> "Location1",
          "firstFramework" -> "Commercial",
          "secondFramework" -> "Digital and technology"
        ),
        "secondLocation" -> BSONDocument(
          "location" -> "Location2",
          "firstFramework" -> "Business",
          "secondFramework" -> "Finance"
        ),
        "alternatives" -> BSONDocument(
          "location" -> true,
          "framework" -> true
        )
      ),
      "personal-details" -> BSONDocument(
        "aLevel" -> true,
        "stemLevel" -> true
      ),
      "assistance-details" -> BSONDocument(
        "needsAssistance" -> "No",
        "needsAdjustment" -> "No",
        "guaranteedInterview" -> "No"
      ),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> BSONDocument(
        "registered" -> "true"
      )
    )).futureValue
  }

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    )).futureValue
  }
}
