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
import model._
import model.ApplicationStatus._
import model.SchemeType.SchemeType
import model.report.CandidateProgressReport
import org.joda.time.LocalDate
import reactivemongo.bson.{ BSONArray, BSONDocument }
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
      createApplicationWithAllFields(userId, appId, "FastStream-2016")

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head must be(CandidateProgressReport(appId, Some("registered"),
        List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
        Some("No"), Some("No"), Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("1234567"))
      )
    }

    "Get overall report for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastStream-2016")

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head must be(CandidateProgressReport(appId, Some("registered"),
        List.empty[SchemeType], None, None, None, None, None, None, None, None, None, None)
      )

    }

    "Find user by id" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val frameworkId = "FastStream-2016"
      createApplicationWithAllFields(userId, appId, frameworkId)

      val applicationResponse = repository.findByUserId(userId, frameworkId).futureValue

      applicationResponse.userId mustBe userId
      applicationResponse.applicationId mustBe appId
      applicationResponse.civilServiceExperienceDetails.get mustBe
        CivilServiceExperienceDetails(applicable = true, Some(CivilServiceExperienceType.CivilServant),
        Some(List(InternshipType.SDIPCurrentYear, InternshipType.EDIP)), fastPassReceived = Some(true),
        certificateNumber = Some("1234567"))
    }

    "Find application status" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val frameworkId = "FastStream-2016"
      createApplicationWithAllFields(userId, appId, frameworkId, appStatus = SUBMITTED)

      val applicationStatusDetails = repository.findStatus(appId).futureValue

      applicationStatusDetails.status mustBe SUBMITTED.toString
      applicationStatusDetails.statusDate.get mustBe LocalDate.now().toDateTimeAtStartOfDay

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

    "find by first/preferred name with special regex character" in {
      createApplicationWithAllFields(userId = "userId", appId = "appId123", frameworkId = "FastStream-2016",
        firstName = Some("Char+lie.+x123"))

      val applicationResponse = repository.findByCriteria(
        Some("Char+lie.+x123"), None, None
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

    "find by lastname with special regex character" in {
      createApplicationWithAllFields(userId = "userId", appId = "appId123", frameworkId = "FastStream-2016",
        lastName = Some("Barr+y.+x123"))

      val applicationResponse = repository.findByCriteria(
        None, Some("Barr+y.+x123"), None
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

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  // scalastyle:off parameter.number
  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
     appStatus: ApplicationStatus = IN_PROGRESS, hasDisability: String = "Yes", needsSupportForOnlineAssessment: Boolean = false,
     needsSupportAtVenue: Boolean = false, guaranteedInterview: Boolean = false, lastName: Option[String] = None,
     firstName: Option[String] = None, preferredName: Option[String] = None) = {
    import repositories.BSONLocalDateHandler
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "scheme-preferences" -> BSONDocument(
        "schemes" -> BSONArray(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService)
      ),
      "personal-details" -> BSONDocument(
        "firstName" -> firstName.getOrElse(s"${testCandidate("firstName")}"),
        "lastName" -> lastName.getOrElse(s"${testCandidate("lastName")}"),
        "preferredName" -> preferredName.getOrElse(s"${testCandidate("preferredName")}"),
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}"
      ),
      "civil-service-experience-details" -> BSONDocument(
        "applicable" -> true,
        "fastPassReceived" -> true,
        "certificateNumber" -> "1234567",
        "civilServiceExperienceType" -> CivilServiceExperienceType.CivilServant,
        "internshipTypes" -> BSONArray(InternshipType.SDIPCurrentYear, InternshipType.EDIP)
      ),
      "assistance-details" -> BSONDocument(
        "hasDisability" -> "Yes",
        "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
        "needsSupportAtVenue" -> needsSupportAtVenue,
        "guaranteedInterview" -> guaranteedInterview
      ),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> BSONDocument(
        "registered" -> "true"
      ),
      "progress-status-dates" -> BSONDocument(
        "submitted" -> LocalDate.now()
      )
    )).futureValue
  }
  // scalastyle:on

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    )).futureValue
  }
}

