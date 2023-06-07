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

import factories.ITDateTimeFactoryMock
import model.EvaluationResults.{Amber, Green, Red}
import model.Exceptions.{CannotFindTestByOrderIdException, PassMarkEvaluationNotFound}
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{PHASE1_TESTS_COMPLETED, PHASE1_TESTS_EXPIRED, PHASE1_TESTS_STARTED, _}
import model.persisted._
import model.{ApplicationStatus, ProgressStatuses, persisted, _}
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.{CollectionNames, dateTimeToBson}
import testkit.MongoRepositorySpec

import scala.concurrent.Await

class Phase1TestRepositorySpec extends MongoRepositorySpec with ApplicationDataFixture {

  override val collectionName: String = CollectionNames.APPLICATION

  implicit val Now = DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)

  val phase1Test = model.Phase1TestExamples.firstPsiTest.copy(testResult = None)

  val TestProfile = Phase1TestProfile(expirationDate = DatePlus7Days, tests = List(phase1Test))
  val testProfileWithAppId = Phase1TestGroupWithUserIds(
    "appId",
    "userId",
    TestProfile.copy(tests = List(
      phase1Test.copy(usedForResults = true, resultsReadyToDownload = true),
      phase1Test.copy(usedForResults = true, resultsReadyToDownload = true))
    )
  )

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository(ITDateTimeFactoryMock, mongo)

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

  "Get online test by orderId" should {
    "return None if there is no test with the orderId" in {
      val result = phase1TestRepo.getTestProfileByOrderId("orderId").failed.futureValue
      result mustBe a[CannotFindTestByOrderIdException]
    }

    "return an online test for the specific orderId" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase1TestRepo.getTestProfileByOrderId("orderId1").futureValue
      result mustBe TestProfile
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
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "SUBMITTED",
        fastPassApplicable = true, fastPassReceived = true, fastPassAccepted = Some(true)
      ).futureValue

      val result = phase1TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue
      result mustBe Nil
    }

    "return no application if there is one fast pass rejected candidate" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "SUBMITTED",
        fastPassApplicable = true, fastPassReceived = true, fastPassAccepted = Some(false)
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
      val resultToSave = List(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString),
        SchemeEvaluationResult(Sdip, Green.toString)
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
      val resultToSave = List(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString),
        SchemeEvaluationResult(Sdip, Amber.toString)
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

  "Insert test result" should {
    "correctly update a test group with results" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE1_TESTS",
        additionalProgressStatuses = List((PHASE1_TESTS_RESULTS_READY, true)),
        phase1TestProfile = Some(testProfileWithAppId.testGroup)
      ).futureValue

      val testResult = persisted.PsiTestResult(tScore = 55.33d, rawScore = 65.32d, testReportUrl = None)

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
      guaranteedInterview, needsAtVenueAdjustments, preferredName, lastName, etrayAdjustments,
      videoInterviewAdjustments) =>

        applicationId mustBe "appId"
        applicationStatus mustBe ApplicationStatus.SUBMITTED.toString
        userId mustBe "userId"
        testAccountId mustBe "testAccountId"
        guaranteedInterview mustBe true
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
      guaranteedInterview, needsAtVenueAdjustments, preferredName, lastName, etrayAdjustments,
      videoInterviewAdjustments) =>

        applicationId mustBe "appId"
        applicationStatus mustBe ApplicationStatus.SUBMITTED.toString // Will be read back from mongo in upper case
        userId mustBe "userId"
        testAccountId mustBe "testAccountId"
        guaranteedInterview mustBe true
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
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        phase1TestRepo.nextExpiringApplication(phase1ExpirationEvent).futureValue mustBe Some(ExpiringOnlineTest(AppId, UserId, "Georgy"))
      }
    }

    "return no results" when {
      "there are no applications in PHASE1_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document("applicationStatus" -> ApplicationStatus.IN_PROGRESS.toBson)), AppId).futureValue
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
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue

        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus.toBson,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_EXPIRED" -> dateTimeToBson(DateTime.now())//TODO: mongo should be utc?
        )), AppId).futureValue

        phase1TestRepo.nextExpiringApplication(phase1ExpirationEvent).futureValue mustBe None
      }

      "the test is completed" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue

        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus.toBson,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_COMPLETED" -> dateTimeToBson(DateTime.now())//TODO: mongo should be utc?
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
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
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
        updateApplication(Document("$set" -> Document("applicationStatus" -> ApplicationStatus.IN_PROGRESS.toBson)), AppId).futureValue
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
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue

        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus.toBson,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_EXPIRED" -> dateTimeToBson(DateTime.now())
        )), AppId).futureValue

        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe None
      }

      "the test is completed" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue

        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus.toBson,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE1_TESTS_COMPLETED" -> dateTimeToBson(DateTime.now())
        )), AppId).futureValue

        phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe None
      }

      "we already sent a second reminder" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase1TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue

        updateApplication(Document("$set" -> Document(
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

  "remove test group evaluation" should {
    val date = DateTime.now().plusHours(1)
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    val appId = "appId"

    "remove the test group" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue

      val resultToSave = List(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString))
      val evaluation =
        PassmarkEvaluation("version1", previousPhasePassMarkVersion = None, resultToSave, "version1-res", previousPhaseResultVersion = None)

      phase1EvaluationRepo.savePassmarkEvaluation(appId, evaluation, Some(ProgressStatuses.PHASE1_TESTS_PASSED)).futureValue

      phase1TestRepo.findEvaluation(appId).futureValue mustBe Some(Seq(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)))

      phase1TestRepo.removeTestGroupEvaluation(appId).futureValue

      phase1TestRepo.findEvaluation(appId).futureValue mustBe None
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

      val resultToSave = List(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString))
      val evaluation =
        PassmarkEvaluation("version1", previousPhasePassMarkVersion = None, resultToSave, "version1-res", previousPhaseResultVersion = None)

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

      a[PassMarkEvaluationNotFound] must be thrownBy Await.result(phase1EvaluationRepo.getPassMarkEvaluation("appId"), timeout)
    }

    "throw an exception if the applicationStatus is incorrect" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE2_TESTS).futureValue
      an[IllegalArgumentException] must be thrownBy Await.result(phase1TestRepo.updateProgressStatus("appId", PHASE2_TESTS_STARTED), timeout)
    }
  }

  "update progress status only" should {
    "update progress status to PHASE1_TESTS_STARTED" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue

      phase1TestRepo.updateProgressStatusOnly("appId", PHASE1_TESTS_STARTED).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1ProgressResponse.phase1TestsStarted mustBe true

      val appStatusDetails = helperRepo.findStatus(app.applicationId).futureValue
      appStatusDetails.status mustBe ApplicationStatus.PHASE1_TESTS.toString
    }

    "throw an exception if the applicationStatus is incorrect" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE2_TESTS).futureValue
      an[IllegalArgumentException] must be thrownBy Await.result(phase1TestRepo.updateProgressStatusOnly("appId", PHASE2_TESTS_STARTED), timeout)
    }
  }

  "update psi test" should {
    "add the start time for a psi test" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue

      val startedDateTime = DateTime.now()
      phase1TestRepo.updateTestStartTime(TestProfile.tests.head.orderId, startedDateTime).futureValue

      val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue.get
      val psiTest = phase1TestProfile.tests.head

      psiTest.startedDateTime mustBe Some(new DateTime(startedDateTime.getMillis, DateTimeZone.UTC))
    }

    "add the completion time for a psi test if the test group has not expired" in {
      val now = DateTime.now(DateTimeZone.UTC)
      val input = Phase1TestProfile(expirationDate = now.plusDays(5), tests = List(phase1Test))

      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE1_TESTS", phase1TestProfile = Some(input))
        .futureValue

      phase1TestRepo.updateTestCompletionTime("orderId1", now).futureValue
      val result = phase1TestRepo.getTestGroupByOrderId("orderId1").futureValue
      result.testGroup.tests.head.completedDateTime mustBe Some(now)
    }

    "mark the psi test as inactive" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue

      phase1TestRepo.markTestAsInactive(TestProfile.tests.head.orderId).futureValue
      val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue.get

      val psiTest = phase1TestProfile.tests.head
      psiTest.usedForResults mustBe false
    }

    "mark the psi test as inactive and insert new tests" in {
      insertApplication("appId", "userId")
      phase1TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue

      phase1TestRepo.markTestAsInactive(TestProfile.tests.head.orderId).futureValue

      val newTestProfile = TestProfile.copy(tests = List(phase1Test.copy(orderId = "orderId2")))

      phase1TestRepo.insertPsiTests("appId", newTestProfile).futureValue
      val phase1TestProfile = phase1TestRepo.getTestGroup("appId").futureValue.get

      phase1TestProfile.tests.size mustBe 2
      phase1TestProfile.activeTests.size mustBe 1
    }
  }

  "getApplicationIdForOrderId" should {
    val date = DateTime.now().plusHours(1)
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    val appId = "appId"

    "return an applicationId for a valid orderId" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue
      phase1TestRepo.getApplicationIdForOrderId("orderId1").futureValue mustBe Some(appId)
    }

    "not return an applicationId for an invalid orderId" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue
      phase1TestRepo.getApplicationIdForOrderId("invalidOrderId").futureValue mustBe None
    }
  }

  "upsert test group evaluation" should {
    val date = DateTime.now().plusHours(1)
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    val appId = "appId"

    "update the evaluation" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue

      val resultToSave = List(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString))
      val evaluation =
        PassmarkEvaluation("version1", previousPhasePassMarkVersion = None, resultToSave, "version1-res", previousPhaseResultVersion = None)

      phase1EvaluationRepo.savePassmarkEvaluation(appId, evaluation, Some(ProgressStatuses.PHASE1_TESTS_PASSED)).futureValue

      phase1TestRepo.findEvaluation(appId).futureValue mustBe Some(Seq(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)))

      val newEvaluation = PassmarkEvaluation(passmarkVersion = "version2",
        previousPhasePassMarkVersion = Some("version1"),
        result = List(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString)),
        resultVersion = "version2-res",
        previousPhaseResultVersion = Some("version1-res")
      )

      phase1TestRepo.upsertTestGroupEvaluationResult(appId, newEvaluation).futureValue
      phase1TestRepo.findEvaluation(appId).futureValue mustBe Some(Seq(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString)))
    }
  }

  "getTestGroup" should {
    val date = DateTime.now().plusHours(1)
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    val appId = "appId"

    "return the test group when there is data" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue
      phase1TestRepo.getTestGroup(appId).futureValue mustBe defined
    }

    "not return the test group when there is no data" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.getTestGroup(appId).futureValue must not be defined
    }
  }

  "update test group expiry time" should {
    val date = DateTime.now().plusHours(1)
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    val appId = "appId"

    "update the expiry time" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue

      phase1TestRepo.updateGroupExpiryTime(appId, Now)

      val p1TestProfile = phase1TestRepo.getTestProfileByOrderId("orderId1").futureValue
      p1TestProfile.expirationDate mustBe Now
    }
  }

  "next test for reminder" should {
    val date = DateTime.now().plusHours(1)
    val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
    val appId = "appId"

    "fetch the application for reminder when we are 24 hours before the expiry" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue

      phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue mustBe defined
    }

    "not fetch the application for reminder when we are more than 24 hours before the expiry" in {
      createApplicationWithAllFields("userId", appId, "testAccountId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      phase1TestRepo.insertOrUpdateTestGroup(appId, testProfile).futureValue

      phase1TestRepo.updateGroupExpiryTime(appId, Now.plusHours(25))
      phase1TestRepo.nextTestForReminder(Phase1SecondReminder).futureValue must not be defined
    }
  }
}
