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

package repositories.sift

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Commands.Candidate
import model.Commands.Implicits.createApplicationRequestFormat
import model.EvaluationResults.Green
import model.Exceptions.ApplicationNotFound
import model.command.ApplicationForSift
import model._
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.application.GeneralApplicationRepoBSONReader
import repositories.{ CollectionNames, CommonBSONDocuments, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ApplicationSiftRepository extends RandomSelection with ReactiveRepositoryHelpers with GeneralApplicationRepoBSONReader {
  this: ReactiveRepository[_, _] =>

  def thisApplicationStatus: ApplicationStatus
  def dateTime: DateTimeFactory
  def siftableSchemeIds: Seq[SchemeId]
  val phaseName = "SIFT_PHASE"

  def nextApplicationsForSiftStage(maxBatchSize: Int): Future[List[ApplicationForSift]]

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]]
  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit]

}

class ApplicationSiftMongoRepository(
  val dateTime: DateTimeFactory,
  val siftableSchemeIds: Seq[SchemeId]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with ApplicationSiftRepository {

  val thisApplicationStatus = ApplicationStatus.SIFT
  val prevPhase = ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
  val prevTestGroup = "PHASE3"

  val eligibleForSiftQuery = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationStatus" -> prevPhase),
    BSONDocument(s"testGroups.$prevTestGroup.evaluation.result" -> BSONDocument("$elemMatch" ->
      BSONDocument("schemeId" -> BSONDocument("$in" -> siftableSchemeIds),
      "result" -> EvaluationResults.Green.toString)
  ))))

  def nextApplicationsForSiftStage(batchSize: Int): Future[List[ApplicationForSift]] = {
    selectRandom[BSONDocument](eligibleForSiftQuery, batchSize).map {
      _.map { document =>
        val applicationId = document.getAs[String]("applicationId").get
        val testGroupsRoot = document.getAs[BSONDocument]("testGroups").get
        val phase3PassMarks = testGroupsRoot.getAs[BSONDocument](prevTestGroup).get
        val phase3Evaluation = phase3PassMarks.getAs[PassmarkEvaluation]("evaluation").get
        ApplicationForSift(applicationId, phase3Evaluation)
      }
    }
  }

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]] = {
    val prevPhaseSchemePassed = BSONDocument(s"cumulativeEvaluation.${schemeId.value}" -> Green.toString)

    val notSiftedOnScheme = BSONDocument(
      s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(schemeId.value))
    )

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED}" -> true),
      BSONDocument(s"scheme-preferences.schemes" -> BSONDocument("$all" -> BSONArray(schemeId.value))),
      BSONDocument(s"withdraw" -> BSONDocument("$exists" -> false)),
      prevPhaseSchemePassed,
      notSiftedOnScheme
    ))
    bsonCollection.find(query).cursor[Candidate]().collect[List]()
  }

  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {

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
