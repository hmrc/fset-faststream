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

package model

import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.Commands.PostCode
import org.joda.time.LocalDate
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.JodaWrites._ // This is needed for LocalDate serialization
import play.api.libs.json.JodaReads._  // This is needed for LocalDate serialization
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.util.Try

case class Candidate(userId: String,
                     applicationId: Option[String],
                     testAccountId: Option[String],
                     email: Option[String],
                     firstName: Option[String],
                     lastName: Option[String],
                     preferredName: Option[String],
                     dateOfBirth: Option[LocalDate],
                     address: Option[Address],
                     postCode: Option[PostCode],
                     country: Option[String],
                     applicationRoute: Option[ApplicationRoute],
                     applicationStatus: Option[String]
                    ) {
  override def toString = s"userId:$userId,applicationId:$applicationId,testAccountId:$testAccountId,email:$email,firstName:$firstName," +
  s"lastName:$lastName,preferredName:$preferredName,dateOfBirth:$dateOfBirth,address:$address,postCode:$postCode,country:$country," +
  s"applicationRoute:$applicationRoute,applicationStatus:$applicationStatus"

  def name: String = preferredName.getOrElse(firstName.getOrElse(""))
}

object Candidate {
  implicit val candidateFormat: OFormat[Candidate] = Json.format[Candidate]

  def fromBson(doc: Document) = {
    val userId = doc.get("userId").get.asString().getValue
    val applicationId = doc.get("applicationId").map(_.asString().getValue)
    val testAccountId = doc.get("testAccountId").map(_.asString().getValue)
    val personalDetailsRootOpt = doc.get("personal-details").map( _.asDocument() )
    val firstName = personalDetailsRootOpt.flatMap(doc => Try(doc.get("firstName").asString().getValue).toOption)
    val lastName = personalDetailsRootOpt.flatMap(doc => Try(doc.get("lastName").asString().getValue).toOption)
    val preferredName = personalDetailsRootOpt.flatMap(doc => Try(doc.get("preferredName").asString().getValue).toOption)
    val dob = personalDetailsRootOpt.flatMap(doc => Try(new LocalDate(doc.get("dateOfBirth").asString().getValue)).toOption)
    val applicationRoute = Try(Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").get)).toOption
    val applicationStatus = Try(Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)).toOption.map(_.toString)
    Candidate(userId, applicationId, testAccountId, email = None, firstName, lastName, preferredName, dob, address = None,
      postCode = None, country = None, applicationRoute, applicationStatus)
  }
}

case class CandidateIds(userId: String, applicationId: String)

object CandidateIds {
  implicit val candidateIdFormat: OFormat[CandidateIds] = Json.format[CandidateIds]
}
