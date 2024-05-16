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

import connectors.launchpadgateway.exchangeobjects.in.{SetupProcessCallbackRequest, ViewPracticeQuestionCallbackRequest}
import model.ProgressStatuses._
import model._
import model.command.ApplicationForSkippingPhase3
import model.persisted.phase3tests.{LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup}
import model.persisted.{PassmarkEvaluation, Phase2TestGroup, SchemeEvaluationResult}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories.offsetDateTimeToBson
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, OffsetDateTime, ZoneId}
import java.util.UUID

class Phase3TestRepositorySpec extends MongoRepositorySpec with ApplicationDataFixture {

  val Now = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS)
  val DatePlus7Days = Now.plusDays(7)
  val Token = newToken

  def newToken = UUID.randomUUID.toString

  val phase3Test = LaunchpadTest(
    interviewId = 123,
    usedForResults = true,
    token = Token,
    testUrl = "test.com",
    invitationDate = Now,
    candidateId = "CND_123456",
    customCandidateId = "FSCND_123",
    startedDateTime = None,
    completedDateTime = None,
    callbacks = LaunchpadTestCallbacks()
  )

  val callbackToAppend = SetupProcessCallbackRequest(
    OffsetDateTime.now,
    UUID.randomUUID().toString,
    "FSCND-1234",
    12345,
    None,
    "FSINV-456",
    LocalDate.parse("2016-11-09")
  )

  val TestGroup = Phase3TestGroup(expirationDate = DatePlus7Days, tests = List(phase3Test))

  def multiTestGroup(interviewOffset: Int = 0): Phase3TestGroup = TestGroup.copy(
    tests = List(
      phase3Test.copy(
        interviewId = interviewOffset + 123,
        token = newToken
      ),
      phase3Test.copy(
        usedForResults = false,
        interviewId = interviewOffset + 456,
        token = newToken
      ),
      phase3Test.copy(
        usedForResults = false,
        interviewId = interviewOffset + 789,
        token = newToken
      )
    )
  )

  val progressStatusesToResetInPhase3 = List(PHASE3_TESTS_EXPIRED, PHASE3_TESTS_STARTED, PHASE3_TESTS_FIRST_REMINDER,
    PHASE3_TESTS_SECOND_REMINDER, PHASE3_TESTS_COMPLETED, PHASE3_TESTS_RESULTS_RECEIVED, PHASE3_TESTS_FAILED,
    PHASE3_TESTS_FAILED_NOTIFIED, PHASE3_TESTS_PASSED, PHASE3_TESTS_PASSED_WITH_AMBER)

  def fetchPhase3Evaluation(applicationId: String) = {
    val query = Document(
      "applicationId" -> applicationId,
      "testGroups.PHASE3.evaluation.result" ->  Document("$exists" -> true)
    )
    val projection = Projections.include(s"testGroups.PHASE3.evaluation.result", "applicationId")

    applicationCollection.find[Document](query).projection(projection).headOption() map { docList =>
      docList.flatMap { doc =>
        doc.get("testGroups")
          .map(_.asDocument().get("PHASE3"))
          .map(_.asDocument().get("evaluation"))
          .map(_.asDocument().get("result")).map { bson =>
          val evaluation = Codecs.fromBson[Seq[SchemeEvaluationResult]](bson)
          evaluation
        }
      }.getOrElse(Nil)
    }
  }

  "Get online test" should {
    "return None if there is no test for the specific user id" in {
      val result = phase3TestRepo.getTestGroup("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      insertApplication("appId", "userId")
      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue
      val result = phase3TestRepo.getTestGroup("appId").futureValue
      result mustBe Some(TestGroup)
    }
  }

  "Skip phase3 test" should {
    "fetch an application who can skip phase3 test if there is at least a single Green scheme and no Amber schemes at P2 " in {
      val p2 = Phase2TestGroup(
        expirationDate = Now, tests = List(model.Phase2TestExamples.fifthPsiTest(Now)),
        evaluation = Some(
          PassmarkEvaluation(
            passmarkVersion = "previousVersion",
            previousPhasePassMarkVersion = None,
            result = List(
              SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
              SchemeEvaluationResult(Commercial, EvaluationResults.Red.toString)
            ),
            resultVersion = "version1",
            previousPhaseResultVersion = None
          )
        )
      )

      createApplicationWithAllFields("userId", "appId","testAccountId", "frameworkId", "PHASE2_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE2_TESTS_PASSED, true)),
        phase2TestGroup = Some(p2),
        currentSchemeStatus = Some(Seq(
          SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
          SchemeEvaluationResult(Commercial, EvaluationResults.Red.toString)
        ))
      ).futureValue

      val results = phase3TestRepo.nextApplicationsReadyToSkipPhase3(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId"
    }

    "do not fetch an application who can skip phase3 test if there is a single Amber scheme at P2 " in {
      val p2 = Phase2TestGroup(
        expirationDate = Now, tests = List(model.Phase2TestExamples.fifthPsiTest(Now)),
        evaluation = Some(
          PassmarkEvaluation(
            passmarkVersion = "previousVersion",
            previousPhasePassMarkVersion = None,
            result = List(
              SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
              SchemeEvaluationResult(Commercial, EvaluationResults.Amber.toString)
            ),
            resultVersion = "version1",
            previousPhaseResultVersion = None
          )
        )
      )

      createApplicationWithAllFields("userId", "appId","testAccountId", "frameworkId", "PHASE2_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE2_TESTS_PASSED, true)),
        phase2TestGroup = Some(p2),
        currentSchemeStatus = Some(Seq(
          SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
          SchemeEvaluationResult(Commercial, EvaluationResults.Amber.toString)
        ))
      ).futureValue

      val results = phase3TestRepo.nextApplicationsReadyToSkipPhase3(1).futureValue

      results.length mustBe 0
    }

    "process an application so they skip to the end of phase 3" in {
      val p2 = Phase2TestGroup(
        expirationDate = Now, tests = List(model.Phase2TestExamples.fifthPsiTest(Now)),
        evaluation = Some(
          PassmarkEvaluation(
            passmarkVersion = "previousVersion",
            previousPhasePassMarkVersion = None,
            result = List(
              SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
              SchemeEvaluationResult(Commercial, EvaluationResults.Red.toString)
            ),
            resultVersion = "version1",
            previousPhaseResultVersion = None
          )
        )
      )

      createApplicationWithAllFields("userId", "appId","testAccountId", "frameworkId", "PHASE2_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE2_TESTS_PASSED, true)),
        phase2TestGroup = Some(p2),
        currentSchemeStatus = Some(Seq(
          SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
          SchemeEvaluationResult(Commercial, EvaluationResults.Red.toString)
        ))
      ).futureValue

      val application = ApplicationForSkippingPhase3("appId",
        currentSchemeStatus = Seq(
          SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
          SchemeEvaluationResult(Commercial, EvaluationResults.Red.toString)
        )
      )
      phase3TestRepo.skipPhase3(application).futureValue
      val status = helperRepo.findStatus(application.applicationId).futureValue
      status.status mustBe ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED.toString
      fetchPhase3Evaluation(application.applicationId).futureValue mustBe
        Seq(
          SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString),
          SchemeEvaluationResult(Commercial, EvaluationResults.Red.toString)
        )
    }
  }

  "Append callbacks" should {
    "create a one callback array when the key is not set" in new CallbackFixture {
      insertApplication("appId", "userId")
      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue

      val token = TestGroup.tests.head.token

      phase3TestRepo.appendCallback(token, SetupProcessCallbackRequest.key, callbackToAppend).futureValue

      val testWithCallback = phase3TestRepo.getTestGroup("appId").futureValue.get

      val test = testWithCallback.tests.find(t => t.token == token).get

      test.callbacks.setupProcess.length mustBe 1
      inside(test.callbacks.setupProcess.head) { case SetupProcessCallbackRequest(received, candidateId, customCandidateId,
      interviewId, customInterviewId, customInviteId, deadline) =>
        received.toInstant.toEpochMilli mustBe callbackToAppend.received.toInstant.toEpochMilli
        candidateId mustBe callbackToAppend.candidateId
        customCandidateId mustBe callbackToAppend.customCandidateId
        interviewId mustBe callbackToAppend.interviewId
        customInterviewId mustBe callbackToAppend.customInterviewId
        customInviteId mustBe callbackToAppend.customInviteId
        deadline mustBe callbackToAppend.deadline
      }
    }

    "Append a callback when at least one is already set" in new CallbackFixture {
      insertApplication("appId", "userId")
      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue

      val token = TestGroup.tests.head.token

      phase3TestRepo.appendCallback(token, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token, ViewPracticeQuestionCallbackRequest.key, callbackToAppend).futureValue

      val testWithCallback = phase3TestRepo.getTestGroup("appId").futureValue.get

      val test = testWithCallback.tests.find(t => t.token == token).get
      assertCallbacks(test, 2, 1)
    }

    "Append callbacks to multiple tests in the same application" in new CallbackFixture {
      insertApplication("appId", "userId")
      val testGroup = multiTestGroup()
      phase3TestRepo.insertOrUpdateTestGroup("appId", testGroup).futureValue

      val token1 = testGroup.tests(0).token
      val token2 = testGroup.tests(1).token
      val token3 = testGroup.tests(2).token

      phase3TestRepo.appendCallback(token1, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token1, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token2, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token3, ViewPracticeQuestionCallbackRequest.key, callbackToAppend).futureValue

      val testWithCallback = phase3TestRepo.getTestGroup("appId").futureValue.get

      val test1 = testWithCallback.tests.find(t => t.token == token1).get
      assertCallbacks(test1, 2)

      val test2 = testWithCallback.tests.find(t => t.token == token2).get
      assertCallbacks(test2, 1)

      val test3 = testWithCallback.tests.find(t => t.token == token3).get
      assertCallbacks(test3, 0, 1)
    }

    "Append callbacks to multiple tests in multiple applications" in new CallbackFixture {
      insertApplication("appId", "userId")
      insertApplication("appId2", "userId2")
      insertApplication("appId3", "userId3")
      val testGroup1 = multiTestGroup(1)
      val testGroup2 = multiTestGroup(2)
      val testGroup3 = multiTestGroup(3)
      phase3TestRepo.insertOrUpdateTestGroup("appId", testGroup1).futureValue
      phase3TestRepo.insertOrUpdateTestGroup("appId2", testGroup2).futureValue
      phase3TestRepo.insertOrUpdateTestGroup("appId3", testGroup3).futureValue

      val token1 = testGroup1.tests.head.token
      val token2 = testGroup2.tests.head.token
      val token3 = testGroup3.tests.head.token

      phase3TestRepo.appendCallback(token1, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token1, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token2, SetupProcessCallbackRequest.key, callbackToAppend).futureValue
      phase3TestRepo.appendCallback(token3, ViewPracticeQuestionCallbackRequest.key, callbackToAppend).futureValue

      val testWithCallback1 = phase3TestRepo.getTestGroup("appId").futureValue.get
      val testWithCallback2 = phase3TestRepo.getTestGroup("appId2").futureValue.get
      val testWithCallback3 = phase3TestRepo.getTestGroup("appId3").futureValue.get

      val test1 = testWithCallback1.tests.find(t => t.token == token1).get
      assertCallbacks(test1, 2)

      val test2 = testWithCallback2.tests.find(t => t.token == token2).get
      assertCallbacks(test2, 1)

      val test3 = testWithCallback3.tests.find(t => t.token == token3).get
      assertCallbacks(test3, 0, 1)
    }
  }

  "Next application ready for online testing" should {
    "exclude applications with SDIP or EDIP application routes" in {
      createApplicationWithAllFields("userId0", "appId0","testAccountId", "frameworkId", "PHASE2_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE2_TESTS_PASSED, true)), applicationRoute = "Sdip").futureValue
      createApplicationWithAllFields("userId1", "appId1","testAccountId", "frameworkId", "PHASE2_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE2_TESTS_PASSED, true)), applicationRoute = "Edip").futureValue
      createApplicationWithAllFields("userId2", "appId2", "testAccountId","frameworkId", "PHASE2_TESTS_PASSED",
        additionalProgressStatuses = List((PHASE2_TESTS_PASSED, true))).futureValue

      val results = phase3TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId2"
      results.head.userId mustBe "userId2"
    }

    "return one application if there is only one" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE2_TESTS_PASSED",
        additionalProgressStatuses = List((model.ProgressStatuses.PHASE2_TESTS_PASSED, true))
      ).futureValue

      val result = phase3TestRepo.nextApplicationsReadyForOnlineTesting(1).futureValue

      result.size mustBe 1
      result.head.applicationId mustBe "appId"
      result.head.userId mustBe "userId"
    }
  }

  "Insert a phase 3 test" should {
    "correctly insert a test" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE2_TESTS_PASSED").futureValue

      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue

      val result = phase3TestRepo.getTestGroup("appId").futureValue
      result.isDefined mustBe true
      result.get.expirationDate mustBe TestGroup.expirationDate
      result.get.tests mustBe TestGroup.tests
    }
  }

  "Remove a phase 3 test" should {
    "remove test when requested" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", "frameworkId", "PHASE3",
        additionalProgressStatuses = List((ProgressStatuses.PHASE3_TESTS_INVITED, true))
      ).futureValue

      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue
      val result1 = phase3TestRepo.getTestGroup("appId").futureValue
      result1.isDefined mustBe true

      phase3TestRepo.removeTestGroup("appId").futureValue

      val result2 = phase3TestRepo.getTestGroup("appId").futureValue
      result2.isDefined mustBe false
    }
  }

  "nextTestForReminder" should {
    "return one result" when {
      "there is an application in PHASE3_TESTS and is about to expire in the next 72 hours" in {
        val date = Now.plusHours(Phase3FirstReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testGroup = Phase3TestGroup(expirationDate = date, tests = List(phase3Test))
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testGroup).futureValue
        val notification = phase3TestRepo.nextTestForReminder(Phase3FirstReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.toInstant.toEpochMilli mustBe date.toInstant.toEpochMilli
        // Because we are far away from the 24h reminder's window
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "there is an application in PHASE3_TESTS and is about to expire in the next 24 hours" in {
        val date = Now.plusHours(Phase3SecondReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testGroup = Phase3TestGroup(expirationDate = date, tests = List(phase3Test))
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testGroup).futureValue
        val notification = phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.toInstant.toEpochMilli mustBe date.toInstant.toEpochMilli
      }
    }

    "return no results" when {
      val date = Now.plusHours(22)
      val testProfile = Phase3TestGroup(expirationDate = date, tests = List(phase3Test))

      "there are no applications in PHASE3_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document("applicationStatus" -> ApplicationStatus.IN_PROGRESS.toBson)), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3FirstReminder).futureValue mustBe None
      }

      "the expiration date is in 26h but we send the second reminder only after 24h" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(
          AppId,
          Phase3TestGroup(expirationDate = Now.plusHours(30), tests = List(phase3Test))).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "the test is expired" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE3_TESTS_EXPIRED.applicationStatus.toBson,
          s"progress-status.$PHASE3_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE3_TESTS_EXPIRED" -> offsetDateTimeToBson(Now)
        )), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "the test is completed" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document(
          "applicationStatus" -> PHASE3_TESTS_COMPLETED.applicationStatus.toBson,
          s"progress-status.$PHASE3_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE3_TESTS_COMPLETED" -> offsetDateTimeToBson(Now)
        )), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "we already sent a second reminder" in {
        createApplicationWithAllFields(UserId, AppId, TestAccountId,"frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(Document("$set" -> Document(
          s"progress-status.$PHASE3_TESTS_SECOND_REMINDER" -> true
        )), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }
    }
  }

  "reset progress statuses" should {
    "reset PHASE3_TESTS status for an application at PHASE3_TESTS_RESULTS_RECEIVED" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE3_TESTS,
        additionalProgressStatuses = List(
          ProgressStatuses.PHASE3_TESTS_INVITED -> true,
          ProgressStatuses.PHASE3_TESTS_STARTED -> true,
          ProgressStatuses.PHASE3_TESTS_COMPLETED -> true,
          ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED -> true
        )).futureValue

      phase3TestRepo.resetTestProfileProgresses("appId", progressStatusesToResetInPhase3).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      assertResetPhase3ApplicationAndProgressStatus(app)
    }

    "reset PHASE3_TESTS_PASSED status" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE3_TESTS_PASSED,
        additionalProgressStatuses = List(
          ProgressStatuses.PHASE3_TESTS_INVITED -> true,
          ProgressStatuses.PHASE3_TESTS_STARTED -> true,
          ProgressStatuses.PHASE3_TESTS_COMPLETED -> true,
          ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED -> true,
          ProgressStatuses.PHASE3_TESTS_PASSED -> true
        )).futureValue

      phase3TestRepo.resetTestProfileProgresses("appId", progressStatusesToResetInPhase3).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      assertResetPhase3ApplicationAndProgressStatus(app)
    }

    "reset PHASE3_TESTS_FAILED status at PHASE3_TESTS_FAILED_NOTIFIED" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE3_TESTS_FAILED,
        additionalProgressStatuses = List(
          ProgressStatuses.PHASE3_TESTS_INVITED -> true,
          ProgressStatuses.PHASE3_TESTS_STARTED -> true,
          ProgressStatuses.PHASE3_TESTS_COMPLETED -> true,
          ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED -> true,
          ProgressStatuses.PHASE3_TESTS_FAILED -> true,
          ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED -> true
        )).futureValue

      phase3TestRepo.resetTestProfileProgresses("appId", progressStatusesToResetInPhase3).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      assertResetPhase3ApplicationAndProgressStatus(app)
    }

    "reset PHASE3_TESTS_PASSED_WITH_AMBER status" in {
      createApplicationWithAllFields("userId", "appId", "testAccountId", appStatus = ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER,
        additionalProgressStatuses = List(
          ProgressStatuses.PHASE3_TESTS_INVITED -> true,
          ProgressStatuses.PHASE3_TESTS_STARTED -> true,
          ProgressStatuses.PHASE3_TESTS_COMPLETED -> true,
          ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED -> true,
          ProgressStatuses.PHASE3_TESTS_PASSED_WITH_AMBER -> true
        )).futureValue

      phase3TestRepo.resetTestProfileProgresses("appId", progressStatusesToResetInPhase3).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      assertResetPhase3ApplicationAndProgressStatus(app)
    }
  }

  trait CallbackFixture {
    def assertCallbacks(test: LaunchpadTest, setupProcesses: Int = 0, viewPracticeQuestions: Int = 0,
                        finalCallbacks: Int = 0, finished: Int = 0, viewBrandedVideo: Int = 0, questions: Int = 0) = {
      test.callbacks.setupProcess.length mustBe setupProcesses
      test.callbacks.viewPracticeQuestion.length mustBe viewPracticeQuestions
      test.callbacks.finalCallback.length mustBe finalCallbacks
      test.callbacks.finished.length mustBe finished
      test.callbacks.viewBrandedVideo.length mustBe viewBrandedVideo
      test.callbacks.question.length mustBe questions
    }
  }

  private def assertResetPhase3ApplicationAndProgressStatus(app: ApplicationResponse) = {
    app.applicationStatus mustBe ApplicationStatus.PHASE3_TESTS.toString
    app.progressResponse.phase3ProgressResponse.phase3TestsInvited mustBe true // reset always imply re invite
    app.progressResponse.phase3ProgressResponse.phase3TestsStarted mustBe false
    app.progressResponse.phase3ProgressResponse.phase3TestsCompleted mustBe false
    app.progressResponse.phase3ProgressResponse.phase3TestsResultsReceived mustBe false
    app.progressResponse.phase3ProgressResponse.phase3TestsPassed mustBe false
  }
}
