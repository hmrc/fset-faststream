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

package model.exchange.candidateevents

import play.api.libs.json.{ Format, Json }

case class CandidateRemoveReason(key: String, description: String, failApp: Boolean)

object CandidateRemoveReason {

  implicit val candidateRemoveReason: Format[CandidateRemoveReason] = Json.format[CandidateRemoveReason]

  val NoShow = "No-show"

  val Values = List(
    CandidateRemoveReason(NoShow, NoShow, failApp = true),
    CandidateRemoveReason("Candidate_contacted", "Candidate contacted", failApp = false),
    CandidateRemoveReason("Expired", "Expired", failApp = true),
    CandidateRemoveReason("Other", "Other", failApp = false)
  )

  def find(key: String): Option[CandidateRemoveReason] = Values.find(_.key == key)
}
