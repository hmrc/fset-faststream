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
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.Json
import repositories.subDocRoot
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed for ISODate

case class NotificationExpiringOnlineTest(
  applicationId: String,
  userId: String,
  preferredName: String,
  expiryDate: DateTime
)

object NotificationExpiringOnlineTest {

  def fromBson(doc: Document, phase: String) = {
    require(phase.nonEmpty, s"""Phase must be supplied but the value = \"$phase\"""")

    val applicationId = doc.get("applicationId").get.asString().getValue
    val userId = doc.get("userId").get.asString().getValue
    val personalDetailsRoot = subDocRoot("personal-details")(doc).get
    val preferredName = personalDetailsRoot.get("preferredName").asString().getValue
    val testGroupsRoot = subDocRoot("testGroups")(doc).get
    val PHASERoot = testGroupsRoot.get(phase).asDocument()
    val expiryDate = Codecs.fromBson[DateTime](PHASERoot.get("expirationDate").asDateTime())

    NotificationExpiringOnlineTest(applicationId, userId, preferredName, expiryDate)
  }

  implicit val notificationExpiringOnlineTestFormats = Json.format[NotificationExpiringOnlineTest]
  implicit val bsonReader = this
}
