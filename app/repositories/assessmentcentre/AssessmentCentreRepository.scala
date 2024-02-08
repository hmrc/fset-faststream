/*
 * Copyright 2023 HM Revenue & Customs
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
import model.EvaluationResults.{Amber, AssessmentEvaluationResult, CompetencyAverageResult, ExerciseAverageResult, FsacResults}
import model.Exceptions.NotFoundException
import model.ProgressStatuses.{ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_PASSED}
import model._
import model.assessmentscores.FixUserStuckInScoresAccepted
import model.command.{ApplicationForProgression, ApplicationForSift}
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.AssessmentCentreTests
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories._
import repositories.application.GeneralApplicationRepoBSONReader
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

object AssessmentCentreRepository {
  def applicationForFsacBsonReads(document: Document): ApplicationForProgression = {
    val applicationId = document.get("applicationId").get.asString().getValue
    val appStatus = Codecs.fromBson[ApplicationStatus](document.get("applicationStatus").get)
    val currentSchemeStatus = document.get("currentSchemeStatus").map { bsonValue =>
      Codecs.fromBson[Seq[SchemeEvaluationResult]](bsonValue)
    }.getOrElse(Nil)

    ApplicationForProgression(applicationId, appStatus, currentSchemeStatus)
  }
}

trait AssessmentCentreRepository {
  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def progressToAssessmentCentre(application: ApplicationForProgression, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]
  def getTests(applicationId: String): Future[AssessmentCentreTests]
  def updateTests(applicationId: String, tests: AssessmentCentreTests): Future[Unit]
  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String, batchSize: Int): Future[Seq[UniqueIdentifier]]
  def nextSpecificApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String,
                                                               applicationId: String): Future[List[UniqueIdentifier]]
  def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]]
  def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation,
                                    currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
//  def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation2,
//                                    currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
  def getFsacEvaluationResultAverages(applicationId: String): Future[Option[CompetencyAverageResult]]
  def getFsacExerciseResultAverages(applicationId: String): Future[Option[ExerciseAverageResult]]
  def getFsacEvaluatedSchemes(applicationId: String): Future[Option[Seq[SchemeEvaluationResult]]]
  def removeFsacTestGroup(applicationId: String): Future[Unit]
  def removeFsacEvaluation(applicationId: String): Future[Unit]
  def findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted: Future[Seq[FixUserStuckInScoresAccepted]]
}

@Singleton
class AssessmentCentreMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                                 schemeRepository: SchemeRepository, //TODO:fix guice just inject the list
//                                                  val siftableSchemeIds: Seq[SchemeId],
                                                 mongo: MongoComponent
                                                )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[ApplicationForSift](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = ApplicationForSift.applicationForSiftFormat,
    indexes = Nil
  ) with AssessmentCentreRepository with RandomSelection with ReactiveRepositoryHelpers with GeneralApplicationRepoBSONReader
    with CommonBSONDocuments with CurrentSchemeStatusHelper {

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val applicationForProgressionCollection: MongoCollection[ApplicationForProgression] =
  CollectionFactory.collection(
    collectionName = CollectionNames.APPLICATION,
    db = mongo.database,
    domainFormat = ApplicationForProgression.applicationForProgressionFormat
  )

  val fsacKey = "FSAC"

  override def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    val fastStreamNoSiftableSchemes = Document(
      "applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED.toBson,
      "currentSchemeStatus" -> Document("$elemMatch" -> Document(
        "schemeId" -> Document("$nin" -> Codecs.toBson(schemeRepository.siftableSchemeIds)),
        "result" -> EvaluationResults.Green.toString
      )))

    val query = Document("$or" -> BsonArray(
      fastStreamNoSiftableSchemes,
      Document(
        "applicationStatus" -> ApplicationStatus.SIFT.toBson,
        s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
        "currentSchemeStatus" -> Document("$elemMatch" ->
          Document("result" -> EvaluationResults.Green.toString, "schemeId" -> Document("$nin" -> BsonArray(Scheme.Sdip, Scheme.Edip)))
        )
      )
    ))

    val unfiltered = selectRandom[ApplicationForProgression](applicationForProgressionCollection, query, batchSize)
    unfiltered.map(_.filter { app =>
      app.applicationStatus match {
        case ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED => app.currentSchemeStatus.filter(_.result == EvaluationResults.Green.toString)
          .forall(s => !schemeRepository.siftableSchemeIds.contains(s.schemeId))
        case ApplicationStatus.SIFT => app.currentSchemeStatus.exists(_.result == EvaluationResults.Green.toString)
      }
    })
  }

  private val commonProgressStatusStateForEvaluation = Document(
    "$and" -> BsonArray(
      Document(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED}" -> true),
      Document(s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.WITHDRAWN}" -> Document("$exists" -> false))
    )
  )

  // Note we specify the applicationStatus of ASSESSMENT_CENTRE deliberately as a candidate can move outside of
  // FSAC with a single Green scheme and still have others that are in Amber. These candidates need to be re-evaluated
  // when the pass marks change
  override def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String,
                                                                batchSize: Int): Future[Seq[UniqueIdentifier]] = {
    val query =
      Document("$or" ->
        BsonArray(
          Document(
            "$and" -> BsonArray(
              Document("testGroups.FSAC.evaluation.passmarkVersion" -> Document("$exists" -> false)),
              commonProgressStatusStateForEvaluation
            )
          ),
          Document(
            "$and" -> BsonArray(
              Document("testGroups.FSAC.evaluation.passmarkVersion" -> Document("$exists" -> true)),
              Document("testGroups.FSAC.evaluation.passmarkVersion" -> Document("$ne" -> currentPassmarkVersion)),
              commonProgressStatusStateForEvaluation
            )
          )
        )
      )

    selectRandom[UniqueIdentifier](query)(doc => UniqueIdentifier(getAppId(doc)), ec)
  }

  override def nextSpecificApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String,
                                                                        applicationId: String): Future[List[UniqueIdentifier]] = {
    val projection = Projections.include("applicationId")
    val query =
      Document("$or" ->
        BsonArray(
          Document(
            "$and" -> BsonArray(
              Document("applicationId" -> applicationId),
              Document("testGroups.FSAC.evaluation.passmarkVersion" -> Document("$exists" -> false)),
              commonProgressStatusStateForEvaluation
            )
          ),
          Document(
            "$and" -> BsonArray(
              Document("applicationId" -> applicationId),
              Document("testGroups.FSAC.evaluation.passmarkVersion" -> Document("$exists" -> true)),
              Document("testGroups.FSAC.evaluation.passmarkVersion" -> Document("$ne" -> currentPassmarkVersion)),
              commonProgressStatusStateForEvaluation
            )
          )
        )
      )

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) => List(UniqueIdentifier(doc.get("applicationId").get.asString().getValue))
      case _ => Nil
    }
  }

  override def getAssessmentScoreEvaluation(applicationId: String): Future[Option[AssessmentPassMarkEvaluation]] = {
    val query = Document(
      "applicationId" -> applicationId,
      s"testGroups.$fsacKey.evaluation" ->  Document("$exists" -> true)
    )
    val projection = Projections.include(s"testGroups.$fsacKey.evaluation")

    collection.find[Document](query).projection(projection).headOption() map { docOpt =>
      docOpt.flatMap { doc =>
        doc.get("testGroups")
          .map(_.asDocument().get(fsacKey))
          .map(_.asDocument().get("evaluation"))
          .map { evaluationBson =>
            AssessmentPassMarkEvaluation(
              UniqueIdentifier(applicationId),
              passmarkVersion = evaluationBson.asDocument().get("passmarkVersion").asString().getValue,
              evaluationResult = AssessmentEvaluationResult(
                FsacResults( //TODO: can we improve this????
                  Codecs.fromBson[CompetencyAverageResult](evaluationBson.asDocument().get("competency-average")),
                  Codecs.fromBson[ExerciseAverageResult](evaluationBson.asDocument().get("exercise-average")),
                ),
                Codecs.fromBson[Seq[SchemeEvaluationResult]](evaluationBson.asDocument().get("schemes-evaluation"))
              )
            )
        }
      }
    }
  }

  override def saveAssessmentScoreEvaluation(evaluation: model.AssessmentPassMarkEvaluation,
                                             currentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {
    val query = Document(
      "applicationId" -> evaluation.applicationId.toBson,
      "applicationStatus" -> Document("$ne" -> ApplicationStatus.WITHDRAWN.toBson)
    )

    val passMarkEvaluation = Document("$set" ->
      (
        Document("testGroups.FSAC.evaluation" -> Document("passmarkVersion" -> evaluation.passmarkVersion)
        .++(
          Document("competency-average" -> Codecs.toBson(evaluation.evaluationResult.fsacResults.competencyAverageResult))
        ).++(
          Document("exercise-average" -> Codecs.toBson(evaluation.evaluationResult.fsacResults.exerciseAverageResult))
        ).++(
          Document("schemes-evaluation" -> Codecs.toBson(evaluation.evaluationResult.schemesEvaluation))
        )
        ) ++
        currentSchemeStatusBSON(currentSchemeStatus)
      )
    )

    collection.updateOne(query, passMarkEvaluation).toFuture() map { _ => () }
  }

  /* // TODO: mongo who called this in reactive-mongo?
  private def booleanToBSON(schemeName: String, result: Option[Boolean]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r)
    case _ => BSONDocument.empty
  }*/

  override def progressToAssessmentCentre(application: ApplicationForProgression,
                                          progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val update = Document("$set" -> applicationStatusBSON(progressStatus))
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to assessment centre")

    collection.updateOne(query, update).toFuture() map validator
  }

  override def getTests(applicationId: String): Future[AssessmentCentreTests] = {
    val query = Document(
      "applicationId" -> applicationId,
      s"testGroups.$fsacKey.tests" ->  Document("$exists" -> true)
    )
    val projection = Projections.include(s"testGroups.$fsacKey.tests")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(bsonTests) =>
        bsonTests.get("testGroups").map(_.asDocument().get(fsacKey)).map(_.asDocument().get("tests")).map { testsBson =>
          Codecs.fromBson[AssessmentCentreTests](testsBson)
        }.getOrElse(AssessmentCentreTests())
      case _ => AssessmentCentreTests()
    }
  }

  override def getFsacEvaluationResultAverages(applicationId: String): Future[Option[CompetencyAverageResult]] = {
    val query = Document(
      "applicationId" -> applicationId,
      s"testGroups.$fsacKey.evaluation.competency-average" ->  Document("$exists" -> true)
    )
    val projection = Projections.include(s"testGroups.$fsacKey.evaluation.competency-average")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(document) =>
        document.get("testGroups")
          .map(_.asDocument().get(fsacKey))
          .map(_.asDocument().get("evaluation"))
          .map(_.asDocument().get("competency-average")).map { averagesBson =>
            Codecs.fromBson[CompetencyAverageResult](averagesBson)
          }
      case None => None
    }
  }

  override def getFsacExerciseResultAverages(applicationId: String): Future[Option[ExerciseAverageResult]] = {
    val query = Document(
      "applicationId" -> applicationId,
      s"testGroups.$fsacKey.evaluation.exercise-average" ->  Document("$exists" -> true)
    )
    val projection = Projections.include(s"testGroups.$fsacKey.evaluation.exercise-average")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(document) =>
        document.get("testGroups")
          .map(_.asDocument().get(fsacKey))
          .map(_.asDocument().get("evaluation"))
          .map(_.asDocument().get("exercise-average")).map { averagesBson =>
            Codecs.fromBson[ExerciseAverageResult](averagesBson)
          }
      case None => None
    }
  }

  override def getFsacEvaluatedSchemes(applicationId: String): Future[Option[Seq[SchemeEvaluationResult]]] = {
    val query = Document(
      "applicationId" -> applicationId,
      s"testGroups.$fsacKey.evaluation.schemes-evaluation" ->  Document("$exists" -> true)
    )
    val projection = Projections.include(s"testGroups.$fsacKey.evaluation.schemes-evaluation")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(document) =>
        document.get("testGroups")
          .map(_.asDocument().get(fsacKey))
          .map(_.asDocument().get("evaluation"))
          .map(_.asDocument().get("schemes-evaluation")).map { bson =>
          Codecs.fromBson[Seq[SchemeEvaluationResult]](bson)
        }
      case None => None
    }
  }

  override def updateTests(applicationId: String, tests: AssessmentCentreTests): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$set" -> Document(s"testGroups.$fsacKey.tests" -> Codecs.toBson(tests)))

    val validator = singleUpdateValidator(applicationId, actionDesc = "Updating assessment centre tests")
    collection.updateOne(query, update).toFuture() map validator
  }

  override def removeFsacTestGroup(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val updateOp = Document("$unset" -> Document(s"testGroups.$fsacKey" -> ""))

    collection.updateOne(query, updateOp).toFuture() map { result =>
      if (result.getModifiedCount == 0) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

  override def removeFsacEvaluation(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val updateOp = Document("$unset" -> Document(s"testGroups.$fsacKey.evaluation" -> ""))

    collection.updateOne(query, updateOp).toFuture() map{ result =>
      if (result.getModifiedCount == 0) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

  override def findNonPassedNonFailedNonAmberUsersInAssessmentScoresAccepted: Future[Seq[FixUserStuckInScoresAccepted]] = {
    val query = Document(
      "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE.toBson,
      s"progress-status.${ASSESSMENT_CENTRE_PASSED.toString}" -> Document("$exists" -> false),
      s"progress-status.${ASSESSMENT_CENTRE_FAILED.toString}" -> Document("$exists" -> false),
      s"testGroups.$fsacKey.evaluation.schemes-evaluation.result" -> Document("$nin" -> BsonArray(Amber.toString)),
      s"testGroups.$fsacKey.evaluation.schemes-evaluation" ->  Document("$exists" -> true)
    )
    val projection = Projections.include(s"testGroups.$fsacKey.evaluation", "applicationId")

    collection.find[Document](query).projection(projection).toFuture() map { docList =>
      docList.flatMap { doc =>
        doc.get("testGroups")
          .map(_.asDocument().get(fsacKey))
          .map(_.asDocument().get("evaluation"))
          .map(_.asDocument().get("schemes-evaluation")).map { bson =>
            val evaluation = Codecs.fromBson[Seq[SchemeEvaluationResult]](bson)

            FixUserStuckInScoresAccepted(
              doc.get("applicationId").get.asString().getValue,
              evaluation
            )
          }
      }
    }
  }
}
