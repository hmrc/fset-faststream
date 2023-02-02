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
import model.Exceptions.NotFoundException
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
                                                          mongoComponent: MongoComponent,
                                                          val uuidFactory: UUIDFactory)
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

    // TODO MIGUEL: Esta mal, solo hay que hacer upsert el primer ejercicio que escribes en la base
    // de datos, los demas son update. Usamos el oldVersion.isEmpty pero cogemos el old version por
    // ejercicio, no del conjunto de ejercicios, que no existe realmente. Pero es una vez para
    // los assessors, y otra vez para los reviewers.
    // TODO MIGUEL: Lo mejor es hacer upsert siempre. La alternativa, es averigur si hay datos ya
    // para ese applciation id en la coleccion de assessors-scores o reviewers-scores, y si ya hay datos
    // configurar para no esperar upsert.
    // Otra opcion es en vez de calcular oldVersion.isEmpty es coger todos los ejercicios y sacar
    // el oldVersion de todos ellos y solo hacer upsert si todos ellos son empty.
    val query = buildQueryForSaveWithOptimisticLocking(applicationId, section, oldVersion)
    println(s"--MIGUEL: query: $query")
    val update = buildUpdateForSaveWithOptimisticLocking(applicationId, section, bsonSection, oldVersion)
    println(s"--MIGUEL: update: $update")
    val validator = if (oldVersion.isEmpty) {
      singleUpsertOrUpdateValidator(applicationId.toString(), actionDesc = s"saving assessment score for final feedback", new Exception)
      //singleUpsertValidator
      /singleUpsertValidatorNew(applicationId.toString(), actionDesc = s"saving assessment score for final feedback")
    } else {
      singleUpdateValidator(applicationId.toString(), actionDesc = s"saving assessment score for final feedback")
    }

    //TODO: mongo take a closer look at this: when performing the upsert the validator shows no modified count
    collection.updateOne(query, update, UpdateOptions().upsert(oldVersion.isEmpty)).toFuture().map(validator).recover {
      // This kind of error happens when this is the first time we write in assessor-scores collection
          // and the collection does not contain yet this application id and there are two
          // assessors concurrently saving scores in the same exercise.
      case ex: Throwable if ex.getMessage.contains("E11000 duplicate key error collection") =>
        val message = s"------MIGUEL: You are trying to update a version of a [$section] " +
          s"for application id [$applicationId] that has been updated already"
        val message2 = s"-----MIGUEL: query: [$query], update: [$update], namespace collection: [${collection.namespace}]"
        val message3 = s"-----MIGUEL: indexes [${collection.listIndexes()}]"
        // Esto podria querer decir que estamos intentando actualizar la misma tupla,
        // o que en assessor-scores collection el id es aplicacionId y en reviewer-score es applicationid-version
        val message4 = s"------MIGUEL upserting ${oldVersion.isEmpty}"
        val message5 = s"-------MIGUEL ex: ${ex}, ex.getMEssage ${ex.getMessage}"
        logger.info(message)
        logger.info(message2)
        logger.info(message3)
        logger.info(message4)
        logger.info(message5)

        throw new NotFoundException(message)
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
class AssessorAssessmentScoresMongoRepository @Inject() (mongoComponent: MongoComponent, override val uuidFactory: UUIDFactory)
  extends AssessmentScoresMongoRepository(CollectionNames.ASSESSOR_ASSESSMENT_SCORES, mongoComponent, uuidFactory) {
  override def findAccepted(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    throw new UnsupportedOperationException("This method is only applicable for a reviewer")
  }
}

@Singleton
class ReviewerAssessmentScoresMongoRepository @Inject() (mongoComponent: MongoComponent, override val uuidFactory: UUIDFactory)
  extends AssessmentScoresMongoRepository(CollectionNames.REVIEWER_ASSESSMENT_SCORES, mongoComponent, uuidFactory)
