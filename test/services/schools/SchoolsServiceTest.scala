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

package services.schools

import model.School
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import repositories.{ InMemorySchoolsRepository, SchoolsRepository }
import testkit.MockitoSugar

class SchoolsServiceTest extends PlaySpec with MockitoSugar with ScalaFutures {

  "Schools Service" should {
    val sut = new SchoolsService {
      override val schoolsRepo: SchoolsRepository = new InMemorySchoolsRepository()
    }

    "return simple 3 letter matches from beginning of school name" in {
      val term = "Abb"

      sut.getSchools(term).futureValue mustBe List(School("542-0059", "Abbey Christian Brothers Grammar School"),
        School("321-0313", "Abbey Community College"))
    }

    "return simple 3 letter matches from beginning of school name ignoring case" in {
      val term = "aBB"

      sut.getSchools(term).futureValue mustBe List(School("542-0059", "Abbey Christian Brothers Grammar School"),
        School("321-0313", "Abbey Community College"))
    }

    "match on middle words" in {
      val term = "Grammar"

      sut.getSchools(term).futureValue mustBe
        List(School("542-0059", "Abbey Christian Brothers Grammar School"),
        School("341-0209", "Antrim Grammar School"),
        School("142-0277", "Aquinas Diocesan Grammar School"),
        School("442-0086", "Assumption Grammar School"),
        School("442-0015", "Bangor Grammar School"),
        School("341-0297", "Cambridge House Grammar School"),
        School("341-0098", "Carrickfergus Grammar School"))
    }

    "ignore whitespace in term" in {
      val term = "A b b "

      sut.getSchools(term).futureValue mustBe List(School("542-0059", "Abbey Christian Brothers Grammar School"),
        School("321-0313", "Abbey Community College"))
    }

    "ignore punctuation in term" in {
      val term = "-A?(b_@'b,)&"

      sut.getSchools(term).futureValue mustBe List(School("542-0059", "Abbey Christian Brothers Grammar School"),
        School("321-0313", "Abbey Community College"))
    }

    "ignore punctuation in school name" in {
      val term = "Girls High"

      sut.getSchools(term).futureValue mustBe List(School("121-0014", "Ashfield Girls' High School"))
    }
  }
}
