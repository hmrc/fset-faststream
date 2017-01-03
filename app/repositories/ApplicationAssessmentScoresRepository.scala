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
import model.CandidateScoresCommands
import model.CandidateScoresCommands._
import model.Exceptions.UnexpectedException
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ApplicationAssessmentScoresRepository {
  def allScores: Future[Map[String, CandidateScoresAndFeedback]]

  def tryFind(applicationId: String): Future[Option[CandidateScoresAndFeedback]]

  def save(candidateScoresAndFeedbck: CandidateScoresAndFeedback): Future[Unit]
}

class ApplicationAssessmentScoresMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[CandidateScoresAndFeedback, BSONObjectID]("application-assessment-scores", mongo,
    CandidateScoresCommands.Implicits.CandidateScoresAndFeedbackFormats, ReactiveMongoFormats.objectIdFormats)
  with ApplicationAssessmentScoresRepository with ReactiveRepositoryHelpers {

  def tryFind(applicationId: String): Future[Option[CandidateScoresAndFeedback]] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.find(query).one[BSONDocument].map { _.map(candidateScoresAndFeedback.read) }
  }

  def allScores: Future[Map[String, CandidateScoresAndFeedback]] = {
    val query = BSONDocument.empty
    val queryResult = collection.find(query).cursor[BSONDocument](ReadPreference.nearest).collect[List]()
    queryResult.map { docs =>
      docs.map { doc =>
        val cf = candidateScoresAndFeedback.read(doc)
        (cf.applicationId, cf)
      }.toMap
    }
  }

  def save(candidateScoresAndFeedbck: CandidateScoresAndFeedback): Future[Unit] = {
    val applicationId = candidateScoresAndFeedbck.applicationId
    val query = BSONDocument("applicationId" -> applicationId)

    val candidateScoresAndFeedbackBSON = BSONDocument(
      "applicationId" -> candidateScoresAndFeedbck.applicationId,
      "attendancy" -> candidateScoresAndFeedbck.attendancy,
      "assessmentIncomplete" -> candidateScoresAndFeedbck.assessmentIncomplete,
      "leadingAndCommunicating" -> candidateScoresAndFeedbck.leadingAndCommunicating,
      "collaboratingAndPartnering" -> candidateScoresAndFeedbck.collaboratingAndPartnering,
      "deliveringAtPace" -> candidateScoresAndFeedbck.deliveringAtPace,
      "makingEffectiveDecisions" -> candidateScoresAndFeedbck.makingEffectiveDecisions,
      "changingAndImproving" -> candidateScoresAndFeedbck.changingAndImproving,
      "buildingCapabilityForAll" -> candidateScoresAndFeedbck.buildingCapabilityForAll,
      "motivationFit" -> candidateScoresAndFeedbck.motivationFit,
      "feedback" -> candidateScoresAndFeedbck.feedback
    )

    val validator = singleUpsertValidator(applicationId, actionDesc = "saving allocation")

    collection.update(query, candidateScoresAndFeedbackBSON, upsert = true) map validator
  }
}
