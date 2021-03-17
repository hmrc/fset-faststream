/*
 * Copyright 2021 HM Revenue & Customs
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

import config.{ LaunchpadGatewayConfig, MicroserviceAppConfig }
import factories.DateTimeFactory
import javax.inject.{ Inject, Singleton }
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.Exceptions.PassMarkEvaluationNotFound
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.persisted._
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import model.{ ApplicationStatus, Phase => _, _ }
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONDocumentReader, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestEvaluationRepository extends CommonBSONDocuments with ReactiveRepositoryHelpers with RandomSelection
  with CurrentSchemeStatusHelper {

  this: ReactiveRepository[ApplicationReadyForEvaluation, _] =>

  val phase: Phase
  val evaluationApplicationStatuses: Set[ApplicationStatus]
  val evaluationProgressStatus: ProgressStatus
  val expiredProgressStatus: ProgressStatus

  implicit val applicationReadyForEvaluationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation]

  val nextApplicationQuery: String => BSONDocument

  // Gives classes implementing this trait the opportunity to log anything important when looking for candidates to evaluate
  def preEvaluationLogging(): Unit = ()

  def validEvaluationPhaseStatuses(phase: ApplicationStatus): Set[ApplicationStatus] = {
    val statusesToIgnore = List(ApplicationStatus.PHASE1_TESTS_FAILED, ApplicationStatus.PHASE2_TESTS_FAILED)
    ApplicationStatus.values.filter(s =>
      s >= phase && s < ApplicationStatus.PHASE3_TESTS_PASSED && !statusesToIgnore.contains(s))
  }

  def nextApplicationsReadyForEvaluation(currentPassmarkVersion: String, batchSize: Int): Future[List[ApplicationReadyForEvaluation]] = {
    preEvaluationLogging()
    selectRandom[ApplicationReadyForEvaluation](nextApplicationQuery(currentPassmarkVersion), batchSize)
  }

  def savePassmarkEvaluation(applicationId: String, evaluation: PassmarkEvaluation,
                             newProgressStatus: Option[ProgressStatus]): Future[Unit] = {
    // Warn level so we see it in prod logs
    logger.warn(s"applicationId = $applicationId - now saving progressStatus as $newProgressStatus")

    val selectQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses))
    ))

    val updateQuery = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phase.evaluation" -> evaluation
    ).merge(
      newProgressStatus.map(applicationStatusBSON).getOrElse(BSONDocument.empty)
    ).merge(
      currentSchemeStatusBSON(evaluation.result)
    ))
    val validator = singleUpdateValidator(applicationId, actionDesc = s"saving passmark evaluation during $phase evaluation")

    collection.update(ordered = false).one(selectQuery, updateQuery) map validator
  }

  def addSchemeResultToPassmarkEvaluation(applicationId: String,
                                          schemeEvaluationResult: SchemeEvaluationResult,
                                          passmarkVersion: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val removeEvaluationIfExists = BSONDocument(
      "$pull" -> BSONDocument(s"testGroups.$phase.evaluation.result" ->
        BSONDocument("schemeId" -> SchemeId("Sdip"))))

    val passMarkEvaluation = BSONDocument(
      "$addToSet" -> BSONDocument(s"testGroups.$phase.evaluation.result" -> schemeEvaluationResult),
      "$set" -> BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> passmarkVersion)
    )

    val evalSdipvalidator = singleUpdateValidator(applicationId,
      actionDesc = s"add scheme evaluation result to passmark evaluation during $phase evaluation")

    collection.update(ordered = false).one(query, removeEvaluationIfExists) flatMap { _ =>
      collection.update(ordered = false).one(query, passMarkEvaluation) map evalSdipvalidator
    }
  }

  def applicationEvaluationBuilder(activePsiTests: List[PsiTest],
                                   activeLaunchPadTest: Option[LaunchpadTest],
                                   prevPhaseEvaluation: Option[PassmarkEvaluation])(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
    val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
    val isGis = doc.getAs[BSONDocument]("assistance-details").exists(_.getAs[Boolean]("guaranteedInterview").contains(true))
    val preferences = doc.getAs[SelectedSchemes]("scheme-preferences").get
    ApplicationReadyForEvaluation(applicationId, applicationStatus, applicationRoute, isGis, activePsiTests,
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
    collection.find(query, Some(projection)).one[BSONDocument] map { optDoc =>
      passMarkEvaluationReader(phase, applicationId, optDoc)
    }
  }
}

@Singleton
class Phase1EvaluationMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory, mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository with CommonBSONDocuments {

  val phase = PHASE1
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE1_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE1_TESTS_EXPIRED

  implicit val applicationReadyForEvaluationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val bsonPhase1: Option[BSONDocument] = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase1: Phase1TestProfile = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
    applicationEvaluationBuilder(phase1.activeTests, None, None)(doc)
  })

  val nextApplicationQuery = (currentPassmarkVersion: String) => {
    BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses)),
      BSONDocument(s"progress-status.$evaluationProgressStatus" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.$expiredProgressStatus" -> BSONDocument("$ne" -> true)),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion))
      ))
    ))
  }
}

@Singleton
class Phase2EvaluationMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory, mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository with CommonBSONDocuments {

  val phase = PHASE2
  val prevPhase = PHASE1
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE2_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE2_TESTS_EXPIRED

  implicit val applicationReadyForEvaluationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
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
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN}" -> BSONDocument("$exists" -> false)),
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

@Singleton
class Phase3EvaluationMongoRepository @Inject() (appConfig: MicroserviceAppConfig,
                                                 val dateTimeFactory: DateTimeFactory,
                                                 mongoComponent: ReactiveMongoComponent
                                                )
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository with BaseBSONReader {

  import repositories.BSONDateTimeHandler

  val launchpadGatewayConfig: LaunchpadGatewayConfig = appConfig.launchpadGatewayConfig

  val phase = PHASE3
  val prevPhase = PHASE2
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE3_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE3_TESTS_EXPIRED

  implicit val applicationReadyForEvaluationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val applicationId = doc.getAs[String]("applicationId").get
    val bsonPhase3 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase3 = bsonPhase3.map(Phase3TestGroup.bsonHandler.read).get
    val phase2Evaluation = passMarkEvaluationReader(prevPhase, applicationId, Some(doc))
    applicationEvaluationBuilder(Nil, phase3.activeTests.headOption, Some(phase2Evaluation))(doc)
  })

  override def preEvaluationLogging(): Unit =
    logger.warn("Phase 3 evaluation is looking for candidates with results received " +
      s"${launchpadGatewayConfig.phase3Tests.evaluationWaitTimeAfterResultsReceivedInHours} hours ago...")

  val nextApplicationQuery = (currentPassmarkVersion: String) => {
    // The where clause specifies evaluation will trigger for this phase if the either the previous phase passmark version
    // is changed or the previous phase result version is changed, which can happen if the phase 1 pass marks were changed.
    // This allows us to trigger a phase 3 evaluation from a phase 1 pass mark change (and also a phase 2 evaluation)
    val whereClause =
    s"this.testGroups.$phase.evaluation.previousPhasePassMarkVersion != this.testGroups.$prevPhase.evaluation.passmarkVersion" +
      s" || this.testGroups.$phase.evaluation.previousPhaseResultVersion != this.testGroups.$prevPhase.evaluation.resultVersion"

    BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses)),
      BSONDocument(s"progress-status.$evaluationProgressStatus" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.$expiredProgressStatus" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"testGroups.$prevPhase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> true)),
      BSONDocument(s"testGroups.$phase.tests" ->
        BSONDocument("$elemMatch" -> BSONDocument(
          "usedForResults" -> true, "callbacks.reviewed" -> BSONDocument("$exists" -> true),
          "callbacks.reviewed" -> BSONDocument("$elemMatch" -> BSONDocument("received" -> BSONDocument("$lte" ->
            dateTimeFactory.nowLocalTimeZone.minusHours(
              launchpadGatewayConfig.phase3Tests.evaluationWaitTimeAfterResultsReceivedInHours
            ))))
        )
        )
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$exists" -> false)),
        BSONDocument(s"testGroups.$phase.evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion)),
        BSONDocument("$where" -> whereClause)
      ))
    ))
  }
}
