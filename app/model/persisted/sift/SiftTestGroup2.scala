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

package model.persisted.sift

import model.persisted.PsiTest
import org.joda.time.DateTime
import play.api.libs.json.{ Json, OFormat }
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

// The tests are optional because it depends on the candidate having schemes that require a numeric test for the tests to be populated
case class SiftTestGroup2(expirationDate: DateTime, tests: Option[List[PsiTest]]) {
  def activeTests: List[PsiTest] = tests.getOrElse(Nil).filter(_.usedForResults)
}

object SiftTestGroup2 {
  import repositories.BSONDateTimeHandler
  implicit val bsonHandler: BSONHandler[BSONDocument, SiftTestGroup2] = Macros.handler[SiftTestGroup2]
  implicit val siftTestGroupFormat: OFormat[SiftTestGroup2] = Json.format[SiftTestGroup2]
}
