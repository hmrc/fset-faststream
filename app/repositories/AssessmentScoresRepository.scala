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

package repositories

import factories.UUIDFactory
import model.Exceptions.{NotFoundException, OptimisticLockException}
import model.UniqueIdentifier
import model.assessmentscores._
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonArray, BsonValue}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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
                                                          mongoComponent: MongoComponent,
                                                          val uuidFactory: UUIDFactory)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[AssessmentScoresAllExercises](
    collectionName = collectionName,
    mongoComponent = mongoComponent,
    domainFormat = AssessmentScoresAllExercises.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("applicationId"), IndexOptions().unique(true))
    )
  ) with AssessmentScoresRepository with BaseBSONReader with ReactiveRepositoryHelpers {

  override def saveExercise(applicationId: UniqueIdentifier,
                            section: AssessmentScoresSectionType.AssessmentScoresSectionType,
                            exercisesScores: AssessmentScoresExercise,
                            newVersion: Option[String] = Some(uuidFactory.generateUUID())): Future[Unit] = {

    // We need to set legacyNumbers = true otherwise the numeric data gets stored as Int32 instead of Double
    // eg Some(4.0) is stored as 4 not 4.0
    val bsonSection = Codecs.toBson(exercisesScores.copy(version = newVersion), legacyNumbers = true)
    saveExerciseOrFinalFeedback(applicationId, section, bsonSection, exercisesScores.version)
  }

  override def saveFinalFeedback(applicationId: UniqueIdentifier,
                                 finalFeedback: AssessmentScoresFinalFeedback,
                                 newVersion: Option[String] = Some(uuidFactory.generateUUID())): Future[Unit] = {

    val bsonSection = Codecs.toBson(finalFeedback.copy(version = newVersion))
    saveExerciseOrFinalFeedback(applicationId, AssessmentScoresSectionType.finalFeedback, bsonSection, finalFeedback.version)
  }

  //scalastyle:off method.length
  private def saveExerciseOrFinalFeedback(applicationId: UniqueIdentifier,
                                          section: AssessmentScoresSectionType.AssessmentScoresSectionType,
                                          bsonSection: BsonValue,
                                          oldVersion: Option[String]): Future[Unit] = {

    def buildQueryForSaveWithOptimisticLocking(applicationId: UniqueIdentifier,
                                               section: AssessmentScoresSectionType.AssessmentScoresSectionType, version: Option[String]) = {

      def getVersionBSON(versionOpt: Option[String]): Document = {
        versionOpt match {
          case Some(version) =>
            Document("$or" -> BsonArray(
              Document(s"$section.version" -> Document("$exists" -> false)),
              Document(s"$section.version" -> version)
            ))
          case None =>
            Document(s"$section.version" -> Document("$exists" -> false))
        }
      }

      Document("$and" -> BsonArray(
        Document("applicationId" -> applicationId.toBson),
        getVersionBSON(version)
      ))
    }

    def buildUpdateForSaveWithOptimisticLocking(applicationId: UniqueIdentifier,
                                                exercise: AssessmentScoresSectionType.AssessmentScoresSectionType,
                                                exerciseScoresBSON: BsonValue, version: Option[String]) = {
      val applicationScoresBSON = version match {
        case Some(_) => Document(
          s"${exercise.toString}" -> exerciseScoresBSON
        )
        case _ => Document(
          "applicationId" -> applicationId.toString(),
          s"${exercise.toString}" -> exerciseScoresBSON
        )
      }
      Document("$set" -> applicationScoresBSON)
    }

    val query = buildQueryForSaveWithOptimisticLocking(applicationId, section, oldVersion)
    val update = buildUpdateForSaveWithOptimisticLocking(applicationId, section, bsonSection, oldVersion)
    val validator = if (oldVersion.isEmpty) {
      singleUpsertValidator(applicationId.toString(), actionDesc = s"saving assessment score for $section")
    } else {
      singleUpdateValidator(applicationId.toString(), actionDesc = s"saving assessment score for $section")
    }

    //TODO: mongo take a closer look at this: when performing the upsert the validator shows no modified count
    collection.updateOne(query, update, UpdateOptions().upsert(oldVersion.isEmpty)).toFuture().map(validator).recover {
      case ex: com.mongodb.MongoWriteException if ex.getMessage.contains("E11000 duplicate key error") =>
        // This scenario handles the user trying to submit data that has already been submitted by another user
        throw new OptimisticLockException(s"You are trying to update a version of [$section] " +
          s"for application id [$applicationId] that has been updated already")
    }
  } //scalastyle:on

  // This save method does not remove exercise subdocument when allExercisesScores's field are None
  override def save(allExercisesScores: AssessmentScoresAllExercises): Future[Unit] = {
    val applicationId = allExercisesScores.applicationId.toString()
    val query = Document("applicationId" -> applicationId)
    // We need to set legacyNumbers = true otherwise the numeric data gets stored as Int32 instead of Double
    // eg Some(4.0) is stored as 4 not 4.0
    val updateBSON = Document("$set" -> Codecs.toBson(allExercisesScores, legacyNumbers = true))
    val validator = singleUpsertValidator(applicationId, actionDesc = "saving assessment scores")
    collection.updateOne(query, updateBSON, UpdateOptions().upsert(insertIfNoRecordFound)).toFuture() map validator
  }

  override def find(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    val query = Document("applicationId" -> applicationId.toString())
    collection.find(query).headOption()
  }

  override def findAccepted(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    val query = Document(
      "applicationId" -> applicationId.toString(),
      "finalFeedback" -> Document("$exists" -> true)
    )
    collection.find(query).headOption()
  }

  override def findAll: Future[List[AssessmentScoresAllExercises]] = {
    findByQuery(Document.empty)
  }

  override def findAllByIds(applicationIds: Seq[String]): Future[List[AssessmentScoresAllExercises]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    findByQuery(query)
  }

  private def findByQuery(query: Document): Future[List[AssessmentScoresAllExercises]] = {
    collection.find(query).toFuture().map( _.toList )
  }

  override def resetExercise(applicationId: UniqueIdentifier, exercisesToRemove: List[String]): Future[Unit] = {
    val query = Document("applicationId" -> applicationId.toBson)

    val exercisesToUnset = exercisesToRemove.flatMap { exercise =>
      Map(s"$exercise" -> "")
    }

    val validator = singleUpdateValidator(applicationId.toString, actionDesc = "resetting exercises")
    val unsetDoc = Document("$unset" -> Document(exercisesToUnset))
    collection.updateOne(query, unsetDoc).toFuture() map validator
  }
}

@Singleton
class AssessorAssessmentScoresMongoRepository @Inject() (mongoComponent: MongoComponent, override val uuidFactory: UUIDFactory)(
  implicit ec: ExecutionContext)
  extends AssessmentScoresMongoRepository(CollectionNames.ASSESSOR_ASSESSMENT_SCORES, mongoComponent, uuidFactory) {
  override def findAccepted(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    throw new UnsupportedOperationException("This method is only applicable for a reviewer")
  }
}

@Singleton
class ReviewerAssessmentScoresMongoRepository @Inject() (mongoComponent: MongoComponent, override val uuidFactory: UUIDFactory)(
  implicit ec: ExecutionContext)
  extends AssessmentScoresMongoRepository(CollectionNames.REVIEWER_ASSESSMENT_SCORES, mongoComponent, uuidFactory)
