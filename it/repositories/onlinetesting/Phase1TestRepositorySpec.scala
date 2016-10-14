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

package repositories.onlinetesting

import java.util.UUID

import model.Exceptions.CannotFindTestByCubiksId
import model.OnlineTestCommands.OnlineTestApplication
import model.persisted.{ CubiksTest, Phase1TestProfile }
import model.persisted.ExpiringOnlineTest
import model.ProgressStatuses.{ PHASE1_TESTS_COMPLETED, PHASE1_TESTS_EXPIRED, PHASE1_TESTS_STARTED, _ }
import model.persisted.Phase1TestWithUserIds
import model.{ ApplicationStatus, ProgressStatuses, ReminderNotice, persisted }
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import testkit.MongoRepositorySpec

class Phase1TestRepositorySpec extends ApplicationDataFixture with MongoRepositorySpec {
  import TextFixture._

  val Token = UUID.randomUUID.toString
  val Now =  DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)
  val CubiksUserId = 999

  val phase1Test = CubiksTest(
    scheduleId = 123,
    usedForResults = true,
    cubiksUserId = CubiksUserId,
    token = Token,
    testUrl = "test.com",
    invitationDate = Now,
    participantScheduleId = 456
  )

  val TestProfile = Phase1TestProfile(expirationDate = DatePlus7Days, tests = List(phase1Test))
  val testProfileWithAppId = Phase1TestWithUserIds(
    "appId",
    "userId",
    TestProfile.copy(tests = List(
      phase1Test.copy(usedForResults = true, resultsReadyToDownload = true),
      phase1Test.copy(usedForResults = true, resultsReadyToDownload = true))
    )
  )

  "Get online test" should {
    "return None if there is no test for the specific user id" in {
      val result = phase1TestRepo.getTestGroup("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase1TestRepo.getTestGroup("appId").futureValue
      result mustBe Some(TestProfile)
    }

    "return None if there is an application with out test group" in {
      insertApplication("appId", "userId")
      val result = phase1TestRepo.getTestGroup("appId").futureValue
      result mustBe None
    }
  }

  "Get online test by token" should {
    "return None if there is no test with the token" in {
      val result = phase1TestRepo.getTestProfileByToken("token").failed.futureValue
      result mustBe a[CannotFindTestByCubiksId]
    }

    "return an online tet for the specific token" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase1TestRepo.getTestProfileByToken(Token).futureValue
      result mustBe TestProfile
    }
  }

  "Get online test by cubiksId" should {
    "return None if there is no test with the cubiksId" in {
      val result = phase1TestRepo.getTestProfileByCubiksId(CubiksUserId).failed.futureValue
      result mustBe a[CannotFindTestByCubiksId]
    }

    "return an online tet for the specific cubiks id" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase1TestRepo.getTestProfileByCubiksId(CubiksUserId).futureValue
      result mustBe Phase1TestWithUserIds("appId", "userId", TestProfile)
    }

  }

  "Next application ready for online testing" should {
    "return no application if there is only one and it is a fast pass candidate" in{
      createApplicationWithAllFields("appId", "userId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = true,
        fastPassReceived = true
      ).futureValue

      val result = phase1TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      result must be (Nil)
    }

    "return one application if there is only one and it is not a fast pass candidate" in{
      createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      val results = phase1TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 1
      results(0).applicationId mustBe "appId"
      results(0).userId mustBe "userId"
    }
  }

  "Next phase1 test group with report ready" should {
    "not return a test group if the progress status is not appropriately set" in {
       createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS",
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_RESULTS_READY, false))
      ).futureValue

      val result = phase1TestRepo.nextTestGroupWithReportReady.futureValue

      result.isDefined mustBe false
    }

    "return a test group if the progress status is set to PHASE1_TEST_RESULTS_READY" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_RESULTS_READY, true)),
        phase1TestProfile = Some(testProfileWithAppId.phase1TestProfile)
      ).futureValue

      val phase1TestResultsReady = phase1TestRepo.nextTestGroupWithReportReady.futureValue
      phase1TestResultsReady.isDefined mustBe true
      phase1TestResultsReady.get mustBe testProfileWithAppId
    }

    "correctly update a test group with results" in {
       createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_RESULTS_READY, true)),
        phase1TestProfile = Some(testProfileWithAppId.phase1TestProfile)
      ).futureValue


      val testResult = persisted.TestResult(status = "completed", norm = "some norm",
          tScore = Some(55.33d), percentile = Some(34.876d), raw = Some(65.32d), sten = Some(12.1d))

      phase1TestRepo.insertPhase1TestResult("appId", testProfileWithAppId.phase1TestProfile.tests.head,
        testResult
      ).futureValue

      val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue
      phase1TestProfile.isDefined mustBe true
      phase1TestProfile.foreach { profile =>
        profile.tests.head.testResult.isDefined mustBe true
        profile.tests.head.testResult.get mustBe testResult
      }

      val status = helperRepo.findProgress("appId").futureValue
      status.phase1TestsResultsReceived mustBe false

    }
  }

  "The OnlineTestApplication case model" should {
    "be correctly read from mongo" in {
       createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false, isGis = true
      ).futureValue

      val onlineTestApplications = phase1TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      onlineTestApplications.length mustBe 1

      inside (onlineTestApplications(0)) { case OnlineTestApplication(applicationId, applicationStatus, userId,
        guaranteedInterview, needsAdjustments, preferredName, lastName, timeAdjustments) =>

        applicationId mustBe "appId"
        applicationStatus mustBe "SUBMITTED"
        userId mustBe "userId"
        guaranteedInterview mustBe true
        needsAdjustments mustBe false
        preferredName mustBe testCandidate("preferredName")
        lastName mustBe testCandidate("lastName")
        timeAdjustments mustBe None
      }
    }
  }

  "nextExpiringApplication" should {
    val date = new DateTime("2015-03-08T13:04:29.643Z")
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    "return one result" when {
      "there is an application in PHASE1_TESTS and should be expired" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        phase1TestRepo.nextExpiringApplication.futureValue must be (Some(ExpiringOnlineTest(AppId,UserId,"Georgy")))
      }
    }
    "return no results" when {
      "there are no application in PHASE1_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("applicationStatus" -> ApplicationStatus.IN_PROGRESS), AppId).futureValue
        phase1TestRepo.nextExpiringApplication.futureValue must be(None)
      }

      "the date is not expired yet" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(
          AppId,
          Phase1TestProfile(expirationDate = new DateTime().plusHours(2), tests = List(phase1Test))).futureValue
        phase1TestRepo.nextExpiringApplication.futureValue must be(None)
      }

      "the test is already expired" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_EXPIRED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextExpiringApplication.futureValue must be(None)
      }

      "the test is completed" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_COMPLETED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextExpiringApplication.futureValue must be(None)
      }
    }
  }

  "nextTestForReminder" should {
    "return one result" when {
      "there is an application in PHASE1_TESTS and is about to expiry in the next 72 hours" in {
        val date = DateTime.now().plusHours(Phase1FirstReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        val notification = phase1TestRepo.nextTestForReminder(Phase1FirstReminder).futureValue
        notification.isDefined must be(true)
        notification.get.applicationId must be(AppId)
        notification.get.userId must be(UserId)
        notification.get.preferredName must be("Georgy")
        notification.get.expiryDate.getMillis must be(date.getMillis)
        // Because we are far away from the 24h reminder's window
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue must be(None)
      }

      "there is an application in PHASE1_TESTS and is about to expiry in the next 24 hours" in {
        val date = DateTime.now().plusHours(Phase1SecondReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        val notification = phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue
        notification.isDefined must be(true)
        notification.get.applicationId must be(AppId)
        notification.get.userId must be(UserId)
        notification.get.preferredName must be("Georgy")
        notification.get.expiryDate.getMillis must be(date.getMillis)
      }
    }

    "return no results" when {
      val date = DateTime.now().plusHours(22)
      val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
      "there are no application in PHASE1_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("applicationStatus" -> ApplicationStatus.IN_PROGRESS), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1FirstReminder).futureValue must be(None)
      }

      "the expiration date is in 26h but we send the second reminder only after 24h" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(
          AppId,
          Phase1TestProfile(expirationDate = new DateTime().plusHours(30), tests = List(phase1Test))).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue must be(None)
      }

      "the test is expired" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_EXPIRED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue must be(None)
      }

      "the test is completed" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_COMPLETED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue must be(None)
      }

      "we already sent a second reminder" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          s"progress-status.$PHASE1_TESTS_SECOND_REMINDER" -> true
        )), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue must be(None)
      }
    }
  }

  "Progress status" should {
    "update progress status to PHASE1_TESTS_STARTED" in {
      createApplicationWithAllFields("userId", "appId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.updateProgressStatus("appId", PHASE1_TESTS_STARTED).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1TestsStarted mustBe true

      val appStatusDetails = helperRepo.findStatus(app.applicationId).futureValue
      appStatusDetails.status mustBe ApplicationStatus.PHASE1_TESTS.toString
    }

    "remove progress statuses" in {
      createApplicationWithAllFields("userId", "appId", appStatus = ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = List(ProgressStatuses.PHASE1_TESTS_INVITED -> true,
          ProgressStatuses.PHASE1_TESTS_COMPLETED -> true)).futureValue
      phase1TestRepo.removeTestProfileProgresses("appId", List(
        ProgressStatuses.PHASE1_TESTS_INVITED,
        ProgressStatuses.PHASE1_TESTS_COMPLETED)
      ).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1TestsInvited mustBe false
      app.progressResponse.phase1TestsCompleted mustBe false
    }
  }

}

object TextFixture {
  val Phase1FirstReminder = ReminderNotice(72, PHASE1_TESTS_FIRST_REMINDER)
  val Phase1SecondReminder = ReminderNotice(24, PHASE1_TESTS_SECOND_REMINDER)
}
