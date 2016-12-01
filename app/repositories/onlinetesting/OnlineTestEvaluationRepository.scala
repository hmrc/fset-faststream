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

import config.LaunchpadGatewayConfig
import factories.DateTimeFactory
import model.ApplicationStatus._
import model.Exceptions.PassMarkEvaluationNotFound
import model.persisted._
import model.{ ApplicationStatus, ProgressStatuses, SelectedSchemes }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONDocumentReader, BSONObjectID }
import repositories.{ BSONHelpers, BaseBSONReader, CommonBSONDocuments, RandomSelection }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import play.api.libs.json.Format

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestEvaluationRepository[T] extends CommonBSONDocuments with BSONHelpers with RandomSelection {

  this: ReactiveRepository[_, _] =>

  val phase: Phase

  val evaluationApplicationStatuses: List[ApplicationStatus]

  val evaluationProgressStatus: ProgressStatus

  implicit val applicationBSONReader: BSONDocumentReader[T]

  val nextApplicationQuery: String => BSONDocument

  def nextApplicationsReadyForEvaluation(currentPassmarkVersion: String, batchSize: Int)(implicit jsonFormat: Format[T]):
  Future[List[T]] = selectRandom[T](nextApplicationQuery(currentPassmarkVersion), batchSize)

  def savePassmarkEvaluation(applicationId: String, evaluation: PassmarkEvaluation,
                             newProgressStatus: Option[ProgressStatus]): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses))
    ))

    val passMarkEvaluation = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phase.evaluation" -> evaluation
    ).add(
      newProgressStatus.map(applicationStatusBSON).getOrElse(BSONDocument.empty)
    ))

    collection.update(query, passMarkEvaluation) map { r =>
      require(r.n == 1, s"None or more than one application have been updated during $phase evaluation: appId=$applicationId")
    }
  }

  def applicationEvaluationBuilder(activeCubiksTests: List[CubiksTest],
                                   activeLaunchPadTest: Option[LaunchpadTest],
                                   prevPhaseEvaluation: Option[PassmarkEvaluation])(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
    val isGis = doc.getAs[BSONDocument]("assistance-details").exists(_.getAs[Boolean]("guaranteedInterview").contains(true))
    val preferences = doc.getAs[SelectedSchemes]("scheme-preferences").get
    ApplicationReadyForEvaluation(applicationId, applicationStatus, isGis, activeCubiksTests,
      activeLaunchPadTest, prevPhaseEvaluation, preferences)
  }

  def passMarkEvaluationReader(passMarkPhase: String, applicationId: String, optDoc: Option[BSONDocument]): PassmarkEvaluation =
    optDoc.flatMap {_.getAs[BSONDocument]("testGroups")}
      .flatMap {_.getAs[BSONDocument](passMarkPhase)}
      .flatMap {_.getAs[PassmarkEvaluation]("evaluation")}
      .getOrElse(throw PassMarkEvaluationNotFound(applicationId))

  def getPassMarkEvaluation(applicationId: String): Future[PassmarkEvaluation] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(s"testGroups.$phase.evaluation" -> 1, "_id" -> 0)
    collection.find(query, projection).one[BSONDocument] map { optDoc =>
      passMarkEvaluationReader(phase, applicationId, optDoc)
    }
  }
}

class Phase1EvaluationMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID]("application", mongo,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository[ApplicationReadyForEvaluation] with BaseBSONReader{

  val phase = PHASE1

  val evaluationApplicationStatuses = List(ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationStatus.PHASE2_TESTS)

  val evaluationProgressStatus = ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED

  implicit val applicationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val bsonPhase1: Option[BSONDocument] = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase1: Phase1TestProfile = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
    applicationEvaluationBuilder(phase1.activeTests, None, None)(doc)
  })

  val nextApplicationQuery = (currentPassmarkVersion: String) =>
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses)),
        BSONDocument(s"progress-status.$evaluationProgressStatus" -> true),
        BSONDocument("$or" -> BSONArray(
          BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
          BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion))
        ))
      ))
}

class Phase2EvaluationMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID]("application", mongo,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository[ApplicationReadyForEvaluation] with BaseBSONReader {

  val phase = PHASE2

  val prevPhase = PHASE1

  val evaluationApplicationStatuses = List(ApplicationStatus.PHASE2_TESTS)

  val evaluationProgressStatus = ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED

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
        BSONDocument(s"testGroups.$prevPhase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
        BSONDocument("$or" -> BSONArray(
          BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
          BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion)),
          BSONDocument("$where" ->
            s"this.testGroups.$phase.evaluation.previousPhasePassMarkVersion != this.testGroups.$prevPhase.evaluation.passmarkVersion"))
        )
      ))
}

class Phase3EvaluationMongoRepository(launchpadGatewayConfig: LaunchpadGatewayConfig,
                                      dateTimeFactory: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID]("application", mongo,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository[ApplicationReadyForEvaluation] with BaseBSONReader {

  import repositories.BSONDateTimeHandler

  val phase = PHASE3

  val prevPhase = PHASE2

  val evaluationApplicationStatuses = List(ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER)

  val evaluationProgressStatus = ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED

  implicit val applicationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val applicationId = doc.getAs[String]("applicationId").get
    val bsonPhase3 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase3 = bsonPhase3.map(Phase3TestGroup.bsonHandler.read).get
    val phase2Evaluation = passMarkEvaluationReader(prevPhase, applicationId, Some(doc))
    applicationEvaluationBuilder(Nil, phase3.activeTests.headOption, Some(phase2Evaluation))(doc)
  })

  val nextApplicationQuery = (currentPassmarkVersion: String) =>
    BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses)),
      BSONDocument(s"progress-status.$evaluationProgressStatus" -> true),
      BSONDocument(s"testGroups.$prevPhase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
      BSONDocument(s"testGroups.$phase.tests" ->
        BSONDocument("$elemMatch" -> BSONDocument(
          "usedForResults" -> true, "callbacks.reviewed" -> BSONDocument("$exists" -> true),
          "callbacks.reviewed" -> BSONDocument("$not" ->
              BSONDocument("$elemMatch" -> BSONDocument("received" -> BSONDocument("$gt" ->
                dateTimeFactory.nowLocalTimeZone.minusHours(
                  launchpadGatewayConfig.phase3Tests.evaluationWaitTimeAfterResultsReceivedInHours
                )))))
          )
        )
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion)),
        BSONDocument("$where" ->
          s"this.testGroups.$phase.evaluation.previousPhasePassMarkVersion != this.testGroups.$prevPhase.evaluation.passmarkVersion"))
      )
    ))
}
