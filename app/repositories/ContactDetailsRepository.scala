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

package repositories

import config.MicroserviceAppConfig
import model.Commands._
import model.Address
import model.Exceptions.{ CannotUpdateContactDetails, ContactDetailsNotFound }
import model.PersistedObjects
import model.PersistedObjects._
import reactivemongo.api._
import reactivemongo.bson.{ BSONDocument, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@deprecated("fasttrack version")
trait ContactDetailsRepository {

  val errorCode = 500

  def update(userId: String, contactDetails: ContactDetails): Future[Unit]

  def find(userId: String): Future[ContactDetails]

  def findByPostCode(postCode: String): Future[List[ContactDetailsWithId]]

  def findAll: Future[List[ContactDetailsWithId]]
}

@deprecated("fasttrack version")
class ContactDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID]("contact-details", mongo,
    PersistedObjects.Implicits.contactDetailsFormats, ReactiveMongoFormats.objectIdFormats) with ContactDetailsRepository {

  override def update(userId: String, contactDetails: ContactDetails): Future[Unit] = {

    val contactDetailsBson = BSONDocument("$set" -> BSONDocument(
      "contact-details" -> contactDetails
    ))

    collection.update(BSONDocument("userId" -> userId), contactDetailsBson, upsert = true) map {
      case lastError if lastError.nModified == 0 && lastError.n == 0 =>
        logger.error(s"""Failed to write contact details for user: $userId -> ${lastError.writeConcernError.map(_.errmsg).mkString(",")}""")
        throw new CannotUpdateContactDetails(userId)
      case _ => ()
    }
  }

  override def find(userId: String): Future[ContactDetails] = {

    val query = BSONDocument("userId" -> userId)
    val projection = BSONDocument("contact-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("contact-details").isDefined => {
        val root = document.getAs[BSONDocument]("contact-details").get
        val address = root.getAs[Address]("address").get
        val postCode = root.getAs[PostCode]("postCode").getOrElse("")
        val phone = root.getAs[PhoneNumber]("phone")
        val email = root.getAs[String]("email").getOrElse("")
        ContactDetails(address, postCode, email, phone)
      }
      case None => throw new ContactDetailsNotFound(userId)
    }
  }

  override def findByPostCode(postCode: String) = {

    val query = BSONDocument("contact-details.postCode" -> BSONRegex("^" + postCode + "$", "i"))

    collection.find(query).cursor[BSONDocument]().collect[List]().map(_.map { doc =>
      val id = doc.getAs[String]("userId").get
      val root = doc.getAs[BSONDocument]("contact-details").get
      val address = root.getAs[Address]("address").get
      val postCode = root.getAs[PostCode]("postCode").get
      val phone = root.getAs[PhoneNumber]("phone")
      val email = root.getAs[String]("email").get

      ContactDetailsWithId(id, address, postCode, email, phone)
    })
  }

  override def findAll: Future[List[ContactDetailsWithId]] = {
    val query = BSONDocument()

    collection.find(query).cursor[BSONDocument]().collect[List](MicroserviceAppConfig.maxNumberOfDocuments).map(_.map { doc =>
      val id = doc.getAs[String]("userId").get
      val root = doc.getAs[BSONDocument]("contact-details").get
      val address = root.getAs[Address]("address").get
      val postCode = root.getAs[PostCode]("postCode").get
      val phone = root.getAs[PhoneNumber]("phone")
      val email = root.getAs[String]("email").get

      ContactDetailsWithId(id, address, postCode, email, phone)
    })
  }
}
