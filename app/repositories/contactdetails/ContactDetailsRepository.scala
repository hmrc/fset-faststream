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

package repositories.contactdetails

import config.MicroserviceAppConfig
import javax.inject.{ Inject, Singleton }
import model.Address
import model.Commands._
import model.Exceptions.{ ContactDetailsNotFound, ContactDetailsNotFoundForEmail }
import model.persisted.{ ContactDetails, ContactDetailsWithId, UserIdWithEmail }
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{ Cursor, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ContactDetailsRepository {
  def update(userId: String, contactDetails: ContactDetails): Future[Unit]
  def find(userId: String): Future[ContactDetails]
  def findUserIdByEmail(email: String): Future[String]
  def findAll: Future[List[ContactDetailsWithId]]
  def findAllPostcodes(): Future[Map[String, String]]
  def findByPostCode(postCode: String): Future[List[ContactDetailsWithId]]
  def findByUserIds(userIds: List[String]): Future[List[ContactDetailsWithId]]
  def archive(originalUserId: String, userIdToArchiveWith: String): Future[Unit]
  def findEmails: Future[List[UserIdWithEmail]]
  def removeContactDetails(userId: String): Future[Unit]
}

@Singleton
class ContactDetailsMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent, appConfig: MicroserviceAppConfig)
  extends ReactiveRepository[ContactDetails, BSONObjectID](
    CollectionNames.CONTACT_DETAILS, mongoComponent.mongoConnector.db, ContactDetails.contactDetailsFormat,
    ReactiveMongoFormats.objectIdFormats) with ContactDetailsRepository with ReactiveRepositoryHelpers {

  val ContactDetailsDocumentKey = "contact-details"

  private val unlimitedMaxDocs = -1

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("userId", Ascending)), unique = true)
  )

  override def update(userId: String, contactDetails: ContactDetails): Future[Unit] = {
    val query = BSONDocument("userId" -> userId)
    val contactDetailsBson = BSONDocument("$set" -> BSONDocument(ContactDetailsDocumentKey -> contactDetails))

    val validator = singleUpsertValidator(userId, actionDesc = s"updating contact details for $userId")

    collection.update(ordered = false).one(query, contactDetailsBson, upsert = true) map validator
  }

  override def find(userId: String): Future[ContactDetails] = {
    val query = BSONDocument("userId" -> userId)
    val projection = BSONDocument(ContactDetailsDocumentKey -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("contact-details").isDefined =>
        document.getAs[ContactDetails]("contact-details").get
      case None => throw ContactDetailsNotFound(userId)
    }
  }

  override def findUserIdByEmail(email: String): Future[String] = {
    val query = BSONDocument("contact-details.email" -> email)
    val projection = BSONDocument("userId" -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(d) if d.getAs[String]("userId").isDefined =>
        d.getAs[String]("userId").get
      case None => throw ContactDetailsNotFoundForEmail()
    }
  }

  override def findAll: Future[List[ContactDetailsWithId]] = {
    val query = BSONDocument()

    collection.find(query, projection = Option.empty[JsObject]).cursor[BSONDocument]()
      .collect[List](appConfig.maxNumberOfDocuments, Cursor.FailOnError[List[BSONDocument]]()).map(_.map { doc =>
      val id = doc.getAs[String]("userId").get
      val root = doc.getAs[BSONDocument]("contact-details").get
      val outsideUk = root.getAs[Boolean]("outsideUk").getOrElse(false)
      val address = root.getAs[Address]("address").get
      val postCode = root.getAs[PostCode]("postCode")
      val phone = root.getAs[PhoneNumber]("phone")
      val email = root.getAs[String]("email").get

      ContactDetailsWithId(id, address, postCode, outsideUk, email, phone)
    })
  }

  override def findAllPostcodes(): Future[Map[String, String]] = {
    val query = BSONDocument("contact-details.postCode" -> BSONDocument("$exists" -> true))
    val projection = BSONDocument("userId" -> 1, "contact-details.postCode" -> 1)
    implicit val tupleReads: Reads[(String, String)] = (
      (JsPath \ "userId").read[String] and
        (JsPath \ "contact-details" \ "postCode").read[String]
      )((_, _))
    val result = collection.find(query, Some(projection)).cursor[(String, String)](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[(String, String)]]())
    result.map(_.toMap)
  }

  override def findByPostCode(postCode: String): Future[List[ContactDetailsWithId]] = {

    val query = BSONDocument("contact-details.postCode" -> postCode)

    collection.find(query, projection = Option.empty[JsObject]).cursor[BSONDocument]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map(_.map { doc =>
      val id = doc.getAs[String]("userId").get
      val root = doc.getAs[BSONDocument]("contact-details").get
      val outsideUk = root.getAs[Boolean]("outsideUk").getOrElse(false)
      val address = root.getAs[Address]("address").get
      val postCode = root.getAs[PostCode]("postCode")
      val phone = root.getAs[PhoneNumber]("phone")
      val email = root.getAs[String]("email").get

      ContactDetailsWithId(id, address, postCode, outsideUk, email, phone)
    })
  }

  override def findByUserIds(userIds: List[String]): Future[List[ContactDetailsWithId]] = {
    val query = BSONDocument("userId" -> BSONDocument("$in" -> userIds))

    collection.find(query, projection = Option.empty[JsObject]).cursor[BSONDocument]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map(_.map { doc =>
      val id = doc.getAs[String]("userId").get
      val root = doc.getAs[BSONDocument]("contact-details").getOrElse(throw new Exception(s"Contact details not found for $id"))
      val outsideUk = root.getAs[Boolean]("outsideUk").getOrElse(throw new Exception(s"Outside UK not found for $id"))
      val address = root.getAs[Address]("address").getOrElse(throw new Exception(s"Address not found for $id"))
      val postCode = root.getAs[PostCode]("postCode")
      val phone = root.getAs[PhoneNumber]("phone")
      val email = root.getAs[String]("email").getOrElse(throw new Exception(s"Email not found for $id"))

      ContactDetailsWithId(id, address, postCode, outsideUk, email, phone)
    })
  }

  override def archive(originalUserId: String, userIdToArchiveWith: String): Future[Unit] = {
    val query = BSONDocument("userId" -> originalUserId)

    val updateWithArchiveUserId = BSONDocument("$set" -> BSONDocument(
      "originalUserId" -> originalUserId,
      "userId" -> userIdToArchiveWith
    ))

    val validator = singleUpdateValidator(originalUserId, actionDesc = "archiving contact details")
    collection.update(ordered = false).one(query, updateWithArchiveUserId) map validator
  }

  override def findEmails: Future[List[UserIdWithEmail]] = {
    val query = BSONDocument("contact-details" -> BSONDocument("$exists" -> true))
    val projection = BSONDocument(
      "userId" -> 1,
      "contact-details.email" -> 1,
      "_id" -> 0
    )

    collection.find(query, Some(projection)).cursor[BSONDocument]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map(_.map { doc =>
      val id = doc.getAs[String]("userId").get
      val root = doc.getAs[BSONDocument]("contact-details").get
      val email = root.getAs[String]("email").get

      UserIdWithEmail(id, email)
    })
  }

  override def removeContactDetails(userId: String): Future[Unit] = {
    val query = BSONDocument("userId" -> userId)
    collection.delete().one(query, limit = Some(1)).map(_ => ())
  }
}
