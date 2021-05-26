/*
 * Copyright 2021 HM Revenue & Customs
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

package repositories.personaldetails

import factories.DateTimeFactory
import model.ApplicationStatus
import model.Exceptions.PersonalDetailsNotFound
import model.persisted.PersonalDetails
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Projections, Updates}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}
import repositories.{CollectionNames, CommonBSONDocuments, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PersonalDetailsRepository {
  def update(appId: String, userId: String, personalDetails: PersonalDetails,
             requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit]
  def updateWithoutStatusChange(appid: String, userId: String, personalDetails: PersonalDetails): Future[Unit]
  def find(appId: String): Future[PersonalDetails]
  def findByIds(appIds: Seq[String]): Future[Seq[(String, Option[PersonalDetails])]]
  def findByIds2(appIds: Seq[String]): Future[Seq[(String, Option[PersonalDetails])]]
}

@Singleton
class PersonalDetailsMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory, mongo: MongoComponent)
  extends PlayMongoRepository[PersonalDetails](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = PersonalDetails.mongoFormat,
    indexes = Nil
  ) with PersonalDetailsRepository with CommonBSONDocuments with ReactiveRepositoryHelpers {
  val PersonalDetailsDocumentKey = "personal-details"

  case class AppIdPersonalDetails(applicationId: String, personalDetails: Option[PersonalDetails])

  object AppIdPersonalDetails {
    val mongoFormat: Format[AppIdPersonalDetails] = (
      (__ \ "applicationId").format[String] and
        (__ \ PersonalDetails.root).formatNullable[PersonalDetails]
      )(AppIdPersonalDetails.apply, unlift(AppIdPersonalDetails.unapply))
  }

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val appIdPersonalDetailsCollection: MongoCollection[AppIdPersonalDetails] =
  CollectionFactory.collection(
    db = mongo.database,
    collectionName = CollectionNames.APPLICATION,
    domainFormat = AppIdPersonalDetails.mongoFormat
  )

  /*
  def update(applicationId: String, userId: String, personalDetails: PersonalDetails,
             requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId, "userId" -> userId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> requiredStatuses))
    ))

    val personalDetailsBSON = BSONDocument("$set" ->
      BSONDocument(
        "progress-status.personal-details" -> true,
        PersonalDetailsCollection -> personalDetails
      ).merge(
        applicationStatusBSON(newApplicationStatus)
      )
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating personal details",
      PersonalDetailsNotFound(applicationId))

    collection.update(ordered = false).one(query, personalDetailsBSON) map validator
  }*/
  override def update(applicationId: String, userId: String, personalDetails: PersonalDetails,
                      requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit] = {

    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId, "userId" -> userId),
      Document("applicationStatus" -> Document("$in" -> requiredStatuses.map(status => status.toBson)))
    ))

    val update = Updates.combine(
      Updates.set(s"progress-status.$PersonalDetailsDocumentKey", true),
      Updates.set(PersonalDetailsDocumentKey, Codecs.toBson(personalDetails)),
      applicationStatusBSON(newApplicationStatus)
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating personal details",
      PersonalDetailsNotFound(applicationId))

    collection.updateOne(query, update).toFuture() map validator
  }

  /*
  def updateWithoutStatusChange(appId: String, userId: String, personalDetails: PersonalDetails): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> appId, "userId" -> userId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.WITHDRAWN))
    ))

    val personalDetailsBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.personal-details" -> true,
      PersonalDetailsCollection -> personalDetails
    ))

    val validator = singleUpdateValidator(appId, actionDesc = "update personal details without status change")

    collection.update(ordered = false).one(query, personalDetailsBSON) map validator
  }*/
  override def updateWithoutStatusChange(appId: String, userId: String, personalDetails: PersonalDetails): Future[Unit] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> appId, "userId" -> userId),
      Document("applicationStatus" -> Document("$ne" -> ApplicationStatus.WITHDRAWN.toBson))
    ))

    val update = Updates.combine(
      Updates.set("progress-status.personal-details", true),
      // This uses the implicit json formatter in the PersonalDetails companion object from which the Bson
      // is automatically generated and the data stored in the db. Note that there is no root specified using this
      // mechanism so the data is stored under the sub-document key as expected. If we just used the registered
      // mongoFormat, it would include an additional document root (because it is defined in the mongoFormat)
      // The mongo format is therefore only used for reading the existing sub-document structure.
      Updates.set(PersonalDetailsDocumentKey, Codecs.toBson(personalDetails))
    )

    val validator = singleUpdateValidator(appId, actionDesc = "update personal details without status change")

    collection.updateOne(query, update).toFuture() map validator
  }

  /*
  override def find(applicationId: String): Future[PersonalDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(PersonalDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](PersonalDetailsCollection).isDefined =>
        document.getAs[PersonalDetails](PersonalDetailsCollection).get
      case _ => throw PersonalDetailsNotFound(applicationId)
    }
  }*/
  override def find(applicationId: String): Future[PersonalDetails] = {
    val query = Filters.and(
      Filters.equal("applicationId", applicationId),
      Filters.exists(PersonalDetailsDocumentKey)
    )
    val projection = Projections.include(PersonalDetailsDocumentKey)

    for(
      personalDetailsOpt <- collection.find(query).projection(projection).headOption()
    ) yield
      personalDetailsOpt match {
        case Some(pd) => pd
        case _ => throw PersonalDetailsNotFound(applicationId)
      }
  }

  def findByIds2(applicationIds: Seq[String]): Future[Seq[(String, Option[PersonalDetails])]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.include("applicationId", PersonalDetailsDocumentKey)

    val personalDetailsCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
    personalDetailsCollection.find(query).projection(projection).toFuture().map { docList =>
      docList.map { doc =>
        //scalastyle:off
        println(s"**** findByIds2 - $doc")
        //scalastyle:on
        val appId = doc.getString("applicationId")
        val personalDetailsOpt = None
        // Results in compilation error:
        // type arguments [model.persisted.PersonalDetails] do not conform to method get's type parameter bounds
        // [TResult <: org.mongodb.scala.bson.BsonValue]
//        val personalDetailsOpt = doc.get[PersonalDetails](PersonalDetailsDocumentKey)
        //scalastyle:off

//        doc.get("personal-details", Document.class)

//        val xx = doc.getEmbedded("personal-details.firstName")
//        println(s"**** firstName=$xx")
        //scalastyle:on
        appId -> personalDetailsOpt
      }
    }
  }

  override def findByIds(applicationIds: Seq[String]): Future[Seq[(String, Option[PersonalDetails])]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.include("applicationId", PersonalDetailsDocumentKey)

    appIdPersonalDetailsCollection.find(query).projection(projection).toFuture().map {
      _.map { doc => doc.applicationId -> doc.personalDetails }
    }
  }

//  override def findByIds(applicationIds: Seq[String]): Future[List[(String, Option[PersonalDetails])]] = ???

  /*
  override def findByIds(applicationIds: Seq[String]): Future[List[(String, Option[PersonalDetails])]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    val projection = BSONDocument(
      "applicationId" -> 1,
      PersonalDetailsCollection -> 1, "_id" -> 0
    )

    collection.find(query, Some(projection)).cursor[BSONDocument]()
      .collect[List](maxDocs = -1, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      docs.map { doc =>
        val appId = doc.getAs[String]("applicationId").get
        val personalDetailsOpt = doc.getAs[PersonalDetails](PersonalDetailsCollection)
        (appId, personalDetailsOpt)
      }
    }
  }*/
}
