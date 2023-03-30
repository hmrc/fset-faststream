/*
 * Copyright 2023 HM Revenue & Customs
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

package services.onlinetesting

import factories.{ DateTimeFactory, DateTimeFactoryMock }
import org.mockito.Mockito._
import testkit.UnitSpec

class TimeExtensionSpec extends UnitSpec {

  "Extend time" should {
    "add days to current date if the test has already expired" in new TimeExtensionFixture {
      val extendTimeInDays = 3
      val newExpirationDate = timeExtension.extendTime(alreadyExpired = true, now.minusDays(1))(extendTimeInDays)
      newExpirationDate mustBe now.plusDays(3)
    }

    "add days to expiration date if it's in the future" in new TimeExtensionFixture {
      val extendTimeInDays = 3
      val newExpirationDate = timeExtension.extendTime(alreadyExpired = false, now.plusDays(1))(extendTimeInDays)
      newExpirationDate mustBe now.plusDays(4)
    }
  }

  trait TimeExtensionFixture {
    val now = DateTimeFactoryMock.nowLocalTimeZoneJavaTime
    val mockDateTimeFactory = mock[DateTimeFactory]
    when(mockDateTimeFactory.nowLocalTimeZoneJavaTime).thenReturn(now)

    val timeExtension = new TimeExtension {
      val dateTimeFactory: DateTimeFactory = mockDateTimeFactory
    }
  }
}
