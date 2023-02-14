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

package model.exchange

import model.FSACIndicator
import org.joda.time.DateTime
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.{ Format, Json }

case class CandidateEligibleForEvent(
  applicationId: String,
  firstName: String,
  lastName: String,
  needsAdjustment: Boolean,
  fsbScoresAndFeedbackSubmitted: Boolean,
  fsacScoresEntered: Boolean,
  fsacIndicator: FSACIndicator,
  dateReady: DateTime)

object CandidateEligibleForEvent {
  implicit val CandidateEligibleForEventFormat: Format[CandidateEligibleForEvent] = Json.format[CandidateEligibleForEvent]
}
