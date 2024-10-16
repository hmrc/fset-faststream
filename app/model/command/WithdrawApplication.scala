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

package model.command

import model.SchemeId
import org.mongodb.scala.bson.BsonValue
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.Codecs

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
  implicit val withdrawApplicationFormat: Format[WithdrawApplication] = Json.format[WithdrawApplication]

  implicit class BsonOps(val withdrawApplication: WithdrawApplication) extends AnyVal {
    def toBson: BsonValue = Codecs.toBson(withdrawApplication)
  }
}

case class WithdrawScheme(
  schemeId: SchemeId,
  reason: String,
  withdrawer: String
) extends WithdrawRequest

object WithdrawScheme {
  implicit val withdrawSchemeFormat: OFormat[WithdrawScheme] = Json.format[WithdrawScheme]
}
