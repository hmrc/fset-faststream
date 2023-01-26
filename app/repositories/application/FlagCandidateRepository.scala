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

package repositories.application

import model.FlagCandidatePersistedObject.FlagCandidate
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait FlagCandidateRepository {
  def tryGetCandidateIssue(appId: String): Future[Option[FlagCandidate]]
  def save(flagCandidate: FlagCandidate): Future[Unit]
  def remove(appId: String): Future[Unit]
}

@Singleton
class FlagCandidateMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[FlagCandidate](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = FlagCandidate.FlagCandidateFormats,
    indexes = Nil
  ) with FlagCandidateRepository with ReactiveRepositoryHelpers {

  override def tryGetCandidateIssue(appId: String): Future[Option[FlagCandidate]] = {
    val query = Document("applicationId" -> appId)
    val projection = Projections.include("applicationId", "issue")

    collection.find(query).projection(projection).first().toFutureOption().map {
      case flag @ Some(FlagCandidate(_, Some(_))) => flag
      case _ => None
    }
  }

  def save(flagCandidate: FlagCandidate): Future[Unit] = {
    val query = Document("applicationId" -> flagCandidate.applicationId)
    val update = Document("$set" -> Document("issue" -> flagCandidate.issue))

    val validator = singleUpdateValidator(flagCandidate.applicationId, actionDesc = "saving flag")
    collection.updateOne(query, update).toFuture().map(updateResult => validator(updateResult))
  }

  def remove(appId: String): Future[Unit] = {
    val filter = Document("applicationId" -> appId)
    val update = Document("$unset" -> Document("issue" -> ""))

    val validator = singleUpdateValidator(appId, actionDesc = "removing flag")
    collection.updateOne(filter, update).toFuture().map(updateResult => validator(updateResult))
  }
}
