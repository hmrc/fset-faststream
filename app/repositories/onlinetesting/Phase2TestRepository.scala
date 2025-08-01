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

import common.Phase2TestConcern
import factories.DateTimeFactory
import model.*
import model.ApplicationStatus.ApplicationStatus
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.*
import model.command.ApplicationForSkippingPhases
import model.persisted.*
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonArray, BsonDocument}
import org.mongodb.scala.model.Projections
import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture, bsonDocumentToDocument}
import repositories.{CollectionNames, subDocRoot}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait Phase2TestRepository extends OnlineTestRepository with Phase2TestConcern {
  this: PlayMongoRepository[_] =>

  def getTestGroup(applicationId: String): Future[Option[Phase2TestGroup]]
  def getTestGroupByUserId(userId: String): Future[Option[Phase2TestGroup]]
  def getTestGroupByOrderId(orderId: String): Future[Option[Phase2TestGroup]]
  def getTestProfileByOrderId(orderId: String): Future[Phase2TestGroupWithAppId]
  def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup): Future[Unit]
  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit]
  def saveTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup): Future[Unit]
  def updateGroupExpiryTime(applicationId: String, expirationDate: OffsetDateTime): Future[Unit]
  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]
  def applicationReadyForOnlineTesting(applicationId: String): Future[Option[OnlineTestApplication]]
  def nextApplicationsReadyToSkipPhases(batchSize: Int): Future[Seq[ApplicationForSkippingPhases]]
  def skipPhases(application: ApplicationForSkippingPhases): Future[Unit]
}

@Singleton
class Phase2TestMongoRepository @Inject()(dateTime: DateTimeFactory, mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Phase2TestGroup](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongoComponent,
    domainFormat = model.persisted.Phase2TestGroup.phase2TestProfileFormat,
    indexes = Nil
  ) with Phase2TestRepository {

  override val phaseName = "PHASE2"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE2_TESTS
  override val resetStatuses = List[String](ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_FAILED,
    ApplicationStatus.PHASE2_TESTS_PASSED, ApplicationStatus.PHASE3_TESTS)
  override val dateTimeFactory = dateTime

  override val expiredTestQuery: Document = {
    Document("$and" -> BsonArray(
      Document(s"progress-status.$PHASE2_TESTS_COMPLETED" -> Document("$ne" -> true)),
      Document(s"progress-status.$PHASE2_TESTS_EXPIRED" -> Document("$ne" -> true))
    ))
  }

  //  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase2TestGroup] = Phase2TestGroup.bsonHandler
  override implicit val bsonReads: play.api.libs.json.Reads[T] = Phase2TestGroup.phase2TestProfileFormat

  override def getTestGroup(applicationId: String): Future[Option[Phase2TestGroup]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestGroupByUserId(userId: String): Future[Option[Phase2TestGroup]] = {
    val query = Document("userId" -> userId)
    val projection = Projections.include("testGroups.PHASE2")

    collection.find[Document](query).projection(projection).headOption() map { optDocument =>
      optDocument.flatMap { doc =>

        doc.get("testGroups").map(_.asDocument().get("PHASE2").asDocument()).map { p =>
          Codecs.fromBson[Phase2TestGroup](p)
        }
      }
    }
  }

  override def getTestGroupByOrderId(orderId: String): Future[Option[Phase2TestGroup]] = {
    val query = BsonDocument(s"testGroups.$phaseName.tests" -> BsonDocument(
      "$elemMatch" -> BsonDocument("orderId" -> orderId)
    ))
    val projection = Projections.include("applicationId", s"testGroups.$phaseName")

    collection.find[Document](query).projection(projection).headOption() map { optDoc =>
      optDoc.flatMap { doc => subDocRoot("testGroups")(doc).flatMap( doc => subDocRoot(phaseName)(doc) ) }
        .map { doc => Codecs.fromBson[Phase2TestGroup](doc) }
    }
  }

  override def applicationReadyForOnlineTesting(applicationId: String): Future[Option[OnlineTestApplication]] = {
    val query = BsonDocument("applicationId" -> applicationId)
    collection.find[Document](query).headOption() map { optDocument =>
      optDocument.map { doc => repositories.bsonDocToOnlineTestApplication(doc) }
    }
  }

  override def nextApplicationsReadyForOnlineTesting(batchSize: Int): Future[Seq[OnlineTestApplication]] = {
    logger.warn(s"Looking for candidates to invite to $phaseName with a batch size of $batchSize...")
    val query = inviteToTestBSON(PHASE1_TESTS_PASSED) ++ Document("applicationRoute" -> Document("$nin" -> BsonArray("Sdip", "Edip")))

    selectRandom[OnlineTestApplication](query, batchSize)(
      doc => repositories.bsonDocToOnlineTestApplication(doc), ec
    )
  }

  override def getTestProfileByOrderId(orderId: String): Future[Phase2TestGroupWithAppId] = {
    val query = Document("testGroups.PHASE2.tests" -> Document(
      "$elemMatch" -> Document("orderId" -> orderId)
    ))
    val projection = Projections.include("applicationId", s"testGroups.$phaseName")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        val applicationId = doc.get("applicationId").get.asString().getValue
        val bsonPhase2 = doc.get("testGroups").map( _.asDocument().get(phaseName).asDocument() )
        val phase2TestGroup = bsonPhase2.map( Codecs.fromBson[Phase2TestGroup] )
          .getOrElse(cannotFindTestByOrderId(orderId))
        Phase2TestGroupWithAppId(applicationId, phase2TestGroup)
      case _ => cannotFindTestByOrderId(orderId)
    }
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: OffsetDateTime): Future[Unit] = {
    updateGroupExpiryTime(applicationId, expirationDate, phaseName)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup): Future[Unit] = {
    val filter = Document("applicationId" -> applicationId)
    val update = Document("$set" ->
      (applicationStatusBSON(PHASE2_TESTS_INVITED) ++ Document("testGroups.PHASE2" -> phase2TestProfile.toBson))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "inserting test group")
    collection.updateOne(filter, update).toFuture() map validator
  }

  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit] = {
    upsertTestGroupEvaluationResult(applicationId, passmarkEvaluation)
  }

  override def saveTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup): Future[Unit] = {
    val query = BsonDocument("applicationId" -> applicationId)
    val updateBSON = BsonDocument("$set" -> BsonDocument("testGroups.PHASE2" -> Codecs.toBson(phase2TestProfile)))

    val validator = singleUpdateValidator(applicationId, actionDesc = "Saving phase2 test group")
    collection.updateOne(query, updateBSON).toFuture() map validator
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = Document("$and" -> BsonArray(
      Document(s"progress-status.$PHASE2_TESTS_COMPLETED" -> Document("$ne" -> true)),
      Document(s"progress-status.$PHASE2_TESTS_EXPIRED" -> Document("$ne" -> true)),
      Document(s"progress-status.${reminder.progressStatus}" -> Document("$ne" -> true))
    ))

    nextTestForReminder(reminder, progressStatusQuery)
  }

  // Additional collection configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val applicationForSkippingCollection: MongoCollection[ApplicationForSkippingPhases] =
  CollectionFactory.collection(
    collectionName = CollectionNames.APPLICATION,
    db = mongoComponent.database,
    domainFormat = ApplicationForSkippingPhases.applicationForSkippingPhases
  )

  override def nextApplicationsReadyToSkipPhases(batchSize: Int): Future[Seq[ApplicationForSkippingPhases]] = {
    // Faststream only (not Sdip) applications that we need to move to a state where they have skipped phase2 and passed phase3:
    // They need to be in PHASE1_TESTS_PASSED
    // They must have at least one P2 scheme evaluated to Green
    // And no Amber banded schemes because all schemes must in a terminal evaluation state (Greens or Reds)
    val query = Document("$and" -> BsonArray(
      Document("applicationRoute" -> ApplicationRoute.Faststream.toBson),
      Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED.toBson),
      Document(s"testGroups.PHASE1.evaluation.result" -> Document("$elemMatch" -> Document("result" -> EvaluationResults.Green.toString))),
      Document(s"testGroups.PHASE1.evaluation.result" ->
        Document("$not" -> Document("$elemMatch" -> Document("result" -> EvaluationResults.Amber.toString))))
    ))
    selectRandom[ApplicationForSkippingPhases](applicationForSkippingCollection, query, batchSize)
  }

  // Put the candidates in the same state as they would be in if they had passed phase3
  override def skipPhases(application: ApplicationForSkippingPhases): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val update = Document("$set" ->
      Document(
        "testGroups.PHASE3.evaluation.result" -> Codecs.toBson(application.currentSchemeStatus),
        "applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED.toBson,
      ).++(
        progressStatusOnlyBSON(ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED)
      )
    )

    val validator = singleUpdateValidator(application.applicationId, actionDesc = s"Skipping phases for ${application.applicationId}")
    collection.updateOne(query, update).toFuture() map validator
  }
}
