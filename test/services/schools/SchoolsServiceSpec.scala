/*
 * Copyright 2020 HM Revenue & Customs
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
import repositories.csv.SchoolsCSVRepository
import repositories.csv.{ SchoolsCSVRepository, SchoolsRepository }
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.Future

class SchoolsServiceSpec extends UnitWithAppSpec with ShortTimeout {
  val service = new SchoolsService {
    override val schoolsRepo: SchoolsRepository = SchoolsCSVRepository
    override val MaxNumberOfSchools = Integer.MAX_VALUE
  }

  "Schools Service" should {
    "return simple 3 letter matches from beginning of school name" in {
      val term = "Abb"
      val result = service.getSchools(term).futureValue

      expect23SchoolsContains("Abb", result)
    }

    "return simple 3 letter matches from beginning of school name ignoring case" in {
      val term = "aBB"

      val result = service.getSchools(term).futureValue

      result.size mustBe 23
      result.foreach(s => withClue(s"school name: ${s.name}") {
        s.name.toLowerCase.contains("abb") mustBe true
      })
    }

    "match on middle words" in {
      val school1WithGrammarName = School("IRN", "341-0209", "Antrim Grammar School", None, None, None, None, None, None, None)
      val school2WithGrammarName = School("IRN", "142-0277", "Aquinas Diocesan Grammar School", None, None, None, None, None, None, None)
      val schoolWithoutGrammarName = School("IRN", "542-0059", "Abbey Christian Brothers School", None, None, None, None, None, None, None)

      val service = new SchoolsService {
        override val schoolsRepo: SchoolsRepository = new SchoolsRepository {
          def schools: Future[List[School]] =  Future.successful(List(
            school1WithGrammarName,
            schoolWithoutGrammarName,
            school2WithGrammarName
          ))
        }
      }

      val term = "Grammar"

      service.getSchools(term).futureValue mustBe
        List(school1WithGrammarName, school2WithGrammarName)
    }

    "ignore whitespace in term" in {
      val term = "A b b "
      val result = service.getSchools(term).futureValue

      expect23SchoolsContains("Abb", result)
    }

    "ignore punctuation in term" in {
      val term = "-A?(b_@'b,)&"

      val result = service.getSchools(term).futureValue

      expect23SchoolsContains("Abb", result)
    }

    "ignore punctuation in school name" in {
      val term = "Girls High"

      val result = service.getSchools(term).futureValue

      result.size mustBe 20
      result.foreach(s => withClue(s"school name: ${s.name}") {
        s.name.contains("Girls") && s.name.contains("High") mustBe true
      })
    }

    "should limit number of results to 16" in {
      val service = new SchoolsService {
        override val schoolsRepo: SchoolsRepository = SchoolsCSVRepository
      }
      val term = "aBB"
      val result = service.getSchools(term).futureValue

      result.size mustBe 16
      result.foreach(s => withClue(s"school name: ${s.name}") {
        s.name.toLowerCase.contains("abb") mustBe true
      })
    }

    "should return less than 16 if the criteria narrows down the result" in {
      val service = new SchoolsService {
        override val schoolsRepo: SchoolsRepository = SchoolsCSVRepository
      }
      val term = "Abbey Community"
      val result = service.getSchools(term).futureValue

      result.size mustBe 1
      result.foreach(s => withClue(s"school name: ${s.name}") {
        s.name.contains("Abbey Community") mustBe true
      })
    }
  }

  private def expect23SchoolsContains(term: String, actualResult: List[School]) = {
    actualResult.size mustBe 23
    actualResult.foreach(s => withClue(s"school name: ${s.name}") {
      s.name.contains(term) mustBe true
    })
  }
}
