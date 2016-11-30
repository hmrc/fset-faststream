/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories.contactdetails

import model.Exceptions.{ CannotUpdateContactDetails, ContactDetailsNotFound, ContactDetailsNotFoundForEmail }
import model.persisted.ContactDetails
import play.api.Logger
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ContactDetailsRepository {
  def update(userId: String, contactDetails: ContactDetails): Future[Unit]

  def find(userId: String): Future[ContactDetails]

  def findUserIdByEmail(email: String): Future[String]

  def findAllPostcodes(): Future[Map[String, String]]
}

class ContactDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID]("contact-details", mongo, ContactDetails.contactDetailsFormat,
    ReactiveMongoFormats.objectIdFormats) with ContactDetailsRepository {
  val ContactDetailsCollection = "contact-details"

  def update(userId: String, contactDetails: ContactDetails): Future[Unit] = {
    val query = BSONDocument("userId" -> userId)
    val contactDetailsBson = BSONDocument("$set" -> BSONDocument(ContactDetailsCollection -> contactDetails))

    collection.update(query, contactDetailsBson, upsert = true) map {
      case lastError if lastError.nModified == 0 && lastError.n == 0 =>
        Logger.error(s"""Failed to write contact details for user: $userId -> ${lastError.writeConcernError.map(_.errmsg).mkString(",")}""")
        throw CannotUpdateContactDetails(userId)
      case _ => ()
    }
  }

  def find(userId: String): Future[ContactDetails] = {
    val query = BSONDocument("userId" -> userId)
    val projection = BSONDocument(ContactDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(d) if d.getAs[BSONDocument]("contact-details").isDefined =>
        d.getAs[ContactDetails]("contact-details").get
      case None => throw ContactDetailsNotFound(userId)
    }
  }

  def findUserIdByEmail(email: String): Future[String] = {
    val query = BSONDocument("contact-details.email" -> email)
    val projection = BSONDocument("userId" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(d) if d.getAs[String]("userId").isDefined =>
        d.getAs[String]("userId").get
      case None => throw ContactDetailsNotFoundForEmail()
    }
  }

  def findAllPostcodes(): Future[Map[String, String]] = {
    val query = BSONDocument("contact-details.postCode" -> BSONDocument("$exists" -> true))
    val projection = BSONDocument("userId" -> 1, "contact-details.postCode" -> 1)
    implicit val tupleReads: Reads[(String, String)] = (
      (JsPath \ "userId").read[String] and
        (JsPath \ "contact-details" \ "postCode").read[String]
      )((_, _))
    val result = collection.find(query, projection).cursor[(String, String)](ReadPreference.nearest).collect[List]()
    result.map(_.toMap)
  }

}
