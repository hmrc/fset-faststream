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

import model.persisted.Test
import org.joda.time.DateTime
import play.api.libs.json.Json

import java.time.OffsetDateTime

case class LaunchpadTest(interviewId: Int,
                         usedForResults: Boolean,
                         testProvider: String = "launchpad",
                         testUrl: String,
                         token: String,
                         candidateId: String,
                         customCandidateId: String,
                         invitationDate: OffsetDateTime,
                         startedDateTime: Option[OffsetDateTime],
                         completedDateTime: Option[OffsetDateTime],
                         callbacks: LaunchpadTestCallbacks,
                         invigilatedAccessCode: Option[String] = None
                     ) extends Test {
  def toExchange = {
    LaunchpadTestExchange(interviewId,
                          usedForResults,
                          testProvider,
                          testUrl,
                          token,
                          candidateId,
                          customCandidateId,
                          invitationDate,
                          startedDateTime,
                          completedDateTime,
                          callbacks.toExchange,
                          invigilatedAccessCode
    )
  }
}

object LaunchpadTest {
  import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed to handle storing ISODate format
  implicit val launchpadTestFormat = Json.format[LaunchpadTest]
}

case class LaunchpadTestExchange(interviewId: Int,
                                 usedForResults: Boolean,
                                 testProvider: String = "launchpad",
                                 testUrl: String,
                                 token: String,
                                 candidateId: String,
                                 customCandidateId: String,
                                 invitationDate: OffsetDateTime,
                                 startedDateTime: Option[OffsetDateTime],
                                 completedDateTime: Option[OffsetDateTime],
                                 callbacks: LaunchpadTestCallbacksExchange,
                                 invigilatedAccessCode: Option[String] = None
                                ) extends Test

object LaunchpadTestExchange {
  import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
  import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
  implicit val launchpadTestFormat = Json.format[LaunchpadTestExchange]
}
