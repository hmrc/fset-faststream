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

package repositories.application

import controllers.OnlineTestDetails
import factories.DateTimeFactory
import model.Exceptions.{ CannotFindTestByCubiksId, UnexpectedException }
import org.joda.time.DateTime
import model.OnlineTestCommands.{ OnlineTestApplication, Phase1TestProfile }
import model.PersistedObjects.{ ApplicationForNotification, ExpiringOnlineTest, NotificationExpiringOnlineTest }
import model.ProgressStatuses.{ PHASE1_TESTS_INVITED, _ }
import model.persisted.Phase1TestProfileWithAppId
import model.{ ApplicationStatus, Commands, ProgressStatuses, ReminderNotice }
import play.api.Logger
import play.api.libs.json.Json
import reactivemongo.api.DB
import reactivemongo.bson._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestRepository {
  def getPhase1TestGroup(applicationId: String): Future[Option[Phase1TestProfile]]

  def getPhase1TestProfileByToken(token: String): Future[Phase1TestProfile]

  def getPhase1TestProfileByCubiksId(cubiksUserId: Int): Future[Phase1TestProfileWithAppId]

  def updateGroupExpiryTime(applicationId: String, newExpirationDate: DateTime): Future[Unit]

  def insertOrUpdatePhase1TestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

  def nextExpiringApplication: Future[Option[ExpiringOnlineTest]]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]]

  def nextPhase1TestGroupWithReportReady: Future[Option[Phase1TestProfile]]

  def updateProgressStatus(appId: String, progressStatus: ProgressStatus): Future[Unit]

  def removePhase1TestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit]
}

// TODO: Rename to something like: Phase1TestGroupMongoRepository
class OnlineTestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[OnlineTestDetails, BSONObjectID]("application", mongo,
    Commands.Implicits.onlineTestDetailsFormat, ReactiveMongoFormats.objectIdFormats) with OnlineTestRepository with RandomSelection {

  override def getPhase1TestGroup(applicationId: String): Future[Option[Phase1TestProfile]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    phaseTestProfileByQuery(query)
  }

  override def getPhase1TestProfileByToken(token: String): Future[Phase1TestProfile] = {
    val query = BSONDocument("testGroups.PHASE1.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("token" -> token)
    ))
    phaseTestProfileByQuery(query).map(_.getOrElse(cannotFindTestByToken(token)))
  }

  override def getPhase1TestProfileByCubiksId(cubiksUserId: Int): Future[Phase1TestProfileWithAppId] = {
    val query = BSONDocument("testGroups.PHASE1.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument("applicationId" -> 1, "testGroups.PHASE1" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument]("PHASE1").get)
        val phase1TestGroup = bsonPhase1.map(Phase1TestProfile.phase1TestProfileHandler.read).getOrElse(cannotFindTestByCubiksId(cubiksUserId))
        Phase1TestProfileWithAppId(applicationId, phase1TestGroup)
      case _ => cannotFindTestByCubiksId(cubiksUserId)
    }
  }

  private def cannotFindTestByCubiksId(cubiksUserId: Int) = {
    throw CannotFindTestByCubiksId(s"Cannot find test group by cubiks Id: $cubiksUserId")
  }

  private def cannotFindTestByToken(token: String) = {
    throw CannotFindTestByCubiksId(s"Cannot find test group by token: $token")
  }

  private def phaseTestProfileByQuery(query: BSONDocument) = {
    val projection = BSONDocument("testGroups.PHASE1" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val bson = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument]("PHASE1").get)
        bson.map(Phase1TestProfile.phase1TestProfileHandler.read)
      case _ => None
    }
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.update(query, BSONDocument("$set" -> BSONDocument(
      "testGroups.PHASE1.expirationDate" -> expirationDate
    ))).map { status =>
      if (status.n != 1) {
        val msg = s"Query to update testgroup expiration affected ${status.n} rows instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
  }

  override def insertOrUpdatePhase1TestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile) = {
    val query = BSONDocument("applicationId" -> applicationId)

    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.$PHASE1_TESTS_INVITED" -> true,
      "applicationStatus" -> PHASE1_TESTS_INVITED.applicationStatus
    )) ++ BSONDocument("$set" -> BSONDocument(
      "testGroups" -> BSONDocument("PHASE1" -> phase1TestProfile)
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { status =>
      if (status.n != 1) {
        val msg = s"${status.n} rows affected when inserting or updating instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
  }

  override def nextExpiringApplication: Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> ApplicationStatus.PHASE1_TESTS
      ),
      BSONDocument(
        "testGroups.PHASE1.expirationDate" -> BSONDocument("$lte" -> dateTime.nowLocalTimeZone) // Serialises to UTC.
      ),
      BSONDocument("$and" -> BSONArray(
        BSONDocument("progress-status.PHASE1_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
        BSONDocument("progress-status.PHASE1_TESTS_EXPIRED" -> BSONDocument("$ne" -> true))
      ))
    ))
    selectRandom(query).map(_.map(bsonDocToExpiringOnlineTest))
  }

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> ApplicationStatus.PHASE1_TESTS
      ),
      BSONDocument(
        "testGroups.PHASE1.expirationDate" -> BSONDocument(
          "$lte" -> dateTime.nowLocalTimeZone.plusHours(reminder.hoursBeforeReminder)) // Serialises to UTC.
      ),
      BSONDocument("$and" -> BSONArray(
        BSONDocument(s"progress-status.$PHASE1_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.$PHASE1_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
      ))
    ))
    selectRandom(query).map(_.map(bsonDocToNotificationExpiringOnlineTest))
  }

  override def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SUBMITTED),
      BSONDocument("civil-service-experience-details.fastPassReceived" -> BSONDocument("$ne" -> true))
    ))

    selectRandom(query).map(_.map(bsonDocToOnlineTestApplication))
  }


  override def nextPhase1TestGroupWithReportReady: Future[Option[Phase1TestProfile]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_READY}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" ->
        BSONDocument("$ne" -> true)
      )
    ))

    selectRandom(query).map(_.map { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument]("PHASE1").get
      Phase1TestProfile.phase1TestProfileHandler.read(group)
    })
  }

  override def updateProgressStatus(appId: String, progressStatus: ProgressStatus): Future[Unit] = {
    require(progressStatus.applicationStatus == ApplicationStatus.PHASE1_TESTS, "Forbidden progress status update")

    val query = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> ApplicationStatus.PHASE1_TESTS
    )

    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.$progressStatus" -> true
    ))
    collection.update(query, applicationStatusBSON, upsert = false) map ( _ => () )
  }

  override def removePhase1TestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty)
    require(progressStatuses forall (_.applicationStatus == ApplicationStatus.PHASE1_TESTS), "Cannot remove non Phase 1 progress status")

    val query = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> ApplicationStatus.PHASE1_TESTS
    )
    val progressesToRemoveQueryPartial = progressStatuses map (p => s"progress-status.$p" -> BSONString(""))

    val updateQuery = BSONDocument("$unset" -> BSONDocument(progressesToRemoveQueryPartial))

    collection.update(query, updateQuery, upsert = false) map ( _ => () )
  }

  private def bsonDocToExpiringOnlineTest(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ExpiringOnlineTest(applicationId, userId, preferredName)
  }

  private def bsonDocToNotificationExpiringOnlineTest(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    val testGroupsRoot = doc.getAs[BSONDocument]("testGroups").get
    val PHASE1Root = testGroupsRoot.getAs[BSONDocument]("PHASE1").get
    val expiryDate = PHASE1Root.getAs[DateTime]("expirationDate").get
    NotificationExpiringOnlineTest(applicationId, userId, preferredName, expiryDate)
  }

  private def bsonDocToApplicationForNotification(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }

}
