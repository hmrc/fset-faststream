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

package util

import models.frameworks.{ Location, Region }
import org.junit.Assert._
import org.scalatestplus.play.PlaySpec

class RegionToJsonSpec extends PlaySpec {

  "Regions" should {
    "render correctly to Json" in {
      val regions = List(
        Region(
          "London",
          List(
            Location("London", List("B", "C")),
            Location("Hackney", List("B", "C", "S"))
          )
        ),
        Region(
          "East",
          List(
            Location("Bedford", List("B", "F")),
            Location("Southend", List("B", "C", "S"))
          )
        )
      )
      val json = Region.toJson(regions)
      assertEquals(
        """{"London":{"London":["B","C"],"Hackney":["B","C","S"]},"East":{"Bedford":["B","F"],"Southend":["B","C","S"]}}""", json
      )
    }

  }
}
