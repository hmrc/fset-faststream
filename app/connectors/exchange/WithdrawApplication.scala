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

package connectors.exchange

import connectors.exchange.referencedata.SchemeId
import play.api.libs.json._

import scala.language.implicitConversions

trait CandidateWithdrawal {
  def withdrawer: String = "Candidate"
}

case class WithdrawApplication(
  reason: String,
  otherReason: Option[String]
) extends CandidateWithdrawal

object WithdrawApplication {
  implicit val withdrawApplicationWrite = new Writes[WithdrawApplication] {
    def writes(o: WithdrawApplication): JsValue = Json.obj(
      "reason" -> o.reason,
      "otherReason" -> o.otherReason,
      "withdrawer" -> o.withdrawer
    )
  }
}

case class WithdrawScheme(
  schemeId: SchemeId,
  reason: String
) extends CandidateWithdrawal

object WithdrawScheme {
   implicit val withdrawSchemeWrite = new Writes[WithdrawScheme] {
    def writes(o: WithdrawScheme): JsValue = Json.obj(
      "schemeId" -> o.schemeId.value,
      "reason" -> o.reason,
      "withdrawer" -> o.withdrawer
    )
  }
}
