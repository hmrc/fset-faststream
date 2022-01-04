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

import org.joda.time.DateTime
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.Json
import reactivemongo.bson.BSONDocument
import repositories.BSONDateTimeHandler

case class NotificationExpiringOnlineTest(
  applicationId: String,
  userId: String,
  preferredName: String,
  expiryDate: DateTime
)

object NotificationExpiringOnlineTest {
  def fromBson(doc: BSONDocument, phase: String) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    val testGroupsRoot = doc.getAs[BSONDocument]("testGroups").get
    val PHASERoot = testGroupsRoot.getAs[BSONDocument](phase).get
    val expiryDate = PHASERoot.getAs[DateTime]("expirationDate").get
    NotificationExpiringOnlineTest(applicationId, userId, preferredName, expiryDate)
  }

  implicit val notificationExpiringOnlineTestFormats = Json.format[NotificationExpiringOnlineTest]
}
