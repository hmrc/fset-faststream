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

import common.Phase3TestConcern
import factories.DateTimeFactory
import model.*
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.{ApplicationNotFound, NotFoundException, TokenNotFound}
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.*
import model.command.ApplicationForSkippingPhases
import model.persisted.phase3tests.Phase3TestGroup
import model.persisted.{NotificationExpiringOnlineTest, PassmarkEvaluation, Phase3TestGroupWithAppId, SchemeEvaluationResult}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonString}
import org.mongodb.scala.model.Projections
import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture, bsonDocumentToDocument}
import play.api.libs.json.{Reads, Writes}
import repositories.*
import repositories.onlinetesting.Phase3TestRepository.CannotFindTestByLaunchpadId
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object Phase3TestRepository {
  case class CannotFindTestByLaunchpadId(message: String) extends NotFoundException(message)
}

trait Phase3TestRepository extends OnlineTestRepository with Phase3TestConcern {
  this: PlayMongoRepository[_] =>

  def appendCallback[A](token: String, callbacksKey: String, callback: A)(implicit writes: Writes[A]): Future[Unit]
  def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]]
  def getTestGroupByToken(token: String): Future[Phase3TestGroupWithAppId]
  def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit]
  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit]
  def updateTestStartTime(launchpadInviteId: String, startedTime: OffsetDateTime): Future[Unit]
  def updateTestCompletionTime(launchpadInviteId: String, completionTime: OffsetDateTime): Future[Unit]
  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]
  def removePhase3TestGroup(applicationId: String): Future[Unit]
  def removeReviewedCallbacks(token: String): Future[Unit]
  def removeTest(token: String): Future[Unit]
  def markTestAsActive(token: String): Future[Unit]
  def updateExpiryDate(applicationId: String, expiryDate: OffsetDateTime): Future[Unit]
  def updateResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def addResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def nextApplicationsReadyToSkipPhase3(batchSize: Int): Future[Seq[ApplicationForSkippingPhases]]
  def skipPhase3(application: ApplicationForSkippingPhases): Future[Unit]
  def nextApplicationsReadyToFixSdipFsP3SkippedCandidates(batchSize: Int): Future[Seq[ApplicationForSkippingPhases]]
  def fixSdipFsP3SkippedCandidates(application: ApplicationForSkippingPhases): Future[Unit]
}

@Singleton
class Phase3TestMongoRepository @Inject() (dateTime: DateTimeFactory, mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Phase3TestGroup](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongoComponent,
    domainFormat = model.persisted.phase3tests.Phase3TestGroup.phase3TestGroupFormat,
    indexes = Nil
  ) with Phase3TestRepository with CommonBSONDocuments {

  override val phaseName = "PHASE3"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE3_TESTS
  override val resetStatuses = List[String](ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_PASSED,
    ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER, ApplicationStatus.PHASE3_TESTS_FAILED)
  override val dateTimeFactory = dateTime

  override val expiredTestQuery: Document = {
    Document("$and" -> BsonArray(
      Document(s"progress-status.$PHASE3_TESTS_COMPLETED" -> Document("$ne" -> true)),
      Document(s"progress-status.$PHASE3_TESTS_EXPIRED" -> Document("$ne" -> true))
    ))
  }

//  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase3TestGroup] = Phase3TestGroup.bsonHandler
  override implicit val bsonReads: Reads[T] = Phase3TestGroup.phase3TestGroupFormat

  override def updateResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val phase3TestGroupEvaluation = s"testGroups.$phaseName.evaluation"
    val saveEvaluationResultsDoc = BsonDocument(s"$phase3TestGroupEvaluation.result" -> Codecs.toBson(result))
    val removeDoc = BsonDocument(
      "$pull" -> BsonDocument(s"$phase3TestGroupEvaluation.result" -> BsonDocument("schemeId" -> result.schemeId.value))
    )
    val setDoc = BsonDocument("$addToSet" -> saveEvaluationResultsDoc)

    val removePredicate = BsonDocument("$and" -> BsonArray(
      BsonDocument("applicationId" -> applicationId),
      BsonDocument(
        s"$phase3TestGroupEvaluation.result.schemeId" -> BsonDocument("$in" -> BsonArray(result.schemeId.value))
      )
    ))
    val setPredicate = BsonDocument("$and" -> BsonArray(
      BsonDocument("applicationId" -> applicationId),
      BsonDocument(
        s"$phase3TestGroupEvaluation.result.schemeId" -> BsonDocument("$nin" -> BsonArray(result.schemeId.value))
      )
    ))

    val validator = singleUpdateValidator(applicationId, s"Fixing phase3 results for ${result.schemeId}", ApplicationNotFound(applicationId))

    for {
      _ <- collection.updateOne(removePredicate, removeDoc).toFuture() map validator
      _ <- collection.updateOne(setPredicate, setDoc).toFuture() map validator
    } yield ()
  }

  // This allows us to add a P3 evaluation section as a workaround while we do not have a P3 test integration partner
  // By doing this we can get a PHASE2_TESTS_PASSED candidate into sift
  // (after setting the applicationStatus to PHASE3_TESTS_PASSED_NOTIFIED)
  override def addResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val phase3TestGroupEvaluation = s"testGroups.$phaseName.evaluation"
    val saveEvaluationResultsDoc = BsonDocument(s"$phase3TestGroupEvaluation.result" -> Codecs.toBson(result))
    val update = BsonDocument("$addToSet" -> saveEvaluationResultsDoc)

    val predicate = BsonDocument("$and" -> BsonArray(
      BsonDocument("applicationId" -> applicationId),
      BsonDocument(
        s"$phase3TestGroupEvaluation.result.schemeId" -> BsonDocument("$nin" -> BsonArray(result.schemeId.value))
      )
    ))

    val validator = singleUpdateValidator(applicationId, s"Adding phase3 results for ${result.schemeId}", ApplicationNotFound(applicationId))
    collection.updateOne(predicate, update).toFuture() map validator
  }

  // Here we have an implicit BSONHandler which converts from a BSONDocument to the type A. The type A is the type of the
  // passed callback we want to write to mongo
  override def appendCallback[A](token: String, callbacksKey: String, callback: A)(implicit writes: Writes[A]): Future[Unit] = {
    val query = Document(s"testGroups.$phaseName.tests" -> Document(
      "$elemMatch" -> Document("token" -> token)
    ))

    val update = Document("$push" ->
      Document(s"testGroups.$phaseName.tests.$$.callbacks.$callbacksKey" -> Codecs.toBson(callback))
    )

    val validator = singleUpdateValidator(token, actionDesc = "appending phase 3 callback")

    collection.updateOne(query, update).toFuture() map validator
  }

  override def nextApplicationsReadyForOnlineTesting(batchSize: Int): Future[Seq[OnlineTestApplication]] = {
    logger.warn(s"Looking for candidates to invite to $phaseName with a batch size of $batchSize...")
    val query = inviteToTestBSON(PHASE2_TESTS_PASSED) ++ Document("applicationRoute" -> Document("$nin" -> BsonArray("Sdip", "Edip")))

    selectRandom[OnlineTestApplication](query, batchSize)(
      doc => repositories.bsonDocToOnlineTestApplication(doc), ec
    )
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit] = {
    val filter = Document("applicationId" -> applicationId)

    val appStatusBSON = Document("$set" ->
      (applicationStatusBSON(PHASE3_TESTS_INVITED) ++ Document(s"testGroups.$phaseName" -> phase3TestGroup.toBson))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "inserting or updating test group")

    collection.updateOne(filter, appStatusBSON).toFuture() map validator
  }

  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit] = {
    upsertTestGroupEvaluationResult(applicationId, passmarkEvaluation)
  }

  // Note this overrides the default impl in OnlineTestRepository. Maybe rename this method so we have the default available
  override def removeTestGroup(applicationId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val appStatuses = List(ApplicationStatus.PHASE3_TESTS,
      ApplicationStatus.PHASE3_TESTS_FAILED,
      ApplicationStatus.PHASE3_TESTS_PASSED)

    val phase3Progresses = ProgressStatuses.progressesByApplicationStatus(appStatuses: _*)

    val query = Document(
      "applicationId" -> applicationId,
      "applicationStatus" -> Document("$in" -> Codecs.toBson(appStatuses))
    )

    val progressesToRemove = phase3Progresses map (p => s"progress-status.$p" -> BsonString(""))

    val updateQuery = Document(
      "$unset" -> Document(progressesToRemove),
      "$unset" -> Document(s"testGroups.$phaseName" -> "")
    )

    val validator = singleUpdateValidator(applicationId, "removing test group", ApplicationNotFound(applicationId))
    collection.updateOne(query, updateQuery).toFuture() map validator
  }

  // Note this is the same impl as the default removeTestGroup in OnlineTestRepository. Provided here because
  // the default impl is overridden above
  override def removePhase3TestGroup(applicationId: String): Future[Unit] = {
    super.removeTestGroup(applicationId)
  }

  override def removeReviewedCallbacks(token: String): Future[Unit] = {
    val query = BsonDocument(s"testGroups.$phaseName.tests" -> BsonDocument(
      "$elemMatch" -> BsonDocument("token" -> token)
    ))
    val update = BsonDocument(
      "$set" -> BsonDocument(s"testGroups.$phaseName.tests.$$.callbacks.reviewed" -> List.empty[String])
    )

    val validator = singleUpdateValidator(token, "removing reviewed callbacks", TokenNotFound(token))
    collection.updateOne(query, update).toFuture() map validator
  }

  override def removeTest(token: String): Future[Unit] = {
    val removePredicate = BsonDocument(s"testGroups.$phaseName.tests" -> BsonDocument(
      "$elemMatch" -> BsonDocument("token" -> token)
    ))

    val removeDoc = BsonDocument(
      "$pull" -> BsonDocument(s"testGroups.$phaseName.tests" -> BsonDocument("token" -> token))
    )

    val validator = singleUpdateValidator(token, "removing test", TokenNotFound(s"Failed to remove P3 test for $token"))

    collection.updateOne(removePredicate, removeDoc).toFuture() map validator
  }

  override def markTestAsActive(token: String): Future[Unit] = {
    val query = BsonDocument(s"testGroups.$phaseName.tests" -> BsonDocument(
      "$elemMatch" -> BsonDocument("token" -> token)
    ))

    val update = BsonDocument("$set" -> BsonDocument(
      s"testGroups.$phaseName.tests.$$.usedForResults" -> true
    ))

    val validator = singleUpdateValidator(token, "setting P3 test active", TokenNotFound(s"Failed to set P3 test active for $token"))

    collection.updateOne(query, update).toFuture() map validator
  }

  override def updateExpiryDate(applicationId: String, expiryDate: OffsetDateTime): Future[Unit] = {
    val query = BsonDocument("applicationId" -> applicationId)
    val update = BsonDocument("$set" -> BsonDocument(
      s"testGroups.$phaseName.expirationDate" -> offsetDateTimeToBson(expiryDate)
    ))

    val validator = singleUpdateValidator(applicationId, "setting phase3 expiration date", ApplicationNotFound(applicationId))
    collection.updateOne(query, update).toFuture() map validator
  }

  override def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestGroupByToken(token: String): Future[Phase3TestGroupWithAppId] = {
    val query = BsonDocument(s"testGroups.$phaseName.tests" -> BsonDocument(
      "$elemMatch" -> BsonDocument("token" -> token)
    ))
    val projection = Projections.include("applicationId", s"testGroups.$phaseName")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        val applicationId = extractAppIdOpt(doc).get
        val bsonPhase3Opt = subDocRoot("testGroups")(doc).map( doc => subDocRoot(phaseName)(doc).get )
        val phase3TestGroup = bsonPhase3Opt.map { bsonPhase3 =>
          Codecs.fromBson[Phase3TestGroup](bsonPhase3)
        }.getOrElse(defaultUpdateErrorHandler(token))

        Phase3TestGroupWithAppId(applicationId, phase3TestGroup)
      case _ => defaultUpdateErrorHandler(token)
    }
  }

  override def updateTestStartTime(launchpadInviteId: String, startedTime: OffsetDateTime) : Future[Unit] = {
    val update = BsonDocument("$set" -> BsonDocument(
      s"testGroups.$phaseName.tests.$$.startedDateTime" -> Some(offsetDateTimeToBson(startedTime))
    ))

    findAndUpdateLaunchpadTest(launchpadInviteId, update)
  }

  override def updateTestCompletionTime(launchpadInviteId: String, completedTime: OffsetDateTime): Future[Unit] = {
    val update = BsonDocument("$set" -> BsonDocument(
      s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(offsetDateTimeToBson(completedTime))
    ))

    findAndUpdateLaunchpadTest(launchpadInviteId, update)
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = Document("$and" -> BsonArray(
      Document(s"progress-status.$PHASE3_TESTS_COMPLETED" -> Document("$ne" -> true)),
      Document(s"progress-status.$PHASE3_TESTS_EXPIRED" -> Document("$ne" -> true)),
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

  override def nextApplicationsReadyToSkipPhase3(batchSize: Int): Future[Seq[ApplicationForSkippingPhases]] = {
    // Applications that we need to move to a state where they have passed phase3:
    // They need to be in PHASE2_TESTS_PASSED
    // They must have at least one P2 scheme evaluated to Green
    // And no Amber banded schemes because all schemes must in a terminal evaluation state (Greens or Reds)
    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> ApplicationStatus.PHASE2_TESTS_PASSED.toBson),
      Document(s"testGroups.PHASE2.evaluation.result" -> Document("$elemMatch" -> Document("result" -> EvaluationResults.Green.toString))),
      Document(s"testGroups.PHASE2.evaluation.result" ->
        Document("$not" -> Document("$elemMatch" -> Document("result" -> EvaluationResults.Amber.toString))))
      )
    )
    selectRandom[ApplicationForSkippingPhases](applicationForSkippingCollection, query, batchSize)
  }

  override def skipPhase3(application: ApplicationForSkippingPhases): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val update = Document("$set" ->
      Document(
        "testGroups.PHASE3.evaluation.result" -> Codecs.toBson(application.currentSchemeStatus),
        "applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED.toBson,
      ).++(
        progressStatusOnlyBSON(ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED)
      )
    )

    val validator = singleUpdateValidator(application.applicationId, actionDesc = s"Skipping Phase3 for ${application.applicationId}")
    collection.updateOne(query, update).toFuture() map validator
  }

  override def nextApplicationsReadyToFixSdipFsP3SkippedCandidates(batchSize: Int): Future[Seq[ApplicationForSkippingPhases]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationRoute" -> ApplicationRoute.SdipFaststream.toBson),
      Document("applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED.toBson),
      Document(s"progress-status.${ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED}" -> Document("$exists" -> false)),
      Document(s"testGroups.PHASE2.evaluation.result" -> Document("$elemMatch" -> Document("result" -> EvaluationResults.Green.toString))),
      Document(s"testGroups.PHASE2.evaluation.result" ->
        Document("$not" -> Document("$elemMatch" -> Document("result" -> EvaluationResults.Amber.toString)))
      )
    ))
    selectRandom[ApplicationForSkippingPhases](applicationForSkippingCollection, query, batchSize)
  }

  override def fixSdipFsP3SkippedCandidates(application: ApplicationForSkippingPhases): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val update = Document("$set" -> progressStatusOnlyBSON(ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED))

    val validator = singleUpdateValidator(application.applicationId,
      actionDesc = s"Fixing SdipFs P3 skipped candidate ${application.applicationId}"
    )
    collection.updateOne(query, update).toFuture() map validator
  }

  private def findAndUpdateLaunchpadTest(launchpadInviteId: String, update: BsonDocument,
                                         ignoreNotFound: Boolean = false): Future[Unit] = {
    val find = BsonDocument(
      s"testGroups.$phaseName.tests" -> BsonDocument(
        "$elemMatch" -> BsonDocument("token" -> launchpadInviteId)
      )
    )

    val validator = if (ignoreNotFound) {
      singleUpdateValidator(launchpadInviteId, actionDesc = "updating phase3 tests", ignoreNotFound = true)
    } else {
      singleUpdateValidator(launchpadInviteId, actionDesc = "updating phase3 tests",
        CannotFindTestByLaunchpadId(s"Cannot find test group by launchpad Id: $launchpadInviteId"))
    }

    collection.updateOne(find, update).toFuture() map validator
  }

  private def defaultUpdateErrorHandler(launchpadInviteId: String) = {
    logger.error(s"""Failed to update launchpad test: $launchpadInviteId""")
    throw CannotFindTestByLaunchpadId(s"Cannot find test group by launchpad Id: $launchpadInviteId")
  }
}
