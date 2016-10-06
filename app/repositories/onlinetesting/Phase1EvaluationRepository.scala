/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories.onlinetesting

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Result
import model.OnlineTestCommands.Phase1TestProfile
import model.SchemeType.SchemeType
import model.persisted.ApplicationPhase1Evaluation
import model.{ ApplicationStatus, ProgressStatuses, SchemeType, SelectedSchemes }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.RandomSelection
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase1EvaluationRepository {
  def nextApplicationReadyForPhase1ResultEvaluation: Future[Option[ApplicationPhase1Evaluation]]
}

class Phase1EvaluationMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationPhase1Evaluation, BSONObjectID]("application", mongo,
    ApplicationPhase1Evaluation.applicationPhase1EvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with Phase1EvaluationRepository with RandomSelection {

  def nextApplicationReadyForPhase1ResultEvaluation: Future[Option[ApplicationPhase1Evaluation]] = {
    // TODO: Add BSONDocument("passmarkEvaluation.passmarkVersion" -> BSONDocument("$exists" -> false))
    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true)
      )),
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true)
      ))
    ))

    selectRandom(query).map(_.map { doc =>
      val applicationId = doc.getAs[String]("applicationId").get
      val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
      val isGis = doc.getAs[BSONDocument]("assistance-details").exists(_.getAs[Boolean]("guaranteedInterview").contains(true))
      val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument]("PHASE1"))
      val phase1 = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
      val preferences = doc.getAs[SelectedSchemes]("scheme-preferences").get

      ApplicationPhase1Evaluation(applicationId, applicationStatus, isGis, phase1, preferences)
    })
  }

  def savePassmarkEvaluation(applicationId: String, passmarkVersion: String, result: List[(SchemeType, Result)],
                             newApplicationStatus: ApplicationStatus): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("or" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED),
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS)
      ))
    ))

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument("phase1PassmarkEvaluation" ->
        BSONDocument("passmarkVersion" -> passmarkVersion, "result" -> result)
      )
    )

    collection.update(query, passMarkEvaluation, upsert = false) map ( _ => () )
  }
}
