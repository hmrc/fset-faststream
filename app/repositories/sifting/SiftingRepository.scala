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

package repositories.sifting

import model.Commands.{ Candidate, CreateApplicationRequest }
import model.EvaluationResults.Green
import model.Exceptions.ApplicationNotFound
import model.persisted.SchemeEvaluationResult
import model.{ ApplicationStatus, Commands, SchemeId }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.application.GeneralApplicationRepoBSONReader
import repositories.{ CollectionNames, CommonBSONDocuments, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SiftingRepository {

  val phaseName = "SIFT_PHASE"

  def findApplicationsReadyForSifting(schemeId: SchemeId): Future[List[Candidate]]

  def siftCandidateApplication(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
}

class SiftingMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
    Commands.Implicits.createApplicationRequestFormat,
    ReactiveMongoFormats.objectIdFormats) with SiftingRepository with RandomSelection with CommonBSONDocuments
    with GeneralApplicationRepoBSONReader with ReactiveRepositoryHelpers {


  override def findApplicationsReadyForSifting(schemeId: SchemeId): Future[List[Candidate]] = {
    val videoInterviewPassed = BSONDocument("testGroups.PHASE3.evaluation.result" ->
      BSONDocument("$elemMatch" -> BSONDocument("schemeId" -> schemeId.value, "result" -> Green.toString)))

    val notSiftedOnScheme = BSONDocument(
      s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(schemeId.value))
    )

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED),
      BSONDocument(s"progress-status.${ApplicationStatus.PHASE3_TESTS_PASSED}" -> true),
      BSONDocument(s"scheme-preferences.schemes" -> BSONDocument("$all" -> BSONArray(schemeId.value))),
      BSONDocument(s"withdraw" -> BSONDocument("$exists" -> false)),
      videoInterviewPassed,
      notSiftedOnScheme
    ))
    bsonCollection.find(query).cursor[Candidate]().collect[List]()
  }

  override def siftCandidateApplication(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {

    val update = BSONDocument(
      "$addToSet" -> BSONDocument(s"testGroups.$phaseName.evaluation.result" -> result),
      "$set" -> BSONDocument(s"testGroups.$phaseName.evaluation.passmarkVersion" -> "1")
    )

    val applicationNotSiftedForScheme = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
      )
    ))

    val validator = singleUpdateValidator(applicationId, s"submitting $phaseName results", ApplicationNotFound(applicationId))
    collection.update(applicationNotSiftedForScheme, update) map validator
  }
}
