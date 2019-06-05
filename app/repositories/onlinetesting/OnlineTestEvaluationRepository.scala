/*
 * Copyright 2019 HM Revenue & Customs
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
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.EvaluationResults.{ Green, Red }
import model.Exceptions.PassMarkEvaluationNotFound
import model.persisted._
import model.{ ApplicationStatus, Phase => _, _ }
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONDocumentReader, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ BaseBSONReader, CollectionNames, CommonBSONDocuments, CurrentSchemeStatusHelper }
import repositories.{ RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestEvaluationRepository extends CommonBSONDocuments with ReactiveRepositoryHelpers with RandomSelection
  with CurrentSchemeStatusHelper {

  this: ReactiveRepository[ApplicationReadyForEvaluation, _] =>

  val phase: Phase
  val evaluationApplicationStatuses: Set[ApplicationStatus]
  val evaluationProgressStatus: ProgressStatus
  val expiredProgressStatus: ProgressStatus

  implicit val applicationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation]
  implicit val applicationBSONReader2: BSONDocumentReader[ApplicationReadyForEvaluation2]

  val nextApplicationQuery: String => BSONDocument

  def validEvaluationPhaseStatuses(phase: ApplicationStatus): Set[ApplicationStatus] = {
    val statusesToIgnore = List(ApplicationStatus.PHASE1_TESTS_FAILED, ApplicationStatus.PHASE2_TESTS_FAILED)
    ApplicationStatus.values.filter(s =>
      s >= phase && s < ApplicationStatus.PHASE3_TESTS_PASSED && !statusesToIgnore.contains(s))
  }

  def nextApplicationsReadyForEvaluation(currentPassmarkVersion: String, batchSize: Int): Future[List[ApplicationReadyForEvaluation]] =
    selectRandom[ApplicationReadyForEvaluation](nextApplicationQuery(currentPassmarkVersion), batchSize)

  def nextApplicationsReadyForEvaluation2(currentPassmarkVersion: String, batchSize: Int): Future[List[ApplicationReadyForEvaluation2]] =
    selectRandom[ApplicationReadyForEvaluation2](nextApplicationQuery(currentPassmarkVersion), batchSize)

  def savePassmarkEvaluation(applicationId: String, evaluation: PassmarkEvaluation,
                             newProgressStatus: Option[ProgressStatus]): Future[Unit] = {
    Logger.info(s"applicationId = $applicationId - now saving progressStatus as $newProgressStatus")

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> evaluationApplicationStatuses))
    ))

    val passMarkEvaluation = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phase.evaluation" -> evaluation
    ).add(
      newProgressStatus.map(applicationStatusBSON).getOrElse(BSONDocument.empty)
    ).add(
      currentSchemeStatusBSON(evaluation.result)
    ))
    val validator = singleUpdateValidator(applicationId, actionDesc = s"saving passmark evaluation during $phase evaluation")

    collection.update(query, passMarkEvaluation) map validator
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

    collection.update(query, removeEvaluationIfExists) flatMap { _ =>
      collection.update(query, passMarkEvaluation) map evalSdipvalidator
    }
  }

  def applicationEvaluationBuilder(activeCubiksTests: List[CubiksTest],
                                   activeLaunchPadTest: Option[LaunchpadTest],
                                   prevPhaseEvaluation: Option[PassmarkEvaluation])(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
    val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
    val isGis = doc.getAs[BSONDocument]("assistance-details").exists(_.getAs[Boolean]("guaranteedInterview").contains(true))
    val preferences = doc.getAs[SelectedSchemes]("scheme-preferences").get
    ApplicationReadyForEvaluation(applicationId, applicationStatus, applicationRoute, isGis, activeCubiksTests,
      activeLaunchPadTest, prevPhaseEvaluation, preferences)
  }

  def applicationEvaluationBuilder2(activePsiTests: List[PsiTest],
                                    activeLaunchPadTest: Option[LaunchpadTest],
                                    prevPhaseEvaluation: Option[PassmarkEvaluation])(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
    val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
    val isGis = doc.getAs[BSONDocument]("assistance-details").exists(_.getAs[Boolean]("guaranteedInterview").contains(true))
    val preferences = doc.getAs[SelectedSchemes]("scheme-preferences").get
    ApplicationReadyForEvaluation2(applicationId, applicationStatus, applicationRoute, isGis, activePsiTests,
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

class Phase1EvaluationMongoRepository(val dateTimeFactory: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository with CommonBSONDocuments{

  val phase = PHASE1
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE1_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE1_TESTS_EXPIRED

  implicit val applicationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val bsonPhase1: Option[BSONDocument] = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase1: Phase1TestProfile = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
    applicationEvaluationBuilder(phase1.activeTests, None, None)(doc)
  })

  implicit val applicationBSONReader2: BSONDocumentReader[ApplicationReadyForEvaluation2] = bsonReader(doc => {
    val bsonPhase1: Option[BSONDocument] = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase1: Phase1TestProfile2 = bsonPhase1.map(Phase1TestProfile2.bsonHandler.read).get
    applicationEvaluationBuilder2(phase1.activeTests, None, None)(doc)
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

class Phase2EvaluationMongoRepository(val dateTimeFactory: DateTimeFactory)(implicit mongo: () => DB)
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

  //TODO: implement for P2
  implicit val applicationBSONReader2: BSONDocumentReader[ApplicationReadyForEvaluation2] = bsonReader(doc => {
    val bsonPhase1: Option[BSONDocument] = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase1: Phase1TestProfile2 = bsonPhase1.map(Phase1TestProfile2.bsonHandler.read).get
    applicationEvaluationBuilder2(phase1.activeTests, None, None)(doc)
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

class Phase3EvaluationMongoRepository(
  launchpadGatewayConfig: LaunchpadGatewayConfig,
  val dateTimeFactory: DateTimeFactory
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationReadyForEvaluation, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationReadyForEvaluation.applicationReadyForEvaluationFormats,
    ReactiveMongoFormats.objectIdFormats) with OnlineTestEvaluationRepository with BaseBSONReader {

  import repositories.BSONDateTimeHandler

  val phase = PHASE3
  val prevPhase = PHASE2
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE3_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE3_TESTS_EXPIRED

  implicit val applicationBSONReader: BSONDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val applicationId = doc.getAs[String]("applicationId").get
    val bsonPhase3 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase3 = bsonPhase3.map(Phase3TestGroup.bsonHandler.read).get
    val phase2Evaluation = passMarkEvaluationReader(prevPhase, applicationId, Some(doc))
    applicationEvaluationBuilder(Nil, phase3.activeTests.headOption, Some(phase2Evaluation))(doc)
  })

  //TODO: implement for P3
  implicit val applicationBSONReader2: BSONDocumentReader[ApplicationReadyForEvaluation2] = bsonReader(doc => {
    val bsonPhase1: Option[BSONDocument] = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase1: Phase1TestProfile2 = bsonPhase1.map(Phase1TestProfile2.bsonHandler.read).get
    applicationEvaluationBuilder2(phase1.activeTests, None, None)(doc)
  })

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
