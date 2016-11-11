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

package services

import repositories.FrameworkRepository.Region
import repositories._
import testkit.UnitWithAppSpec

import language.postfixOps
import scala.concurrent.Await
import scala.concurrent.duration._

class LoadLocationsSpec extends UnitWithAppSpec {

  val timeout: Duration = 1 second

  "load locations " should {
    "load regions" in {

      val yamlRepository = new FrameworkYamlRepository()

      val s: List[Region] = Await.result(yamlRepository.getFrameworksByRegion, timeout)

      val k: List[(String, String)] = s.flatMap { region =>
        val regionName = region.name
        region.locations.map(location => (location.name, regionName))
      }

      val londonRegion = k.filter(p => p._1 == "London")

      londonRegion.size must be(1)
      londonRegion.head._2 must be("London")

      s.size must be(11)
    }

  }

}
