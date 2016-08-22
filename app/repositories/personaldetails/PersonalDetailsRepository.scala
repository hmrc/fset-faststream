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

package repositories.personaldetails

import model.ApplicationStatus
import model.Exceptions.PersonalDetailsNotFound
import model.persisted.PersonalDetails
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PersonalDetailsRepository {
  def update(appId: String, userId: String, personalDetails: PersonalDetails,
             requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit]

  def updateWithoutStatusChange(appid: String, userId: String, personalDetails: PersonalDetails): Future[Unit]

  def find(appId: String): Future[PersonalDetails]
}

class PersonalDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[PersonalDetails, BSONObjectID]("application", mongo, PersonalDetails.personalDetailsFormat,
    ReactiveMongoFormats.objectIdFormats) with PersonalDetailsRepository {
  val PersonalDetailsCollection = "personal-details"

  def update(applicationId: String, userId: String, personalDetails: PersonalDetails,
             requiredStatuses: Seq[ApplicationStatus.Value], newApplicationStatus: ApplicationStatus.Value): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId, "userId" -> userId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> requiredStatuses))
    ))

    val personalDetailsBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> newApplicationStatus,
      "progress-status.personal-details" -> true,
      PersonalDetailsCollection -> personalDetails
    ))

    collection.update(query, personalDetailsBSON, upsert = false) map (_ => ())
  }

  def updateWithoutStatusChange(appid: String, userId: String, personalDetails: PersonalDetails): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> appid, "userId" -> userId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.WITHDRAWN))
    ))

    val personalDetailsBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.personal-details" -> true,
      PersonalDetailsCollection -> personalDetails
    ))

    collection.update(query, personalDetailsBSON, upsert = false) map (_ => ())
  }

  override def find(applicationId: String): Future[PersonalDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(PersonalDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](PersonalDetailsCollection).isDefined =>
        document.getAs[PersonalDetails](PersonalDetailsCollection).get
      case _ => throw PersonalDetailsNotFound(applicationId)
    }
  }
}
