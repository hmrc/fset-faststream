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
import model.Commands.{Candidate, Report}
import model.Exceptions.NotFoundException
import model.ProgressStatuses.ProgressStatus
import model.{ApplicationStatus, FastPassDetails, ProgressStatuses}
import org.joda.time.{DateTime, LocalDate}
import reactivemongo.bson.{BSONArray, BSONDocument}
import reactivemongo.json.ImplicitBSONHandlers
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class GeneralApplicationMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  import ImplicitBSONHandlers._

  val collectionName = "application"

  def repository = new GeneralApplicationMongoRepository(GBTimeZoneService)

  "General Application repository" should {
    "Get overall report for an application with all fields" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createApplicationWithAllFields(userId, appId, "FastStream-2016")

      val result = repository.overallReport("FastStream-2016").futureValue

      result must not be empty
      result.head must be(Report(
        appId, Some("registered"), Some("Location1"), Some("Commercial"), Some("Digital and technology"),
        Some("Location2"), Some("Business"), Some("Finance"),
        Some("Yes"), Some("Yes"), Some("Yes"), Some("Yes"),
        None, Some("No"), None,
        Some("this candidate has changed the email")
      ))
    }

    "Get overall report for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastStream-2016")

      val result = repository.overallReport("FastStream-2016").futureValue

      result must not be empty
      result.head must be(Report(appId, Some("registered"),
        None, None, None, None, None, None, None, None, None, None, None, None, None, None)
      )

    }

    "Find user by id" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val frameworkId = "FastStream-2016"
      createApplicationWithAllFields(userId, appId, frameworkId)

      val applicationResponse = repository.findByUserId(userId, frameworkId).futureValue

      applicationResponse.userId mustBe  userId
      applicationResponse.applicationId mustBe  appId
      applicationResponse.fastPassDetails.get mustBe FastPassDetails(applicable = false, None, None,
        fastPassReceived = Some(false), certificateNumber = None)
    }
  }

  "Find by criteria" should {
    "find by first name" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("firstName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by preferred name" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("preferredName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        None, Some(testCandidate("lastName")), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find date of birth" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val dobParts = testCandidate("dateOfBirth").split("-").map(_.toInt)
      val (dobYear, dobMonth, dobDay) = (dobParts.head, dobParts(1), dobParts(2))

      val applicationResponse = repository.findByCriteria(
        None, None, Some(new LocalDate(
          dobYear,
          dobMonth,
          dobDay
        ))
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "Return an empty candidate list when there are no results" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some("UnknownFirstName"), None, None
      ).futureValue

      applicationResponse.size mustBe 0
    }

    "filter by provided user Ids" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")
      val matchResponse = repository.findByCriteria(
        None, None, None, List("userId")
      ).futureValue

      matchResponse.size mustBe 1

       val noMatchResponse = repository.findByCriteria(
        None, None, None, List("unknownUser")
      ).futureValue

      noMatchResponse.size mustBe 0
    }

  }

  "Update application status" should {
    "update the application and progress status for a user" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED")
      repository.updateProgressStatus("appId", ProgressStatuses.PHASE1_TESTS_INVITED).futureValue

      val result = repository.findByUserId("userId", "frameworkId").futureValue
      result.applicationStatus mustBe ProgressStatuses.PHASE1_TESTS_INVITED.applicationStatus.toString
    }
  }

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
    appStatus: String = "", needsAdjustment: Boolean = false, adjustmentsConfirmed: Boolean = false,
    timeExtensionAdjustments: Boolean = false, fastPassApplicable: Boolean = false) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
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
        "firstName" -> s"${testCandidate("firstName")}",
        "lastName" -> s"${testCandidate("lastName")}",
        "preferredName" -> s"${testCandidate("preferredName")}",
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}",
        "aLevel" -> true,
        "stemLevel" -> true
      ),
      "fastpass-details" -> BSONDocument(
        "applicable" -> fastPassApplicable,
        "fastPassReceived" -> fastPassApplicable
      ),
      "assistance-details" -> createAssistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> BSONDocument(
        "registered" -> "true"
      )
    )).futureValue
  }

  private def createAssistanceDetails(needsAdjustment: Boolean, adjustmentsConfirmed: Boolean,
    timeExtensionAdjustments:Boolean) = {
    if (needsAdjustment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
            "adjustments-confirmed" -> true,
            "verbalTimeAdjustmentPercentage" -> 9,
            "numericalTimeAdjustmentPercentage" -> 11
          )
        } else {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("room alone"),
            "adjustments-confirmed" -> true
          )
        }
      } else {
        BSONDocument(
          "needsAdjustment" -> "Yes",
          "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
          "adjustments-confirmed" -> false
        )
      }
    } else {
      BSONDocument(
        "needsAdjustment" -> "No"
      )
    }
  }

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    )).futureValue
  }


}
