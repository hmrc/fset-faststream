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

package repositories

import java.util.UUID

import factories.DateTimeFactory
import model.Exceptions.CannotFindTestByCubiksId
import model.OnlineTestCommands.{ OnlineTestApplication, Phase1Test, Phase1TestProfile }
import model.PersistedObjects.ExpiringOnlineTest
import model.ProgressStatuses.{ PHASE1_TESTS_COMPLETED, PHASE1_TESTS_EXPIRED, PHASE1_TESTS_STARTED, ProgressStatus, _ }
import model.persisted.Phase1TestProfileWithAppId
import model.{ ApplicationStatus, ProgressStatuses, ReminderNotice }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import play.api.libs.json.Json
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{ BSONArray, BSONDocument }
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{ GeneralApplicationMongoRepository, OnlineTestMongoRepository }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class OnlineTestRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._
  import TextFixture._

  override val collectionName = "application"

  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  def onlineTestRepo = new OnlineTestMongoRepository(DateTimeFactory)

  val Token = UUID.randomUUID.toString
  val Now =  DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)
  val CubiksUserId = 999

  val phase1Test = Phase1Test(
    scheduleId = 123,
    usedForResults = true,
    cubiksUserId = CubiksUserId,
    token = Token,
    testUrl = "test.com",
    invitationDate = Now,
    participantScheduleId = 456
  )

  val TestProfile = Phase1TestProfile(expirationDate = DatePlus7Days, tests = List(phase1Test))

  "Get online test" should {
    "return None if there is no test for the specific user id" in {
      val result = onlineTestRepo.getPhase1TestGroup("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      insertApplication("appId")
      onlineTestRepo.insertOrUpdatePhase1TestGroup("appId", TestProfile).futureValue
      val result = onlineTestRepo.getPhase1TestGroup("appId").futureValue
      result mustBe Some(TestProfile)
    }
  }

  "Get online test by token" should {
    "return None if there is no test with the token" in {
      val result = onlineTestRepo.getPhase1TestProfileByToken("token").failed.futureValue
      result mustBe a[CannotFindTestByCubiksId]
    }

    "return an online tet for the specific token" in {
      insertApplication("appId")
      onlineTestRepo.insertOrUpdatePhase1TestGroup("appId", TestProfile).futureValue
      val result = onlineTestRepo.getPhase1TestProfileByToken(Token).futureValue
      result mustBe TestProfile
    }
  }

  "Get online test by cubiksId" should {
    "return None if there is no test with the cubiksId" in {
      val result = onlineTestRepo.getPhase1TestProfileByCubiksId(CubiksUserId).failed.futureValue
      result mustBe a[CannotFindTestByCubiksId]
    }

    "return an online tet for the specific cubiks id" in {
      insertApplication("appId")
      onlineTestRepo.insertOrUpdatePhase1TestGroup("appId", TestProfile).futureValue
      val result = onlineTestRepo.getPhase1TestProfileByCubiksId(CubiksUserId).futureValue
      result mustBe Phase1TestProfileWithAppId("appId", TestProfile)
    }

  }

  "Next application ready for online testing" should {
    "return no application if there is only one and it is a fast pass candidate" in{
      createApplicationWithAllFields("appId", "userId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = true,
        fastPassReceived = true
      ).futureValue

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result must be (None)
    }

    "return one application if there is only one and it is not a fast pass candidate" in{
      createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.get.applicationId mustBe "appId"
      result.get.userId mustBe "userId"
    }
  }

  "Next phase1 test group with report ready" should {
    "not return a test group if the progress status is not appropriately set" in {
       createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS",
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_RESULTS_READY, false))
      ).futureValue

      val result = onlineTestRepo.nextPhase1TestGroupWithReportReady.futureValue

      result.isDefined mustBe false
    }

    "return a test group if the progress status is set to PHASE1_TEST_RESULTS_READY" in {

      val resultsReadyGroup = TestProfile.copy(tests = List(
        phase1Test.copy(usedForResults = true, resultsReadyToDownload = true),
        phase1Test.copy(usedForResults = true, resultsReadyToDownload = true))
      )

      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false, additionalProgressStatuses = List((PHASE1_TESTS_RESULTS_READY, true)),
        phase1TestProfile = Some(resultsReadyGroup)
      ).futureValue

      val phase1TestResultsReady = onlineTestRepo.nextPhase1TestGroupWithReportReady.futureValue
      phase1TestResultsReady.isDefined mustBe true
      phase1TestResultsReady.get mustBe resultsReadyGroup
    }
  }

  "The OnlineTestApplication case model" should {
    "be correctly read from mongo" in {
       createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false, isGis = true
      ).futureValue

      val onlineTestApplication = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      onlineTestApplication.isDefined mustBe true

      inside (onlineTestApplication.get) { case OnlineTestApplication(applicationId, applicationStatus, userId,
        guaranteedInterview, needsAdjustments, preferredName, timeAdjustments) =>

        applicationId mustBe "appId"
        applicationStatus mustBe "SUBMITTED"
        userId mustBe "userId"
        guaranteedInterview mustBe true
        needsAdjustments mustBe false
        preferredName mustBe testCandidate("preferredName")
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
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        onlineTestRepo.nextExpiringApplication.futureValue must be (Some(ExpiringOnlineTest(AppId,UserId,"Georgy")))
      }
    }
    "return no results" when {
      "there are no application in PHASE1_TESTS" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("applicationStatus" -> ApplicationStatus.IN_PROGRESS)).futureValue
        onlineTestRepo.nextExpiringApplication.futureValue must be(None)
      }

      "the date is not expired yet" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(
          AppId,
          Phase1TestProfile(expirationDate = new DateTime().plusHours(2), tests = List(phase1Test))).futureValue
        onlineTestRepo.nextExpiringApplication.futureValue must be(None)
      }

      "the test is already expired" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-dates.$PHASE1_TESTS_EXPIRED" -> LocalDate.now()
        ))).futureValue
        onlineTestRepo.nextExpiringApplication.futureValue must be(None)
      }

      "the test is completed" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-dates.$PHASE1_TESTS_COMPLETED" -> LocalDate.now()
        ))).futureValue
        onlineTestRepo.nextExpiringApplication.futureValue must be(None)
      }
    }
  }

  "nextTestForReminder" should {
    "return one result" when {
      "there is an application in PHASE1_TESTS and is about to expiry in the next 72 hours" in {
        val date = DateTime.now().plusHours(FirstReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        val notification = onlineTestRepo.nextTestForReminder(FirstReminder).futureValue
        notification.isDefined must be(true)
        notification.get.applicationId must be(AppId)
        notification.get.userId must be(UserId)
        notification.get.preferredName must be("Georgy")
        notification.get.expiryDate.getMillis must be(date.getMillis)
        // Because we are far away from the 24h reminder's window
        onlineTestRepo.nextTestForReminder(SecondReminder).futureValue must be(None)
      }

      "there is an application in PHASE1_TESTS and is about to expiry in the next 24 hours" in {
        val date = DateTime.now().plusHours(SecondReminder.hoursBeforeReminder - 1).plusMinutes(55)
        val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        val notification = onlineTestRepo.nextTestForReminder(SecondReminder).futureValue
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
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("applicationStatus" -> ApplicationStatus.IN_PROGRESS)).futureValue
        onlineTestRepo.nextTestForReminder(FirstReminder).futureValue must be(None)
      }

      "the expiration date is in 26h but we send the second reminder only after 24h" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(
          AppId,
          Phase1TestProfile(expirationDate = new DateTime().plusHours(30), tests = List(phase1Test))).futureValue
        onlineTestRepo.nextTestForReminder(SecondReminder).futureValue must be(None)
      }

      "the test is expired" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_EXPIRED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_EXPIRED" -> true,
          s"progress-status-dates.$PHASE1_TESTS_EXPIRED" -> LocalDate.now()
        ))).futureValue
        onlineTestRepo.nextTestForReminder(SecondReminder).futureValue must be(None)
      }

      "the test is completed" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          "applicationStatus" -> PHASE1_TESTS_COMPLETED.applicationStatus,
          s"progress-status.$PHASE1_TESTS_COMPLETED" -> true,
          s"progress-status-dates.$PHASE1_TESTS_COMPLETED" -> LocalDate.now()
        ))).futureValue
        onlineTestRepo.nextTestForReminder(SecondReminder).futureValue must be(None)
      }

      "we already sent a second reminder" in {
        createApplicationWithAllFields(UserId, AppId, "frameworkId", "SUBMITTED").futureValue
        onlineTestRepo.insertOrUpdatePhase1TestGroup(AppId, testProfile).futureValue
        updateApplication(BSONDocument("$set" -> BSONDocument(
          s"progress-status.$PHASE1_TESTS_SECOND_REMINDER" -> true
        ))).futureValue
        onlineTestRepo.nextTestForReminder(SecondReminder).futureValue must be(None)
      }
    }
  }

  "Progress status" should {
    "update progress status to PHASE1_TESTS_STARTED" in {
      createApplicationWithAllFields("userId", "appId", appStatus = ApplicationStatus.PHASE1_TESTS).futureValue
      onlineTestRepo.updateProgressStatus("appId", PHASE1_TESTS_STARTED).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1TestsStarted mustBe true
    }

    "remove progress statuses" in {
      createApplicationWithAllFields("userId", "appId", appStatus = ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = List(ProgressStatuses.PHASE1_TESTS_INVITED -> true, ProgressStatuses.PHASE1_TESTS_COMPLETED -> true))
      onlineTestRepo.removePhase1TestProfileProgresses("appId", List(
        ProgressStatuses.PHASE1_TESTS_INVITED,
        ProgressStatuses.PHASE1_TESTS_COMPLETED)
      ).futureValue

      val app = helperRepo.findByUserId("userId", "frameworkId").futureValue
      app.progressResponse.phase1TestsInvited mustBe false
      app.progressResponse.phase1TestsCompleted mustBe false
    }
  }

  def updateApplication(doc: BSONDocument) = onlineTestRepo.collection.update(BSONDocument("applicationId" -> AppId), doc)

  def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String,
    needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments: Boolean,
    fastPassApplicable: Boolean = false) = {

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "civil-service-experience-details.applicable" -> fastPassApplicable,
      "assistance-details" -> createAsistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments)
    )).futureValue
  }

  private def createAsistanceDetails(needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments:Boolean) = {
    if (needsAdjustment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
            "adjustments-confirmed" -> true,
            "verbalTimeAdjustmentPercentage" -> 9,
            "numericalTimeAdjustmentPercentage" -> 11
          )
        } else {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("room alone"),
            "adjustments-confirmed" -> true
          )
        }
      } else {
        BSONDocument(
          "needsAdjustment" -> "Yes",
          "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
          "adjustments-confirmed" -> false
        )
      }
    } else {
      BSONDocument(
        "needsAdjustment" -> "No"
      )
    }
  }



  def createOnlineTestApplication(appId: String, applicationStatus: String, xmlReportSavedOpt: Option[Boolean] = None,
                                  alreadyEvaluatedAgainstPassmarkVersionOpt: Option[String] = None): String = {
    val result = (xmlReportSavedOpt, alreadyEvaluatedAgainstPassmarkVersionOpt) match {
      case (None, None ) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus
        ))
      case (Some(xmlReportSaved), None) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved)
        ))
      case (None, Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
      case (Some(xmlReportSaved), Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved),
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
    }

    result.futureValue

    appId
  }

  // scalastyle:off parameter.number
  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String = "frameworkId",
    appStatus: String, needsAdjustment: Boolean = false, adjustmentsConfirmed: Boolean = false,
    timeExtensionAdjustments: Boolean = false, fastPassApplicable: Boolean = false,
    fastPassReceived: Boolean = false, isGis: Boolean = false,
    additionalProgressStatuses: List[(ProgressStatus, Boolean)] = List.empty,
    phase1TestProfile: Option[Phase1TestProfile] = None
  ): Future[WriteResult] = {
    val doc = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "framework-preferences" -> BSONDocument(
        "firstLocation" -> BSONDocument(
          "region" -> "Region1",
          "location" -> "Location1",
          "firstFramework" -> "Commercial",
          "secondFramework" -> "Digital and technology"
        ),
        "secondLocation" -> BSONDocument(
          "location" -> "Location2",
          "firstFramework" -> "Business",
          "secondFramework" -> "Finance"
        ),
        "alternatives" -> BSONDocument(
          "location" -> true,
          "framework" -> true
        )
      ),
      "personal-details" -> BSONDocument(
        "firstName" -> s"${testCandidate("firstName")}",
        "lastName" -> s"${testCandidate("lastName")}",
        "preferredName" -> s"${testCandidate("preferredName")}",
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}",
        "aLevel" -> true,
        "stemLevel" -> true
      ),
      "civil-service-experience-details" -> BSONDocument(
        "applicable" -> fastPassApplicable,
        "fastPassReceived" -> fastPassReceived
      ),
      "assistance-details" -> createAssistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments, isGis),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> progressStatus(additionalProgressStatuses),
      "testGroups" -> phase1TestGroup(phase1TestProfile)
    )

    helperRepo.collection.insert(doc)
  }
  // scalastyle:on parameter.number

  private def phase1TestGroup(o: Option[Phase1TestProfile]): BSONDocument = {
    BSONDocument("PHASE1" -> o.map(Phase1TestProfile.phase1TestProfileHandler.write))
  }

  private def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): BSONDocument = {
    val baseDoc = BSONDocument(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "partner-graduate-programmes" -> true,
      "assistance-details" -> true,
      "questionnaire" -> questionnaire(),
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc.++(v._1.toString -> v._2))
  }

  private def questionnaire() = {
    BSONDocument(
      "start_questionnaire" -> true,
      "diversity_questionnaire" -> true,
      "education_questionnaire" -> true,
      "occupation_questionnaire" -> true
    )
  }

  private def createAssistanceDetails(needsAdjustment: Boolean, adjustmentsConfirmed: Boolean,
                                      timeExtensionAdjustments:Boolean, isGis: Boolean = false) = {
    if (needsAdjustment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
            "adjustments-confirmed" -> true,
            "verbalTimeAdjustmentPercentage" -> 9,
            "numericalTimeAdjustmentPercentage" -> 11,
            "guaranteedInterview" -> isGis
          )
        } else {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("room alone"),
            "adjustments-confirmed" -> true,
            "guaranteedInterview" -> isGis
          )
        }
      } else {
        BSONDocument(
          "needsAdjustment" -> "Yes",
          "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
          "adjustments-confirmed" -> false,
          "guaranteedInterview" -> isGis
        )
      }
    } else {
      BSONDocument(
        "needsAdjustment" -> "No",
        "guaranteedInterview" -> isGis
      )
    }
  }

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  private def insertApplication(appId: String) = {
    helperRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "personal-details" -> BSONDocument(
        "firstName" -> s"${testCandidate("firstName")}",
        "lastName" -> s"${testCandidate("lastName")}",
        "preferredName" -> s"${testCandidate("preferredName")}",
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}",
        "aLevel" -> true,
        "stemLevel" -> true
    ))).futureValue
  }

}

object TextFixture {
  val FirstReminder = ReminderNotice(72, PHASE1_TESTS_FIRST_REMINDER)
  val SecondReminder = ReminderNotice(24, PHASE1_TESTS_SECOND_REMINDER)
}
