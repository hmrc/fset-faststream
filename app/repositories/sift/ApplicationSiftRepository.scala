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
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ Amber, Green, Red, Withdrawn }
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
  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]]
  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]]
  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]]
  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult, settableFields: Seq[BSONDocument] = Nil ): Future[Unit]
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

  private def applicationForSiftBsonReads(document: BSONDocument): ApplicationForSift = {
    val applicationId = document.getAs[String]("applicationId").get
    val userId = document.getAs[String]("userId").get
    val appStatus = document.getAs[ApplicationStatus]("applicationStatus").get
    val currentSchemeStatus = document.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
    ApplicationForSift(applicationId, userId, appStatus, currentSchemeStatus)
  }

  def nextApplicationsForSiftStage(batchSize: Int): Future[List[ApplicationForSift]] = {
    val fsQuery = (route: ApplicationRoute) => BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationRoute" -> route),
      BSONDocument("applicationStatus" -> prevPhase),
      BSONDocument(s"testGroups.$prevTestGroup.evaluation.result" -> BSONDocument("$elemMatch" ->
        BSONDocument("schemeId" -> BSONDocument("$in" -> siftableSchemeIds),
        "result" -> EvaluationResults.Green.toString)
    ))))

    val xdipQuery = (route: ApplicationRoute) => BSONDocument(
      "applicationRoute" -> route,
      "applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED
    )

    val eligibleForSiftQuery = BSONDocument("$or" -> BSONArray(
      fsQuery(ApplicationRoute.Faststream),
      fsQuery(ApplicationRoute.SdipFaststream),
      xdipQuery(ApplicationRoute.Edip),
      xdipQuery(ApplicationRoute.Sdip)
    ))

    selectRandom[BSONDocument](eligibleForSiftQuery, batchSize).map {
      _.map { document => applicationForSiftBsonReads(document) }
    }
  }

  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]] = {
    val predicate = BSONDocument(
      "applicationStatus" -> ApplicationStatus.SIFT,
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      "currentSchemeStatus.result" -> Red.toString,
      "currentSchemeStatus.result" -> BSONDocument("$nin" -> BSONArray(Green.toString, Amber.toString))
    )

    selectOneRandom[BSONDocument](predicate).map {
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

  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult,
    settableFields: Seq[BSONDocument] = Nil
  ): Future[Unit] = {

    val update = BSONDocument(
      "$addToSet" -> BSONDocument(s"testGroups.$phaseName.evaluation.result" -> result),
      "$set" -> settableFields.foldLeft(BSONDocument(s"testGroups.$phaseName.evaluation.passmarkVersion" -> "2")) { (acc, doc) => acc ++ doc }
    )

    val predicate = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
      )
    ))
    val validator = singleUpdateValidator(applicationId, s"sifting for ${result.schemeId}", ApplicationNotFound(applicationId))

    collection.update(predicate, update) map validator
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
