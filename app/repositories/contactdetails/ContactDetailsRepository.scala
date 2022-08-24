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

import com.mongodb.client.model.{Projections, Updates}
import config.MicroserviceAppConfig
import model.Exceptions.{ContactDetailsNotFound, ContactDetailsNotFoundForEmail}
import model.persisted._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import repositories.{CollectionNames, ReactiveRepositoryHelpers, insertIfNoRecordFound}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ContactDetailsRepository {
  def update(userId: String, contactDetails: ContactDetails): Future[Unit]
  def find(userId: String): Future[ContactDetails]
  def findUserIdByEmail(email: String): Future[String]
  def findAll: Future[Seq[ContactDetailsWithId]]
  def findAllPostcodes(): Future[Map[String, String]]
  def findByPostCode(postCode: String): Future[Seq[ContactDetailsWithId]]
  def findByUserIds(userIds: Seq[String]): Future[Seq[ContactDetailsWithId]]
  def archive(originalUserId: String, userIdToArchiveWith: String): Future[Unit]
  def findEmails: Future[Seq[UserIdWithEmail]]
  def removeContactDetails(userId: String): Future[Unit]
}

@Singleton
class ContactDetailsMongoRepository @Inject() (mongo: MongoComponent, appConfig: MicroserviceAppConfig)
  extends PlayMongoRepository[ContactDetails](
    collectionName = CollectionNames.CONTACT_DETAILS,
    mongoComponent = mongo,
    domainFormat = ContactDetails.mongoFormat,
    indexes = Seq(
      IndexModel(ascending("userId"), IndexOptions().unique(true))
    )
  ) with ContactDetailsRepository with ReactiveRepositoryHelpers {

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val userIdCollection: MongoCollection[ContactDetailsUserId] =
    CollectionFactory.collection(
      collectionName = CollectionNames.CONTACT_DETAILS,
      db = mongo.database,
      domainFormat = ContactDetailsUserId.mongoFormat
    )

  val contactDetailsWithIdCollection: MongoCollection[ContactDetailsWithId] =
    CollectionFactory.collection(
      collectionName = CollectionNames.CONTACT_DETAILS,
      db = mongo.database,
      domainFormat = ContactDetailsWithId.mongoFormat
    )

  val userIdWithEmailCollection: MongoCollection[UserIdWithEmail] =
    CollectionFactory.collection(
      collectionName = CollectionNames.CONTACT_DETAILS,
      db = mongo.database,
      domainFormat = UserIdWithEmail.mongoFormat
    )

  val contactDetailsUserIdPostcodeCollection: MongoCollection[ContactDetailsUserIdPostcode] =
    CollectionFactory.collection(
      collectionName = CollectionNames.CONTACT_DETAILS,
      db = mongo.database,
      domainFormat = ContactDetailsUserIdPostcode.mongoFormat
    )

  val ContactDetailsDocumentKey = "contact-details"

  override def update(userId: String, contactDetails: ContactDetails): Future[Unit] = {
    val query = Document("userId" -> userId)
    val update = Updates.set(ContactDetailsDocumentKey, Codecs.toBson(contactDetails))

    val validator = singleUpsertValidator(userId, actionDesc = s"updating contact details for $userId")

    collection.updateOne(query, update, UpdateOptions().upsert(insertIfNoRecordFound)).toFuture() map validator
  }

  override def find(userId: String): Future[ContactDetails] = {
    val query = Document("userId" -> userId)
    val projection = Projections.include(ContactDetailsDocumentKey) // This is the sub-document key

    for {
      contactDetailsOpt <- collection.find(query).projection(projection).headOption()
    } yield {
      contactDetailsOpt match {
        case Some(cd) => cd
        case _ => throw ContactDetailsNotFound(userId)
      }
    }
  }

  // TODO: This works but it seems a bit wasteful to have the ContactDetailsUserId which just wraps a single string
  override def findUserIdByEmail(email: String): Future[String] = {
    val query = Document("contact-details.email" -> email)
    val projection = Projections.include("userId")

    for {
      userIdOpt <- userIdCollection.find(query).projection(projection).headOption()
    } yield {
      userIdOpt match {
        case Some(id) => id.userId
        case _ => throw ContactDetailsNotFoundForEmail()
      }
    }
  }

  override def findAll: Future[Seq[ContactDetailsWithId]] = {
    val query = Document.empty
    contactDetailsWithIdCollection.find(query).limit(appConfig.maxNumberOfDocuments).toFuture()
  }

  override def findAllPostcodes(): Future[Map[String, String]] = {
    val query = Document("contact-details.postCode" -> Document("$exists" -> true))
    val projection = Projections.include("userId", "contact-details.postCode")
    val result = contactDetailsUserIdPostcodeCollection.find(query).projection(projection).toFuture()

    result.map(_.map ( data => data.userId -> data.postcode ).toMap)
  }

  override def findByPostCode(postCode: String): Future[Seq[ContactDetailsWithId]] = {
    val query = Document("contact-details.postCode" -> postCode)
    contactDetailsWithIdCollection.find(query).toFuture
  }

  override def findByUserIds(userIds: Seq[String]): Future[Seq[ContactDetailsWithId]] = {
    val query = Document("userId" -> Document("$in" -> userIds))
    contactDetailsWithIdCollection.find(query).toFuture
  }

  override def archive(originalUserId: String, userIdToArchiveWith: String): Future[Unit] = {
    val query = Document("userId" -> originalUserId)

    val updateWithArchiveUserId = Document("$set" -> Document(
      "originalUserId" -> originalUserId,
      "userId" -> userIdToArchiveWith
    ))

    val validator = singleUpdateValidator(originalUserId, actionDesc = "archiving contact details")
    collection.updateOne(query, updateWithArchiveUserId).toFuture map validator
  }

  override def findEmails: Future[Seq[UserIdWithEmail]] = {
    val query = Document("contact-details" -> Document("$exists" -> true))
    userIdWithEmailCollection.find(query).toFuture
  }

  // Record is created after submitting Page 1 Personal details
  override def removeContactDetails(userId: String): Future[Unit] = {
    val query = Document("userId" -> userId)
    val validator = singleRemovalValidator(userId , actionDesc = s"deleting contact details for userId:$userId")
    collection.deleteOne(query).toFuture() map validator
  }
}
