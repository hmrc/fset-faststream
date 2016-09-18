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
import model.FastPassDetails
import org.joda.time.LocalDate
import repositories.ApplicationRepositoryHelper
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class GeneralApplicationMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = "application"

  def repository = new GeneralApplicationMongoRepository(GBTimeZoneService)
  val helperRepository = new ApplicationRepositoryHelper(repository)

  "General Application repository" should {
    "Get overall report for an application with all fields" in {
      val userId = generateUUID()
      val appId = generateUUID()
      helperRepository.createApplicationWithAllFields(userId, appId, "FastStream-2016")

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
      helperRepository.createMinimumApplication(userId, appId, "FastStream-2016")

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
      helperRepository.createApplicationWithAllFields(userId, appId, frameworkId)

      val applicationResponse = repository.findByUserId(userId, frameworkId).futureValue

      applicationResponse.userId mustBe  userId
      applicationResponse.applicationId mustBe  appId
      applicationResponse.fastPassDetails.get mustBe FastPassDetails(applicable = false, None, None,
        fastPassReceived = Some(false), certificateNumber = None)
    }
  }

  "Find by criteria" should {
    "find by first name" in {
      helperRepository.createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some(helperRepository.testCandidate("firstName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by preferred name" in {
      helperRepository.createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some(helperRepository.testCandidate("preferredName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname" in {
      helperRepository.createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        None, Some(helperRepository.testCandidate("lastName")), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find date of birth" in {
      helperRepository.createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val dobParts = helperRepository.testCandidate("dateOfBirth").split("-").map(_.toInt)
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
      helperRepository.createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some("UnknownFirstName"), None, None
      ).futureValue

      applicationResponse.size mustBe 0
    }

    "filter by provided user Ids" in {
      helperRepository.createApplicationWithAllFields("userId", "appId123", "FastStream-2016")
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
}
