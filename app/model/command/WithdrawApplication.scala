/*
 * Copyright 2017 HM Revenue & Customs
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

package model.command

import model.SchemeId
import play.api.libs.json.{ Format, Json }

import scala.language.implicitConversions

trait WithdrawRequest {
  def reason: String
  def withdrawer: String
}

case class WithdrawApplication(
  reason: String,
  otherReason: Option[String],
  withdrawer: String
) extends WithdrawRequest

object WithdrawApplication {
  implicit val withdrawApplicationFormats: Format[WithdrawApplication] = Json.format[WithdrawApplication]
}

case class WithdrawScheme(
  schemeId: SchemeId,
  reason: String,
  withdrawer: String
) extends WithdrawRequest

object WithdrawScheme {
  implicit val withdrawSchemeFormat = Json.format[WithdrawScheme]
}
