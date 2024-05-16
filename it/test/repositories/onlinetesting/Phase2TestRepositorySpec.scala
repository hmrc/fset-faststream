/*
 * Copyright 2024 HM Revenue & Customs
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

import model.ProgressStatuses._
import model.persisted._
import model.{ApplicationStatus, Phase2FirstReminder, Phase2SecondReminder, ProgressStatuses}
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.offsetDateTimeToBson
import testkit.MongoRepositorySpec

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneId}
import java.util.UUID

class Phase2TestRepositorySpec extends MongoRepositorySpec with ApplicationDataFixture {

  implicit val Now = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS)
  val DatePlus7Days = Now.plusDays(7)
  val Token = UUID.randomUUID.toString

  val phase2Test = model.Phase2TestExamples.fifthPsiTest.copy(testResult = None)

  val TestProfile = Phase2TestGroup(expirationDate = DatePlus7Days, tests = List(phase2Test))
  val testProfileWithAppId = Phase2TestGroupWithAppId(
    "appId",
    TestProfile.copy(tests = List(
      phase2Test.copy(usedForResults = true, resultsReadyToDownload = true),
      phase2Test.copy(usedForResults = true, resultsReadyToDownload = true))
    )
  )

  "Get test group" must {
    "return NONE if there is no test got the specific user id" in {
      val result = phase2TestRepo.getTestGroupByUserId("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      val userId = "userId"
      insertApplication("appId", userId)
      phase2TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result: Option[Phase2TestGroup] = phase2TestRepo.getTestGroupByUserId(userId).futureValue
      result mustBe Some(TestProfile)
    }
  }

  "Get online test" must {
    "return None if there is no test for the specific user id" in {
      val result = phase2TestRepo.getTestGroup("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      insertApplication("appId", "userId")
      phase2TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase2TestRepo.getTestGroup("appId").futureValue
      result mustBe Some(TestProfile)
    }
  }

  "Next application ready for online testing" must {
    "exclude applications with SDIP or EDIP application routes" in {
      createApplicationWithAllFields("userId0", "appId0", "testAccountId1", "frameworkId", "PHASE1_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)), applicationRoute = "Sdip").futureValue
      createApplicationWithAllFields("userId1", "appId1", "testAccountId2", "frameworkId", "PHASE1_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)), applicationRoute = "Edip").futureValue
      createApplicationWithAllFields("userId2", "appId2", "testAccountId3", "frameworkId", "PHASE1_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true))).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId2"
      results.head.userId mustBe "userId2"
    }

    "return application when does not need adjustments and is no gis and its status is PHASE1_TESTS_PASSED" in {
      createApplicationWithAllFields("userId0", "appId0", "testAccountId", "frameworkId", "PHASE1_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)), typeOfEtrayOnlineAdjustments = Nil
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId0"
      results.head.userId mustBe "userId0"
    }

    "return application when it is gis and adjustments have been confirmed (etray time extension) and its status is PHASE1_TESTS_PASSED" in {
      createApplicationWithAllFields("userId3", "appId3", "testAccountId", "frameworkId", "PHASE1_TESTS_PASSED", adjustmentsConfirmed = true,
        isGis = true, additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)), typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId3"
      results.head.userId mustBe "userId3"
    }

    "return application when needs online adjustments, adjustments have been confirmed and its status is PHASE1_TESTS_PASSED" +
      " and adjustment is etray time extension" in {
      createApplicationWithAllFields("userId4", "appId4", "testAccountId4", "frameworkId", "PHASE1_TESTS_PASSED",
        adjustmentsConfirmed = true, timeExtensionAdjustments = true, isGis = true,
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)), typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId4"
      results.head.userId mustBe "userId4"
    }

    "return application which needs adjustments at venue, adjustments have been confirmed and its status is PHASE1_TESTS_PASSED" +
      " but no specific adjustment has been applied" in {
      createApplicationWithAllFields("userId5", "appId5", "testAccountId5", "frameworkId", "PHASE1_TESTS_PASSED",
        needsSupportAtVenue = true, adjustmentsConfirmed = true, isGis = true,
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true))
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId5"
      results.head.userId mustBe "userId5"
    }

    "do not return application when application status is not PHASE1_TESTS_PASSED and no adjustments and no gis" in {
      createApplicationWithAllFields("userId6", "appId6", "testAccountId6", "frameworkId", "SUBMITTED",
        additionalProgressStatuses = List((SUBMITTED, true)), typeOfEtrayOnlineAdjustments = Nil
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue
      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is gis but there is no need for adjustments" +
      "and adjustments have not been confirmed" in {
      createApplicationWithAllFields("userId6", "appId6", "testAccountId6", "frameworkId", "SUBMITTED", isGis = true,
        additionalProgressStatuses = List((SUBMITTED, true)), typeOfEtrayOnlineAdjustments = Nil
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue
      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is not gis but there is need for online adjustments" +
      "(e-tray time extension) and adjustments have not been confirmed" in {
      createApplicationWithAllFields("userId7", "appId7", "testAccountId7", "frameworkId", "SUBMITTED",
        timeExtensionAdjustments = true, additionalProgressStatuses = List((SUBMITTED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue
      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is no gis but there is need for adjustments at venue" +
      "and adjustments have not been confirmed" in {
      createApplicationWithAllFields("userId7", "appId7", "testAccountId7", "frameworkId", "SUBMITTED", needsSupportAtVenue = true,
        timeExtensionAdjustments = true, additionalProgressStatuses = List((SUBMITTED, true))
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue
      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is no gis but there is need for adjustments at venue" +
      "and adjustments have been confirmed but adjustments is etray invigilated" in {
      createApplicationWithAllFields("userId8", "appId8", "testAccountId8", "frameworkId", "SUBMITTED", needsSupportAtVenue = true,
        timeExtensionAdjustments = true, additionalProgressStatuses = List((ProgressStatuses.SUBMITTED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayInvigilated")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue
      results.length mustBe 0
    }

    // Before the 2023/24 campaign changes, candidates with unconfirmed adjustments were not invited to P2
    // This has now changed and these candidates are invited to P2. The only adjustment type now is a fsac
    // adjustment, whereas previously there were also online test adjustments. The fsac adjustment no
    // longer causes the candidate to be held at PHASE1_TESTS_PASSED
    "include applications that need adjustments and have not been confirmed" in {
      // This candidate has adjustments, which have not yet been confirmed and should be invited to P2
      createApplicationWithAllFields(
        "userId1", "appId1", "testAccountId1", "frameworkId",
        "PHASE1_TESTS_PASSED", needsSupportAtVenue = true,
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true))
      ).futureValue

      // This candidate has no adjustments and should be invited to P2
      createApplicationWithAllFields("userId2", "appId2", "testAccountId2", "frameworkId", "PHASE1_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true))
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(2).futureValue

      results.length mustBe 2
      results.map(_.applicationId) must contain theSameElementsAs Seq("appId1", "appId2")
      results.map(_.userId) must contain theSameElementsAs Seq("userId1", "userId2")
    }

    "return more than one candidate for batch processing" ignore {
      pending
    }

    "Not return candidates whose phase 1 tests have expired" in {
      createApplicationWithAllFields("userId1", "appId1", "testAccountId", "frameworkId", "PHASE1_TESTS_PASSED",
        additionalProgressStatuses = List(PHASE1_TESTS_EXPIRED -> true)
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue
      results.isEmpty mustBe true
    }
  }

  "Insert a phase 2 test" must {
    "correctly insert a test" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE1_TESTS_PASSED").futureValue

      val input = Phase2TestGroup(expirationDate = Now, tests = List(model.Phase2TestExamples.fifthPsiTest))

      phase2TestRepo.insertOrUpdateTestGroup("appId", input).futureValue

      val result = phase2TestRepo.getTestGroup("appId").futureValue
      result.isDefined mustBe true
      result.get.expirationDate mustBe input.expirationDate
      result.get.tests mustBe input.tests
    }
  }

  "Updating completion time" must {
    "update test completion time" in {
      val input = Phase2TestGroup(expirationDate = Now.plusDays(5), tests = List(model.Phase2TestExamples.fifthPsiTest))

      createApplicationWithAllFields("userId", "appId", "testAccountId","frameworkId",
        "PHASE2_TESTS", phase2TestGroup = Some(input)).futureValue

      phase2TestRepo.updateTestCompletionTime("orderId5", Now).futureValue
      val result = phase2TestRepo.getTestProfileByOrderId("orderId5").futureValue
      result.testGroup.tests.head.completedDateTime mustBe Some(Now)
    }
  }

  "Insert test result" should {
    "correctly update a test group with results" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId","frameworkId", "PHASE2_TESTS",
        additionalProgressStatuses = List((PHASE2_TESTS_RESULTS_READY, true)), phase2TestGroup = Some(testProfileWithAppId.testGroup)
      ).futureValue

      val testResult = PsiTestResult(tScore = 55.33d, rawScore = 65.32d, testReportUrl = None)

      phase2TestRepo.insertTestResult("appId", testProfileWithAppId.testGroup.tests.head, testResult).futureValue

      val phase2TestGroup = phase2TestRepo.getTestGroup("appId").futureValue
      phase2TestGroup.isDefined mustBe true
      phase2TestGroup.foreach { profile =>
        profile.tests.head.testResult.isDefined mustBe true
        profile.tests.head.testResult.get mustBe testResult
      }

      val status = helperRepo.findProgress("appId").futureValue
      status.phase2ProgressResponse.phase2TestsResultsReceived mustBe false
    }
  }

  "nextTestForReminder" should {
    "return one result" when {
      "there is an application in PHASE2_TESTS and is about to expire in the next 72 hours" in {
        val date = Now.plusHours(Phase2FirstReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testGroup = Phase2TestGroup(expirationDate = date, tests = List(phase2Test))
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase2TestRepo.insertOrUpdateTestGroup(AppId, testGroup).futureValue
        val notification = phase2TestRepo.nextTestForReminder(Phase2FirstReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.toInstant.toEpochMilli mustBe date.toInstant.toEpochMilli
        // Because we are far away from the 24h reminder's window
        phase2TestRepo.nextTestForReminder(Phase2SecondReminder).futureValue mustBe None
      }

      "there is an application in PHASE2_TESTS and is about to expire in the next 24 hours" in {
        val date = Now.plusHours(Phase2SecondReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testGroup = Phase2TestGroup(expirationDate = date, tests = List(phase2Test))
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase2TestRepo.insertOrUpdateTestGroup(AppId, testGroup).futureValue
        val notification = phase2TestRepo.nextTestForReminder(Phase2SecondReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.toInstant.toEpochMilli mustBe date.toInstant.toEpochMilli
      }
    }

    "return no results" when {
      val date = Now.plusHours(22)
      val testProfile = Phase2TestGroup(expirationDate = date, tests = List(phase2Test))

      "there are no applications in PHASE2_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase2TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document("applicationStatus" -> ApplicationStatus.IN_PROGRESS.toBson)), AppId).futureValue
        phase2TestRepo.nextTestForReminder(Phase2FirstReminder).futureValue mustBe None
      }

      "the expiration date is in 26h but we send the second reminder only after 24h" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase2TestRepo.insertOrUpdateTestGroup(
          AppId,
          Phase2TestGroup(expirationDate = Now.plusHours(30), tests = List(phase2Test))).futureValue
        phase2TestRepo.nextTestForReminder(Phase2SecondReminder).futureValue mustBe None
      }

      "the test is expired" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase2TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE2_TESTS_EXPIRED.applicationStatus.toBson,
          s"progress-status.$PHASE2_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE2_TESTS_EXPIRED" -> offsetDateTimeToBson(Now)
        )), AppId).futureValue
        phase2TestRepo.nextTestForReminder(Phase2SecondReminder).futureValue mustBe None
      }

      "the test is completed" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase2TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE2_TESTS_COMPLETED.applicationStatus.toBson,
          s"progress-status.$PHASE2_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE2_TESTS_COMPLETED" -> offsetDateTimeToBson(Now)
        )), AppId).futureValue
        phase2TestRepo.nextTestForReminder(Phase2SecondReminder).futureValue mustBe None
      }

      "we already sent a second reminder" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase2TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document(
          s"progress-status.$PHASE2_TESTS_SECOND_REMINDER" -> true
        )), AppId).futureValue
        phase2TestRepo.nextTestForReminder(Phase2SecondReminder).futureValue mustBe None
      }
    }
  }
}
