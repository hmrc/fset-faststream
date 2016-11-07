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

import common.Phase1TestConcern
import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.UnexpectedException
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{ PHASE1_TESTS_INVITED, _ }
import model.persisted.{ NotificationExpiringOnlineTest, Phase1TestGroupWithUserIds, Phase1TestProfile }
import model.{ ApplicationStatus, ReminderNotice }
import org.joda.time.DateTime
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase1TestRepository extends OnlineTestRepository with Phase1TestConcern {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile]]

  def getTestProfileByToken(token: String): Future[Phase1TestProfile]

  def getTestProfileByCubiksId(cubiksUserId: Int): Future[Phase1TestGroupWithUserIds]

  def insertOrUpdateTestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile): Future[Unit]

  def nextTestGroupWithReportReady: Future[Option[Phase1TestGroupWithUserIds]]

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit]

  def removeTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

}

class Phase1TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase1TestProfile, BSONObjectID]("application", mongo,
    model.persisted.Phase1TestProfile.phase1TestProfileFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase1TestRepository {

  override val phaseName = "PHASE1"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE1_TESTS
  override val dateTimeFactory = dateTime
  override val expiredTestQuery: BSONDocument = {
    BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE1_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE1_TESTS_EXPIRED" -> BSONDocument("$ne" -> true))
    ))
  }

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase1TestProfile] = Phase1TestProfile.bsonHandler

  override def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestProfileByToken(token: String): Future[Phase1TestProfile] = {
    getTestProfileByToken(token, phaseName)
  }

  override def nextApplicationsReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    val submittedStatuses = List[String](ApplicationStatus.SUBMITTED, ApplicationStatus.SUBMITTED.toLowerCase)

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> submittedStatuses)),
      BSONDocument("civil-service-experience-details.fastPassReceived" -> BSONDocument("$ne" -> true))
    ))

    implicit val reader = bsonReader(repositories.bsonDocToOnlineTestApplication)
    selectRandom[OnlineTestApplication](query, 1)
  }

  override def getTestProfileByCubiksId(cubiksUserId: Int): Future[Phase1TestGroupWithUserIds] = {
    val query = BSONDocument("testGroups.PHASE1.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument("applicationId" -> 1, "userId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val userId = doc.getAs[String]("userId").get
        val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phase1TestGroup = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).getOrElse(cannotFindTestByCubiksId(cubiksUserId))
        Phase1TestGroupWithUserIds(applicationId, userId, phase1TestGroup)
      case _ => cannotFindTestByCubiksId(cubiksUserId)
    }
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    updateGroupExpiryTime(applicationId, expirationDate, phaseName)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile) = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument("$set" -> applicationStatusBSON(PHASE1_TESTS_INVITED)) ++
      BSONDocument("$set" -> BSONDocument(s"testGroups.$phaseName" -> phase1TestProfile)
    )

    collection.update(query, update, upsert = false) map { status =>
      if (status.n != 1) {
        val msg = s"${status.n} rows affected when inserting or updating instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
      val progressStatusQuery = BSONDocument("$and" -> BSONArray(
        BSONDocument(s"progress-status.$PHASE1_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.$PHASE1_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
      ))

    nextTestForReminder(reminder, progressStatusQuery)
  }

  def nextTestGroupWithReportReady: Future[Option[Phase1TestGroupWithUserIds]] = {

    implicit val reader = bsonReader { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument](phaseName).get
      Phase1TestGroupWithUserIds(
        applicationId = doc.getAs[String]("applicationId").get,
        userId = doc.getAs[String]("userId").get,
        Phase1TestProfile.bsonHandler.read(group)
      )
    }

    nextTestGroupWithReportReady[Phase1TestGroupWithUserIds]
  }

  override def removeTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit] = {
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
}
