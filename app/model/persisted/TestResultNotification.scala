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
import play.api.libs.json.{Json, OFormat}

case class TestResultNotification(
  applicationId: String,
  userId: String,
  preferredName: String)

object TestResultNotification {

  def fromBson(doc: Document) = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val userId = doc.get("userId").get.asString().getValue
    val personalDetailsRoot = doc.get("personal-details").map( _.asDocument() ).get
    val preferredName = personalDetailsRoot.get("preferredName").asString().getValue
    TestResultNotification(applicationId, userId, preferredName)
  }

  implicit val testResultNotificationFormat: OFormat[TestResultNotification] = Json.format[TestResultNotification]
}
