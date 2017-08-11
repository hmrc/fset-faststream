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

import factories.DateTimeFactory
import model.UniqueIdentifier
import model.assessmentscores._
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessmentScoresRepository {
  def save(scoresAndFeedback: AssessmentScoresAllExercises): Future[Unit]

  def find(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]]

  def findAll: Future[List[AssessmentScoresAllExercises]]
}

abstract class AssessmentScoresMongoRepository(dateTime: DateTimeFactory, collectionName: String)(implicit mongo: () => DB)
  extends ReactiveRepository[AssessmentScoresAllExercises, BSONObjectID](collectionName, mongo,
    AssessmentScoresAllExercises.jsonFormat, ReactiveMongoFormats.objectIdFormats)
    with AssessmentScoresRepository with BaseBSONReader with ReactiveRepositoryHelpers {

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
