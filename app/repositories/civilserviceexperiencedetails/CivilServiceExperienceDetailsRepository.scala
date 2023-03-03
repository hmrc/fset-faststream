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

package repositories.civilserviceexperiencedetails

import javax.inject.{Inject, Singleton}
import model.CivilServiceExperienceDetails
import model.Exceptions.CannotUpdateCivilServiceExperienceDetails
import org.mongodb.scala.bson.{BsonArray, BsonDocument}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Projections}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import repositories.{CollectionNames, ReactiveRepositoryHelpers}

import scala.concurrent.{ExecutionContext, Future}

trait CivilServiceExperienceDetailsRepository {
  val CivilServiceExperienceDetailsDocumentKey = "civil-service-experience-details"

  def update(applicationId: String, civilServiceExperienceDetails: CivilServiceExperienceDetails): Future[Unit]
  def find(applicationId: String): Future[Option[CivilServiceExperienceDetails]]
  def evaluateFastPassCandidate(applicationId: String, accepted: Boolean): Future[Unit]
}

@Singleton
class CivilServiceExperienceDetailsMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext) extends
  PlayMongoRepository[CivilServiceExperienceDetails](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = CivilServiceExperienceDetails.mongoFormat,
    indexes = Nil
  ) with CivilServiceExperienceDetailsRepository with ReactiveRepositoryHelpers {

  override def update(applicationId: String, civilServiceExperienceDetails: CivilServiceExperienceDetails): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val updateBSON = Document("$set" -> Document(
      CivilServiceExperienceDetailsDocumentKey -> Codecs.toBson(civilServiceExperienceDetails)
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating civil service details",
      CannotUpdateCivilServiceExperienceDetails(applicationId))
    collection.updateOne(query, updateBSON).toFuture() map validator
  }

  override def find(applicationId: String): Future[Option[CivilServiceExperienceDetails]] = {
    val query = Filters.and(Filters.equal("applicationId", applicationId), Filters.exists(CivilServiceExperienceDetailsDocumentKey))
    val projection = Projections.include(CivilServiceExperienceDetailsDocumentKey)

    collection.find(query).projection(projection).headOption()
  }

  override def evaluateFastPassCandidate(applicationId: String, accepted: Boolean): Future[Unit] = {
    val query = BsonDocument("$and" -> BsonArray(
      BsonDocument("applicationId" -> applicationId),
      BsonDocument(s"$CivilServiceExperienceDetailsDocumentKey.fastPassReceived" -> true)
    ))
    val updateBSON = Document("$set" ->
      Document(s"$CivilServiceExperienceDetailsDocumentKey.fastPassAccepted" -> accepted)
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "evaluating fast pass candidate",
      CannotUpdateCivilServiceExperienceDetails(applicationId))
    collection.updateOne(query, updateBSON).toFuture() map validator
  }
}
