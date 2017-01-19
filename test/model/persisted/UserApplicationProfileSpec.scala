/*
 * Copyright 2017 HM Revenue & Customs
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

package model.persisted

import factories.DateTimeFactory
import model.ProgressStatuses.SUBMITTED
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class UserApplicationProfileSpec extends PlaySpec {
  val dob = DateTimeFactory.nowLocalDate.minusYears(30)
  val differentDob = dob.plusDays(30)
  val source = create("John", "Doe", dob)

  "Same first name, last name and date of birth" should {
    "match application with exactly three fields equal" in {
      val app = create("John", "Doe", dob)

      withClue(s"$source must match with itself") {
        source.sameFirstNameLastNameAndDOB(source) mustBe true
      }
      withClue(s"$source must match $app") {
        source.sameFirstNameLastNameAndDOB(app) mustBe true
      }
    }

    "does not match applications without exactly three fields equal" in {
      val app1 = create("John", "Doe", differentDob)
      val app2 = create("John", "Diff", dob)
      val app3 = create("Diff", "Doe", dob)
      val app4 = create("Diff", "Diff", differentDob)

      withClue(s"$source must not match $app1") {
        source.sameFirstNameLastNameAndDOB(app1) mustBe false
      }
      withClue(s"$source must not match $app2") {
        source.sameFirstNameLastNameAndDOB(app2) mustBe false
      }
      withClue(s"$source must not match $app3") {
        source.sameFirstNameLastNameAndDOB(app3) mustBe false
      }
      withClue(s"$source must not match $app4") {
        source.sameFirstNameLastNameAndDOB(app4) mustBe false
      }
    }
  }

  "Same exactly two: first name, last name and date of birth" should {
    "match application with exactly two fields equal" in {
      val app1 = create("John", "Doe", differentDob)
      val app2 = create("John", "Diff", dob)
      val app3 = create("Diff", "Doe", dob)

      withClue(s"$source must match $app1") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(app1) mustBe true
      }
      withClue(s"$source must match $app2") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(app2) mustBe true
      }
      withClue(s"$source must match $app3") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(app3) mustBe true
      }
    }

    "does not match application without exactly two fields equal" in {
      val app1 = create("John", "Doe", dob)
      val app2 = create("John", "Diff", differentDob)
      val app3 = create("Diff", "Doe", differentDob)
      val app4 = create("Diff", "Diff", dob)

      withClue(s"$source must not match itself") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(source) mustBe false
      }
      withClue(s"$source must not match $app1") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(app1) mustBe false
      }
      withClue(s"$source must not match $app2") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(app2) mustBe false
      }
      withClue(s"$source must not match $app3") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(app3) mustBe false
      }
      withClue(s"$source must not match $app4") {
        source.sameExactlyTwoFirstNameLastNameAndDOB(app4) mustBe false
      }
    }
  }

  private def create(firstName: String, lastName: String, dob: LocalDate): UserApplicationProfile = {
    UserApplicationProfile("", SUBMITTED, firstName, lastName, dob, exportedToParity = false)
  }
}
