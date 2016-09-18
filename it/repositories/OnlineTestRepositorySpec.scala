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
import model.OnlineTestCommands.{ Phase1Test, Phase1TestProfile }
import org.joda.time.DateTime
import reactivemongo.bson.{ BSONArray, BSONDocument }
import repositories.application.{ GeneralApplicationMongoRepository, OnlineTestMongoRepository }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class OnlineTestRepositorySpec extends MongoRepositorySpec {

  override val collectionName = "application"

  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  def onlineTestRepo = new OnlineTestMongoRepository(DateTimeFactory)
  def applicationHelperRepo = new ApplicationRepositoryHelper(onlineTestRepo)

  val phase1Test = Phase1Test(scheduleId = 123,
    usedForResults = true, cubiksUserId = 999, token = UUID.randomUUID.toString, testUrl = "test.com",
    invitationDate = DateTime.now, participantScheduleId = 456
  )

  "Get online test" should {
    "return None if there is no test for the specific user id" in {
      val result = onlineTestRepo.getPhase1TestProfile("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      val date = new DateTime("2016-03-08T13:04:29.643Z")
      val testProfile = Phase1TestProfile(expirationDate = date, tests = List(phase1Test))

      onlineTestRepo.insertPhase1TestProfile("appId", testProfile)

      onlineTestRepo.getPhase1TestProfile("appId").futureValue.foreach { result =>
        result.expirationDate.toDate must be (new DateTime("2016-03-15T13:04:29.643Z").toDate)
        result.tests.head.testUrl mustBe phase1Test.testUrl
      }
    }
  }

  "Next application ready for online testing" should {
    "return no application if htere is only one and it is a fast pass candidate" in {
      applicationHelperRepo.createApplicationWithAllFields("appId", "userId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = true
      )

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result must be(None)
    }

    "return one application if there is only one and it is not a fast pass candidate" in {
      applicationHelperRepo.createApplicationWithAllFields("userId", "appId", "frameworkId", "SUBMITTED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false
      )

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.get.applicationId mustBe "appId"
      result.get.userId mustBe "userId"
    }
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
}
