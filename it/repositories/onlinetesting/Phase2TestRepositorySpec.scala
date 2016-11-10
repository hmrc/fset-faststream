package repositories.onlinetesting

import java.util.UUID

import model.ProgressStatuses
import model.persisted.{ CubiksTest, Phase2TestGroup, Phase2TestGroupWithAppId }
import model.ProgressStatuses._
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

  val TestProfile = Phase2TestGroup(expirationDate = DatePlus7Days, tests = List(phase2Test))
  val testProfileWithAppId = Phase2TestGroupWithAppId(
    "appId",
    TestProfile.copy(tests = List(
                       phase2Test.copy(usedForResults = true, resultsReadyToDownload = true),
                       phase2Test.copy(usedForResults = true, resultsReadyToDownload = true))
    )
  )

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
    "return application when does not need adjustments and is no gis and its status is PHASE1_TESTS_PASSED" in {
      createApplicationWithAllFields("userId0", "appId0", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, isGis = false, additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)),
        typeOfEtrayOnlineAdjustments = Nil
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId0"
      results.head.userId mustBe "userId0"
    }

    "return application when it is gis and adjustments have been confirmed (etray time extension) and its status is PHASE1_TESTS_PASSED" in {
      createApplicationWithAllFields("userId3", "appId3", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = true, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, isGis = true, additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId3"
      results.head.userId mustBe "userId3"
    }

    "return application when needs online adjustments, adjustments have been confirmed and its status is PHASE1_TESTS_PASSED" +
      " and adjustment is etray time extension" in {
      createApplicationWithAllFields("userId4", "appId4", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = true,
        needsSupportAtVenue = false, adjustmentsConfirmed = true, timeExtensionAdjustments = true, fastPassApplicable = false,
        fastPassReceived = false, isGis = true, additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId4"
      results.head.userId mustBe "userId4"
    }

    "return application when needs adjustments at venue, adjustments have been confirmed and its status is PHASE1_TESTS_PASSED" +
      " and adjustment is etray time extension" in {
      createApplicationWithAllFields("userId5", "appId5", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = true, adjustmentsConfirmed = true, timeExtensionAdjustments = true, fastPassApplicable = false,
        fastPassReceived = false, isGis = true, additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId5"
      results.head.userId mustBe "userId5"
    }

    "do not return application when application status is not PHASE1_TESTS_PASSED and no adjustments and no gis" in {
      createApplicationWithAllFields("userId6", "appId6", "frameworkId", "SUBMITTED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, isGis = false, additionalProgressStatuses = List((SUBMITTED, true)),
        typeOfEtrayOnlineAdjustments = Nil
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is gis but there is no need for adjustments" +
      "and adjustments have not been confirmed" in {
      createApplicationWithAllFields("userId6", "appId6", "frameworkId", "SUBMITTED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, isGis = true, additionalProgressStatuses = List((SUBMITTED, true)),
        typeOfEtrayOnlineAdjustments = Nil
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is no gis but there is need for online adjustments" +
      "(e-tray time extension) and adjustments have not been confirmed" in {
      createApplicationWithAllFields("userId7", "appId7", "frameworkId", "SUBMITTED", needsSupportForOnlineAssessment = true,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = true, fastPassApplicable = false,
        fastPassReceived = false, isGis = false, additionalProgressStatuses = List((SUBMITTED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is no gis but there is need for adjustments at venue" +
      "and adjustments have not been confirmed" in {
      createApplicationWithAllFields("userId7", "appId7", "frameworkId", "SUBMITTED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = true, adjustmentsConfirmed = false, timeExtensionAdjustments = true, fastPassApplicable = false,
        fastPassReceived = false, isGis = false, additionalProgressStatuses = List((SUBMITTED, true))
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 0
    }

    "do not return application when application status is PHASE1_TESTS_PASSED and is no gis but there is need for adjustments at venue" +
      "and adjustments have been confirmed but adjustments is etray invigilated" in {
      createApplicationWithAllFields("userId8", "appId8", "frameworkId", "SUBMITTED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = true, adjustmentsConfirmed = false, timeExtensionAdjustments = true, fastPassApplicable = false,
        fastPassReceived = false, isGis = false, additionalProgressStatuses = List((ProgressStatuses.SUBMITTED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayInvigilated")
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 0
    }

    "exclude applications that need adjustments and have not been confirmed" in {
      createApplicationWithAllFields("userId1", "appId1", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = true,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = true, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true)),
        typeOfEtrayOnlineAdjustments = List("etrayTimeExtension")
      ).futureValue

      createApplicationWithAllFields("userId2", "appId2", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_PASSED, true))
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 1
      results.head.applicationId mustBe "appId2"
      results.head.userId mustBe "userId2"
    }

    "return more than one candidate for batch processing" in {
      pending
    }

    "Not return candidates whose phase 1 tests have expired" in {
      createApplicationWithAllFields("userId1", "appId1", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = true,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_EXPIRED -> true))
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue
      results.isEmpty mustBe true
    }
  }

  "Insert a phase 2 test" must {
    "correctly insert a test" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS_PASSED", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      val now =  DateTime.now(DateTimeZone.UTC)

      val input = Phase2TestGroup(expirationDate = now,
        tests = List(CubiksTest(scheduleId = 1,
          usedForResults = true,
          token = "token",
          cubiksUserId = 111,
          testUrl = "testUrl",
          invitationDate = now,
          participantScheduleId = 222
        ))
      )

      phase2TestRepo.insertOrUpdateTestGroup("appId", input).futureValue

      val result = phase2TestRepo.getTestGroup("appId").futureValue
      result.isDefined mustBe true
      result.get.expirationDate mustBe input.expirationDate
      result.get.tests mustBe input.tests
    }
  }


  "Updating completion time" must {
    "update test completion time" in {
      val now =  DateTime.now(DateTimeZone.UTC)
      val input = Phase2TestGroup(expirationDate = now.plusDays(5),
        tests = List(CubiksTest(scheduleId = 1,
          usedForResults = true,
          token = "token",
          cubiksUserId = 111,
          testUrl = "testUrl",
          invitationDate = now,
          participantScheduleId = 222
        ))
      )

      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE2_TESTS", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, phase2TestGroup = Some(input)
      ).futureValue

      phase2TestRepo.updateTestCompletionTime(111, now).futureValue
      val result = phase2TestRepo.getTestProfileByCubiksId(111).futureValue
      result.testGroup.tests.head.completedDateTime mustBe Some(now)
    }

    "not update profiles that have expired" in {
      val now =  DateTime.now(DateTimeZone.UTC)
      val input = Phase2TestGroup(expirationDate = now,
        tests = List(CubiksTest(scheduleId = 1,
          usedForResults = true,
          token = "token",
          cubiksUserId = 111,
          testUrl = "testUrl",
          invitationDate = now,
          participantScheduleId = 222
        ))
      )

      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE2_TESTS", needsSupportForOnlineAssessment = false,
        needsSupportAtVenue = false, adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, phase2TestGroup = Some(input)
      ).futureValue

      phase2TestRepo.updateTestCompletionTime(111, now).futureValue
      val result = phase2TestRepo.getTestProfileByCubiksId(111).futureValue
      result.testGroup.tests.head.completedDateTime mustBe None
    }
  }

  "Insert test result" should {
    "correctly update a test group with results" in {
       createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE2_TESTS", needsSupportForOnlineAssessment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((PHASE2_TESTS_RESULTS_READY, true)),
        phase2TestGroup = Some(testProfileWithAppId.testGroup)
      ).futureValue

      val testResult = model.persisted.TestResult(status = "completed", norm = "some norm",
          tScore = Some(55.33d), percentile = Some(34.876d), raw = Some(65.32d), sten = Some(12.1d))

      phase2TestRepo.insertTestResult("appId", testProfileWithAppId.testGroup.tests.head,
        testResult
      ).futureValue

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
}
