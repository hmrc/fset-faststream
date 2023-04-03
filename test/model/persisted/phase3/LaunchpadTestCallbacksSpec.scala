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

package model.persisted.phase3

import model.Phase3TestProfileExamples._
import model.persisted.phase3tests.LaunchpadTestCallbacks
import testkit.UnitSpec

import java.time.OffsetDateTime

class LaunchpadTestCallbacksSpec extends UnitSpec {

  val reviewedCallback1 = buildReviewCallBack(1.5, OffsetDateTime.now().minusMinutes(5))
  val reviewedCallback2 = buildReviewCallBack(1.5, OffsetDateTime.now().minusMinutes(4))
  val reviewedCallback3 = buildReviewCallBack(1.5, OffsetDateTime.now().minusMinutes(3))

  "getLatestReviewed" should {
    "return None when there are not items in reviewed" in {
      val noReviewed = LaunchpadTestCallbacks(reviewed = Nil)
      noReviewed.getLatestReviewed mustBe None
    }
    "return the only item when there is on item in reviewed" in {
      val oneReviewed = LaunchpadTestCallbacks(reviewed = List(reviewedCallback1))
      oneReviewed.getLatestReviewed mustBe Some(reviewedCallback1)
    }
    "return the items sorted there are two items in reviewed" in {
      val severalReviewed = LaunchpadTestCallbacks(reviewed = List(reviewedCallback2, reviewedCallback3, reviewedCallback1))
      severalReviewed.getLatestReviewed mustBe Some(reviewedCallback3)
    }
  }
}
