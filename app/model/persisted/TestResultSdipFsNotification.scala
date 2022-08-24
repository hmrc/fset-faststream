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

package model.persisted

import model.ApplicationStatus.ApplicationStatus
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.play.json.Codecs

case class TestResultSdipFsNotification(
  applicationId: String,
  userId: String,
  applicationStatus: ApplicationStatus,
  preferredName: String)

object TestResultSdipFsNotification {
  def fromBson(doc: Document) = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val userId = doc.get("userId").get.asString().getValue
    val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
    val personalDetailsRoot = doc.get("personal-details").map( _.asDocument() ).get
    val preferredName = personalDetailsRoot.get("preferredName").asString().getValue
    TestResultSdipFsNotification(applicationId, userId, applicationStatus, preferredName)
  }

  implicit val testResultSdipFsNotificationFormat = Json.format[TestResultSdipFsNotification]
}
