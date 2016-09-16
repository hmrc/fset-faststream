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
import model.OnlineTestCommands.{ Phase1Test, Phase1TestProfile }
import model.OnlineTestCommands.Implicits.Phase1TestProfileFormats
import model.PersistedObjects.ApplicationIdWithUserIdAndStatus
import org.joda.time.DateTime
import reactivemongo.bson.{ BSONArray, BSONDocument }
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.GeneralApplicationMongoRepository
import repositories.onlinetests.{ OnlineTestMongoRepository, OnlineTestStatusFlags }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class OnlineTestRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "application"

  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  def onlineTestRepo = new OnlineTestMongoRepository(DateTimeFactory)

  val phase1Test = Phase1Test(scheduleId = 123,
    usedForResults = true, cubiksUserId = 999, token = UUID.randomUUID.toString, testUrl = "test.com",
    invitationDate = DateTime.now, participantScheduleId = 456
  )

  "Get online test" should {
    "return None if there is no test for the specific user id" in {
      val result = onlineTestRepo.getPhase1TestProfile("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      val date = new DateTime("2016-03-08T13:04:29.643Z")
      val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))

      onlineTestRepo.insertPhase1TestProfile("appId", testProfile).futureValue

      onlineTestRepo.getPhase1TestProfile("appId").futureValue.foreach { result =>
        result.expirationDate.toDate mustBe date
        result.tests.head.testUrl mustBe phase1Test.testUrl
      }
    }
  }

  "set a status flag for a test" should {
    "set the status flag to true" in {
      val date = DateTime.now
      val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
      onlineTestRepo.insertPhase1TestProfile("appId", testProfile).futureValue

      onlineTestRepo.setTestStatusFlag("appId", testProfile.tests.head.token,
        OnlineTestStatusFlags.started
      ).futureValue

      onlineTestRepo.getPhase1TestProfile("appId").futureValue.foreach { result =>
        result.tests.head.started mustBe true
      }
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

  def createOnlineTest(userId: String, appStatus: String, phase1Tests: List[Phase1Test],
    expirationDate: DateTime = DateTime.now) = {
    import model.OnlineTestCommands.Phase1TestProfile.phase1TestProfileHandler

    val appId = UUID.randomUUID().toString

    val profile = Phase1TestProfile(expirationDate = expirationDate, tests = phase1Tests)
    onlineTestRepo.collection.insert(profile)

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "applicationId" -> appId,
      "frameworkId" -> "frameworkId",
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "progress-status-dates" -> BSONDocument("allocation_unconfirmed" -> "2016-04-05"),
      "assistance-details" -> BSONDocument(
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "No",
        "needsSupportAtVene" -> "No"
      )
    )).futureValue

    ApplicationIdWithUserIdAndStatus(appId, userId, appStatus)
  }

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
