/*
 * Copyright 2018 HM Revenue & Customs
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

package helpers

import org.scalatest.WordSpec
import org.scalatest.MustMatchers

class DateFormatterSpec extends WordSpec with MustMatchers {

  import DateFormatterSpec._

  "DateFormatter" should {
    "format a date in dd MMMM yyyy format" in {
      DateFormatter.toddMMMMyyyyFormat(date_02_01_1985) mustBe "02 January 1985"
    }
    "format a date in d MMMM yyyy, h:mma format" in {
      DateFormatter.todMMMMyyyyhmmaFormat(date_01_01_1985) mustBe "1 January 1985, 10:06am"
    }
  }
}

object DateFormatterSpec{
  import org.joda.time.{ DateTime, LocalDate }

  private val date_02_01_1985 = new LocalDate(473472000000L)

  private val date_01_01_1985 = new DateTime(473422000000L)
}
