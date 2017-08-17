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
import model._
import model.Exceptions.ApplicationNotFound
import model.command.ApplicationForSift
import model.persisted.SchemeEvaluationResult
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.application.GeneralApplicationRepoBSONReader
import repositories.{ CollectionNames, CurrentSchemeStatusHelper, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ApplicationSiftRepository {

  def thisApplicationStatus: ApplicationStatus
  def dateTime: DateTimeFactory
  def siftableSchemeIds: Seq[SchemeId]
  val phaseName = "SIFT_PHASE"

  def nextApplicationsForSiftStage(maxBatchSize: Int): Future[List[ApplicationForSift]]

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]]
  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]]
  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def siftApplicationForSchemeBSON(applicationId: String, result: SchemeEvaluationResult): (BSONDocument, BSONDocument)

  def update(applicationId: String, predicate: BSONDocument, update: BSONDocument, action: String): Future[Unit]

}

class ApplicationSiftMongoRepository(
  val dateTime: DateTimeFactory,
  val siftableSchemeIds: Seq[SchemeId]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with ApplicationSiftRepository with CurrentSchemeStatusHelper with RandomSelection with ReactiveRepositoryHelpers
  with GeneralApplicationRepoBSONReader
{

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
    def applicationForSiftBsonReads(document: BSONDocument): ApplicationForSift = {
      val applicationId = document.getAs[String]("applicationId").get
      val appStatus = document.getAs[ApplicationStatus]("applicationStatus").get
      val currentSchemeStatus = document.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
      ApplicationForSift(applicationId, appStatus, currentSchemeStatus)
    }

    selectRandom[BSONDocument](eligibleForSiftQuery, batchSize).map {
      _.map { document => applicationForSiftBsonReads(document) }
    }
  }

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]] = {

    val notSiftedOnScheme = BSONDocument(
      s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(schemeId.value))
    )

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> true),
      currentSchemeStatusGreen(schemeId),
      notSiftedOnScheme
    ))
    bsonCollection.find(query).cursor[Candidate]().collect[List]()
  }

  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val (predicate, update) = siftApplicationForSchemeBSON(applicationId, result)

    val validator = singleUpdateValidator(applicationId, s"sifting for ${result.schemeId}", ApplicationNotFound(applicationId))
    collection.update(predicate, update) map validator
  }

   def siftApplicationForSchemeBSON(applicationId: String, result: SchemeEvaluationResult): (BSONDocument, BSONDocument) = {

     val update = BSONDocument(
       "$addToSet" -> BSONDocument(s"testGroups.$phaseName.evaluation.result" -> result),
       "$set" -> BSONDocument(s"testGroups.$phaseName.evaluation.passmarkVersion" -> "1")
     )

     val predicate = BSONDocument("$and" -> BSONArray(
       BSONDocument("applicationId" -> applicationId),
       BSONDocument(
         s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
       )
     ))

     (predicate, update)
   }

  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    val predicate = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("_id" -> 0, s"testGroups.$phaseName.evaluation.result" -> 1)

    collection.find(predicate, projection).one[BSONDocument].map(_.flatMap { doc =>
      doc.getAs[BSONDocument]("testGroups")
        .flatMap { _.getAs[BSONDocument](phaseName) }
        .flatMap { _.getAs[BSONDocument]("evaluation") }
        .flatMap { _.getAs[Seq[SchemeEvaluationResult]]("result") }
    }.getOrElse(Nil))
  }

  def update(applicationId: String, predicate: BSONDocument, update: BSONDocument, action: String): Future[Unit] = {
    val validator = singleUpdateValidator(applicationId, action)
    collection.update(predicate, update) map validator
  }
}
