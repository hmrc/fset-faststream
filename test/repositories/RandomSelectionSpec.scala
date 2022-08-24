/*
 * Copyright 2022 HM Revenue & Customs
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

import testkit.UnitSpec

import scala.language.postfixOps

class RandomSelectionSpec extends UnitSpec {
  "RandomSelection" should {
    "calculate the correct batch size and random offset" in {
/*
      RandomSelection.calculateBatchSize(1, 1) mustBe ((0,1))
      val (offset, size) = RandomSelection.calculateBatchSize(1, 9)
      offset must (be >= 0 and be <= 8)
      size mustBe 1

      1 to 200 foreach { _ =>
        val numberOfDocs = scala.util.Random.nextInt(1000)
        1 to calculateRepeats(numberOfDocs) foreach { _ =>
          val (offset, size) = RandomSelection.calculateBatchSize(50, numberOfDocs)
          if (numberOfDocs > 50) {
            offset must (be >= 0 and be <= (numberOfDocs - 50))
            size mustBe 50
          } else {
            offset mustBe 0
            size mustBe numberOfDocs
          }
        }
     }
 */
    }
  }

  def calculateRepeats(limit: Int): Int = {
    val adjusted = limit + 1
    1 to adjusted map (x=> adjusted / x) sum
  }
}
