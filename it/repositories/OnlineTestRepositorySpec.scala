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

import java.util.UUID

import factories.DateTimeFactory
import model.ApplicationStatuses
import model.Exceptions.NotFoundException
import model.OnlineTestCommands.{OnlineTestApplicationWithCubiksUser, OnlineTestProfile}
import model.PersistedObjects.{ApplicationForNotification, ApplicationIdWithUserIdAndStatus, ExpiringOnlineTest}
import org.joda.time.{DateTime, DateTimeZone}
import reactivemongo.bson.{BSONArray, BSONDocument}
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{GeneralApplicationMongoRepository, OnlineTestMongoRepository}
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class OnlineTestRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "application"

  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  def onlineTestRepo = new OnlineTestMongoRepository(DateTimeFactory)



  "Get online test" should {
    "throw an exception if there is no test for the specific user id" in {
      val result = onlineTestRepo.getOnlineTestDetails("userId").failed.futureValue

      result mustBe an[NotFoundException]
    }

    "return an online test for the specific user id" in {
      val date = new DateTime("2016-03-08T13:04:29.643Z")
      createOnlineTest("userId", "status", "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)))

      val result = onlineTestRepo.getOnlineTestDetails("userId").futureValue

      result.expireDate.toDate must be (new DateTime("2016-03-15T13:04:29.643Z").toDate)
      result.onlineTestLink must be("http://www.someurl.com")
      result.isOnlineTestEnabled must be(true)
    }
  }


  "Store online test profile" should {
    "update online test profile and set the status to ONLINE_TEST_INVITED" in {
      val date = new DateTime("2016-03-08T13:04:29.643Z")
      val appIdWithUserId = createOnlineTest("userId", "SUBMITTED", "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), xmlReportSaved = Some(true), pdfReportSaved = Some(true))

      val TestProfile = OnlineTestProfile(
        1234,
        "tokenId",
        "http://someurl.com",
        invitationDate = date,
        expirationDate = date.plusDays(7),
        123456,
        567879
      )
      onlineTestRepo.storeOnlineTestProfile(appIdWithUserId.applicationId, TestProfile).futureValue

      val result = onlineTestRepo.getOnlineTestDetails(appIdWithUserId.userId).futureValue
      // The expireDate has +7 days, as the method get from the repo adds 7 days
      result.expireDate.toDate must be(new DateTime("2016-03-15T13:04:29.643Z").toDate)
      result.inviteDate.toDate must be(date.toDate)
      result.isOnlineTestEnabled must be(true)
      result.onlineTestLink must be("http://someurl.com")

      val query = BSONDocument("applicationId" -> appIdWithUserId.applicationId)
      val (xml, pdf) = helperRepo.collection.find(query).one[BSONDocument].map { docOpt =>
        val root = docOpt.get.getAs[BSONDocument]("online-tests").get
        (root.getAs[Boolean]("xmlReportSaved"),
          root.getAs[Boolean]("pdfReportSaved"))
      }.futureValue

      xml must be (empty)
      pdf must be (empty)
    }

    // TODO: in faststream
    "unset the online test flags for already completed online test when storeOnlineTestProfileAndUpdateStatus is called again" ignore {
      val InvitationDate = DateTime.now()
      val ExpirationDate = InvitationDate.plusDays(7)
      val TestProfile = OnlineTestProfile(1234, "tokenId", "http://someurl.com", InvitationDate, ExpirationDate, 123456, 67890)
      helperRepo.collection.insert(BSONDocument(
        "applicationId" -> "appId",
        "applicationStatus" -> "ONLINE_TEST_FAILED_NOTIFIED",
        "progress-status" -> BSONDocument(
          "online_test_started" -> true,
          "online_test_completed" -> true,
          "online_test_expired" -> true,
          "awaiting_online_test_re_evaluation" -> true,
          "online_test_failed" -> true,
          "online_test_failed_notified" -> true,
          "awaiting_online_test_allocation" -> true
        ),
        "online-tests" -> BSONDocument(
          "cubiksUserId" -> 1111,
          "token" -> "previousToken",
          "onlineTestUrl" -> "previousOnlineTestUrl",
          "invitationDate" -> DateTime.now().minusDays(10),
          "expiratinDate" -> DateTime.now().minusDays(3),
          "participantScheduleId" -> "previousScheduleId",
          "xmlReportSaved" -> true,
          "pdfReportSaved" -> true
        ),
        "passmarkEvaluation" -> "notEmpty"
      )).futureValue

      onlineTestRepo.storeOnlineTestProfile("appId", TestProfile).futureValue

      val query = BSONDocument("applicationId" -> "appId")
      helperRepo.collection.find(query).one[BSONDocument].map {
        case Some(doc) =>
          doc.getAs[String]("applicationStatus") must be(Some("ONLINE_TEST_INVITED"))

          val progressStatus = doc.getAs[BSONDocument]("progress-status").get
          val allProgressStatuses = progressStatus.elements.map(_._1).toList
          allProgressStatuses must be(List("online_test_invited"))

          val onlineTests = doc.getAs[BSONDocument]("online-tests").get
          onlineTests.getAs[Int]("cubiksUserId") must be(Some(1234))
          onlineTests.getAs[String]("token") must be(Some("tokenId"))
          onlineTests.getAs[String]("onlineTestUrl") must be(Some("http://someurl.com"))
          onlineTests.getAs[DateTime]("invitationDate").get must be(InvitationDate.withZone(DateTimeZone.UTC))
          onlineTests.getAs[DateTime]("expirationDate").get must be(ExpirationDate.withZone(DateTimeZone.UTC))
          onlineTests.getAs[Int]("participantScheduleId") must be(Some(123456))
          onlineTests.getAs[Boolean]("xmlReportSaved") must be(empty)
          onlineTests.getAs[Boolean]("pdfReportSaved") must be(empty)

          doc.getAs[BSONDocument]("passmarkEvaluation") must be (empty)

        case None => fail("Application should have been already created and cannot be empty")
      }.futureValue
    }
  }

  /* TODO: Refactor in faststream

   "Getting the next application for expiry" should {
    "return one application if there is one expired un-started test" in {
      val appIdWithUserId = createOnlineTest("ONLINE_TEST_INVITED", expirationDate = DateTime.now().minusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe Some(ExpiringOnlineTest(appIdWithUserId.applicationId, appIdWithUserId.userId, "Test Preferred Name"))
    }

    "return one application if there is one expired started test" in {
      val appIdWithUserId = createOnlineTest("ONLINE_TEST_STARTED", expirationDate = DateTime.now().minusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe Some(ExpiringOnlineTest(appIdWithUserId.applicationId, appIdWithUserId.userId, "Test Preferred Name"))
    }

    "return no applications if there are started and un-started tests, but none expired" in {
      createOnlineTest("ONLINE_TEST_INVITED", expirationDate = DateTime.now().plusMinutes(1))
      createOnlineTest("ONLINE_TEST_STARTED", expirationDate = DateTime.now().plusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe None
    }

    "return no applications if there are expired tests, but are not active" in {
      createOnlineTest("CREATED", expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest("WITHDRAWN", expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest("IN_PROGRESS", expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest("ONLINE_TEST_COMPLETED", expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest("ONLINE_TEST_EXPIRED", expirationDate = DateTime.now().minusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe None
    }

    "return a random application from a choice of multiple applications in relevant states" in {
      createOnlineTest("userId1", "ONLINE_TEST_INVITED", expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest("userId2", "ONLINE_TEST_INVITED", expirationDate = DateTime.now().minusMinutes(1))

      val userIds = (1 to 20).map { _ =>
        val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue
        result.get.userId
      }

      userIds must contain("userId1")
      userIds must contain("userId2")
    }
  }

  "Update expiry time" should {
    "set ONLINE_TEST_INVITED to ONLINE_TEST_INVITED" in {
      updateExpiryAndAssert("ONLINE_TEST_INVITED", "ONLINE_TEST_INVITED")
    }
    "set ONLINE_TEST_STARTED tests to ONLINE_TEST_STARTED" in {
      updateExpiryAndAssert("ONLINE_TEST_STARTED", "ONLINE_TEST_STARTED")
    }
    "set EXPIRED tests to INVITED" in {
      updateExpiryAndAssert("ONLINE_TEST_EXPIRED", "ONLINE_TEST_INVITED")
    }

    def updateExpiryAndAssert(currentStatus: String, newStatus: String) = {
      val oldExpiration = DateTime.now()
      val newExpiration = oldExpiration.plusDays(3)
      val appIdWithUserId = createOnlineTest(currentStatus, expirationDate = oldExpiration)

      onlineTestRepo.updateExpiryTime(appIdWithUserId.userId, newExpiration).futureValue

      val expireDate = onlineTestRepo.getOnlineTestDetails(appIdWithUserId.userId).map(_.expireDate).futureValue
      expireDate.toDate must be(newExpiration.toDate)

      val appStatus = onlineTestRepo.getOnlineTestApplication(appIdWithUserId.applicationId).map(_.get.applicationStatus).futureValue
      appStatus must be(newStatus)
    }
  }

    "Getting the next application for failure notification" should {
    "return one application if there is one failed test and pdf report has been saved" in {
      val appIdWithUserId = createOnlineTest("ONLINE_TEST_FAILED", xmlReportSaved=Some(true), pdfReportSaved = Some(true))

      val result = onlineTestRepo.nextApplicationPendingFailure.futureValue

      result mustBe Some(ApplicationForNotification(appIdWithUserId.applicationId,
        appIdWithUserId.userId, "Test Preferred Name", "ONLINE_TEST_FAILED"))
    }

    "return no application if there is one failed test but pdf report has not been saved" in {
      createOnlineTest("ONLINE_TEST_FAILED", xmlReportSaved=Some(true), pdfReportSaved=Some(false))

      val result = onlineTestRepo.nextApplicationPendingFailure.futureValue

      result mustBe None
    }

    "return no applications if there are applications which don't require notifying of failure" in {
      createOnlineTest("ONLINE_TEST_STARTED")
      createOnlineTest("ONLINE_TEST_INVITED")
      createOnlineTest("ONLINE_TEST_PASSED")
      createOnlineTest("ONLINE_TEST_FAILED_NOTIFIED")

      val result = onlineTestRepo.nextApplicationPendingFailure.futureValue

      result mustBe None
    }

    "return a random application from a choice of multiple failed tests" in {
      createOnlineTest("userId1", "ONLINE_TEST_FAILED", xmlReportSaved=Some(true), pdfReportSaved = Some(true))
      createOnlineTest("userId2", "ONLINE_TEST_FAILED", xmlReportSaved=Some(true), pdfReportSaved = Some(true))
      createOnlineTest("userId3", "ONLINE_TEST_FAILED", xmlReportSaved=Some(true), pdfReportSaved = Some(true))

      val userIds = (1 to 15).map { _ =>
        val result = onlineTestRepo.nextApplicationPendingFailure.futureValue
        result.get.userId
      }

      userIds must contain("userId1")
      userIds must contain("userId2")
      userIds must contain("userId3")
    }
  }

  "removing a candidate's allocation status" should {
    "remove the status, and status flags" in {
      val appIdWithUserId = createOnlineTest(UUID.randomUUID().toString, ApplicationStatuses.AllocationConfirmed)

      val result = onlineTestRepo.removeCandidateAllocationStatus(appIdWithUserId.applicationId).futureValue

      result must be(())

      val checkResult = onlineTestRepo.collection
        .find(BSONDocument("applicationId" -> appIdWithUserId.applicationId)).one[BSONDocument].futureValue

      checkResult.isDefined must be(true)
      checkResult.get.getAs[String]("applicationStatus").get must be(ApplicationStatuses.AwaitingAllocation)
      checkResult.get.get("progress-status-dates.allocation_unconfirmed").isDefined must be(false)
    }
  }

  "next application ready for online test evaluation" should {
    "return no candidate if there is only a candidate in ONLINE_TEST_STARTED status" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.OnlineTestStarted)

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must be(empty)
    }

    "return a candidate who has the report xml saved and who has never been evaluated before" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.OnlineTestCompleted, xmlReportSavedOpt = Some(true))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must not be empty
      result.get.applicationId must be(AppId)
    }

    "return a candidate who is in AWAITING_ONLINE_TEST_RE_EVALUATION status and with an old passmark version" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AwaitingOnlineTestReevaluation,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("oldVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must not be empty
      result.get.applicationId must be(AppId)
    }

    "return no candidate if there is only one who has been already evaluated against the same Passmark version" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AwaitingOnlineTestReevaluation,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("currentVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must be(empty)
    }

    "return a candidate who is in ASSESSMENT_SCORES_ACCEPTED status and with an old passmark version" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AssessmentScoresAccepted,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("oldVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must not be empty
      result.get.applicationId must be(AppId)
    }

    "return no candidate if there is only one who has been already evaluated but the application status is ASSESSMENT_SCORES_ENTERED" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AssessmentScoresEntered,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("currentVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must be(empty)
    }
  }*/

  def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String,
    needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments: Boolean,
    fastPassApplicable: Boolean = false) = {

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "fastpass-details.applicable" -> fastPassApplicable,
      "assistance-details" -> createAsistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments)
    )).futureValue
  }

  private def createAsistanceDetails(needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments:Boolean) = {
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

  def createOnlineTest(appStatus: String): Unit =
    createOnlineTest(UUID.randomUUID().toString, appStatus, DateTime.now().plusDays(5))

  def createOnlineTest(appStatus: String, xmlReportSaved: Option[Boolean], pdfReportSaved: Option[Boolean]): ApplicationIdWithUserIdAndStatus =
    createOnlineTest(UUID.randomUUID().toString, appStatus, DateTime.now().plusDays(5), xmlReportSaved, pdfReportSaved)

  def createOnlineTest(appStatus: String, expirationDate: DateTime): ApplicationIdWithUserIdAndStatus =
    createOnlineTest(UUID.randomUUID().toString, appStatus, expirationDate)

  def createOnlineTest(userId: String, appStatus: String): ApplicationIdWithUserIdAndStatus =
    createOnlineTest(userId, appStatus, DateTime.now().plusDays(5))

  def createOnlineTest(userId: String, appStatus: String, xmlReportSaved: Option[Boolean], pdfReportSaved: Option[Boolean]): Unit =
    createOnlineTest(userId, appStatus, DateTime.now().plusDays(5), xmlReportSaved, pdfReportSaved)

  def createOnlineTest(userId: String, appStatus: String, expirationDate: DateTime): ApplicationIdWithUserIdAndStatus =
    createOnlineTest(userId, appStatus, "token", Some("http://www.someurl.com"),
      invitationDate = Some(expirationDate.minusDays(7)), expirationDate = Some(expirationDate))

  def createOnlineTest(userId: String, appStatus: String, expirationDate: DateTime, xmlReportSaved: Option[Boolean],
                       pdfReportSaved: Option[Boolean]): ApplicationIdWithUserIdAndStatus = {
    createOnlineTest(userId, appStatus, "token", Some("http://www.someurl.com"),
      invitationDate = Some(expirationDate.minusDays(7)), expirationDate = Some(expirationDate), xmlReportSaved = xmlReportSaved,
      pdfReportSaved = pdfReportSaved)
  }

  //scalastyle:off
  def createOnlineTest(userId: String, appStatus: String, token: String, onlineTestUrl: Option[String],
                       invitationDate: Option[DateTime], expirationDate: Option[DateTime], cubiksUserId: Option[Int] = None,
                       xmlReportSaved: Option[Boolean] = None, pdfReportSaved: Option[Boolean] = None): ApplicationIdWithUserIdAndStatus = {
    val onlineTests = if (pdfReportSaved.isDefined && xmlReportSaved.isDefined) {
      BSONDocument(
        "cubiksUserId" -> cubiksUserId.getOrElse(0),
        "onlineTestUrl" -> onlineTestUrl.get,
        "invitationDate" -> invitationDate.get,
        "expirationDate" -> expirationDate.get,
        "token" -> token,
        "xmlReportSaved" -> xmlReportSaved.get,
        "pdfReportSaved" -> pdfReportSaved.get
      )
    } else {
      if (xmlReportSaved.isDefined) {
        BSONDocument(
          "cubiksUserId" -> cubiksUserId.getOrElse(0),
          "onlineTestUrl" -> onlineTestUrl.get,
          "invitationDate" -> invitationDate.get,
          "expirationDate" -> expirationDate.get,
          "token" -> token,
          "xmlReportSaved" -> xmlReportSaved.get
        )
      } else {
        BSONDocument(
          "cubiksUserId" -> cubiksUserId.getOrElse(0),
          "onlineTestUrl" -> onlineTestUrl.get,
          "invitationDate" -> invitationDate.get,
          "expirationDate" -> expirationDate.get,
          "token" -> token
        )
      }
    }

    val appId = UUID.randomUUID().toString

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "applicationId" -> appId,
      "frameworkId" -> "frameworkId",
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "online-tests" -> onlineTests,
      "progress-status-dates" -> BSONDocument("allocation_unconfirmed" -> "2016-04-05"),
      "assistance-details" -> BSONDocument(
        "guaranteedInterview" -> "Yes",
        "needsAdjustment" -> "No",
        "expirationDate" -> expirationDate.get,
        "token" -> token
      )
    )).futureValue

    ApplicationIdWithUserIdAndStatus(appId, userId, appStatus)
  }
  //scalastyle:on

  def createOnlineTestApplication(appId: String, applicationStatus: String, xmlReportSavedOpt: Option[Boolean] = None,
                                  alreadyEvaluatedAgainstPassmarkVersionOpt: Option[String] = None): String = {
    val result = (xmlReportSavedOpt, alreadyEvaluatedAgainstPassmarkVersionOpt) match {
      case (None, None ) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus
        ))
      case (Some(xmlReportSaved), None) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved)
        ))
      case (None, Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
      case (Some(xmlReportSaved), Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved),
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
    }

    result.futureValue

    appId
  }

}
