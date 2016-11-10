package repositories.onlinetesting

import java.util.UUID

import model.ProgressStatuses.{ PHASE3_TESTS_SECOND_REMINDER, PHASE3_TESTS_COMPLETED, PHASE3_TESTS_EXPIRED }
import model.{ ApplicationStatus, Phase3SecondReminder, Phase3FirstReminder }
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import testkit.MongoRepositorySpec

class Phase3TestRepositorySpec extends ApplicationDataFixture with MongoRepositorySpec {

  val Now =  DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)
  val Token = UUID.randomUUID.toString

  val phase3Test = LaunchpadTest(
    interviewId = 123,
    usedForResults = true,
    token = Token,
    testUrl = "test.com",
    invitationDate = Now,
    candidateId = "CND_123456",
    customCandidateId = "FSCND_123",
    startedDateTime = None,
    completedDateTime = None
  )

  val TestGroup = Phase3TestGroup(expirationDate = DatePlus7Days, tests = List(phase3Test))

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

  "Next application ready for online testing" should {
    "return one application if there is only one" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE2_TESTS_PASSED", needsSupportForOnlineAssessment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((model.ProgressStatuses.PHASE2_TESTS_PASSED, true))
      ).futureValue

      val result = phase3TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      result.size mustBe 1
      result.head.applicationId mustBe "appId"
      result.head.userId mustBe "userId"
    }
  }

  "Insert a phase 3 test" must {
    "correctly insert a test" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE2_TESTS_PASSED", needsSupportForOnlineAssessment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue

      val result = phase3TestRepo.getTestGroup("appId").futureValue
      result.isDefined mustBe true
      result.get.expirationDate mustBe TestGroup.expirationDate
      result.get.tests mustBe TestGroup.tests
    }
  }

  "nextTestForReminder" should {
    "return one result" when {
      "there is an application in PHASE3_TESTS and is about to expire in the next 72 hours" in {
        val date = DateTime.now().plusHours(Phase3FirstReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testGroup = Phase3TestGroup(expirationDate = date, tests = List(phase3Test))
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testGroup).futureValue
        val notification = phase3TestRepo.nextTestForReminder(Phase3FirstReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.getMillis mustBe date.getMillis
        // Because we are far away from the 24h reminder's window
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "there is an application in PHASE3_TESTS and is about to expire in the next 24 hours" in {
        val date = DateTime.now().plusHours(Phase3SecondReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testGroup = Phase3TestGroup(expirationDate = date, tests = List(phase3Test))
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testGroup).futureValue
        val notification = phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue
        notification.isDefined mustBe true
        notification.get.applicationId mustBe AppId
        notification.get.userId mustBe UserId
        notification.get.preferredName mustBe "Georgy"
        notification.get.expiryDate.getMillis mustBe date.getMillis
      }
    }

    "return no results" when {
      val date = DateTime.now().plusHours(22)
      val testProfile = Phase3TestGroup(expirationDate = date, tests = List(phase3Test))
      "there are no applications in PHASE3_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("applicationStatus" -> ApplicationStatus.IN_PROGRESS), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3FirstReminder).futureValue mustBe None
      }

      "the expiration date is in 26h but we send the second reminder only after 24h" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(
          AppId,
          Phase3TestGroup(expirationDate = new DateTime().plusHours(30), tests = List(phase3Test))).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "the test is expired" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE3_TESTS_EXPIRED.applicationStatus,
          s"progress-status.$PHASE3_TESTS_EXPIRED" -> true,
          s"progress-status-timestamp.$PHASE3_TESTS_EXPIRED" -> DateTime.now()
        )), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "the test is completed" in {
        import repositories.BSONDateTimeHandler
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE3_TESTS_COMPLETED.applicationStatus,
          s"progress-status.$PHASE3_TESTS_COMPLETED" -> true,
          s"progress-status-timestamp.$PHASE3_TESTS_COMPLETED" -> DateTime.now()
        )), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }

      "we already sent a second reminder" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        phase3TestRepo.insertOrUpdateTestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          s"progress-status.$PHASE3_TESTS_SECOND_REMINDER" -> true
        )), AppId).futureValue
        phase3TestRepo.nextTestForReminder(Phase3SecondReminder).futureValue mustBe None
      }
    }
  }
}
