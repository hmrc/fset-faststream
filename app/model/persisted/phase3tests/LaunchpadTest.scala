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

package model.persisted.phase3tests

import connectors.launchpadgateway.exchangeobjects.in.SetupProcessCallbackRequest
import model.persisted.Test
import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class LaunchpadTest(interviewId: Int,
                         usedForResults: Boolean,
                         testProvider: String = "launchpad",
                         testUrl: String,
                         token: String,
                         candidateId: String,
                         customCandidateId: String,
                         invitationDate: DateTime,
                         startedDateTime: Option[DateTime],
                         completedDateTime: Option[DateTime],
                         callbacks: LaunchpadTestCallbacks,
                         invigilatedAccessCode: Option[String] = None
                     ) extends Test

object LaunchpadTest {
  implicit val launchpadTestFormat = Json.format[LaunchpadTest]
  import repositories.BSONDateTimeHandler
  implicit val bsonHandler: BSONHandler[BSONDocument, LaunchpadTest] = Macros.handler[LaunchpadTest]
}
