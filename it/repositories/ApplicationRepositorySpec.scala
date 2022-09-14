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

import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.Exceptions.{ApplicationNotFound, CannotUpdateRecord}
import model.command.WithdrawApplication
import model.persisted.AssistanceDetails
import model.{ApplicationRoute, ProgressStatuses}
import org.joda.time.DateTime
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.concurrent.Await

//TODO: mongo note this tests the GeneralApplicationMongoRepository and so does
// repositories.application.GeneralApplicationMongoRepositorySpec
class ApplicationRepositorySpec extends MongoRepositorySpec {

  val frameworkId = "FastStream-2016"

  val collectionName: String = CollectionNames.APPLICATION

  def applicationRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  def assistanceRepo = new AssistanceDetailsMongoRepository(mongo)

  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(CollectionNames.APPLICATION)

  "Application repository" should {
    "create indexes for the repository" in {
      val indexes = indexDetails(applicationRepo).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "applicationId_1_userId_1", keys = Seq(("applicationId", "Ascending"), ("userId", "Ascending")), unique = true),
          IndexDetails(name = "userId_1_frameworkId_1", keys = Seq(("userId", "Ascending"), ("frameworkId", "Ascending")), unique = true),
          IndexDetails(name = "applicationStatus_1", keys = Seq(("applicationStatus", "Ascending")), unique = false),
          IndexDetails(name = "assistance-details.needsSupportForOnlineAssessment_1",
            keys = Seq(("assistance-details.needsSupportForOnlineAssessment", "Ascending")), unique = false),
          IndexDetails(name = "assistance-details.needsSupportAtVenue_1",
            keys = Seq(("assistance-details.needsSupportAtVenue", "Ascending")), unique = false),
          IndexDetails(name = "assistance-details.guaranteedInterview_1",
            keys = Seq(("assistance-details.guaranteedInterview", "Ascending")), unique = false)
        )
    }

    "return the gis parameter" in {
      val userId = "userId9876"
      val applicationId = applicationRepo.create(userId, "frameworkId", ApplicationRoute.Faststream).futureValue.applicationId

      val details = AssistanceDetails(hasDisability = "Yes", disabilityImpact = Some("No"),
        disabilityCategories = Some(List("category1")), otherDisabilityDescription = Some("disability"),
        guaranteedInterview = Some(true), needsSupportForOnlineAssessment = Some(true),
        needsSupportForOnlineAssessmentDescription = Some("adjustment online"), needsSupportAtVenue = Some(true),
        needsSupportAtVenueDescription = Some("adjustment venue"), needsSupportForPhoneInterview = None,
        needsSupportForPhoneInterviewDescription = None)
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
      Await.ready(applicationCollection.insertOne(Document(
        "userId" -> "validUser",
        "frameworkId" -> "validFrameworkField"
        // but application Id framework, which is mandatory, so will fail to deserialise
      )).toFuture(), timeout)

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

      applicationStatus.status mustBe SUBMITTED.toString
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
        _ <- applicationRepo.withdraw(app.applicationId, WithdrawApplication("test", otherReason = None, "test"))
        appStatus <- applicationRepo.findStatus(app.applicationId)
      } yield appStatus).futureValue

      applicationStatus.status mustBe WITHDRAWN.toString
      timesApproximatelyEqual(applicationStatus.statusDate.get, DateTime.now()) mustBe true
    }
  }

  def hasWithdrawSection(applicationId: String) = {
    val query = Document(
      "applicationId" -> applicationId,
      "withdraw" -> Document("$exists" -> true)
    )
    applicationCollection.find[BsonDocument](query)
      .projection(Projections.include("withdraw")).headOption().map { doc => doc.isDefined }
  }

  "remove withdraw reason" should {
    "throw an exception if there is no application" in {
      val result = applicationRepo.removeWithdrawReason(AppId).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }

    "remove the withdraw reason" in {
      val (appId, applicationStatus) = (for {
        app <- applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream)
        _ <- applicationRepo.withdraw(app.applicationId, WithdrawApplication("test", otherReason = None, "test"))
        appStatus <- applicationRepo.findStatus(app.applicationId)
      } yield app.applicationId -> appStatus).futureValue

      hasWithdrawSection(appId).futureValue mustBe true
      applicationStatus.status mustBe WITHDRAWN.toString
      applicationRepo.removeWithdrawReason(appId).futureValue
      hasWithdrawSection(appId).futureValue mustBe false
    }
  }

  "preview" should {
    "change progress status to preview" in {
      createApplication("app1", IN_PROGRESS)

      applicationRepo.preview("app1").futureValue

      val status = getApplicationStatus("app1")
      status mustBe IN_PROGRESS

      val progressResponse = applicationRepo.findProgress("app1").futureValue
      progressResponse.preview mustBe true
    }
  }

  def getApplicationStatus(appId: String) = {
    applicationRepo.collection.find[Document](Document("applicationId" -> "app1")).headOption().map { docOpt =>
      docOpt must not be empty
      val doc = docOpt.get
      Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
    }.futureValue
  }

  private def createApplication(appId: String, appStatus: String): Unit = {
    applicationCollection.insertOne(Document(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus
    )).toFuture().futureValue
  }

  private def createApplicationWithPassmark(appId: String, appStatus: String, passmarkVersion: String): Unit = {
    applicationCollection.insertOne(Document(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "assessment-centre-passmark-evaluation" -> Document(
        "passmarkVersion" -> passmarkVersion
      )
    )).toFuture().futureValue
  }
}
