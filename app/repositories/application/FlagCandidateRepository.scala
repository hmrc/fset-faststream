/*
 * Copyright 2017 HM Revenue & Customs
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

import model.Exceptions.NotFoundException
import model.FlagCandidatePersistedObject.FlagCandidate
import reactivemongo.api.DB
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FlagCandidateRepository {

  def tryGetCandidateIssue(appId: String): Future[Option[FlagCandidate]]

  def save(flagCandidate: FlagCandidate): Future[Unit]

  def remove(appId: String): Future[Unit]
}

class FlagCandidateMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[FlagCandidate, BSONObjectID](CollectionNames.APPLICATION, mongo,
      FlagCandidate.FlagCandidateFormats, ReactiveMongoFormats.objectIdFormats) with FlagCandidateRepository
    with ReactiveRepositoryHelpers {

  def tryGetCandidateIssue(appId: String): Future[Option[FlagCandidate]] = {
    val query = BSONDocument("applicationId" -> appId)
    val projection = BSONDocument("applicationId" -> 1, "issue" -> 1)

    collection.find(query, projection).one[BSONDocument].map { docOpt =>
      docOpt.map(flagCandidateHandler.read) match {
        case flag @ Some(FlagCandidate(_, Some(_))) => flag
        case _ => None
      }
    }
  }

  def save(flagCandidate: FlagCandidate): Future[Unit] = {
    val query = BSONDocument("applicationId" -> flagCandidate.applicationId)
    val result = BSONDocument("$set" -> BSONDocument(
      "issue" -> flagCandidate.issue
    ))

    val validator = singleUpdateValidator(flagCandidate.applicationId, actionDesc = "saving flag")

    collection.update(query, result) map validator
  }

  def remove(appId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> appId)
    val result = BSONDocument("$unset" -> BSONDocument("issue" -> ""))

    val validator = singleUpdateValidator(appId, actionDesc = "removing flag")
    collection.update(query, result) map validator
  }

}
