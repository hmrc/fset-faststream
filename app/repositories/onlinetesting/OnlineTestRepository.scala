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
import model.Exceptions.{ CannotFindTestByCubiksId, UnexpectedException }
import org.joda.time.DateTime
import model.OnlineTestCommands.{ OnlineTestApplication, TestProfile }
import model.PersistedObjects.{ ApplicationForNotification, ExpiringOnlineTest, NotificationExpiringOnlineTest }
import model.ProgressStatuses.ProgressStatus
import model._
import play.api.Logger
import reactivemongo.bson._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestRepository extends RandomSelection {
  this: ReactiveRepository[_, _] =>

  val thisApplicationStatus: ApplicationStatus
  val dateTimeFactory: DateTimeFactory
  def testProfileBsonHandler[T <: TestProfile]: BSONHandler[BSONDocument, T]

  def getTestGroup[T <: TestProfile](applicationId: String, phase: String = "PHASE1"): Future[Option[T]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    phaseTestProfileByQuery(query)
  }

  def getTestProfileByToken[T <: TestProfile](token: String, phase: String = "PHASE1"): Future[T] = {
    val query = BSONDocument(s"testGroups.$phase.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("token" -> token)
    ))
    phaseTestProfileByQuery[T](query).map { x =>
      x.getOrElse(cannotFindTestByToken(token))
    }
  }

  def cannotFindTestByCubiksId(cubiksUserId: Int) = {
    throw CannotFindTestByCubiksId(s"Cannot find test group by cubiks Id: $cubiksUserId")
  }

  def cannotFindTestByToken(token: String) = {
    throw CannotFindTestByCubiksId(s"Cannot find test group by token: $token")
  }

  private def phaseTestProfileByQuery[T <: TestProfile](query: BSONDocument, phase: String = "PHASE1"): Future[Option[T]] = {
    val projection = BSONDocument(s"testGroups.$phase" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val bson = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phase).get)
        bson.map(x => testProfileBsonHandler.read(x)): Option[T]
      case _ => None
    }
  }

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime, phase: String = "PHASE1"): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.update(query, BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phase.expirationDate" -> expirationDate
    ))).map { status =>
      if (status.n != 1) {
        val msg = s"Query to update testgroup expiration affected ${status.n} rows instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
  }

  def nextExpiringApplication(progressStatusQuery: BSONDocument): Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> thisApplicationStatus
      ),
      BSONDocument(
        "testGroups.PHASE1.expirationDate" -> BSONDocument("$lte" -> dateTimeFactory.nowLocalTimeZone) // Serialises to UTC.
      ), progressStatusQuery))

    selectRandom(query).map(_.map(bsonDocToExpiringOnlineTest))
  }

  def nextTestForReminder(reminder: ReminderNotice, progressStatusQuery: BSONDocument): Future[Option[NotificationExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> thisApplicationStatus),
      BSONDocument("testGroups.PHASE1.expirationDate" ->
        BSONDocument( "$lte" -> dateTimeFactory.nowLocalTimeZone.plusHours(reminder.hoursBeforeReminder)) // Serialises to UTC.
      ),
      progressStatusQuery
    ))

    selectRandom(query).map(_.map(bsonDocToNotificationExpiringOnlineTest))
  }

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SUBMITTED),
      BSONDocument("civil-service-experience-details.fastPassReceived" -> BSONDocument("$ne" -> true))
    ))

    selectRandom(query).map(_.map(bsonDocToOnlineTestApplication))
  }

  def updateProgressStatus(appId: String, progressStatus: ProgressStatus): Future[Unit] = {
    require(progressStatus.applicationStatus == thisApplicationStatus, "Forbidden progress status update")

    val query = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> thisApplicationStatus
    )

    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.$progressStatus" -> true
    ))
    collection.update(query, applicationStatusBSON, upsert = false) map ( _ => () )
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
