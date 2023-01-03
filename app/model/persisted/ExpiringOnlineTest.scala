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

package model.persisted

import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.Json
import repositories.subDocRoot

case class ExpiringOnlineTest(
  applicationId: String,
  userId: String,
  preferredName: String
)

object ExpiringOnlineTest {
  def fromBson(doc: Document): ExpiringOnlineTest = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val userId = doc.get("userId").get.asString().getValue
    val personalDetailsRoot = subDocRoot("personal-details")(doc).get
    val preferredName = personalDetailsRoot.get("preferredName").asString().getValue
    ExpiringOnlineTest(applicationId, userId, preferredName)
  }

  implicit val expiringOnlineTestFormats = Json.format[ExpiringOnlineTest]
  implicit val bsonReader = this
}
