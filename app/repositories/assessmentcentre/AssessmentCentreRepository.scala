/*
 * Copyright 2020 HM Revenue & Customs
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
import model.EvaluationResults.{ Amber, AssessmentEvaluationResult, CompetencyAverageResult }
import model.Exceptions.NotFoundException
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_PASSED }
import model._
import model.assessmentscores.FixUserStuckInScoresAccepted
import model.command.{ ApplicationForProgression, ApplicationForSift }
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.AssessmentCentreTests
import reactivemongo.api.collections.bson.BSONBatchCommands.FindAndModifyCommand
import reactivemongo.api.commands.Collation
import reactivemongo.api.{ Cursor, DB, WriteConcern }
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import repositories.application.GeneralApplicationRepoBSONReader
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
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
  def nextSpecificApplicationReadyForAssessmentScoreEvaluation(
    currentPassmarkVersion: String, applicationId: String): Future[List[UniqueIdentifier]]
  def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]]
  def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation,
    currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
  def getFsacEvaluationResultAverages(applicationId: String): Future[Option[CompetencyAverageResult]]
  def getFsacEvaluatedSchemes(applicationId: String): Future[Option[Seq[SchemeEvaluationResult]]]
  def removeFsacTestGroup(applicationId: String): Future[Unit]
  def removeFsacEvaluation(applicationId: String): Future[Unit]
  def findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted: Future[Seq[FixUserStuckInScoresAccepted]]
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

  private val commonProgressStatusStateForEvaluation = BSONDocument(
    "$and" -> BSONArray(
      BSONDocument(s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.WITHDRAWN}" -> BSONDocument("$exists" -> false))
    )
  )

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
              commonProgressStatusStateForEvaluation
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED}" -> true),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion)),
              commonProgressStatusStateForEvaluation
            )
          )
        )
      )

    selectRandom[BSONDocument](query, batchSize).map(docs => docs.map(doc => doc.getAs[UniqueIdentifier]("applicationId").get))
  }

  override def nextSpecificApplicationReadyForAssessmentScoreEvaluation(
    currentPassmarkVersion: String,
    applicationId: String): Future[List[UniqueIdentifier]] = {
    val projection = BSONDocument("_id" -> false, "applicationId" -> true)
    val query =
      BSONDocument("$or" ->
        BSONArray(
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("applicationId" -> applicationId),
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED}" -> true),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
              commonProgressStatusStateForEvaluation
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("applicationId" -> applicationId),
              BSONDocument(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED}" -> true),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
              BSONDocument("testGroups.FSAC.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion)),
              commonProgressStatusStateForEvaluation
            )
          )
        )
      )

    collection.find(query, Some(projection)).one[BSONDocument].map {
      case Some(doc) => List(doc.getAs[UniqueIdentifier]("applicationId").get)
      case _ => Nil
    }
  }

  override def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("testGroups.FSAC.evaluation" -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument].map { docOpt =>
      docOpt.flatMap { doc =>
        doc.getAs[BSONDocument]("testGroups")
          .flatMap(_.getAs[BSONDocument](fsacKey)
            .flatMap(_.getAs[BSONDocument]("evaluation"))).map { evaluationDoc =>
          AssessmentPassMarkEvaluation(
            UniqueIdentifier(applicationId),
            passmarkVersion = evaluationDoc.getAs[String]("passmarkVersion").get,
            evaluationResult = AssessmentEvaluationResult(
              evaluationDoc.getAs[Boolean]("passedMinimumCompetencyLevel"),
              evaluationDoc.getAs[CompetencyAverageResult]("competency-average").get,
              evaluationDoc.getAs[Seq[SchemeEvaluationResult]]("schemes-evaluation").get
            )
          )
        }
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
          .merge(booleanToBSON("passedMinimumCompetencyLevel", evaluation.evaluationResult.passedMinimumCompetencyLevel))
          .merge(BSONDocument("competency-average" -> evaluation.evaluationResult.competencyAverageResult))
          .merge(BSONDocument("schemes-evaluation" -> evaluation.evaluationResult.schemesEvaluation))
      ).merge(currentSchemeStatusBSON(currentSchemeStatus)))

//    collection.update(query, passMarkEvaluation, upsert = false) map { _ => () }
    collection.update(ordered = false).one(query, passMarkEvaluation) map { _ => () }
  }

  private def booleanToBSON(schemeName: String, result: Option[Boolean]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r)
    case _ => BSONDocument.empty
  }

  def progressToAssessmentCentre(application: ApplicationForProgression, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val update =  BSONDocument("$set" -> applicationStatusBSON(progressStatus))
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to assessment centre")

    collection.update(ordered = false).one(query, update) map validator
  }

  def getTests(applicationId: String): Future[AssessmentCentreTests] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("_id" -> 0, s"testGroups.$fsacKey.tests" -> 2)

    collection.find(query, Some(projection)).one[BSONDocument].map {
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

    collection.find(query, Some(projection)).one[BSONDocument] map {
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

    collection.find(query, Some(projection)).one[BSONDocument] map {
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
    collection.update(ordered = false).one(query, update) map validator
  }

  // Wrap the findAndModify method to provide all the defaults
  private def findAndModify(query: BSONDocument, updateOp: FindAndModifyCommand.Update) =
    bsonCollection.findAndModify(
      query, updateOp, sort = None, fields = None, bypassDocumentValidation = false,
      writeConcern = WriteConcern.Default, maxTime = Option.empty[FiniteDuration], collation = Option.empty[Collation],
      arrayFilters = Seq.empty[BSONDocument]
    )

  override def removeFsacTestGroup(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateOp: FindAndModifyCommand.Update = bsonCollection.updateModifier(
      BSONDocument(
        "$unset" -> BSONDocument(s"testGroups.$fsacKey" -> "")
      )
    )

    findAndModify(query, updateOp). map { result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

  override def removeFsacEvaluation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateOp = bsonCollection.updateModifier(
      BSONDocument(
        "$unset" -> BSONDocument(s"testGroups.$fsacKey.evaluation" -> "")
      )
    )

    findAndModify(query, updateOp).map{ result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

  def findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted: Future[Seq[FixUserStuckInScoresAccepted]] = {
    val query = BSONDocument(
      "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE,
      s"progress-status.${ASSESSMENT_CENTRE_PASSED.toString}" -> BSONDocument("$exists" -> false),
      s"progress-status.${ASSESSMENT_CENTRE_FAILED.toString}" -> BSONDocument("$exists" -> false),
      "testGroups.FSAC.evaluation.schemes-evaluation.result" -> BSONDocument("$nin" -> BSONArray(Amber.toString))
    )
    val projection = BSONDocument("testGroups.FSAC.evaluation" -> 1, "applicationId" -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).cursor[BSONDocument]()
      .collect[Seq](maxDocs = -1, Cursor.FailOnError[Seq[BSONDocument]]()).map { docList =>
      docList.flatMap { doc =>
        val evaluationSection = doc.getAs[BSONDocument]("testGroups")
          .flatMap(_.getAs[BSONDocument]("FSAC"))
          .flatMap(_.getAs[BSONDocument]("evaluation"))

        evaluationSection.flatMap(_.getAs[Seq[SchemeEvaluationResult]]("schemes-evaluation")).map { evaluation =>
          FixUserStuckInScoresAccepted(
            doc.getAs[String]("applicationId").get,
            evaluation
          )
        }
      }
    }
  }
}
