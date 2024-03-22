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

package connectors.launchpadgateway.exchangeobjects.in

import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat
import play.api.libs.json.{Json, OFormat}

import java.time.{LocalDate, OffsetDateTime}

case class FinalCallbackRequest(received: OffsetDateTime, candidateId: String, customCandidateId: String, interviewId: Int,
  customInterviewId: Option[String], customInviteId: String, deadline: LocalDate) {
  def toExchange = FinalCallbackRequestExchange(received, candidateId, customCandidateId, interviewId,
                                                customInterviewId, customInviteId, deadline)
  }

object FinalCallbackRequest {
  // Should match LaunchpadTestsCallback case class
  import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat // Needed to handle storing ISODate format
  val key = "finalCallback"
  implicit val finalCallbackRequestFormat: OFormat[FinalCallbackRequest] = Json.format[FinalCallbackRequest]
}

case class FinalCallbackRequestExchange(received: OffsetDateTime, candidateId: String, customCandidateId: String, interviewId: Int,
                                customInterviewId: Option[String], customInviteId: String, deadline: LocalDate)

object FinalCallbackRequestExchange {
  val key = "finalCallback"
  implicit val finalCallbackRequestFormat: OFormat[FinalCallbackRequestExchange] = Json.format[FinalCallbackRequestExchange]
}
