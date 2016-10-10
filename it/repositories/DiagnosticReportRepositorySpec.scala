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

package repositories

import model.Exceptions.NotFoundException
import model.PersistedObjects.{ApplicationProgressStatus, ApplicationProgressStatuses, ApplicationUser}
import reactivemongo.bson.{BSONBoolean, BSONDocument}
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{DiagnosticReportingMongoRepository, GeneralApplicationMongoRepository}
import services.GBTimeZoneService
import config.MicroserviceAppConfig._
import testkit.MongoRepositorySpec

class DiagnosticReportRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "application"
  
  def diagnosticReportRepo = new DiagnosticReportingMongoRepository()
  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig)

  "Find by user id" should {
    "return NotFound if there is nobody with this userId" in {
      val result = diagnosticReportRepo.findByUserId("123").failed.futureValue
      result mustBe an[NotFoundException]
    }

    "return user with the specific Id" in {
      helperRepo.collection.insert(UserWithAllDetails).futureValue

      val result = diagnosticReportRepo.findByUserId("user1").futureValue

      val expectedApplicationUser = ApplicationUser("app1", "user1", "FastStream-2016", "AWAITING_ALLOCATION",
        ApplicationProgressStatuses(Some(List(
          ApplicationProgressStatus("registered", true),
          ApplicationProgressStatus("personal_details_completed", true),
          ApplicationProgressStatus("schemes_and_locations_completed", true),
          ApplicationProgressStatus("assistance_details_completed", true),
          ApplicationProgressStatus("preview_completed", true),
          ApplicationProgressStatus("submitted", true),
          ApplicationProgressStatus("online_test_invited", true),
          ApplicationProgressStatus("online_test_started", true),
          ApplicationProgressStatus("online_test_completed", true),
          ApplicationProgressStatus("awaiting_online_test_allocation", true)
        )), Some(List(
          ApplicationProgressStatus("start_diversity_questionnaire", true),
          ApplicationProgressStatus("diversity_questions_completed", true),
          ApplicationProgressStatus("education_questions_completed", true),
          ApplicationProgressStatus("occupation_questions_completed", true)
        ))))

      result.progressStatuses.statuses must be(expectedApplicationUser.progressStatuses.statuses)
      result.progressStatuses.questionnaireStatuses must be(expectedApplicationUser.progressStatuses.questionnaireStatuses)
      result must be(expectedApplicationUser)
    }

    "return user without questionnaire" in {
      helperRepo.collection.insert(UserWithoutQuestionnaire).futureValue

      val result = diagnosticReportRepo.findByUserId("user1").futureValue

      val expectedApplicationUser = ApplicationUser("app1", "user1", "FastStream-2016", "SUBMITED",
        ApplicationProgressStatuses(Some(List(
          ApplicationProgressStatus("registered", true),
          ApplicationProgressStatus("personal_details_completed", true),
          ApplicationProgressStatus("schemes_and_locations_completed", true),
          ApplicationProgressStatus("assistance_details_completed", true),
          ApplicationProgressStatus("preview_completed", true),
          ApplicationProgressStatus("submitted", true),
          ApplicationProgressStatus("online_test_invited", true),
          ApplicationProgressStatus("online_test_started", true),
          ApplicationProgressStatus("online_test_completed", true),
          ApplicationProgressStatus("awaiting_online_test_allocation", true)
        )), None))

      result.progressStatuses.statuses must be(expectedApplicationUser.progressStatuses.statuses)
      result.progressStatuses.questionnaireStatuses must be(expectedApplicationUser.progressStatuses.questionnaireStatuses)
      result must be(expectedApplicationUser)
    }

    "return user which application status is CREATED" in {
      helperRepo.collection.insert(UserWithOnlyRegistedStatus).futureValue

      val result = diagnosticReportRepo.findByUserId("user1").futureValue

      val expectedApplicationUser = ApplicationUser("app1", "user1", "FastStream-2016", "CREATED",
        ApplicationProgressStatuses(Some(List(
          ApplicationProgressStatus("registered", true)
        )), None))

      result.progressStatuses.statuses must be(expectedApplicationUser.progressStatuses.statuses)
      result.progressStatuses.questionnaireStatuses must be(expectedApplicationUser.progressStatuses.questionnaireStatuses)
      result must be(expectedApplicationUser)
    }
  }

  val UserWithAllDetails = BSONDocument(
    "applicationId" -> "app1",
    "userId" -> "user1",
    "frameworkId" -> "FastStream-2016",
    "applicationStatus" -> "AWAITING_ALLOCATION",
    "progress-status" -> BSONDocument(
      "registered" -> BSONBoolean(true),
      "personal_details_completed" -> BSONBoolean(true),
      "schemes_and_locations_completed" -> BSONBoolean(true),
      "assistance_details_completed" -> BSONBoolean(true),
      "preview_completed" -> BSONBoolean(true),
      "questionnaire" -> BSONDocument(
        "start_diversity_questionnaire" -> BSONBoolean(true),
        "diversity_questions_completed" -> BSONBoolean(true),
        "education_questions_completed" -> BSONBoolean(true),
        "occupation_questions_completed" -> BSONBoolean(true)
      ),
      "submitted" -> BSONBoolean(true),
      "online_test_invited" -> BSONBoolean(true),
      "online_test_started" -> BSONBoolean(true),
      "online_test_completed" -> BSONBoolean(true),
      "awaiting_online_test_allocation" -> BSONBoolean(true)
    ))

  val UserWithoutQuestionnaire = BSONDocument(
    "applicationId" -> "app1",
    "userId" -> "user1",
    "frameworkId" -> "FastStream-2016",
    "applicationStatus" -> "SUBMITED",
    "progress-status" -> BSONDocument(
      "registered" -> BSONBoolean(true),
      "personal_details_completed" -> BSONBoolean(true),
      "schemes_and_locations_completed" -> BSONBoolean(true),
      "assistance_details_completed" -> BSONBoolean(true),
      "preview_completed" -> BSONBoolean(true),
      "submitted" -> BSONBoolean(true),
      "online_test_invited" -> BSONBoolean(true),
      "online_test_started" -> BSONBoolean(true),
      "online_test_completed" -> BSONBoolean(true),
      "awaiting_online_test_allocation" -> BSONBoolean(true)
    ))

  val UserWithOnlyRegistedStatus = BSONDocument(
    "applicationId" -> "app1",
    "userId" -> "user1",
    "frameworkId" -> "FastStream-2016",
    "applicationStatus" -> "CREATED"
  )
}
