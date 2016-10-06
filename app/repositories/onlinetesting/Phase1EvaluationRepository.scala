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
import model.persisted.{ ApplicationPhase1Evaluation, PassmarkEvaluation }
import model.{ ApplicationStatus, ProgressStatuses, SchemeType, SelectedSchemes }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.{ CommonBSONDocuments, RandomSelection }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase1EvaluationRepository {
  def nextApplicationReadyForPhase1ResultEvaluation(currentPassmarkVersion: String): Future[Option[ApplicationPhase1Evaluation]]

  def savePassmarkEvaluation(applicationId: String, evaluation: PassmarkEvaluation,
                             newApplicationStatus: Option[ApplicationStatus]): Future[Unit]
}

class Phase1EvaluationMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationPhase1Evaluation, BSONObjectID]("application", mongo,
    ApplicationPhase1Evaluation.applicationPhase1EvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with Phase1EvaluationRepository with RandomSelection with CommonBSONDocuments {
  private val BSONDocumentPhase1OrPhase2AppStatus = BSONDocument("$or" -> BSONArray(
    BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
    BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED),
    BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS)
  ))

  def nextApplicationReadyForPhase1ResultEvaluation(currentPassmarkVersion: String): Future[Option[ApplicationPhase1Evaluation]] = {
    // eTray (Phase2) requires all schemes to be evaluated. However, candidate
    // can be promoted to Phase2 with some Ambers. We need to select all applications in PHASE2
    // who were evaluated against the old passmark, as the second evaluation may get rid of
    // all Ambers for them
    val query = BSONDocument("$and" -> BSONArray(
        BSONDocumentPhase1OrPhase2AppStatus,
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
        BSONDocument("testGroups.PHASE1.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion))
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

  def savePassmarkEvaluation(applicationId: String, evaluation: PassmarkEvaluation,
                             newApplicationStatus: Option[ApplicationStatus]): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocumentPhase1OrPhase2AppStatus
    ))

    val passMarkEvaluation = BSONDocument("$set" -> BSONDocument(
      "testGroups.PHASE1.evaluation" -> evaluation
    ).add(
      if (newApplicationStatus.isDefined) applicationStatusBSON(newApplicationStatus.get) else BSONDocument.empty
    ))

    collection.update(query, passMarkEvaluation) map ( r => require(r.n >= 1, "Passmark evaluation for PHASE1 was not saved") )
  }
}
