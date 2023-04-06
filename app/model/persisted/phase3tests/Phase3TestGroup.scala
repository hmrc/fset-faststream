/*
 * Copyright 2023 HM Revenue & Customs
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

import model.persisted.{PassmarkEvaluation, TestProfile}
import org.mongodb.scala.bson.BsonValue
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.{Instant, OffsetDateTime, ZoneOffset}

case class Phase3TestGroup(expirationDate: Instant,
                           tests: List[LaunchpadTest],
                           evaluation: Option[PassmarkEvaluation] = None) extends TestProfile[LaunchpadTest] {
  def activeTest = {
    val activeTests = tests.filter(_.usedForResults)
    require(activeTests.size == 1, "There is more than one active launchpad test. First token: " + activeTests.head.token)
    activeTests.head
  }
  def toExchange = Phase3TestGroupExchange(expirationDate, tests.map(_.toExchange), evaluation)

}

object Phase3TestGroup {
  import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed to handle storing ISODate format
  implicit val phase3TestGroupFormat = Json.format[Phase3TestGroup]

  implicit class BsonOps(val phase3TestGroup: Phase3TestGroup) extends AnyVal {
    def toBson: BsonValue = Codecs.toBson(phase3TestGroup)
  }
}

case class Phase3TestGroupExchange(
                                  expirationDate: Instant,
                                  tests: List[LaunchpadTestExchange],
                                  evaluation: Option[PassmarkEvaluation] = None) extends TestProfile[LaunchpadTestExchange]

object Phase3TestGroupExchange {
  import play.api.libs.json.JodaWrites._
  import play.api.libs.json.JodaReads._
  implicit val phase3TestGroupFormat = Json.format[Phase3TestGroupExchange]
}
