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

package repositories

import model.School

trait SchoolsRepository {
  def getAllSchools: List[School]
}

class InMemorySchoolsRepository extends SchoolsRepository {
  val masterList: List[School] =
      School("542-0059", "Abbey Christian Brothers Grammar School") ::
      School("321-0313", "Abbey Community College") ::
      School("341-0209", "Antrim Grammar School") ::
      School("142-0277", "Aquinas Diocesan Grammar School") ::
      School("121-0015", "Ashfield Boys' High School") ::
      School("121-0014", "Ashfield Girls' High School") ::
      School("442-0086", "Assumption Grammar School") ::
      School("521-0153", "Aughnacloy High School") ::
      School("321-0124", "Ballycastle High School") ::
      School("341-0008", "Ballyclare High School") ::
      School("321-0134", "Ballyclare Secondary School") ::
      School("342-0011", "Ballymena Academy") ::
      School("321-0133", "Ballymoney High School") ::
      School("541-0013", "Banbridge Academy") ::
      School("521-0047", "Banbridge High School") ::
      School("421-0296", "Bangor Academy and 6th Form College") ::
      School("442-0015", "Bangor Grammar School") ::
      School("121-0022", "Belfast Boys' Model School") ::
      School("342-0077", "Belfast High School") ::
      School("121-0021", "Belfast Model School For Girls") ::
      School("142-0028", "Belfast Royal Academy") ::
      School("426-0309", "Blackwater Integrated College") ::
      School("141-0315", "Bloomfield Collegiate") ::
      School("421-0316", "Breda Academy") ::
      School("525-0216", "Brownlow Int College") ::
      School("341-0297", "Cambridge House Grammar School") ::
      School("142-0020", "Campbell College") ::
      School("321-0091", "Carrickfergus College") ::
      School("341-0098", "Carrickfergus Grammar School") ::
      Nil

  def getAllSchools: List[School] = {
    masterList
  }
}
