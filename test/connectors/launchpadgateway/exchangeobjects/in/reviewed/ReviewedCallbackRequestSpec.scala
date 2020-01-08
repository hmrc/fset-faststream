/*
 * Copyright 2020 HM Revenue & Customs
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

package connectors.launchpadgateway.exchangeobjects.in.reviewed

import model.Phase3TestProfileExamples._
import testkit.UnitSpec

class ReviewedCallbackRequestSpec extends UnitSpec {

  "Calculate total score" should {
    "compute correctly for decimal scores" in {
      val reviewedCallback = buildReviewCallBack(1.5)
      reviewedCallback.calculateTotalScore() mustBe 24
    }
    "compute correctly for non decimal scores" in {
      val reviewedCallback = buildReviewCallBack(2)
      reviewedCallback.calculateTotalScore() mustBe 32
    }
    "compute correctly with single decimal precision" in {
      val reviewedCallback = buildReviewCallBack(1.6)
      reviewedCallback.calculateTotalScore() mustBe 25.6
    }
    "compute correctly with double decimal precision" in {
      val reviewedCallback = buildReviewCallBack(1.67)
      reviewedCallback.calculateTotalScore() mustBe 26.72
    }
  }

  "calculate review criteria 1 score" should {
    "compute correctly for decimal scores" in {
      val reviewedCallback = buildReviewCallBack(1.5)
      reviewedCallback.calculateReviewCriteria1Score() mustBe 12
    }
    "compute correctly for non decimal scores" in {
      val reviewedCallback = buildReviewCallBack(2)
      reviewedCallback.calculateReviewCriteria1Score() mustBe 16
    }
    "compute correctly with single decimal precision" in {
      val reviewedCallback = buildReviewCallBack(1.6)
      reviewedCallback.calculateReviewCriteria1Score() mustBe 12.8
    }
    "compute correctly with double decimal precision" in {
      val reviewedCallback = buildReviewCallBack(1.67)
      reviewedCallback.calculateReviewCriteria1Score() mustBe 13.36
    }
  }

  "calculate review criteria 2 score" should {
    "compute correctly for decimal scores" in {
      val reviewedCallback = buildReviewCallBack(1.5)
      reviewedCallback.calculateReviewCriteria2Score() mustBe 12
    }
    "compute correctly for non decimal scores" in {
      val reviewedCallback = buildReviewCallBack(2)
      reviewedCallback.calculateReviewCriteria2Score() mustBe 16
    }
    "compute correctly with single decimal precision" in {
      val reviewedCallback = buildReviewCallBack(1.6)
      reviewedCallback.calculateReviewCriteria2Score() mustBe 12.8
    }
    "compute correctly with double decimal precision" in {
      val reviewedCallback = buildReviewCallBack(1.67)
      reviewedCallback.calculateReviewCriteria2Score() mustBe 13.36
    }
  }
}
