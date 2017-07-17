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

package repositories.sift

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Phase.{ PHASE1, PHASE2 }
import model.{ ApplicationStatus, ProgressStatuses }
import model.persisted.{ ApplicationReadyForEvaluation, Phase2TestGroup }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONDocumentReader, BSONHandler, BSONObjectID }
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.{ CollectionNames, CommonBSONDocuments, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future

object ProgressToSiftRepository extends ProgressToSiftRepository

trait ProgressToSiftRepository extends RandomSelection with ReactiveRepositoryHelpers with CommonBSONDocuments {
  this: ReactiveRepository[_, _] =>

  val thisApplicationStatus: ApplicationStatus
  val phaseName: String
  val dateTimeFactory: DateTimeFactory
  val expiredTestQuery: BSONDocument
  val resetStatuses: List[String]
  implicit val bsonHandler: BSONHandler[BSONDocument, T]


  def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[ApplicationReadyForEvaluation]]

}

class ProgressToSiftRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository
    with CommonBSONDocuments {

  val phase = PHASE2
  val prevPhase = PHASE1
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE2_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE2_TESTS_EXPIRED

  implicit val applicationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val applicationId = doc.getAs[String]("applicationId").get
    val bsonPhase2 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase2 = bsonPhase2.map(Phase2TestGroup.bsonHandler.read).get
    val phase1Evaluation = passMarkEvaluationReader(prevPhase, applicationId, Some(doc))
    applicationEvaluationBuilder(phase2.activeTests, None, Some(phase1Evaluation))(doc)
  })

  val nextApplicationQuery = (currentPassmarkVersion: String) =>
    BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses)),
      BSONDocument(s"progress-status.$evaluationProgressStatus" -> true),
      BSONDocument(s"progress-status.$expiredProgressStatus" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"testGroups.$prevPhase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion)),
        BSONDocument("$where" ->
          s"this.testGroups.$phase.evaluation.previousPhasePassMarkVersion != this.testGroups.$prevPhase.evaluation.passmarkVersion"))
      )
    ))
}