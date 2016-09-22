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

package services.onlinetesting

import model.OnlineTestCommands.Phase1Test
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._

class ResetPhase1TestSpec extends PlaySpec with MockitoSugar {
  import model.ProgressStatuses._

  val resetPhase1Test = new ResetPhase1Test {}

  val test1 = mock[Phase1Test]
  val test2 = mock[Phase1Test]
  val test3 = mock[Phase1Test]

  "determine statuses to remove" should {
    when(test1.startedDateTime).thenReturn(None)
    when(test2.startedDateTime).thenReturn(Some(DateTime.now))
    when(test3.startedDateTime).thenReturn(Some(DateTime.now))

    "throw an exception when there too many tests requsted to remove" in {
      intercept[IllegalArgumentException] {
        resetPhase1Test.determineStatusesToRemove(List(), List("sjq"))
      }
    }

    "return only completed and results received statuses when not all tests are requested to reset" in {
      val result = resetPhase1Test.determineStatusesToRemove(List(test2, test3), List("sjq"))
      result mustBe List(PHASE1_TESTS_COMPLETED, PHASE1_TESTS_RESULTS_RECEIVED)
    }

    "return completed, results received, invited and started tests when all started tests are reseted" in {
      val result = resetPhase1Test.determineStatusesToRemove(List(test1, test2, test3), List("sjq", "bq"))
      result mustBe List(PHASE1_TESTS_COMPLETED, PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_STARTED, PHASE1_TESTS_INVITED)
    }
  }
}
