package repositories.onlinetesting

import java.util.UUID

import model.persisted.phase3tests.{ Phase3Test, Phase3TestGroup }
import model.persisted.{ CubiksTest, Phase2TestGroup }
import org.joda.time.{ DateTime, DateTimeZone }
import testkit.MongoRepositorySpec

class Phase3TestRepositorySpec extends ApplicationDataFixture with MongoRepositorySpec {

  val Now =  DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)
  val Token = UUID.randomUUID.toString

  val phase3Test = Phase3Test(
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
      insertApplication("appId")
      phase3TestRepo.insertOrUpdateTestGroup("appId", TestGroup).futureValue
      val result = phase3TestRepo.getTestGroup("appId").futureValue
      result mustBe Some(TestGroup)
    }
  }

  "Next application ready for online testing" should {
    "return one application if there is only one" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE2_TESTS_PASSED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      val result = phase3TestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.get.applicationId mustBe "appId"
      result.get.userId mustBe "userId"
    }
  }

  "Insert a phase 3 test" must {
    "correctly insert a test" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE2_TESTS_PASSED", needsAdjustment = false,
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
