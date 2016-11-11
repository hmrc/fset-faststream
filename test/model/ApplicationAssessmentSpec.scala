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

import fixture.UnitSpec
import model.Commands.ApplicationAssessment
import org.joda.time.{ DateTimeZone, LocalDate, LocalTime }

class ApplicationAssessmentSpec extends UnitSpec {

  val Today = LocalDate.now(DateTimeZone.UTC)

  "Application Assessment" should {
    "map AM to 8:30 for all venues except Manchester and London (Berkeley House)" in {
      val assessment = ApplicationAssessment("appId", "London", Today, "AM", 1, confirmed = true)
      assessment.assessmentDateTime must be(toDateTime(8, 30))
    }

    "map AM to 9:00 for Manchester" in {
      val assessment = ApplicationAssessment("appId", "Manchester", Today, "AM", 1, confirmed = true)
      assessment.assessmentDateTime must be(toDateTime(9, 0))
    }

    "map AM to 9:00 for London (Berkeley House)" in {
      val assessment = ApplicationAssessment("appId", "London (Berkeley House)", Today, "AM", 1, confirmed = true)
      assessment.assessmentDateTime must be(toDateTime(9, 0))
    }

    "map PM to 12:30 for all venues except Manchester and London (Berkeley House)" in {
      val assessment = ApplicationAssessment("appId", "London", Today, "PM", 1, confirmed = true)
      assessment.assessmentDateTime must be(toDateTime(12, 30))
    }

    "map PM to 13:00 for Manchester" in {
      val assessment = ApplicationAssessment("appId", "Manchester", Today, "PM", 1, confirmed = true)
      assessment.assessmentDateTime must be(toDateTime(13, 0))
    }

    "map PM to 13:00 for London (Berkeley House)" in {
      val assessment = ApplicationAssessment("appId", "London (Berkeley House)", Today, "PM", 1, confirmed = true)
      assessment.assessmentDateTime must be(toDateTime(13, 0))
    }

    "expire date is set to 11 days before allocation date which gives 10 days effectively when 23:59 will be assumed" in {
      val allocationDate = new LocalDate(2016, 5, 29)
      val assessment = ApplicationAssessment("appId", "venue", allocationDate, "am", 1, confirmed = true)

      val expireDate = assessment.expireDate
      expireDate must be(new LocalDate(2016, 5, 18))
    }
  }

  def toDateTime(hh: Int, mm: Int) = Today.toLocalDateTime(new LocalTime(hh, mm)).toDateTime
}
