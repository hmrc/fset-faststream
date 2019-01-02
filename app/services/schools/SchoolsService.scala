/*
 * Copyright 2019 HM Revenue & Customs
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
import repositories._
import repositories.csv.SchoolsRepository

import scala.concurrent.ExecutionContext.Implicits.global

object SchoolsService extends SchoolsService{
  val schoolsRepo = schoolsRepository
}

trait SchoolsService {
  val MaxNumberOfSchools = 16
  val schoolsRepo : SchoolsRepository

  def getSchools(term: String) = {

    for {
      allSchools <- schoolsRepo.schools
    } yield {
      val sanitizedTerm = sanitize(term)
      applyLimit(allSchools.filter(s => sanitize(s.name).contains(sanitizedTerm)))
    }
  }

  private def sanitize(str: String) = {
    str.replaceAll("""([\p{Punct}])*""", "")
        .replaceAll("""\s*""","")
        .toLowerCase()
  }

  private def applyLimit(allSchools: List[School]) = allSchools.take(MaxNumberOfSchools)
}
