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

import config.{LaunchpadGatewayConfig, MicroserviceAppConfig}
import factories.DateTimeFactory

import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.Exceptions.PassMarkEvaluationNotFound
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.persisted._
import model.persisted.phase3tests.{LaunchpadTest, Phase3TestGroup}
import model.{ApplicationStatus, Phase => _, _}
import org.bson.BsonDocumentReader
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
//import play.modules.reactivemongo.ReactiveMongoComponent
//import reactivemongo.bson.{ BSONArray, BSONDocument, BSONDocumentReader, BSONObjectID }
//import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
//import uk.gov.hmrc.mongo.ReactiveRepository
//import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// Differences: this trait works with ReadApplicationReadyForEvaluation instead of ApplicationReadyForEvaluation
trait OnlineTestEvaluationRepository2 extends CommonBSONDocuments with ReactiveRepositoryHelpers with RandomSelection
  with CurrentSchemeStatusHelper {

  // Enforce that the class implementing the trait must be a PlayMongoRepository[ReadApplicationReadyForEvaluation]
  this: PlayMongoRepository[ReadApplicationReadyForEvaluation] =>

//  val applicationCollection: MongoCollection[Document] = this.getCollection(collectionName)

  val phase: Phase
  val evaluationApplicationStatuses: Set[ApplicationStatus]
  val evaluationProgressStatus: ProgressStatus
  val expiredProgressStatus: ProgressStatus

  //  implicit val applicationReadyForEvaluationBSONReader: BsonDocumentReader[ApplicationReadyForEvaluation]

  // Function that takes a String as argument (the current pass mark version) and returns a Document that contains the query to run
  val nextApplicationQuery: String => Document

  // Gives classes implementing this trait the opportunity to log anything important when looking for candidates to evaluate
  def preEvaluationLogging(): Unit = ()

  def validEvaluationPhaseStatuses(phase: ApplicationStatus): Set[ApplicationStatus] = {
    val statusesToIgnore = List(ApplicationStatus.PHASE1_TESTS_FAILED, ApplicationStatus.PHASE2_TESTS_FAILED)
    ApplicationStatus.values.filter(s =>
      s >= phase && s < ApplicationStatus.PHASE3_TESTS_PASSED && !statusesToIgnore.contains(s))
  }

  /*
  def nextApplicationsReadyForEvaluation(currentPassmarkVersion: String, batchSize: Int): Future[List[ApplicationReadyForEvaluation]] = {
    preEvaluationLogging()
    selectRandom[ApplicationReadyForEvaluation](nextApplicationQuery(currentPassmarkVersion), batchSize)
  }*/
  def nextApplicationsReadyForEvaluation(currentPassmarkVersion: String, batchSize: Int): Future[Seq[ApplicationReadyForEvaluation]] = {
    preEvaluationLogging()
    //    selectRandom[ApplicationReadyForEvaluation](nextApplicationQuery(currentPassmarkVersion), batchSize) //TODO: mongo fix select random
    // TODO: mongo temp code until we get the selectRandom migrated
    val futureResult = collection.find(nextApplicationQuery(currentPassmarkVersion)).limit(batchSize).toFuture()
    val mappedResult = futureResult.map { appsReadyForEvaluation =>
      appsReadyForEvaluation.map { app =>
        ApplicationReadyForEvaluation(app.applicationId, app.applicationStatus, app.applicationRoute, app.isGis, app.activePsiTests,
          activeLaunchpadTest = None, prevPhaseEvaluation = None, app.preferences)
      }
    }
    mappedResult
  }

  /*
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
  }*/
  def savePassmarkEvaluation(applicationId: String, evaluation: PassmarkEvaluation,
                             newProgressStatus: Option[ProgressStatus]): Future[Unit] = {
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

  /*
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
  }*/
  def addSchemeResultToPassmarkEvaluation(applicationId: String,
                                          schemeEvaluationResult: SchemeEvaluationResult,
                                          passmarkVersion: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val removeEvaluationIfExists = Document(
      "$pull" -> Document(s"testGroups.$phase.evaluation.result" ->
        Document("schemeId" -> SchemeId("Sdip").toBson)))

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

  // Curried function. Passes in some data in the first parameter list and then passes in a doc in the 2nd param list
  // manually builds an ApplicationReadyForEvaluation from all the data
  def applicationEvaluationBuilder(activePsiTests: List[PsiTest],
                                   activeLaunchPadTest: Option[LaunchpadTest],
                                   prevPhaseEvaluation: Option[PassmarkEvaluation])(doc: Document) = {

    val applicationId = doc.get("applicationId").get.asString().getValue
    val applicationStatusBsonValue = doc.get("applicationStatus").get

    val applicationStatus = Codecs.fromBson[ApplicationStatus](applicationStatusBsonValue)

    //scalastyle:off
    println(s"**** $applicationId")
    println(s"**** $applicationStatus")
    //scalastyle:on

    //    val applicationId = doc.get[String]("applicationId").get
    //    val applicationStatus = doc.get[ApplicationStatus]("applicationStatus").get
    //    val applicationRoute = doc.get[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
    //    val isGis = doc.get[Document]("assistance-details").exists(_.get[Boolean]("guaranteedInterview").contains(true))
    //    val preferences = doc.get[SelectedSchemes]("scheme-preferences").get
    //    ApplicationReadyForEvaluation(applicationId, applicationStatus, applicationRoute, isGis, activePsiTests,
    //      activeLaunchPadTest, prevPhaseEvaluation, preferences)
    ???
  }

  /*
  def passMarkEvaluationReader(passMarkPhase: String, applicationId: String, optDoc: Option[BSONDocument]): PassmarkEvaluation =
    optDoc.flatMap {_.getAs[BSONDocument]("testGroups")}
      .flatMap {_.getAs[BSONDocument](passMarkPhase)}
      .flatMap {_.getAs[PassmarkEvaluation]("evaluation")}
      .getOrElse(throw PassMarkEvaluationNotFound(applicationId))*/

  /*
  def getPassMarkEvaluation(applicationId: String): Future[PassmarkEvaluation] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(s"testGroups.$phase.evaluation" -> 1, "_id" -> 0)
    collection.find(query, Some(projection)).one[BSONDocument] map { optDoc =>
      passMarkEvaluationReader(phase, applicationId, optDoc)
    }
  }*/
  private def passMarkEvaluationReader(passMarkPhase: String, applicationId: String, optDoc: Option[Document]): PassmarkEvaluation = {
    optDoc.map { doc =>
      val bsonPassmarkEvaluation = doc.get("testGroups").map(_.asDocument().get(passMarkPhase).asDocument().get("evaluation").asDocument() )
      bsonPassmarkEvaluation.map( bson => Codecs.fromBson[PassmarkEvaluation](bson) ).get
    }.getOrElse(throw PassMarkEvaluationNotFound(applicationId))
  }

  def getPassMarkEvaluation(applicationId: String): Future[PassmarkEvaluation] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include(s"testGroups.$phase.evaluation")

    collection.find[Document](query).projection(projection).headOption().map { optDoc =>
      passMarkEvaluationReader(phase, applicationId, optDoc)
    }
  }
}
//scalastyle:on

@Singleton
class Phase1EvaluationMongoRepository2 @Inject() (val dateTimeFactory: DateTimeFactory, mongo: MongoComponent)
  extends PlayMongoRepository[ReadApplicationReadyForEvaluation](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = ReadApplicationReadyForEvaluation.mongoFormat,
    indexes = Nil
  ) with OnlineTestEvaluationRepository2 with CommonBSONDocuments {

  val phase = PHASE1
  val evaluationApplicationStatuses = validEvaluationPhaseStatuses(ApplicationStatus.PHASE1_TESTS)
  val evaluationProgressStatus = ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED
  val expiredProgressStatus = ProgressStatuses.PHASE1_TESTS_EXPIRED

//  implicit val applicationReadyForEvaluationBSONReader: BsonDocumentReader[ApplicationReadyForEvaluation] = ???
/*
  implicit val applicationReadyForEvaluationBSONReader: BsonDocumentReader[ApplicationReadyForEvaluation] = bsonReader(doc => {
    val bsonPhase1: Option[BSONDocument] = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument](phase))
    val phase1: Phase1TestProfile = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
    applicationEvaluationBuilder(phase1.activeTests, None, None)(doc)
  })*/

  val nextApplicationQuery = (currentPassmarkVersion: String) => {
    //scalastyle:off
    println(s"**** evaluationApplicationStatuses=$evaluationApplicationStatuses")
    //scalastyle:on

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
