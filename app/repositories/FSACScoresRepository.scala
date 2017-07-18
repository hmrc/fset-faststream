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
import model.FSACScores
import model.FSACScores._
import model.UniqueIdentifier
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FSACScoresRepository {
  def save(scoresAndFeedback: FSACAllExercisesScoresAndFeedback): Future[Unit]
  def find(applicationId: UniqueIdentifier): Future[Option[FSACAllExercisesScoresAndFeedback]]
  def findAll: Future[List[FSACAllExercisesScoresAndFeedback]]
}

class FSACScoresMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[FSACAllExercisesScoresAndFeedback, BSONObjectID](CollectionNames.FSAC_SCORES, mongo,
    FSACScores.FSACAllExercisesScoresAndFeedback.format, ReactiveMongoFormats.objectIdFormats)
  with FSACScoresRepository with ReactiveRepositoryHelpers {


  def save(allExercisesScoresAndFeedback: FSACAllExercisesScoresAndFeedback): Future[Unit] = {
    val applicationId = allExercisesScoresAndFeedback.applicationId
    val query = BSONDocument("applicationId" -> applicationId.toString())
    val updateBSON = BSONDocument("$set" -> fSACAllExercisesScoresAndFeedbackHandler.write(allExercisesScoresAndFeedback))
    val validator = singleUpsertValidator(applicationId.toString(), actionDesc = "saving fsac scores")
    collection.update(query, updateBSON, upsert = true) map validator
  }

  def find(applicationId: UniqueIdentifier): Future[Option[FSACAllExercisesScoresAndFeedback]] = {
    val query = BSONDocument("applicationId" -> applicationId.toString())
    //collection.find(query).one[BSONDocument].map { _.map(fSACAllExercisesScoresAndFeedbackHandler.read) }
    collection.find(query).one[FSACAllExercisesScoresAndFeedback]
  }

  def findAll: Future[List[FSACAllExercisesScoresAndFeedback]] = {
    val query = BSONDocument.empty
    collection.find(query).cursor[FSACAllExercisesScoresAndFeedback](ReadPreference.nearest).collect[List]()


    //collection.find(query).cursor[BSONDocument](ReadPreference.nearest).
    // collect[List]().map(_.map(fSACAllExercisesScoresAndFeedbackHandler.read))
  }
}
