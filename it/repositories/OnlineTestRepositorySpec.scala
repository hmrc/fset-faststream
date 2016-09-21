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
import model.PersistedObjects.ApplicationIdWithUserIdAndStatus
import model.persisted.Phase1TestProfileWithAppId
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.{ BSONArray, BSONDocument }
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{ GeneralApplicationMongoRepository, OnlineTestMongoRepository }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class OnlineTestRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

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
      val result = onlineTestRepo.getPhase1TestProfile("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      insertApplication("appId")
      onlineTestRepo.insertOrUpdatePhase1TestGroup("appId", TestProfile).futureValue
      val result = onlineTestRepo.getPhase1TestProfile("appId").futureValue
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
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = true
      )

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result must be (None)
    }

    "return one application if there is only one and it is not a fast pass candidate" in{
      createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false
      )

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.get.applicationId mustBe "appId"
      result.get.userId mustBe "userId"
    }
  }

  "The OnlineTestApplication case model" should {
    "be correctly read from mongo" in {
       createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false, isGis = true
      )

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

  def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String,
    needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments: Boolean,
    fastPassApplicable: Boolean = false) = {

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "fastpass-details.applicable" -> fastPassApplicable,
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

  def createOnlineTest(userId: String, appStatus: String, phase1Tests: List[Phase1Test],
    expirationDate: DateTime = DateTime.now) = {
    import model.OnlineTestCommands.Phase1TestProfile.phase1TestProfileHandler

    val appId = UUID.randomUUID().toString

    val profile = Phase1TestProfile(expirationDate = expirationDate, tests = phase1Tests)
    onlineTestRepo.collection.insert(profile)

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "applicationId" -> appId,
      "frameworkId" -> "frameworkId",
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "progress-status-dates" -> BSONDocument("allocation_unconfirmed" -> "2016-04-05"),
      "assistance-details" -> BSONDocument(
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "No",
        "needsSupportAtVene" -> "No"
      )
    )).futureValue

    ApplicationIdWithUserIdAndStatus(appId, userId, appStatus)
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
  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
                                     appStatus: String = "", needsAdjustment: Boolean = false, adjustmentsConfirmed: Boolean = false,
                                     timeExtensionAdjustments: Boolean = false, fastPassApplicable: Boolean = false, isGis: Boolean = false) = {
    helperRepo.collection.insert(BSONDocument(
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
      "fastpass-details" -> BSONDocument(
        "applicable" -> fastPassApplicable,
        "fastPassReceived" -> fastPassApplicable
      ),
      "assistance-details" -> createAssistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments, isGis),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> BSONDocument(
        "registered" -> "true"
      )
    )).futureValue
  }
  // scalastyle:on parameter.number

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
    helperRepo.collection.insert(BSONDocument("applicationId" -> appId)).futureValue
  }

}
