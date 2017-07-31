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
import model.persisted.PassmarkEvaluation
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONArray, BSONObjectID }
import repositories.application.GeneralApplicationRepoBSONReader
import repositories.{ CollectionNames, RandomSelection, ReactiveRepositoryHelpers }
import repositories.competencyAverageResultHandler
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait AssessmentCentreRepository {
  def dateTime: DateTimeFactory
  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]]
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
) with AssessmentCentreRepository with RandomSelection with ReactiveRepositoryHelpers with GeneralApplicationRepoBSONReader {

  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]] = {
    implicit def applicationForFsacBsonReads(document: BSONDocument): ApplicationForFsac = {
      val applicationId = document.getAs[String]("applicationId").get
      val testGroupsRoot = document.getAs[BSONDocument]("testGroups").get
      val phase3PassMarks = testGroupsRoot.getAs[BSONDocument]("PHASE3").get
      val phase3Evaluation = phase3PassMarks.getAs[PassmarkEvaluation]("evaluation").get
      ApplicationForFsac(applicationId, phase3Evaluation)
    }

    def query = BSONDocument(
      "applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
      "testGroups.PHASE3.evaluation.result" -> BSONDocument("$elemMatch" -> BSONDocument(
        "schemeId" -> BSONDocument("$nin" -> siftableSchemeIds),
        "result" -> EvaluationResults.Green.toPassmark
      ))
    )

    selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForFsac).filter { app =>
      app.evaluationResult.result.filter(_.result == EvaluationResults.Green.toPassmark).forall(s => !siftableSchemeIds.contains(s.schemeId))
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
}
