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

package model

import model.ProgressStatuses.{ REGISTERED, WITHDRAWN }
import org.scalatest.{ MustMatchers, WordSpec }

import scala.util.Random

class ApplicationControllerSpec extends WordSpec with MustMatchers{
  "ProgressStatuses" must {
    "Return a list of all statuses" in {
      ProgressStatuses.allStatuses.size must not be(0)
      ProgressStatuses.allStatuses.contains(WITHDRAWN) mustBe true
    }
    "Registered must be the lowest status" in {
      Random.shuffle(ProgressStatuses.allStatuses).sorted.head mustBe REGISTERED
    }
    "Withdrawn must be the highest status" in {
      Random.shuffle(ProgressStatuses.allStatuses).sorted.reverse.head mustBe WITHDRAWN
    }
  }
}
