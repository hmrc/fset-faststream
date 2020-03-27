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

import factories.ITDateTimeFactoryMock
import model.EvaluationResults.{ Amber, Green, Red }
import model.Exceptions.{ CannotFindTestByCubiksId, PassMarkEvaluationNotFound }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{ PHASE1_TESTS_COMPLETED, PHASE1_TESTS_EXPIRED, PHASE1_TESTS_STARTED, _ }
import model.exchange.CubiksTestResultReady
import model.persisted._
import model.{ ApplicationStatus, ProgressStatuses, persisted, _ }
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import repositories.CollectionNames
import testkit.MongoRepositorySpec

import scala.concurrent.Await

class Phase1TestRepositorySpec extends MongoRepositorySpec with ApplicationDataFixture {

  override val collectionName = CollectionNames.APPLICATION

  val Token = UUID.randomUUID.toString
  val Now = DateTime.now(DateTimeZone.UTC)
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
  val testProfileWithAppId = Phase1TestGroupWithUserIds(
    "appId",
    "userId",
    TestProfile.copy(tests = List(
      phase1Test.copy(usedForResults = true, resultsReadyToDownload = true),
      phase1Test.copy(usedForResults = true, resultsReadyToDownload = true))
    )
  )
  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository(ITDateTimeFactoryMock)

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

    "return None if there is an application without test group" in {
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

    "return an online test for the specific token" in {
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

    "return an online test for the specific cubiks id" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase1TestRepo.getTestProfileByCubiksId(CubiksUserId).futureValue
      result mustBe Phase1TestGroupWithUserIds("appId", "userId", TestProfile)
    }

  }

  "Next application ready for online testing" should {
    "return no application if there is only one and it is a fast pass candidate" in {
      createApplicationWithAllFields("appId", "userId", "testAccountId", "frameworkId", "SUBMITTED",
        fastPassApplicable = true, fastPassReceived = true
      ).futureValue

      val result = phase1TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      result mustBe Nil
    }

    "return no application if there is one fast pass approved candidate" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "SUBMITTED", fastPassApplicable = true,
        fastPassReceived = true, fastPassAccepted = Some(true)
      ).futureValue

      val result = phase1TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      result mustBe Nil
    }

    "return no application if there is one fast pass rejected candidate" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "SUBMITTED", fastPassApplicable = true,
        fastPassReceived = true, fastPassAccepted = Some(false)
      ).futureValue

      val results = phase1TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId"
      results.head.userId mustBe "userId"
    }

    "return one application if there is only one and it is not a fast pass candidate" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "SUBMITTED").futureValue

      val results = phase1TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId"
      results.head.userId mustBe "userId"
    }
  }

  "Next SdipFasttream test ready for SDIP progression" should {
    "return an SdipFaststream application that already has SDIP scores evaluated to Green/Red" in {

      val resultToSave = List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)
      )
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)
      createApplicationWithAllFields("userId1", "app1",  "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS,
        phase1TestProfile = Some(TestProfile.copy(evaluation = Some(evaluation))), applicationRoute = ApplicationRoute.SdipFaststream.toString
      ).futureValue
      createApplicationWithAllFields("userId2", "app2",  "testAccountId", appStatus = ApplicationStatus.PHASE2_TESTS,
        phase1TestProfile = Some(TestProfile.copy(evaluation = Some(evaluation))), applicationRoute = ApplicationRoute.Sdip.toString
      ).futureValue

      val results = phase1TestRepo.nextSdipFaststreamCandidateReadyForSdipProgression.futureValue
      results.isDefined mustBe true
    }

    "do not return an SdipFaststream application that has SDIP scores evaluated to Ambers" in {

      val resultToSave = List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString)
      )
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)
      createApplicationWithAllFields("userId1", "app1", "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS,
        phase1TestProfile = Some(TestProfile.copy(evaluation = Some(evaluation))), applicationRoute = ApplicationRoute.SdipFaststream.toString
      ).futureValue
      createApplicationWithAllFields("userId2", "app2", "testAccountId", appStatus = ApplicationStatus.PHASE2_TESTS,
        phase1TestProfile = Some(TestProfile.copy(evaluation = Some(evaluation))), applicationRoute = ApplicationRoute.Sdip.toString
      ).futureValue

      val results = phase1TestRepo.nextSdipFaststreamCandidateReadyForSdipProgression.futureValue
      results.isDefined mustBe false
    }
  }

  "Next phase1 test group with report ready" should {
    "not return a test group if the progress status is not appropriately set" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE1_TESTS",
        additionalProgressStatuses = List((PHASE1_TESTS_COMPLETED, false))
      ).futureValue

      val result = phase1TestRepo.nextTestGroupWithReportReady.futureValue

      result.isDefined mustBe false
    }

    "return a test group if the progress status is set to PHASE1_TEST_RESULTS_READY" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE1_TESTS",
        additionalProgressStatuses = List((PHASE1_TESTS_COMPLETED, true), (PHASE1_TESTS_RESULTS_READY, true)),
        phase1TestProfile = Some(testProfileWithAppId.testGroup)
      ).futureValue

      val phase1TestResultsReady = phase1TestRepo.nextTestGroupWithReportReady.futureValue
      phase1TestResultsReady.isDefined mustBe true
      phase1TestResultsReady.get mustBe testProfileWithAppId
    }

    "return a test group if only one report is ready to download" in {
      val profile = testProfileWithAppId.testGroup.copy(tests = List(phase1Test, phase1Test.copy(resultsReadyToDownload = true)))

      createApplicationWithAllFields("userId2", "appId2", "testAccountId", "frameworkId", "PHASE1_TESTS",
        additionalProgressStatuses = List((PHASE1_TESTS_COMPLETED, true), (PHASE1_TESTS_RESULTS_READY, true)),
        phase1TestProfile = Some(profile)
      ).futureValue

      val phase1TestResultsReady = phase1TestRepo.nextTestGroupWithReportReady.futureValue
      phase1TestResultsReady.isDefined mustBe true
      phase1TestResultsReady.get mustBe Phase1TestGroupWithUserIds("appId2", "userId2", profile)
    }
  }

  "Insert test result" should {
    "correctly update a test group with results" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE1_TESTS",
        additionalProgressStatuses = List((PHASE1_TESTS_RESULTS_READY, true)),
        phase1TestProfile = Some(testProfileWithAppId.testGroup)
      ).futureValue

      val testResult = persisted.TestResult(status = "completed", norm = "some norm",
        tScore = Some(55.33d), percentile = Some(34.876d), raw = Some(65.32d), sten = Some(12.1d))

      phase1TestRepo.insertTestResult("appId", testProfileWithAppId.testGroup.tests.head,
        testResult
      ).futureValue

      val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue
      phase1TestProfile.isDefined mustBe true
      phase1TestProfile.foreach { profile =>
        profile.tests.head.testResult.isDefined mustBe true
        profile.tests.head.testResult.get mustBe testResult
      }

      val status = helperRepo.findProgress("appId").futureValue
      status.phase1ProgressResponse.phase1TestsResultsReceived mustBe false
    }
  }

  "The OnlineTestApplication case model" should {
    "be correctly read from mongo" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "SUBMITTED", isGis = true).futureValue

      val onlineTestApplications = phase1TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      onlineTestApplications.length mustBe 1

      inside(onlineTestApplications.head) { case OnlineTestApplication(applicationId, applicationStatus, userId, testAccountId,
      guaranteedInterview, needsOnlineAdjustments, needsAtVenueAdjustments, preferredName, lastName, etrayAdjustments,
      videoInterviewAdjustments) =>

        applicationId mustBe "appId"
        applicationStatus mustBe "SUBMITTED"
        userId mustBe "userId"
        testAccountId mustBe "testAccountId"
        guaranteedInterview mustBe true
        needsOnlineAdjustments mustBe false
        needsAtVenueAdjustments mustBe false
        preferredName mustBe testCandidate("preferredName")
        lastName mustBe testCandidate("lastName")
        etrayAdjustments mustBe None
        videoInterviewAdjustments mustBe None
      }
    }

    "be correctly read from mongo with lower case submitted status" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "submitted", isGis = true).futureValue

      val onlineTestApplications = phase1TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      onlineTestApplications.length mustBe 1

      inside(onlineTestApplications.head) { case OnlineTestApplication(applicationId, applicationStatus, userId, testAccountId,
      guaranteedInterview, needsOnlineAdjustments, needsAtVenueAdjustments, preferredName, lastName, etrayAdjustments,
      videoInterviewAdjustments) =>

        applicationId mustBe "appId"
        applicationStatus mustBe "submitted"
        userId mustBe "userId"
        testAccountId mustBe "testAccountId"
        guaranteedInterview mustBe true
        needsOnlineAdjustments mustBe false
        needsAtVenueAdjustments mustBe false
        preferredName mustBe testCandidate("preferredName")
        lastName mustBe testCandidate("lastName")
        etrayAdjustments mustBe None
        videoInterviewAdjustments mustBe None
      }
    }
  }

  "nextExpiringApplication" should {
    val date = new DateTime("2015-03-08T13:04:29.643Z")
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    val phase1ExpirationEvent = Phase1ExpirationEvent(gracePeriodInSecs = 0)

    "return one result" when {
      "there is an application in PHASE1_TESTS and should be expired" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        phase1TestRepo.nextExpiringApplication(phase1ExpirationEvent).futureValue mustBe Some(ExpiringOnlineTest(AppId, UserId, "Georgy"))
      }
    }

    "return no results" when {
      "there are no applications in PHASE1_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("applicationStatus" -> ApplicationStatus.IN_PROGRESS), AppId).futureValue
        phase1TestRepo.nextExpiringApplication(phase1ExpirationEvent).futureValue mustBe None
      }

      "the date is not expired yet" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(
          AppId,
          Phase1TestProfile(expirationDate = new DateTime().plusHours(2), tests = List(phase1Test))).futureValue
        phase1TestRepo.nextExpiringApplication(phase1ExpirationEvent).futureValue mustBe None
      }

      "the test is already expired" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_EXPIRED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextExpiringApplication(phase1ExpirationEvent).futureValue mustBe None
      }

      "the test is completed" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_COMPLETED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextExpiringApplication(phase1ExpirationEvent).futureValue mustBe None
      }
    }
  }

  "nextTestForReminder" should {
    "return one result" when {
      "there is an application in PHASE1_TESTS and is about to expire in the next 72 hours" in {
        val date = DateTime.now().plusHours(Phase1FirstReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        val notification = phase1TestRepo.nextTestForReminder(Phase1FirstReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.getMillis mustBe date.getMillis
        // Because we are far away from the 24h reminder's window
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe None
      }

      "there is an application in PHASE1_TESTS and is about to expire in the next 24 hours" in {
        val date = DateTime.now().plusHours(Phase1SecondReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        val notification = phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.getMillis mustBe date.getMillis
      }
    }

    "return no results" when {
      val date = DateTime.now().plusHours(22)
      val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
      "there are no application in PHASE1_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("applicationStatus" -> ApplicationStatus.IN_PROGRESS), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1FirstReminder).futureValue mustBe None
      }

      "the expiration date is in 26h but we send the second reminder only after 24h" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(
          AppId,
          Phase1TestProfile(expirationDate = new DateTime().plusHours(30), tests = List(phase1Test))).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe None
      }

      "the test is expired" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_EXPIRED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe None
      }

      "the test is completed" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_COMPLETED" -> DateTime.now()
        )), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe None
      }

      "we already sent a second reminder" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          s"progress-status.$PHASE1_TESTS_SECOND_REMINDER" -> true
        )), AppId).futureValue
        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe None
      }
    }
  }

  "remove test group" should {
    val date = DateTime.now().plusHours(22)
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))

    "remove the test group" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup("appId", testProfile).futureValue

      phase1TestRepo.getTestGroup("appId").futureValue mustBe defined

      phase1TestRepo.removeTestGroup("appId").futureValue

      phase1TestRepo.getTestGroup("appId").futureValue mustNot be(defined)
    }
  }

  "Progress status" should {
    "update progress status to PHASE1_TESTS_STARTED" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue

      phase1TestRepo.updateProgressStatus("appId", PHASE1_TESTS_STARTED).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1ProgressResponse.phase1TestsStarted mustBe true

      val appStatusDetails = helperRepo.findStatus(app.applicationId).futureValue
      appStatusDetails.status mustBe ApplicationStatus.PHASE1_TESTS.toString
    }

    "update progress status should not update if the Application Status is different from that which is being set" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE2_TESTS).futureValue

      val result = phase1TestRepo.updateProgressStatus("appId", PHASE1_TESTS_STARTED).futureValue
      result mustBe unit

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1ProgressResponse.phase1TestsStarted mustBe false
      app.applicationStatus mustBe ApplicationStatus.PHASE2_TESTS.toString
    }

    "reset progress statuses" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = List(ProgressStatuses.PHASE1_TESTS_INVITED -> true,
          ProgressStatuses.PHASE1_TESTS_COMPLETED -> true)).futureValue

      phase1TestRepo.resetTestProfileProgresses("appId", List(
        ProgressStatuses.PHASE1_TESTS_INVITED,
        ProgressStatuses.PHASE1_TESTS_COMPLETED)
      ).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1ProgressResponse.phase1TestsInvited mustBe false
      app.progressResponse.phase1ProgressResponse.phase1TestsCompleted mustBe false
    }

    "reset progress statuses when phase1 tests are failed" in {
      import Phase1EvaluationMongoRepositorySpec._

      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS_FAILED,
        additionalProgressStatuses = List(ProgressStatuses.PHASE1_TESTS_INVITED -> true,
          ProgressStatuses.PHASE1_TESTS_COMPLETED -> true,
          ProgressStatuses.PHASE1_TESTS_FAILED -> true)).futureValue
      phase1TestRepo.insertOrUpdateTestGroup("appId", Phase1TestProfile(now, phase1TestsWithResult)).futureValue

      val resultToSave = List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)

      phase1EvaluationRepo.savePassmarkEvaluation("appId", evaluation, Some(ProgressStatuses.PHASE1_TESTS_FAILED)).futureValue

      phase1TestRepo.resetTestProfileProgresses("appId", List(
        ProgressStatuses.PHASE1_TESTS_INVITED,
        ProgressStatuses.PHASE1_TESTS_COMPLETED,
        ProgressStatuses.PHASE1_TESTS_FAILED)
      ).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1ProgressResponse.phase1TestsInvited mustBe false
      app.progressResponse.phase1ProgressResponse.phase1TestsCompleted mustBe false
      app.progressResponse.phase1ProgressResponse.phase1TestsFailed mustBe false
      ApplicationStatus.withName(app.applicationStatus) mustBe ApplicationStatus.PHASE1_TESTS

      an[PassMarkEvaluationNotFound] must be thrownBy Await.result(phase1EvaluationRepo.getPassMarkEvaluation("appId"), timeout)
    }

    "update cubiks test" should {
      "add the start time for a cubiks test" in {
        insertApplication("appId", "userId")
        phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue

        val startedDateTime = DateTime.now()
        phase1TestRepo.updateTestStartTime(TestProfile.tests.head.cubiksUserId, startedDateTime).futureValue

        val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue.get
        val cubiksTest = phase1TestProfile.tests.head

        cubiksTest.startedDateTime mustBe Some(new DateTime(startedDateTime.getMillis, DateTimeZone.UTC))
      }

      "add the completion time for a cubiks test if the test group has not expired" in {
        val now = DateTime.now(DateTimeZone.UTC)
        val input = Phase1TestProfile(expirationDate = now.plusDays(5),
          tests = List(CubiksTest(scheduleId = 1,
            usedForResults = true,
            token = "token",
            cubiksUserId = 111,
            testUrl = "testUrl",
            invitationDate = now,
            participantScheduleId = 222
          ))
        )

        createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE1_TESTS", phase1TestProfile = Some(input))
          .futureValue

        phase1TestRepo.updateTestCompletionTime(111, now).futureValue
        val result = phase1TestRepo.getTestProfileByCubiksId(111).futureValue
        result.testGroup.tests.head.completedDateTime mustBe Some(now)
      }

      "mark the cubiks test as inactive" in {
        insertApplication("appId", "userId")
        phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue

        phase1TestRepo.markTestAsInactive(TestProfile.tests.head.cubiksUserId).futureValue
        val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue.get

        val cubiksTest = phase1TestProfile.tests.head
        cubiksTest.usedForResults mustBe false
      }

      "mark the cubiks test as inactive and insert new tests" in {
        insertApplication("appId", "userId")
        phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue

        phase1TestRepo.markTestAsInactive(TestProfile.tests.head.cubiksUserId).futureValue

        val newTestProfile = TestProfile.copy(tests = List(phase1Test.copy(cubiksUserId = 234)))

        phase1TestRepo.insertCubiksTests("appId", newTestProfile).futureValue
        val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue.get

        phase1TestProfile.tests.size mustBe 2
        phase1TestProfile.activeTests.size mustBe 1
      }

      "update test results ready" in {
        insertApplication("appId", "userId")
        phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue

        val resultsReady = CubiksTestResultReady(
          reportId = Some(1),
          reportStatus = "Ready",
          reportLinkURL = Some("link")
        )

        phase1TestRepo.updateTestReportReady(TestProfile.tests.head.cubiksUserId, resultsReady).futureValue

        val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue.get
        val cubiksTest = phase1TestProfile.tests.head

        cubiksTest.resultsReadyToDownload mustBe true
        cubiksTest.reportId mustBe resultsReady.reportId
        cubiksTest.reportStatus mustBe Some(resultsReady.reportStatus)
        cubiksTest.reportLinkURL mustBe resultsReady.reportLinkURL
      }
    }
  }
}
