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
import model.SchemeType.SchemeType
import model.persisted.SchemeEvaluationResult
import model.{ ApplicationStatus, Commands }
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

  def findSiftingEligible(chosenSchema: SchemeType): Future[List[Candidate]]

  def siftCandidate(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
}

class SiftingMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
    Commands.Implicits.createApplicationRequestFormat,
    ReactiveMongoFormats.objectIdFormats) with SiftingRepository with RandomSelection with CommonBSONDocuments
    with GeneralApplicationRepoBSONReader with ReactiveRepositoryHelpers {


  /**
    * TODO: implement all criterias
    * Criterias:
    * 1. Is in the PHASE_3_TESTS_PASSED state
    * - has not yet been sifted
    * - has not completed sift
    * - is not in SILFT_FILTER_COMPLETED state - TODO:
    * - has not been invited to FSAC - TODO:
    * 2. Has selected the scheme as a preference
    * 3. Has GREEN for the scheme at Video Interview
    * 4. Has not Withdrawn application
    * 5. Has not Withdrawn from the scheme
    */

  override def findSiftingEligible(chosenSchema: SchemeType): Future[List[Candidate]] = {
    val videoInterviewPassed = BSONDocument("testGroups.PHASE3.evaluation.result" ->
      BSONDocument("$elemMatch" -> BSONDocument("scheme" -> chosenSchema, "result" -> Green.toString)))

    val notSiftedOnScheme = BSONDocument("$or" -> BSONArray(
      BSONDocument(s"testGroups.$phaseName.evaluation.result" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"testGroups.$phaseName.evaluation.result" ->
        BSONDocument("$elemMatch" -> BSONDocument("scheme" -> chosenSchema, "$exists" -> false))
    )))

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED),
      BSONDocument(s"progress-status.${ApplicationStatus.PHASE3_TESTS_PASSED}" -> true),
      BSONDocument(s"scheme-preferences.schemes" -> BSONDocument("$all" -> BSONArray(chosenSchema))),
      BSONDocument(s"withdraw" -> BSONDocument("$exists" -> false)),
      videoInterviewPassed,
      notSiftedOnScheme
    ))
    bsonCollection.find(query).cursor[Candidate]().collect[List]()
  }

  override def siftCandidate(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {

    val update = BSONDocument(
      "$addToSet" -> BSONDocument(s"testGroups.$phaseName.evaluation.result" -> result),
      "$set" -> BSONDocument(s"testGroups.$phaseName.evaluation.passmarkVersion" -> "1")
    )

    val find = BSONDocument("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId, s"submitting $phaseName results", ApplicationNotFound(applicationId))
    collection.update(find, update) map validator
  }
}
