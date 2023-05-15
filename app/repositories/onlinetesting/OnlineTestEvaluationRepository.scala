/*
 * Copyright 2023 HM Revenue & Customs
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

import config.{LaunchpadGatewayConfig, MicroserviceAppConfig}
import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.Exceptions.PassMarkEvaluationNotFound
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.persisted._
import model.persisted.phase3tests.{LaunchpadTest, Phase3TestGroup}
import model.{ApplicationStatus, Phase => _, _}
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait OnlineTestEvaluationRepository extends CommonBSONDocuments with ReactiveRepositoryHelpers with RandomSelection
  with CurrentSchemeStatusHelper with Schemes {

  // Enforce that the class implementing the trait must be a PlayMongoRepository[ApplicationReadyForEvaluation]
  this: PlayMongoRepository[ApplicationReadyForEvaluation] =>

  val phase: Phase
  val evaluationApplicationStatuses: Set[ApplicationStatus]
  val evaluationProgressStatus: ProgressStatus
  val expiredProgressStatus: ProgressStatus

  def applicationReadyForEvaluationBSONReader(doc: Document): ApplicationReadyForEvaluation

  // Function that takes a String as argument (the current pass mark version) and returns a Document that contains the query to run
  val nextApplicationQuery: String => Document

  // Gives classes implementing this trait the opportunity to log anything important when looking for candidates to evaluate
  def preEvaluationLogging(): Unit = ()

  def validEvaluationPhaseStatuses(phase: ApplicationStatus): Set[ApplicationStatus] = {
    val statusesToIgnore = List(ApplicationStatus.PHASE1_TESTS_FAILED, ApplicationStatus.PHASE2_TESTS_FAILED)
    ApplicationStatus.values.filter(s =>
      s >= phase && s < ApplicationStatus.PHASE3_TESTS_PASSED && !statusesToIgnore.contains(s))
  }

  def nextApplicationsReadyForEvaluation(currentPassmarkVersion: String, batchSize: Int)(
    implicit ec: ExecutionContext): Future[Seq[ApplicationReadyForEvaluation]] = {
    preEvaluationLogging()
    selectRandom[ApplicationReadyForEvaluation](nextApplicationQuery(currentPassmarkVersion), batchSize)(doc =>
      applicationReadyForEvaluationBSONReader(doc), ec)
  }

  def savePassmarkEvaluation(applicationId: String, evaluation: PassmarkEvaluation,
                             newProgressStatus: Option[ProgressStatus])(implicit ec: ExecutionContext): Future[Unit] = {
    // Warn level so we see it in prod logs
    logger.warn(s"applicationId = $applicationId - now saving progressStatus as $newProgressStatus")

    val filter = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document("applicationStatus" -> Document("$in" -> Codecs.toBson(evaluationApplicationStatuses)))
    ))

    val fieldsToSet =
      Document(s"testGroups.$phase.evaluation" -> evaluation.toBson) ++
      newProgressStatus.map(applicationStatusBSON).getOrElse(Document.empty) ++
      currentSchemeStatusBSON(evaluation.result)
    val update = Document("$set" -> fieldsToSet)

    val validator = singleUpdateValidator(applicationId, actionDesc = s"saving passmark evaluation during $phase evaluation")
    collection.updateOne(filter, update).toFuture() map validator
  }

  def addSchemeResultToPassmarkEvaluation(applicationId: String,
                                          schemeEvaluationResult: SchemeEvaluationResult,
                                          passmarkVersion: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val removeEvaluationIfExists = Document(
      "$pull" -> Document(s"testGroups.$phase.evaluation.result" ->
        Document("schemeId" -> Sdip.toBson)))

    val passMarkEvaluation = Document(
      "$addToSet" -> Document(s"testGroups.$phase.evaluation.result" -> schemeEvaluationResult.toBson),
      "$set" -> Document(s"testGroups.$phase.evaluation.passmarkVersion" -> passmarkVersion)
    )

    val evalSdipvalidator = singleUpdateValidator(applicationId,
      actionDesc = s"add scheme evaluation result to passmark evaluation during $phase evaluation")

    collection.updateOne(query, removeEvaluationIfExists).toFuture() flatMap { _ =>
      collection.updateOne(query, passMarkEvaluation).toFuture() map evalSdipvalidator
    }
  }

  def applicationEvaluationBuilder(activePsiTests: List[PsiTest],
                                   activeLaunchPadTest: Option[LaunchpadTest],
                                   prevPhaseEvaluation: Option[PassmarkEvaluation])(doc: Document) = {

    val applicationId = doc.get("applicationId").get.asString().getValue
    val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
    val applicationRoute = Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").getOrElse(ApplicationRoute.Faststream.toBson))
    val isGis = Try(doc.get("assistance-details").exists(_.asDocument().getBoolean("guaranteedInterview").getValue)).getOrElse(false)
    val schemePreferencesBsonValue = doc.get("scheme-preferences").map(_.asDocument())
    val preferences = schemePreferencesBsonValue.map( bson => Codecs.fromBson[SelectedSchemes](bson) ).get

    ApplicationReadyForEvaluation(applicationId, applicationStatus, applicationRoute, isGis, activePsiTests,
      activeLaunchPadTest, prevPhaseEvaluation, preferences)
  }

  private[onlinetesting] def passMarkEvaluationReader(passMarkPhase: String,
                                                      applicationId: String, optDoc: Option[Document]): PassmarkEvaluation = {
    optDoc.map { doc =>
      val bsonPassmarkEvaluation = Try(
        doc.get("testGroups").map(_.asDocument().get(passMarkPhase).asDocument().get("evaluation") )
      ).toOption.flatten

      bsonPassmarkEvaluation.map{ bson =>
        Try(Codecs.fromBson[PassmarkEvaluation](bson)).getOrElse(throw PassMarkEvaluationNotFound(applicationId))
      }.getOrElse(throw PassMarkEvaluationNotFound(applicationId))
    }.getOrElse(throw PassMarkEvaluationNotFound(applicationId))
  }

  def getPassMarkEvaluation(applicationId: String)(implicit ec: ExecutionContext): Future[PassmarkEvaluation] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include(s"testGroups.$phase.evaluation")

    collection.find[Document](query).projection(projection).headOption().map { optDoc =>
      passMarkEvaluationReader(phase, applicationId, optDoc)
    }
  }
}

@Singleton
class Phase1EvaluationMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory, mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[ApplicationReadyForEvaluation](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = ApplicationReadyForEvaluation.applicationReadyForEvaluationFormat,
    indexes = Nil
  ) with OnlineTestEvaluationRepository with CommonBSONDocuments {

  val phase = PHASE1
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE1_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE1_TESTS_EXPIRED

  def applicationReadyForEvaluationBSONReader(doc: Document) : ApplicationReadyForEvaluation = {
    val bsonPhase1 = doc.get("testGroups").map( _.asDocument().get(phase.toString).asDocument() )
    val phase1: Phase1TestProfile = bsonPhase1.map( Codecs.fromBson[Phase1TestProfile] ).get
    applicationEvaluationBuilder(phase1.activeTests, activeLaunchPadTest = None, prevPhaseEvaluation = None)(doc)
  }

  val nextApplicationQuery = (currentPassmarkVersion: String) => {
    Document("$and" -> BsonArray(
      Document("applicationStatus" -> Document("$in" -> Codecs.toBson(evaluationApplicationStatuses))),
      Document(s"progress-status.$evaluationProgressStatus" -> true),
      Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN}" -> Document("$exists" -> false)),
      Document(s"progress-status.$expiredProgressStatus" -> Document("$ne" -> true)),
      Document("$or" -> BsonArray(
        Document(s"testGroups.$phase.evaluation.passmarkVersion" -> Document("$exists" -> false)),
        Document(s"testGroups.$phase.evaluation.passmarkVersion" -> Document("$ne" -> currentPassmarkVersion))
      ))
    ))
  }
}

@Singleton
class Phase2EvaluationMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory, mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[ApplicationReadyForEvaluation](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = ApplicationReadyForEvaluation.applicationReadyForEvaluationFormat,
    indexes = Nil
  ) with OnlineTestEvaluationRepository with CommonBSONDocuments {

  val phase = PHASE2
  val prevPhase = PHASE1
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE2_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE2_TESTS_EXPIRED

  def applicationReadyForEvaluationBSONReader(doc: Document) : ApplicationReadyForEvaluation = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val bsonPhase2 = doc.get("testGroups").map(_.asDocument().get(phase.toString).asDocument() )
    val phase2 = bsonPhase2.map( Codecs.fromBson[Phase2TestGroup] ).get
    val phase1Evaluation = passMarkEvaluationReader(prevPhase, applicationId, Some(doc))
    applicationEvaluationBuilder(phase2.activeTests, activeLaunchPadTest = None, Some(phase1Evaluation))(doc)
  }

  //scalastyle:off line.size.limit
  val nextApplicationQuery = (currentPassmarkVersion: String) =>
    Document("$and" -> BsonArray(
      Document("applicationStatus" -> Document("$in" -> Codecs.toBson(evaluationApplicationStatuses))),
      Document(s"progress-status.$evaluationProgressStatus" -> true),
      Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN}" -> Document("$exists" -> false)),
      Document(s"progress-status.$expiredProgressStatus" -> Document("$ne" -> true)),
      Document(s"testGroups.$prevPhase.evaluation.passmarkVersion" -> Document("$exists" -> true)),
      Document("$or" -> BsonArray(
        Document(s"testGroups.$phase.evaluation.passmarkVersion" -> Document("$exists" -> false)),
        Document(s"testGroups.$phase.evaluation.passmarkVersion" -> Document("$ne" -> currentPassmarkVersion)),
        Document("$expr" -> Document(" { $ne: [ \"$testGroups.PHASE2.evaluation.previousPhasePassMarkVersion\", \"$testGroups.PHASE1.evaluation.passmarkVersion\"] } "))
      ))
    ))
  //scalastyle:on
}

@Singleton
class Phase3EvaluationMongoRepository @Inject() (appConfig: MicroserviceAppConfig,
                                                 val dateTimeFactory: DateTimeFactory,
                                                 mongoComponent: MongoComponent
                                                )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[ApplicationReadyForEvaluation](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongoComponent,
    domainFormat = ApplicationReadyForEvaluation.applicationReadyForEvaluationFormat,
    indexes = Nil
  ) with OnlineTestEvaluationRepository with BaseBSONReader {

  val launchpadGatewayConfig: LaunchpadGatewayConfig = appConfig.launchpadGatewayConfig

  val phase = PHASE3
  val prevPhase = PHASE2
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE3_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE3_TESTS_EXPIRED

  def applicationReadyForEvaluationBSONReader(doc: Document) : ApplicationReadyForEvaluation = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val bsonPhase3 = doc.get("testGroups").map(_.asDocument().get(phase.toString).asDocument() )
    val phase3 = bsonPhase3.map( Codecs.fromBson[Phase3TestGroup] ).get
    val phase2Evaluation = passMarkEvaluationReader(prevPhase, applicationId, Some(doc))
    applicationEvaluationBuilder(activePsiTests = Nil, phase3.activeTests.headOption, Some(phase2Evaluation))(doc)
  }

  override def preEvaluationLogging(): Unit =
    logger.warn("Phase 3 evaluation is looking for candidates with results received " +
      s"${launchpadGatewayConfig.phase3Tests.evaluationWaitTimeAfterResultsReceivedInHours} hours ago...")

  val nextApplicationQuery = (currentPassmarkVersion: String) => {
    // This document specifies evaluation will trigger for this phase if the either the previous phase passmark version
    // is changed or the previous phase result version is changed, which can happen if the phase 1 pass marks were changed.
    // This allows us to trigger a phase 3 evaluation from a phase 1 pass mark change (and also a phase 2 evaluation)
    //scalastyle:off line.size.limit
    val passMarksHaveChanged = Document("$or" -> BsonArray(
      Document("$expr" -> Document(" { $ne: [ \"$testGroups.PHASE3.evaluation.previousPhasePassMarkVersion\", \"$testGroups.PHASE2.evaluation.passmarkVersion\"] } ")),
      Document("$expr" -> Document(" { $ne: [ \"$testGroups.PHASE3.evaluation.previousPhaseResultVersion\", \"$testGroups.PHASE2.evaluation.resultVersion\"] } "))
    ))
    //scalastyle:on

    Document("$and" -> BsonArray(
      Document("applicationStatus" -> Document("$in" -> Codecs.toBson(evaluationApplicationStatuses))),
      Document(s"progress-status.$evaluationProgressStatus" -> true),
      Document(s"progress-status.${ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN}" -> Document("$exists" -> false)),
      Document(s"progress-status.$expiredProgressStatus" -> Document("$ne" -> true)),
      Document(s"testGroups.$prevPhase.evaluation.passmarkVersion" -> Document("$exists" -> true)),

      Document(s"testGroups.$phase.tests" ->
        Document("$elemMatch" -> Document(
          "usedForResults" -> true, "callbacks.reviewed" -> Document("$exists" -> true),

          "callbacks.reviewed" -> Document("$elemMatch" -> Document("received" -> Document("$lte" ->
            dateTimeToBson(dateTimeFactory.nowLocalTimeZone.minusHours(
              launchpadGatewayConfig.phase3Tests.evaluationWaitTimeAfterResultsReceivedInHours
            ))
          )))

        ))
      ),

      Document("$or" -> BsonArray(
        Document(s"testGroups.$phase.evaluation.passmarkVersion" -> Document("$exists" -> false)),
        Document(s"testGroups.$phase.evaluation.passmarkVersion" -> Document("$ne" -> currentPassmarkVersion)),
        passMarksHaveChanged
      ))
    ))
  }
}
