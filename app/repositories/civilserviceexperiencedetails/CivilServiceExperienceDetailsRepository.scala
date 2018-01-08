/*
 * Copyright 2018 HM Revenue & Customs
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

package repositories.civilserviceexperiencedetails

import model.CivilServiceExperienceDetails
import model.Exceptions.CannotUpdateCivilServiceExperienceDetails
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CivilServiceExperienceDetailsRepository {

  val CivilServiceExperienceDetailsDocumentKey = "civil-service-experience-details"

  def update(applicationId: String, civilServiceExperienceDetails: CivilServiceExperienceDetails): Future[Unit]

  def find(applicationId: String): Future[Option[CivilServiceExperienceDetails]]

  def evaluateFastPassCandidate(applicationId: String, accepted: Boolean): Future[Unit]
}

class CivilServiceExperienceDetailsMongoRepository(implicit mongo: () => DB) extends
  ReactiveRepository[CivilServiceExperienceDetails, BSONObjectID](CollectionNames.APPLICATION, mongo,
    CivilServiceExperienceDetails.civilServiceExperienceDetailsFormat, ReactiveMongoFormats.objectIdFormats)
  with CivilServiceExperienceDetailsRepository with ReactiveRepositoryHelpers {

  override def update(applicationId: String, civilServiceExperienceDetails: CivilServiceExperienceDetails): Future[Unit] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      CivilServiceExperienceDetailsDocumentKey -> civilServiceExperienceDetails
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating civil service details",
      CannotUpdateCivilServiceExperienceDetails(applicationId))

    collection.update(query, updateBSON) map validator
  }

  override def find(applicationId: String): Future[Option[CivilServiceExperienceDetails]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(CivilServiceExperienceDetailsDocumentKey -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](CivilServiceExperienceDetailsDocumentKey).isDefined =>
        document.getAs[CivilServiceExperienceDetails](CivilServiceExperienceDetailsDocumentKey)
      case _ => None
    }
  }

  override def evaluateFastPassCandidate(applicationId: String, accepted: Boolean): Future[Unit] = {

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(s"$CivilServiceExperienceDetailsDocumentKey.fastPassReceived" -> true)
    ))
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      s"$CivilServiceExperienceDetailsDocumentKey.fastPassAccepted" -> accepted
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "evaluating fast pass candidate",
      CannotUpdateCivilServiceExperienceDetails(applicationId))

    collection.update(query, updateBSON) map validator
  }
}
