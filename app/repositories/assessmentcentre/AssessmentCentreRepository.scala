/*
 * Copyright 2018 HM Revenue & Customs
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
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ AssessmentEvaluationResult, CompetencyAverageResult }
import model.Exceptions.NotFoundException
import model._
import model.command.{ ApplicationForProgression, ApplicationForSift }
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.AssessmentCentreTests
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories._
import repositories.application.GeneralApplicationRepoBSONReader
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

object AssessmentCentreRepository {
  implicit def applicationForFsacBsonReads(document: BSONDocument): ApplicationForProgression = {
    val applicationId = document.getAs[String]("applicationId").get
    val appStatus = document.getAs[ApplicationStatus]("applicationStatus").get
    val currentSchemeStatus = document.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
    ApplicationForProgression(applicationId, appStatus, currentSchemeStatus)
  }
}

trait AssessmentCentreRepository {
  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def progressToAssessmentCentre(application: ApplicationForProgression, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]
  def getTests(applicationId: String): Future[AssessmentCentreTests]
  def updateTests(applicationId: String, tests: AssessmentCentreTests): Future[Unit]
  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String, batchSize: Int): Future[List[UniqueIdentifier]]
  def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]]
  def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation,
    currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
  def getFsacEvaluationResultAverages(applicationId: String): Future[Option[CompetencyAverageResult]]
  def getFsacEvaluatedSchemes(applicationId: String): Future[Option[Seq[SchemeEvaluationResult]]]
  def removeFsacEvaluation(applicationId: String): Future[Unit]
}

class AssessmentCentreMongoRepository (
  val dateTimeFactory: DateTimeFactory,
  val siftableSchemeIds: Seq[SchemeId]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with AssessmentCentreRepository with RandomSelection with ReactiveRepositoryHelpers with GeneralApplicationRepoBSONReader
    with CommonBSONDocuments with CurrentSchemeStatusHelper {

  val fsacKey = "FSAC"

  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads

    val fastStreamNoSiftableSchemes = BSONDocument(
      "applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
      "currentSchemeStatus" -> BSONDocument("$elemMatch" -> BSONDocument(
        "schemeId" -> BSONDocument("$nin" -> siftableSchemeIds),
        "result" -> EvaluationResults.Green.toString
      )))

    val query = BSONDocument("$or" -> BSONArray(
      fastStreamNoSiftableSchemes,
      BSONDocument(
        "applicationStatus" -> ApplicationStatus.SIFT,
        s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
        "currentSchemeStatus" -> BSONDocument("$elemMatch" ->
          BSONDocument("result" -> EvaluationResults.Green.toString, "schemeId" -> BSONDocument("$nin" -> BSONArray(Scheme.Sdip, Scheme.Edip)))
        )
      )
    ))

    val unfiltered = selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForProgression))
    unfiltered.map(_.filter { app =>
      app.applicationStatus match {
        case ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED => app.currentSchemeStatus.filter(_.result == EvaluationResults.Green.toString)
            .forall(s => !siftableSchemeIds.contains(s.schemeId))
        case ApplicationStatus.SIFT => app.currentSchemeStatus.exists(_.result == EvaluationResults.Green.toString)
      }
    })
  }

  override def nextApplicationReadyForAssessmentScoreEvaluation(
    currentPassmarkVersion: String,
    batchSize: Int): Future[List[UniqueIdentifier]] = {
    val query =
      BSONDocument("$or" ->
        BSONArray(
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED}" -> true),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
              BSONDocument(s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> BSONDocument("$exists" -> false)),
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED}" -> BSONDocument("$exists" -> false)),
              BSONDocument(s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> BSONDocument("$exists" -> false))
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED}" -> true),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion)),
              BSONDocument(s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> BSONDocument("$exists" -> false)),
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED}" -> BSONDocument("$exists" -> false)),
              BSONDocument(s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> BSONDocument("$exists" -> false))
            )
          )
        )
      )

    selectRandom[BSONDocument](query, batchSize).map(docs => docs.map(doc => doc.getAs[UniqueIdentifier]("applicationId").get))
  }

  override def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("FSAC.evaluation" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument].map {
      case Some(doc) =>
        doc.getAs[BSONDocument]("testGroups")
          .flatMap(_.getAs[BSONDocument]("FSAC")
            .flatMap(_.getAs[BSONDocument]("evaluation"))).map { evaluationDoc =>
          AssessmentPassMarkEvaluation(
            UniqueIdentifier(applicationId),
            passmarkVersion = doc.getAs[String]("passmarkVersion").get,
            evaluationResult = AssessmentEvaluationResult(
              doc.getAs[Boolean]("passedMinimumCompetencyLevel"),
              doc.getAs[CompetencyAverageResult]("competency-average").get,
              doc.getAs[Seq[SchemeEvaluationResult]]("schemes-evaluation").get
            )
          )
        }
    }
  }

  override def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation,
    currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {
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
      ).add(currentSchemeStatusBSON(currentSchemeStatus)))

    collection.update(query, passMarkEvaluation, upsert = false) map { _ => () }
  }

  private def booleanToBSON(schemeName: String, result: Option[Boolean]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r)
    case _ => BSONDocument.empty
  }

  def progressToAssessmentCentre(application: ApplicationForProgression, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to assessment centre")

    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(progressStatus)
    )) map validator
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

  override def getFsacEvaluationResultAverages(applicationId: String): Future[Option[CompetencyAverageResult]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("_id" -> false, s"testGroups.$fsacKey.evaluation.competency-average" -> true)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) =>
        for {
          testGroups <- document.getAs[BSONDocument]("testGroups")
          fsac <- testGroups.getAs[BSONDocument](fsacKey)
          evaluation <- fsac.getAs[BSONDocument]("evaluation")
          competencyAverage <- evaluation.getAs[CompetencyAverageResult]("competency-average")
        } yield competencyAverage
      case None => None
    }
  }

  override def getFsacEvaluatedSchemes(applicationId: String): Future[Option[Seq[SchemeEvaluationResult]]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("_id" -> false, s"testGroups.$fsacKey.evaluation.schemes-evaluation" -> true)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) =>
        for {
          testGroups <- document.getAs[BSONDocument]("testGroups")
          fsac <- testGroups.getAs[BSONDocument](fsacKey)
          evaluation <- fsac.getAs[BSONDocument]("evaluation")
          schemesEvaluation <- evaluation.getAs[Seq[SchemeEvaluationResult]]("schemes-evaluation")
        } yield schemesEvaluation
      case None => None
    }
  }

  def updateTests(applicationId: String, tests: AssessmentCentreTests): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$set" -> BSONDocument(s"testGroups.$fsacKey.tests" -> tests))

    val validator = singleUpdateValidator(applicationId, actionDesc = "Updating assessment centre tests")

    collection.update(query, update) map validator
  }

  override def removeFsacEvaluation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateOp = bsonCollection.updateModifier(
      BSONDocument(
        "$unset" -> BSONDocument(s"testGroups.$fsacKey" -> "")
      )
    )

    bsonCollection.findAndModify(query, updateOp).map{ result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }
}
