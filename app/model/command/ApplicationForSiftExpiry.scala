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

package model.command

import model.ApplicationStatus.ApplicationStatus
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.Codecs

case class ApplicationForSiftExpiry(applicationId: String,
                                    userId: String,
                                    applicationStatus: ApplicationStatus) {
  override def toString = s"applicationId:$applicationId, userId:$userId, applicationStatus:$applicationStatus"
}

object ApplicationForSiftExpiry {
  implicit val applicationForSiftFormat: OFormat[ApplicationForSiftExpiry] = Json.format[ApplicationForSiftExpiry]

  def fromBson(doc: Document): ApplicationForSiftExpiry = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val userId = doc.get("userId").get.asString().getValue
    val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
    ApplicationForSiftExpiry(applicationId, userId, applicationStatus)
  }
}
