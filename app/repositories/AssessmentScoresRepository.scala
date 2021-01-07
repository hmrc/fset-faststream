/*
 * Copyright 2021 HM Revenue & Customs
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

package repositories

import factories.UUIDFactory
import javax.inject.{ Inject, Singleton }
import model.Exceptions.NotFoundException
import model.UniqueIdentifier
import model.assessmentscores._
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{ Cursor, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessmentScoresRepository {
  val uuidFactory: UUIDFactory
  def save(scoresAndFeedback: AssessmentScoresAllExercises): Future[Unit]
  def saveExercise(applicationId: UniqueIdentifier,
                   section: AssessmentScoresSectionType.AssessmentScoresSectionType,
                   exercisesScores: AssessmentScoresExercise,
                   newVersion: Option[String] = Some(uuidFactory.generateUUID())): Future[Unit]
  def saveFinalFeedback(applicationId: UniqueIdentifier,
                        finalFeedback: AssessmentScoresFinalFeedback,
                        newVersion: Option[String] = Some(uuidFactory.generateUUID())): Future[Unit]
  def find(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]]
  def findAccepted(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]]
  def findAll: Future[List[AssessmentScoresAllExercises]]
  def findAllByIds(applicationIds: Seq[String]): Future[List[AssessmentScoresAllExercises]]
  def resetExercise(applicationId: UniqueIdentifier, exercisesToRemove: List[String]): Future[Unit]
}

abstract class AssessmentScoresMongoRepository @Inject() (collectionName: String,
                                                          mongoComponent: ReactiveMongoComponent,
                                                          val uuidFactory: UUIDFactory)
  extends ReactiveRepository[AssessmentScoresAllExercises, BSONObjectID](
    collectionName,
    mongoComponent.mongoConnector.db,
    AssessmentScoresAllExercises.jsonFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with AssessmentScoresRepository with BaseBSONReader with ReactiveRepositoryHelpers {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("applicationId", Ascending)), unique = true)
  )

  override def saveExercise(applicationId: UniqueIdentifier,
                            section: AssessmentScoresSectionType.AssessmentScoresSectionType,
                            exercisesScores: AssessmentScoresExercise,
                            newVersion: Option[String] = Some(uuidFactory.generateUUID())): Future[Unit] = {

    val bsonSection = AssessmentScoresExercise.bsonHandler.write(exercisesScores.copy(version = newVersion))
    saveExerciseOrFinalFeedback(applicationId, section, bsonSection, exercisesScores.version)
  }

  override def saveFinalFeedback(applicationId: UniqueIdentifier,
                                 finalFeedback: AssessmentScoresFinalFeedback,
                                 newVersion: Option[String] = Some(uuidFactory.generateUUID())): Future[Unit] = {

    val bsonSection = AssessmentScoresFinalFeedback.bsonHandler.write(finalFeedback.copy(version = newVersion))
    saveExerciseOrFinalFeedback(applicationId, AssessmentScoresSectionType.finalFeedback, bsonSection, finalFeedback.version)
  }

  private def saveExerciseOrFinalFeedback(applicationId: UniqueIdentifier,
                                          section: AssessmentScoresSectionType.AssessmentScoresSectionType,
                                          bsonSection: BSONDocument,
                                          oldVersion: Option[String]): Future[Unit] = {

    def buildQueryForSaveWithOptimisticLocking(applicationId: UniqueIdentifier,
                                               section: AssessmentScoresSectionType.AssessmentScoresSectionType, version: Option[String]) = {

      def getVersionBSON(versionOpt: Option[String]): BSONDocument = {
        versionOpt match {
          case Some(version) =>
            BSONDocument("$or" -> BSONArray(
              BSONDocument(s"$section.version" -> BSONDocument("$exists" -> BSONBoolean(false))),
              BSONDocument(s"$section.version" -> version)
            ))
          case None =>
            BSONDocument(s"$section.version" -> BSONDocument("$exists" -> BSONBoolean(false)))
        }
      }

      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationId" -> applicationId),
        getVersionBSON(version)
      ))
    }

    def buildUpdateForSaveWithOptimisticLocking(applicationId: UniqueIdentifier,
                                                exercise: AssessmentScoresSectionType.AssessmentScoresSectionType,
                                                exerciseScoresBSON: BSONDocument, version: Option[String]) = {
      val applicationScoresBSON = version match {
        case Some(_) => BSONDocument(
          s"${exercise.toString}" -> exerciseScoresBSON
        )
        case _ => BSONDocument(
          "applicationId" -> applicationId.toString(),
          s"${exercise.toString}" -> exerciseScoresBSON
        )
      }
      BSONDocument("$set" -> applicationScoresBSON)
    }

    val query = buildQueryForSaveWithOptimisticLocking(applicationId, section, oldVersion)
    val update = buildUpdateForSaveWithOptimisticLocking(applicationId, section, bsonSection, oldVersion)
    val validator = singleUpdateValidator(applicationId.toString(), actionDesc = s"saving assessment score for final feedback")
    collection.update(ordered = false).one(query, update, upsert = oldVersion.isEmpty).map(validator).recover {
      case ex: Throwable if ex.getMessage.startsWith("DatabaseException['E11000 duplicate key error collection") =>
        throw new NotFoundException(s"You are trying to update a version of a [$section] " +
          s"for application id [$applicationId] that has been updated already")
    }
  }

  // This save method does not remove exercise subdocument when allExercisesScores's field are None
  override def save(allExercisesScores: AssessmentScoresAllExercises): Future[Unit] = {
    val applicationId = allExercisesScores.applicationId.toString()
    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> AssessmentScoresAllExercises.bsonHandler.write(allExercisesScores))
    val validator = singleUpsertValidator(applicationId, actionDesc = "saving assessment scores")
    collection.update(ordered = false).one(query, updateBSON, upsert = true) map validator
  }

  override def find(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    val query = BSONDocument("applicationId" -> applicationId.toString())
    collection.find(query, projection = Option.empty[JsObject]).one[BSONDocument].map(_.map(AssessmentScoresAllExercises.bsonHandler.read))
  }

  override def findAccepted(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    val query = BSONDocument("applicationId" -> applicationId.toString(),
      "finalFeedback" -> BSONDocument("$exists" -> BSONBoolean(true)))
    collection.find(query, projection = Option.empty[JsObject]).one[BSONDocument].map(_.map(AssessmentScoresAllExercises.bsonHandler.read))
  }

  override def findAll: Future[List[AssessmentScoresAllExercises]] = {
    findByQuery(BSONDocument.empty)
  }

  override def findAllByIds(applicationIds: Seq[String]): Future[List[AssessmentScoresAllExercises]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    findByQuery(query)
  }

  private def findByQuery(query: BSONDocument): Future[List[AssessmentScoresAllExercises]] = {
    collection.find(query, projection = Option.empty[JsObject]).cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](maxDocs = -1, Cursor.FailOnError[List[BSONDocument]]()).map(_.map(AssessmentScoresAllExercises.bsonHandler.read))
  }

  override def resetExercise(applicationId: UniqueIdentifier, exercisesToRemove: List[String]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val exercisesToUnset = exercisesToRemove.flatMap { exercise =>
      Map(s"$exercise" -> BSONString(""))
    }

    val unsetDoc = BSONDocument("$unset" -> BSONDocument(exercisesToUnset))
    collection.update(ordered = false).one(query, unsetDoc) map( _ => () )
  }
}

@Singleton
class AssessorAssessmentScoresMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent, override val uuidFactory: UUIDFactory)
  extends AssessmentScoresMongoRepository(CollectionNames.ASSESSOR_ASSESSMENT_SCORES, mongoComponent, uuidFactory) {
  override def findAccepted(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    throw new UnsupportedOperationException("This method is only applicable for a reviewer")
  }
}

@Singleton
class ReviewerAssessmentScoresMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent, override val uuidFactory: UUIDFactory)
  extends AssessmentScoresMongoRepository(CollectionNames.REVIEWER_ASSESSMENT_SCORES, mongoComponent, uuidFactory)
