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

package model.persisted.phase3tests

import model.persisted.{ PassmarkEvaluation, TestProfile }
import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class Phase3TestGroup(expirationDate: DateTime,
                           tests: List[LaunchpadTest],
                           evaluation: Option[PassmarkEvaluation] = None) extends TestProfile[LaunchpadTest] {
  def activeTest = {
    val activeTests = tests.filter(_.usedForResults)
    require(activeTests.size == 1, "There is more than one active launchpad test. First token: " + activeTests.head.token)
    activeTests.head
  }
}

object Phase3TestGroup {
  implicit val phase3TestGroupFormat = Json.format[Phase3TestGroup]
  import repositories.BSONDateTimeHandler
  implicit val bsonHandler: BSONHandler[BSONDocument, Phase3TestGroup] = Macros.handler[Phase3TestGroup]
}
