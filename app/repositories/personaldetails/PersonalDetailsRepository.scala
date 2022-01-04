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

package repositories.personaldetails

import factories.DateTimeFactory
import javax.inject.{ Inject, Singleton }
import model.ApplicationStatus
import model.Exceptions.PersonalDetailsNotFound
import model.persisted.PersonalDetails
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{ Cursor, DB }
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ CollectionNames, CommonBSONDocuments, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PersonalDetailsRepository {
  def update(appId: String, userId: String, personalDetails: PersonalDetails,
             requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit]
  def updateWithoutStatusChange(appid: String, userId: String, personalDetails: PersonalDetails): Future[Unit]
  def find(appId: String): Future[PersonalDetails]
  def findByIds(appIds: Seq[String]): Future[List[(String, Option[PersonalDetails])]]
}

@Singleton //TODO:fix CommonBSONDocuments2
class PersonalDetailsMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory, mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[PersonalDetails, BSONObjectID](
    CollectionNames.APPLICATION, mongoComponent.mongoConnector.db, PersonalDetails.personalDetailsFormat,
    ReactiveMongoFormats.objectIdFormats) with PersonalDetailsRepository with CommonBSONDocuments with ReactiveRepositoryHelpers {
  val PersonalDetailsCollection = "personal-details"

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
  }

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
  }

  override def find(applicationId: String): Future[PersonalDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(PersonalDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](PersonalDetailsCollection).isDefined =>
        document.getAs[PersonalDetails](PersonalDetailsCollection).get
      case _ => throw PersonalDetailsNotFound(applicationId)
    }
  }

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
  }
}
