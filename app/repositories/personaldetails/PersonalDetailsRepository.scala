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

package repositories.personaldetails

import factories.DateTimeFactory
import model.ApplicationStatus
import model.Exceptions.PersonalDetailsNotFound
import model.persisted.PersonalDetails
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Projections, Updates}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import repositories.{CollectionNames, CommonBSONDocuments, ReactiveRepositoryHelpers, subDocRoot}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait PersonalDetailsRepository {
  def update(appId: String, userId: String, personalDetails: PersonalDetails,
             requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit]
  def updateWithoutStatusChange(appid: String, userId: String, personalDetails: PersonalDetails): Future[Unit]
  def find(appId: String): Future[PersonalDetails]
  def findByIds(appIds: Seq[String]): Future[Seq[(String, Option[PersonalDetails])]]
}

@Singleton
class PersonalDetailsMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory, mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[PersonalDetails](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = PersonalDetails.mongoFormat,
    indexes = Nil
  ) with PersonalDetailsRepository with CommonBSONDocuments with ReactiveRepositoryHelpers {

  val PersonalDetailsDocumentKey = "personal-details"

  override def update(applicationId: String, userId: String, personalDetails: PersonalDetails,
                      requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit] = {

    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId, "userId" -> userId),
      Document("applicationStatus" -> Document("$in" -> requiredStatuses.map(status => status.toBson)))
    ))

    val update = Document("$set" ->
      (Document(
        "progress-status.personal-details" -> true,
        PersonalDetailsDocumentKey -> Codecs.toBson(personalDetails)
      ) ++ applicationStatusBSON(newApplicationStatus))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating personal details",
      PersonalDetailsNotFound(applicationId))

    collection.updateOne(query, update).toFuture() map validator
  }

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

  override def findByIds(applicationIds: Seq[String]): Future[Seq[(String, Option[PersonalDetails])]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Document("applicationId" -> true, PersonalDetailsDocumentKey -> true)

    collection.find[Document](query).projection(projection).toFuture().map { docList =>
      docList.map { doc =>
        val appId = doc.get("applicationId").get.asString().getValue
        val personalDetailsOpt = subDocRoot(PersonalDetailsDocumentKey)(doc).map ( doc => Codecs.fromBson[PersonalDetails](doc) )
        (appId, personalDetailsOpt)
      }
    }
  }
}
