package repositories.onlinetesting

import java.util.UUID

import model.persisted.{ CubiksTest, Phase2TestProfile }
import org.joda.time.{ DateTime, DateTimeZone }
import testkit.MongoRepositorySpec

class Phase2TestRepositorySpec extends ApplicationDataFixture with MongoRepositorySpec {

  val Now =  DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)
  val CubiksUserId = 999
  val Token = UUID.randomUUID.toString

  val phase2Test = CubiksTest(
    scheduleId = 123,
    usedForResults = true,
    cubiksUserId = CubiksUserId,
    token = Token,
    testUrl = "test.com",
    invitationDate = Now,
    participantScheduleId = 456
  )

  val TestProfile = Phase2TestProfile(expirationDate = DatePlus7Days, tests = List(phase2Test))

  "Get online test" should {
    "return None if there is no test for the specific user id" in {
      val result = phase2TestRepo.getTestGroup("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      insertApplication("appId")
      phase2TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase2TestRepo.getTestGroup("appId").futureValue
      result mustBe Some(TestProfile)
    }
  }

  "Next application ready for online testing" should {
    "return one application if there is only one" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS_PASSED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      val result = phase2TestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.get.applicationId mustBe "appId"
      result.get.userId mustBe "userId"
    }

    "return more than one candidate for batch processing" in {
      pending
    }
  }
}
