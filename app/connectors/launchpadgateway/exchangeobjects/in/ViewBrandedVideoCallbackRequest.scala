/*
 * Copyright 2022 HM Revenue & Customs
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

import org.joda.time.{ DateTime, LocalDate }
//import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
//import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed to handle storing ISODate format
import play.api.libs.json.Json
//import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class ViewBrandedVideoCallbackRequest(received: DateTime, candidateId: String, customCandidateId: String, interviewId: Int,
  customInterviewId: Option[String], customInviteId: String, deadline: LocalDate)

object ViewBrandedVideoCallbackRequest {
  // Should match LaunchpadTestsCallback case class
  val key = "viewBrandedVideo"
  implicit val viewBrandedVideoCallbackRequestFormat = Json.format[ViewBrandedVideoCallbackRequest]
//  import repositories.BSONDateTimeHandler
//  import repositories.BSONLocalDateHandler
//  implicit val bsonHandler: BSONHandler[BSONDocument, ViewBrandedVideoCallbackRequest] = Macros.handler[ViewBrandedVideoCallbackRequest]
}
