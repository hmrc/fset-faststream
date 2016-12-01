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

package services.parity

import org.scalatestplus.play.OneAppPerSuite
import testkit.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

class ParityExportServiceSpec extends UnitSpec with OneAppPerSuite {

  "Export Application" should {
    "Display some things" in new TestFixture {
      sut.exportApplication("e4ed2087-20ad-4e4c-890b-7267196a62aa").recover {
        case _ => print("There was an error\n")
      }.futureValue
    }
  }

  // e4ed2087-20ad-4e4c-890b-7267196a62aa
  // 5ba7d069-7491-4bdf-ba4d-584eb7ef46be

  trait TestFixture {
    val sut = ParityExportService
  }
}
