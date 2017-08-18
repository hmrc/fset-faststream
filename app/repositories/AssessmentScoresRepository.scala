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

package repositories

import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.NotFoundException
import model.UniqueIdentifier
import model.assessmentscores._
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessmentScoresRepository {
  def save(scoresAndFeedback: AssessmentScoresAllExercises): Future[Unit]

  def saveExercise(
    applicationId: UniqueIdentifier,
    section: AssessmentScoresSectionType.AssessmentScoresSectionType,
    exercisesScores: AssessmentScoresExercise): Future[Unit]

  def saveFinalFeedback(
    applicationId: UniqueIdentifier,
    finalFeedback: AssessmentScoresFinalFeedback): Future[Unit]

  def find(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]]

  def findAll: Future[List[AssessmentScoresAllExercises]]
}

abstract class AssessmentScoresMongoRepository(dateTime: DateTimeFactory, collectionName: String)(implicit mongo: () => DB)
  extends ReactiveRepository[AssessmentScoresAllExercises, BSONObjectID](collectionName, mongo,
    AssessmentScoresAllExercises.jsonFormat, ReactiveMongoFormats.objectIdFormats)
    with AssessmentScoresRepository with BaseBSONReader with ReactiveRepositoryHelpers {

  def saveExercise(
    applicationId: UniqueIdentifier,
    section: AssessmentScoresSectionType.AssessmentScoresSectionType,
    exercisesScores: AssessmentScoresExercise): Future[Unit] = {

    val newVersion = Some(UUIDFactory.generateUUID())
    val bsonSection = AssessmentScoresExercise.bsonHandler.write(exercisesScores.copy(version = newVersion))
    saveExerciseOrFinalFeedback(applicationId, section, bsonSection, exercisesScores.version)
  }

  def saveFinalFeedback(
    applicationId: UniqueIdentifier,
    finalFeedback: AssessmentScoresFinalFeedback): Future[Unit] = {

    val newVersion = Some(UUIDFactory.generateUUID())
    val bsonSection = AssessmentScoresFinalFeedback.bsonHandler.write(finalFeedback.copy(version = newVersion))
    saveExerciseOrFinalFeedback(applicationId, AssessmentScoresSectionType.finalFeedback, bsonSection, finalFeedback.version)
  }

//scalastyle:off
  def saveExerciseOrFinalFeedback(
    applicationId: UniqueIdentifier,
    section: AssessmentScoresSectionType.AssessmentScoresSectionType,
    bsonSection: BSONDocument,
    oldVersion: Option[String]): Future[Unit] = {

    def buildQueryForSaveWithOptimisticLocking(applicationId: UniqueIdentifier,
      section: AssessmentScoresSectionType.AssessmentScoresSectionType, version: Option[String]) = {

      def getVersionBSON(versionOpt: Option[String]): BSONDocument = {
        versionOpt match {
          case Some(version) =>
            BSONDocument("$or" -> BSONArray(
              BSONDocument(s"${section}.version" -> BSONDocument("$exists" -> BSONBoolean(false))),
              BSONDocument(s"${section}.version" -> version)
            ))
          case None =>
              BSONDocument(s"${section}.version" -> BSONDocument("$exists" -> BSONBoolean(false)))
        }
      }

      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationId" -> applicationId),
        getVersionBSON(version)
      ))
    }

    def buildUpdateForSaveWithOptimisticLocking(applicationId: UniqueIdentifier,
      exercise: AssessmentScoresSectionType.AssessmentScoresSectionType, exerciseScoresBSON: BSONDocument, version: Option[String]) = {
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
    val bsonExercise = bsonSection
    val update = buildUpdateForSaveWithOptimisticLocking(applicationId, section, bsonExercise, oldVersion)
    val validator = singleUpdateValidator(applicationId.toString(), actionDesc = s"saving assessment score for final feedback")
    collection.update(query, update, upsert = oldVersion.isEmpty).map(validator).recover {
      case ex: Exception if ex.getMessage.startsWith("DatabaseException['E11000 duplicate key error collection") =>
        throw new NotFoundException(s"You are trying to update a version of a [$section] " +
          s"for application id [$applicationId] that has been updated already")
    }


  }

  // This save method does not remove exercise subdocument when allExercisesScores's field are None
  def save(allExercisesScores: AssessmentScoresAllExercises): Future[Unit] = {
    val applicationId = allExercisesScores.applicationId.toString()
    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> AssessmentScoresAllExercises.bsonHandler.write(allExercisesScores))
    val validator = singleUpsertValidator(applicationId, actionDesc = "saving assessment scores")
    collection.update(query, updateBSON, upsert = true) map validator
  }

  def find(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    val query = BSONDocument("applicationId" -> applicationId.toString())
    collection.find(query).one[BSONDocument].map(_.map(AssessmentScoresAllExercises.bsonHandler.read))
  }

  def findAll: Future[List[AssessmentScoresAllExercises]] = {
    val query = BSONDocument.empty
    collection.find(query).cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map(_.map(AssessmentScoresAllExercises.bsonHandler.read))
  }
}

class AssessorAssessmentScoresMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends AssessmentScoresMongoRepository(dateTime, CollectionNames.ASSESSOR_ASSESSMENT_SCORES)

class ReviewerAssessmentScoresMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends AssessmentScoresMongoRepository(dateTime, CollectionNames.REVIEWER_ASSESSMENT_SCORES)
