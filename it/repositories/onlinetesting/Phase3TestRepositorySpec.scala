package repositories.onlinetesting

import java.util.UUID

import connectors.launchpadgateway.exchangeobjects.in.SetupProcessCallbackRequest
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
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
    completedDateTime = None,
    callbacks = None
  )

  val TestGroup = Phase3TestGroup(expirationDate = DatePlus7Days, tests = List(phase3Test))

  "Get online test" ignore {
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

  "Append callbacks" should {
    "create a one callback array when the key is not set" in {
      insertApplication("appId", "userId")
      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue

      val insertedTest = phase3TestRepo.getTestGroup("appId").futureValue.get
      val token = insertedTest.activeTest.token

      phase3TestRepo.appendCallback(token, "setupProcess", SetupProcessCallbackRequest(
        UUID.randomUUID().toString,
        "FSCND-1234",
        12345,
        None,
        "FSINV-456",
        LocalDate.parse("2016-11-09")
      ))

      phase3TestRepo.appendCallback(token, "setupProcess", SetupProcessCallbackRequest(
        UUID.randomUUID().toString,
        "FSCND-1234",
        12345,
        None,
        "FSINV-456",
        LocalDate.parse("2016-11-09")
      ))

      val testWithCallback = phase3TestRepo.getTestGroup("appId").futureValue.get

      testWithCallback.tests.find(t => t.token == token).get.callbacks.get.setupProcess.length mustBe 1
    }

    "Append a callback when at least one is already set" ignore {

    }
  }

  "Next application ready for online testing" ignore {
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

  "Insert a phase 3 test" ignore {
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
}
