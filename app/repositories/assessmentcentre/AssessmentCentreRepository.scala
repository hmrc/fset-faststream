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

package repositories.assessmentcentre

import factories.DateTimeFactory
import model._
import model.command.{ ApplicationForFsac, ApplicationForSift }
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.application.GeneralApplicationRepoBSONReader
import repositories.{ CollectionNames, CommonBSONDocuments, CumulativeEvaluationHelper, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

trait AssessmentCentreRepository {
  def dateTime: DateTimeFactory
  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]]
  def progressToAssessmentCentre(application: ApplicationForFsac, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]
}

class AssessmentCentreMongoRepository (
  val dateTime: DateTimeFactory,
  val siftableSchemeIds: Seq[SchemeId]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with AssessmentCentreRepository with RandomSelection with ReactiveRepositoryHelpers with GeneralApplicationRepoBSONReader
    with CommonBSONDocuments with CumulativeEvaluationHelper {

  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]] = {
    implicit def applicationForFsacBsonReads(document: BSONDocument): ApplicationForFsac = {
      val applicationId = document.getAs[String]("applicationId").get
      val testGroupsRoot = document.getAs[BSONDocument]("testGroups").get
      val phase3Evaluation = testGroupsRoot.getAs[BSONDocument]("PHASE3").flatMap(_.getAs[PassmarkEvaluation]("evaluation")).get
      val siftEvaluation = testGroupsRoot.getAs[BSONDocument]("SIFT_PHASE").flatMap(_.getAs[BSONDocument]("evaluation")
        .flatMap(_.getAs[List[SchemeEvaluationResult]]("result"))).getOrElse(Nil)
      ApplicationForFsac(applicationId, phase3Evaluation, siftEvaluation)
    }

    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        "testGroups.PHASE3.evaluation.result" -> BSONDocument("$elemMatch" -> BSONDocument(
          "schemeId" -> BSONDocument("$nin" -> siftableSchemeIds),
          "result" -> EvaluationResults.Green.toString
        ))),
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
        BSONDocument(s"progress-status.${ProgressStatuses.ALL_SCHEMES_SIFT_COMPLETED}" -> true),
        BSONDocument("testGroups.SIFT_PHASE.evaluation.result" -> BSONDocument("$elemMatch" -> BSONDocument(
          "result" -> EvaluationResults.Green.toString)))
      ))
    ))

    val unfiltered = selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForFsac))
    unfiltered.map(_.filter { app =>
      app.siftEvaluationResult match {
        case Nil => app.phase3Evaluation.result.filter(_.result == EvaluationResults.Green.toString)
          .forall(s => !siftableSchemeIds.contains(s.schemeId))
        case _ => true
      }
    })
  }

  def progressToAssessmentCentre(application: ApplicationForFsac, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "updating progress and app status")

    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(progressStatus)
        .add(cumulativeResultsForLatestPhase(application.siftEvaluationResult match {
          case Nil => application.phase3Evaluation.result
          case _ => application.siftEvaluationResult
        })))
    ) map validator
  }
}
