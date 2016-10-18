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

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.UnexpectedException
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model.persisted._
import model.{ ApplicationStatus, ProgressStatuses, ReminderNotice }
import org.joda.time.DateTime
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase2TestRepository extends OnlineTestRepository[CubiksTest, Phase2TestGroup] {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase2TestGroup]]

  def getTestProfileByToken(token: String): Future[Phase2TestGroup]

  def getTestProfileByCubiksId(cubiksUserId: Int): Future[Phase2TestGroupWithAppId]

  def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup): Future[Unit]

  def nextTestGroupWithReportReady: Future[Option[Phase2TestGroupWithAppId]]

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit]

  def removeTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit]

  def insertTestResult(appId: String, phase2Test: CubiksTest, testResult: TestResult): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

}

class Phase2TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase2TestGroup, BSONObjectID]("application", mongo,
    model.persisted.Phase2TestGroup.phase2TestProfileFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase2TestRepository {

  override val phaseName = "PHASE2"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE2_TESTS
  override val dateTimeFactory = dateTime
  override val expiredTestQuery: BSONDocument = {
    BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE2_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE2_TESTS_EXPIRED" -> BSONDocument("$ne" -> true))
    ))
  }

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase2TestGroup] = Phase2TestGroup.bsonHandler

  override def getTestGroup(applicationId: String): Future[Option[Phase2TestGroup]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestProfileByToken(token: String): Future[Phase2TestGroup] = {
    getTestProfileByToken(token, phaseName)
  }

  override def nextApplicationsReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    val query = BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED)

    implicit val reader = bsonReader(repositories.bsonDocToOnlineTestApplication)
    selectRandom[OnlineTestApplication](query, 50)
  }

  override def getTestProfileByCubiksId(cubiksUserId: Int): Future[Phase2TestGroupWithAppId] = {
    val query = BSONDocument("testGroups.PHASE2.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val bsonPhase2 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phase2TestGroup = bsonPhase2.map(Phase2TestGroup.bsonHandler.read).getOrElse(cannotFindTestByCubiksId(cubiksUserId))
        Phase2TestGroupWithAppId(applicationId, phase2TestGroup)
      case _ => cannotFindTestByCubiksId(cubiksUserId)
    }
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    updateGroupExpiryTime(applicationId, expirationDate, phaseName)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestGroup) = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateBson = BSONDocument("$set" -> applicationStatusBSON(PHASE2_TESTS_INVITED)
    ) ++ BSONDocument("$set" -> BSONDocument("testGroups.PHASE2" -> phase2TestProfile))

    collection.update(query, updateBson, upsert = false) map { status =>
      if (status.n != 1) {
        val msg = s"${status.n} rows affected when inserting or updating instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
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

    collection.update(query, update, upsert = false) map( _ => () )
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
      val progressStatusQuery = BSONDocument("$and" -> BSONArray(
        BSONDocument(s"progress-status.$PHASE2_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.$PHASE2_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
      ))

    nextTestForReminder(reminder, progressStatusQuery)
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase2TestGroupWithAppId]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> thisApplicationStatus),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_READY}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" ->
        BSONDocument("$ne" -> true)
      )
    ))

    implicit val reader = bsonReader { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument](phaseName).get
      Phase2TestGroupWithAppId(
        applicationId = doc.getAs[String]("applicationId").get,
        Phase2TestGroup.bsonHandler.read(group)
      )
    }

    selectOneRandom[Phase2TestGroupWithAppId](query)
  }

  override def removeTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty)
    require(progressStatuses forall (_.applicationStatus == thisApplicationStatus), "Cannot remove non Phase 2 progress status")

    val query = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> thisApplicationStatus
    )
    val progressesToRemoveQueryPartial = progressStatuses map (p => s"progress-status.$p" -> BSONString(""))

    val updateQuery = BSONDocument("$unset" -> BSONDocument(progressesToRemoveQueryPartial))

    collection.update(query, updateQuery, upsert = false) map ( _ => () )
  }

}
