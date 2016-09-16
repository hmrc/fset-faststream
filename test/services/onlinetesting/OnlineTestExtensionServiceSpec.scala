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

/* TODO FAST STREAM FIX ME
package services.onlinetesting

import controllers.OnlineTestDetails
import factories.DateTimeFactory
import model.OnlineTestCommands.OnlineTestApplication
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import repositories.onlinetests.OnlineTestRepository
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import testkit.MockitoSugar

class OnlineTestExtensionServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {
  "when extending the expiration time of a test" should {

    "add extra days onto expiry, from the expiry time, if not expired" in new TestFixture {
      when(dateTime.nowLocalTimeZone).thenReturn(now)
      when(repository.getOnlineTestDetails(any())).thenReturnAsync(onlineTest)
      when(repository.updateExpiryTime(any(), any())).thenReturnAsync()

      service.extendExpiryTime(onlineTestApp, extraDays).futureValue mustBe (())

      verify(repository).updateExpiryTime(userId, expirationDate.plusDays(extraDays))
    }

    "add extra days onto expiry, from today, if already expired" in new TestFixture {
      val nowBeyondExpiry = expirationDate.plusDays(10)
      when(dateTime.nowLocalTimeZone).thenReturn(nowBeyondExpiry)
      when(repository.getOnlineTestDetails(any())).thenReturnAsync(onlineTest)
      when(repository.updateExpiryTime(any(), any())).thenReturnAsync()

      service.extendExpiryTime(onlineTestApp, extraDays).futureValue mustBe (())

      verify(repository).updateExpiryTime(userId, nowBeyondExpiry.plusDays(extraDays))
    }
  }

  trait TestFixture {
    val applicationStatus = "ONLINE_TEST_EXPIRED"
    val applicationId = "abc"
    val userId = "xyz"
    val preferredName = "Jon"
    val emailAddress = "jon@test.com"
    val cubiksEmail = "123@test.com"
    val extraDays = 3
    val now = DateTime.now()
    val invitationDate = now.minusDays(4)
    val expirationDate = now.plusDays(3)
    val onlineTest = OnlineTestDetails(invitationDate, expirationDate, "http://example.com/", cubiksEmail, isOnlineTestEnabled = true)
    val numericalTimeAdjustmentPercentage = 0
    val verbalTimeAdjustmentPercentage = 0
    val onlineTestApp = OnlineTestApplication(
      applicationId, applicationStatus, userId,
      guaranteedInterview = true, needsAdjustments = true, preferredName, None
    )
    val repository = mock[OnlineTestRepository]
    val dateTime = mock[DateTimeFactory]
    val service = new OnlineTestExtensionServiceImpl(repository, dateTime)
  }
} */
