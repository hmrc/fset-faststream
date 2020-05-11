/*
 * Copyright 2020 HM Revenue & Customs
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

import common.Phase2TestConcern2
import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model.persisted._
import model.{ ApplicationStatus, ReminderNotice }
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase2TestRepository2 extends OnlineTestRepository with Phase2TestConcern2 {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase2TestGroup2]]

  def getTestGroupByUserId(userId: String): Future[Option[Phase2TestGroup2]]

  def getTestGroupByOrderId(orderId: String): Future[Option[Phase2TestGroup2]]

  def getTestProfileByOrderId(orderId: String): Future[Phase2TestGroupWithAppId2]

  def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup2): Future[Unit]

  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit]

  def saveTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup2): Future[Unit]

  def nextTestGroupWithReportReady: Future[Option[Phase2TestGroupWithAppId2]]

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit]

  // TODO: Update this to PSI spec later
  def insertTestResult(appId: String, phase2Test: CubiksTest, testResult: TestResult): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

  def applicationReadyForOnlineTesting(applicationId: String): Future[Option[OnlineTestApplication]]
}

class Phase2TestMongoRepository2(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase2TestGroup2, BSONObjectID](CollectionNames.APPLICATION, mongo,
    model.persisted.Phase2TestGroup2.phase2TestProfileFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase2TestRepository2 {

  override val phaseName = "PHASE2"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE2_TESTS
  override val resetStatuses = List[String](ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_FAILED,
    ApplicationStatus.PHASE2_TESTS_PASSED, ApplicationStatus.PHASE3_TESTS)
  override val dateTimeFactory = dateTime
  override val expiredTestQuery: BSONDocument = {
    BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE2_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE2_TESTS_EXPIRED" -> BSONDocument("$ne" -> true))
    ))
  }

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase2TestGroup2] = Phase2TestGroup2.bsonHandler

  override def getTestGroup(applicationId: String): Future[Option[Phase2TestGroup2]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestGroupByUserId(userId: String): Future[Option[Phase2TestGroup2]] = {
    val query = BSONDocument("userId" -> userId)
    val projection = BSONDocument(s"testGroups.PHASE2" -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map { optDocument =>
      optDocument.flatMap {_.getAs[BSONDocument]("testGroups")}
        .flatMap {_.getAs[BSONDocument]("PHASE2")}
        .map {x => bsonHandler.read(x)}
    }
  }

  override def getTestGroupByOrderId(orderId: String): Future[Option[Phase2TestGroup2]] = {
    val query = BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("orderId" -> orderId)
    ))
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map { optDocument =>
      optDocument.flatMap {_.getAs[BSONDocument]("testGroups")}
        .flatMap {_.getAs[BSONDocument]("PHASE2")}
        .map {x => bsonHandler.read(x)}
    }
  }

  override def applicationReadyForOnlineTesting(applicationId: String): Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument.empty

    collection.find(query, Some(projection)).one[BSONDocument] map { optDocument =>
      optDocument.map { doc => repositories.bsonDocToOnlineTestApplication(doc) }
    }
  }

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] = {
    val query = inviteToTestBSON(PHASE1_TESTS_PASSED) ++ BSONDocument("applicationRoute" -> BSONDocument("$nin" -> BSONArray("Sdip", "Edip")))

    implicit val reader = bsonReader(repositories.bsonDocToOnlineTestApplication)
    selectRandom[OnlineTestApplication](query, maxBatchSize)
  }

  def getTestProfileByOrderId(orderId: String): Future[Phase2TestGroupWithAppId2] = {
    val query = BSONDocument("testGroups.PHASE2.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("orderId" -> orderId)
    ))
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val bsonPhase2 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phase2TestGroup = bsonPhase2.map(Phase2TestGroup2.bsonHandler.read)
          .getOrElse(cannotFindTestByOrderId(orderId))
        Phase2TestGroupWithAppId2(applicationId, phase2TestGroup)
      case _ => cannotFindTestByOrderId(orderId)
    }
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    updateGroupExpiryTime(applicationId, expirationDate, phaseName)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup2): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateBson = BSONDocument("$set" ->
      (applicationStatusBSON(PHASE2_TESTS_INVITED) ++ BSONDocument("testGroups.PHASE2" -> phase2TestProfile))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "inserting test group")

    collection.update(ordered = false).one(query, updateBson) map validator
  }

  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit] = {
    upsertTestGroupEvaluationResult(applicationId, passmarkEvaluation)
  }

  override def saveTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup2): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> BSONDocument("testGroups.PHASE2" -> phase2TestProfile))

    val validator = singleUpdateValidator(applicationId, actionDesc = "Saving phase2 test group")
    collection.update(ordered = false).one(query, updateBSON).map(validator)
  }

  override def insertTestResult(appId: String, phase2Test: CubiksTest, testResult: TestResult): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> appId,
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> phase2Test.cubiksUserId)
      )
    )

    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.testResult" -> TestResult.testResultBsonHandler.write(testResult)
    ))

    val validator = singleUpdateValidator(appId, actionDesc = "inserting test results")

    collection.update(ordered = false).one(query, update) map validator
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE2_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE2_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
    ))

    nextTestForReminder(reminder, progressStatusQuery)
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase2TestGroupWithAppId2]] = {

    implicit val reader = bsonReader { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument](phaseName).get
      Phase2TestGroupWithAppId2(
        applicationId = doc.getAs[String]("applicationId").get,
        Phase2TestGroup2.bsonHandler.read(group)
      )
    }

    nextTestGroupWithReportReady[Phase2TestGroupWithAppId2]
  }
}
