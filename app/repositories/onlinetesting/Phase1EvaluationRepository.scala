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

import model.persisted.Phase1TestProfile
import model.{ ApplicationStatus, ApplicationStatuses, ProgressStatuses, SelectedSchemes }
import model.persisted.ApplicationToPhase1Evaluation
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONBoolean, BSONDocument, BSONObjectID }
import repositories.RandomSelection
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait Phase1EvaluationRepository {
  def nextApplicationReadyForPhase1ResultEvaluation: Future[Option[ApplicationToPhase1Evaluation]]
}

class Phase1EvaluationMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationToPhase1Evaluation, BSONObjectID]("application", mongo,
    ApplicationToPhase1Evaluation.applicationToPhase1EvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with Phase1EvaluationRepository with RandomSelection {

  def nextApplicationReadyForPhase1ResultEvaluation: Future[Option[ApplicationToPhase1Evaluation]] = {
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
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val isGis = doc.getAs[BSONDocument]("assistance-details").exists(_.getAs[Boolean]("guaranteedInterview").contains(true))
      val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument]("PHASE1"))
      val phase1 = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
      val preferences = doc.getAs[SelectedSchemes]("scheme-preferences").get

      ApplicationToPhase1Evaluation(applicationId, isGis, phase1, preferences)
    })
  }
}
