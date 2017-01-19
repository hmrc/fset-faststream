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

import org.joda.time.LocalDate
import reactivemongo.bson.Macros

case class UserApplicationProfile(userId: String,
                                  latestProgressStatus: String,
                                  firstName: String,
                                  lastName: String,
                                  dateOfBirth: LocalDate,
                                  exportedToParity: Boolean) {

  final def sameFirstNameLastNameAndDOB(app: UserApplicationProfile): Boolean = {
    countSameFirstNameLastNameAndDoB(app) == 3
  }

  final def sameExactlyTwoFirstNameLastNameAndDOB(app: UserApplicationProfile): Boolean = {
    countSameFirstNameLastNameAndDoB(app) == 2
  }

  private def countSameFirstNameLastNameAndDoB(candidate: UserApplicationProfile): Int = {
    //scalastyle:off
    List(firstName == candidate.firstName,
      lastName == candidate.lastName,
      dateOfBirth == candidate.dateOfBirth).count(_ == true)
    //scalastyle:on
  }
}

object UserApplicationProfile {
  import repositories.BSONLocalDateHandler
  implicit val userApplicationProfileHandler = Macros.handler[UserApplicationProfile]
}
