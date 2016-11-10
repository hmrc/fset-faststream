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

import common.Phase3TestConcern
import config.MicroserviceAppConfig.sendPhase3InvitationJobConfig
import factories.DateTimeFactory
import model.{ ReminderNotice, ApplicationStatus }
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.{ NotFoundException, UnexpectedException }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model.persisted.{ NotificationExpiringOnlineTest, Phase3TestGroupWithAppId }
import org.joda.time.{ DateTime, DateTimeZone }
import model.persisted.phase3tests.Phase3TestGroup
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import repositories._
import repositories.onlinetesting.Phase3TestRepository.CannotFindTestByLaunchpadId
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase3TestRepository {
  case class CannotFindTestByLaunchpadId(message: String) extends NotFoundException(message)
}

trait Phase3TestRepository extends OnlineTestRepository with Phase3TestConcern {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]]

  def getTestGroupByToken(token: String): Future[Phase3TestGroupWithAppId]

  def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit]

  def updateTestStartTime(launchpadInviteId: String, startedTime: DateTime): Future[Unit]

  def updateTestCompletionTime(launchpadInviteId: String, completionTime: DateTime): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]
}

class Phase3TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase3TestGroup, BSONObjectID]("application", mongo,
    model.persisted.phase3tests.Phase3TestGroup.phase3TestGroupFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase3TestRepository with CommonBSONDocuments {

  override val phaseName = "PHASE3"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE3_TESTS
  override val resetStatuses = List[String](thisApplicationStatus)
  override val dateTimeFactory = dateTime
  // TO DO: expiredTestQuery need to be changed once we tackle the expiry test in phase 3
  override val expiredTestQuery: BSONDocument = BSONDocument()

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase3TestGroup] = Phase3TestGroup.bsonHandler

  override def nextApplicationsReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    val query = inviteToTestBSON(PHASE2_TESTS_PASSED, invigilatedKeyToExclude = "videoInvigilated")

    implicit val reader = bsonReader(repositories.bsonDocToOnlineTestApplication)
    selectRandom[OnlineTestApplication](query, sendPhase3InvitationJobConfig.batchSize.getOrElse(1))
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val appStatusBSON = BSONDocument("$set" -> applicationStatusBSON(PHASE3_TESTS_INVITED)
    ) ++ BSONDocument("$set" -> BSONDocument(s"testGroups.$phaseName" -> phase3TestGroup)
    )

    collection.update(query, appStatusBSON, upsert = false) map { status =>
      if (status.n != 1) {
        val msg = s"${status.n} rows affected when inserting or updating instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
  }

  override def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestGroupByToken(token: String): Future[Phase3TestGroupWithAppId] = {
    val query = BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("token" -> token)
    ))
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val bsonPhase3 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phase3TestGroup = bsonPhase3.map(Phase3TestGroup.bsonHandler.read).getOrElse(defaultUpdateErrorHandler(token))
        Phase3TestGroupWithAppId(applicationId, phase3TestGroup)
      case _ => defaultUpdateErrorHandler(token)
    }
  }

  override def updateTestStartTime(launchpadInviteId: String, startedTime: DateTime) = {
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.startedDateTime" -> Some(startedTime)
    ))
    findAndUpdateLaunchpadTest(launchpadInviteId, update)
  }

  override def updateTestCompletionTime(launchpadInviteId: String, completedTime: DateTime) = {
    import repositories.BSONDateTimeHandler
    val query = BSONDocument(s"testGroups.$phaseName.expirationDate" -> BSONDocument("$gt" -> DateTime.now(DateTimeZone.UTC)))
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(completedTime)
    ))

    val errorActionHandler: String => Unit = launchpadInviteId => {
      Logger.warn(s"""Failed to update launchpad test: $launchpadInviteId - test has expired or does not exist""")
      ()
    }

    findAndUpdateLaunchpadTest(launchpadInviteId, update, query, errorActionHandler)
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE3_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE3_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
    ))

    nextTestForReminder(reminder, progressStatusQuery)
  }

  private def findAndUpdateLaunchpadTest(launchpadInviteId: String, update: BSONDocument, query: BSONDocument = BSONDocument(),
                                      errorHandler: String => Unit = defaultUpdateErrorHandler): Future[Unit] = {
    val find = query ++ BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("token" -> launchpadInviteId)
      )
    )

    collection.update(find, update, upsert = false) map {
      case lastError if lastError.nModified == 0 && lastError.n == 0 => errorHandler(launchpadInviteId)
      case _ => ()
    }
  }

  private def defaultUpdateErrorHandler(launchpadInviteId: String) = {
    logger.error(s"""Failed to update launchpad test: $launchpadInviteId""")
    throw CannotFindTestByLaunchpadId(s"Cannot find test group by launchpad Id: $launchpadInviteId")
  }
}
