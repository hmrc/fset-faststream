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
import model.persisted.fsac.AssessmentCentreTests
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.application.GeneralApplicationRepoBSONReader
import repositories.competencyAverageResultHandler
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
  def getTests(applicationId: String): Future[AssessmentCentreTests]
  def updateTests(applicationId: String, tests: AssessmentCentreTests): Future[Unit]
  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]]
  def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation): Future[Unit]
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

  val fsacKey = "FSAC"

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

  override def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]] = {

    val query =
      BSONDocument("$or" ->
        BSONArray(
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED}" -> true),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false))
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED}" -> true),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion))
            )
          )
        )
      )

    selectOneRandom[BSONDocument](query).map(_.map(doc => doc.getAs[String]("applicationId").get)
    )
  }

  override def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> evaluation.applicationId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.WITHDRAWN))
    ))

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "testGroups.FSAC.evaluation" -> BSONDocument("passmarkVersion" -> evaluation.passmarkVersion)
          .add(booleanToBSON("passedMinimumCompetencyLevel", evaluation.evaluationResult.passedMinimumCompetencyLevel))
          .add(BSONDocument("competency-average" -> evaluation.evaluationResult.competencyAverageResult))
          .add(BSONDocument("schemes-evaluation" -> evaluation.evaluationResult.schemesEvaluation))
      ))

    collection.update(query, passMarkEvaluation, upsert = false) map { _ => () }
  }

  private def booleanToBSON(schemeName: String, result: Option[Boolean]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r)
    case _ => BSONDocument.empty
  }

  def progressToAssessmentCentre(application: ApplicationForFsac, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "updating progress and app status")

    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(progressStatus)
        .add(cumulativeResultsForLatestPhaseBSON(application.siftEvaluationResult match {
          case Nil => application.phase3Evaluation.result
          case _ => application.siftEvaluationResult
        })))
    ) map validator
  }

  def getTests(applicationId: String): Future[AssessmentCentreTests] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("_id" -> 0, s"testGroups.$fsacKey.tests" -> 2)

    collection.find(query, projection).one[BSONDocument].map {
      case Some(bsonTests) => (for {
        testGroups <- bsonTests.getAs[BSONDocument]("testGroups")
        fsac <- testGroups.getAs[BSONDocument](fsacKey)
        tests <- fsac.getAs[AssessmentCentreTests]("tests")
      } yield tests).getOrElse(AssessmentCentreTests())

      case _ => AssessmentCentreTests()
    }
  }

  def updateTests(applicationId: String, tests: AssessmentCentreTests): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$set" -> BSONDocument(s"testGroups.$fsacKey.tests" -> tests))

    val validator = singleUpdateValidator(applicationId, actionDesc = "Updating assessment centre tests")

    collection.update(query, update) map validator
  }
}
