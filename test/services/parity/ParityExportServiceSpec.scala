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
      /*
      sut.exportApplication("ddcb9d48-d93e-4c85-9f44-00d9a0d4b64c").recover {
        case ex => print(s"There was an error => $ex\n")
      }.futureValue
      */
    }
  }

  trait TestFixture {
    val sut = ParityExportService
  }
}
