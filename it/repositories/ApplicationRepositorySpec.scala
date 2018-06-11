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

import config.MicroserviceAppConfig.cubiksGatewayConfig
import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.{ ApplicationRoute, ProgressStatuses }
import model.Exceptions.ApplicationNotFound
import model.command.WithdrawApplication
import model.persisted.AssistanceDetails
import org.joda.time.DateTime
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import testkit.MongoRepositorySpec

import scala.concurrent.Await

class ApplicationRepositorySpec extends MongoRepositorySpec {

  import ImplicitBSONHandlers._

  val frameworkId = "FastStream-2016"

  val collectionName = CollectionNames.APPLICATION

  def applicationRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, cubiksGatewayConfig)

  def assistanceRepo = new AssistanceDetailsMongoRepository()

  "Application repository" should {
    "create indexes for the repository" in {
      val repo = repositories.applicationRepository

      val indexes = indexesWithFields(repo)
      indexes must contain theSameElementsAs
        Seq(
          List("_id"),
          List("applicationId", "userId"),
          List("userId", "frameworkId"),
          List("applicationStatus"),
          List("assistance-details.needsSupportForOnlineAssessment"),
          List("assistance-details.needsSupportAtVenue"),
          List("assistance-details.guaranteedInterview")
        )
    }

    "return the gis parameter" in {
      val userId = "userId9876"
      val applicationId = applicationRepo.create(userId, "frameworkId", ApplicationRoute.Faststream).futureValue.applicationId

      val details = AssistanceDetails("Yes", Some("disability"), Some(true), Some(true), Some("adjustment online"),
        Some(true), Some("adjustment venue"), None, None)
      assistanceRepo.update(applicationId, userId, details).futureValue

      applicationRepo.gisByApplication(applicationId).futureValue mustBe true
    }
  }

  "Updating an application's submission deadline" should {
    "Succeed" in {
      val extendTo = new DateTime(2016, 5, 21, 23, 59, 59)
      val applicationId = applicationRepo.create("userId1", "frameworkId", ApplicationRoute.Faststream).futureValue.applicationId
      applicationRepo.updateSubmissionDeadline(applicationId, extendTo).futureValue

      val result = applicationRepo.findByUserId("userId1", "frameworkId").futureValue

      result.overriddenSubmissionDeadline.get.getMillis mustBe extendTo.getMillis
    }
  }

  "Finding an application by User Id" should {
    "throw a NotFound exception when application doesn't exists" in {
      val result = applicationRepo.findByUserId("invalidUser", "invalidFramework")
      result.failed.futureValue mustBe an[ApplicationNotFound]
    }

    "throw an exception not of the type ApplicationNotFound when application is corrupt" in {
      Await.ready(applicationRepo.collection.insert(BSONDocument(
        "userId" -> "validUser",
        "frameworkId" -> "validFrameworkField"
        // but application Id framework, which is mandatory, so will fail to deserialise
      )), timeout)

      val thrown = the[Exception] thrownBy Await.result(applicationRepo
        .findByUserId("validUser", "validFrameworkField"), timeout)
      thrown must not be an[ApplicationNotFound]
    }
  }

  "Finding applications by user id" should {
    "return an empty list when no records for an applicationid exist" in {
      applicationRepo.find(List("appid-1")).futureValue.size mustBe 0
    }

    "return a list of Candidates when records for an applicationid exist" in {
      val appResponse = applicationRepo.create("userId1", "framework", ApplicationRoute.Faststream).futureValue

      val result = applicationRepo.find(List(appResponse.applicationId)).futureValue

      result.size mustBe 1
      result.head.applicationId.get mustBe appResponse.applicationId
      result.head.userId mustBe "userId1"
    }
  }

  "Submit application" should {
    "capture the submission date and change the application status to submitted" in {
      val applicationStatus = (for {
        app <- applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream)
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(app.applicationId, ProgressStatuses.PREVIEW)
        _ <- applicationRepo.submit(app.applicationId)
        appStatus <- applicationRepo.findStatus(app.applicationId)
      } yield appStatus).futureValue

      applicationStatus.applicationStatus mustBe SUBMITTED.toString
      timesApproximatelyEqual(applicationStatus.statusDate.get, DateTime.now()) mustBe true
    }

    "not allow multiple submissions" in {
      val app = applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream).futureValue
      applicationRepo.addProgressStatusAndUpdateAppStatus(app.applicationId, ProgressStatuses.PREVIEW).futureValue

      applicationRepo.submit(app.applicationId).futureValue mustBe unit
      applicationRepo.submit(app.applicationId).failed.futureValue mustBe an[IllegalStateException]
    }

    "not allow submissions unless in PREVIEW" in {
      val app = applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream).futureValue
      applicationRepo.addProgressStatusAndUpdateAppStatus(app.applicationId, ProgressStatuses.CREATED).futureValue

      applicationRepo.submit(app.applicationId).failed.futureValue mustBe an[IllegalStateException]
    }
  }

  "Withdrawn application" should {
    "capture the withdrawn date and change the application status to withdrawn" in {
      val applicationStatus = (for {
        app <- applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream)
        _ <- applicationRepo.withdraw(app.applicationId, WithdrawApplication("test", None, "test"))
        appStatus <- applicationRepo.findStatus(app.applicationId)
      } yield appStatus).futureValue

      applicationStatus.applicationStatus mustBe WITHDRAWN.toString
      timesApproximatelyEqual(applicationStatus.statusDate.get, DateTime.now()) mustBe true
    }
  }

  "preview" should {
    "change progress status to preview" in {
      createApplication("app1", IN_PROGRESS)

      applicationRepo.preview("app1").futureValue

      val status = getApplicationStatus("app1")
      status mustBe IN_PROGRESS.toString

      val progressResponse = applicationRepo.findProgress("app1").futureValue
      progressResponse.preview mustBe true
    }
  }

  def getApplicationStatus(appId: String) = {
    applicationRepo.collection.find(BSONDocument("applicationId" -> "app1")).one[BSONDocument].map { docOpt =>
      docOpt must not be empty
      val doc = docOpt.get
      doc.getAs[String]("applicationStatus").get
    }.futureValue
  }

  def createApplication(appId: String, appStatus: String): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus
    )).futureValue
  }

  def createApplicationWithPassmark(appId: String, appStatus: String, passmarkVersion: String): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "assessment-centre-passmark-evaluation" -> BSONDocument(
        "passmarkVersion" -> passmarkVersion
      )
    )).futureValue
  }
}
