/*
 * Copyright 2018 HM Revenue & Customs
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
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.{ApplicationNotFound, NotFoundException}
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model.persisted.phase3tests.Phase3TestGroup
import model.persisted.{NotificationExpiringOnlineTest, PassmarkEvaluation, Phase3TestGroupWithAppId}
import model.{ApplicationStatus, ProgressStatuses, ReminderNotice}
import org.joda.time.DateTime
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, _}
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
  def appendCallback[A](token: String, callbacksKey: String, callback: A)(implicit format: BSONHandler[BSONDocument, A]): Future[Unit]

  def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]]

  def getTestGroupByToken(token: String): Future[Phase3TestGroupWithAppId]

  def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit]

  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit]

  def updateTestStartTime(launchpadInviteId: String, startedTime: DateTime): Future[Unit]

  def updateTestCompletionTime(launchpadInviteId: String, completionTime: DateTime): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

  def removeTestGroup(applicationId: String): Future[Unit]
}

class Phase3TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase3TestGroup, BSONObjectID](CollectionNames.APPLICATION, mongo,
    model.persisted.phase3tests.Phase3TestGroup.phase3TestGroupFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase3TestRepository with CommonBSONDocuments {

  override val phaseName = "PHASE3"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE3_TESTS
  override val resetStatuses = List[String](ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_PASSED,
    ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER, ApplicationStatus.PHASE3_TESTS_FAILED)
  override val dateTimeFactory = dateTime
  override val expiredTestQuery: BSONDocument = {
    BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE3_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE3_TESTS_EXPIRED" -> BSONDocument("$ne" -> true))
    ))
  }

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase3TestGroup] = Phase3TestGroup.bsonHandler

  override def appendCallback[A](token: String, callbacksKey: String, callback: A)
                                (implicit handler: BSONHandler[BSONDocument, A]): Future[Unit] = {
    val query = BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("token" -> token)
    ))

    val update = BSONDocument("$push" -> BSONDocument(s"testGroups.$phaseName.tests.$$.callbacks.$callbacksKey" -> callback))

    val validator = singleUpdateValidator(token, actionDesc = "appending phase 3 callback")

    collection.update(query, update) map validator
  }

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] = {
    val query = inviteToTestBSON(PHASE2_TESTS_PASSED) ++ BSONDocument("applicationRoute" -> BSONDocument("$nin" -> BSONArray("Sdip", "Edip")))

    implicit val reader = bsonReader(repositories.bsonDocToOnlineTestApplication)
    selectRandom[OnlineTestApplication](query, maxBatchSize)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val appStatusBSON = BSONDocument("$set" ->
      (applicationStatusBSON(PHASE3_TESTS_INVITED) ++ BSONDocument(s"testGroups.$phaseName" -> phase3TestGroup))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "inserting test group")

    collection.update(query, appStatusBSON) map validator
  }

  def upsertTestGroupEvaluation(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit] = {
    upsertTestGroupEvaluationResult(applicationId, passmarkEvaluation)
  }

  def removeTestGroup(applicationId: String): Future[Unit] = {
    val appStatuses = List(ApplicationStatus.PHASE3_TESTS,
      ApplicationStatus.PHASE3_TESTS_FAILED,
      ApplicationStatus.PHASE3_TESTS_PASSED)

    val phase3Progresses = ProgressStatuses.progressesByApplicationStatus(appStatuses: _*)

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> appStatuses))))

    val progressesToRemove = phase3Progresses map (p => s"progress-status.$p" -> BSONString(""))

    val updateQuery = BSONDocument(
      "$unset" -> BSONDocument(progressesToRemove),
      "$unset" -> BSONDocument(s"testGroups.$phaseName" -> "")
    )

    val validator = singleUpdateValidator(applicationId, "removing test group", ApplicationNotFound(applicationId))
    collection.update(query, updateQuery, upsert = false) map validator
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
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(completedTime)
    ))

    findAndUpdateLaunchpadTest(launchpadInviteId, update)
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE3_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE3_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
    ))

    nextTestForReminder(reminder, progressStatusQuery)
  }

  private def findAndUpdateLaunchpadTest(launchpadInviteId: String, update: BSONDocument,
                                         query: BSONDocument = BSONDocument.empty,
                                         ignoreNotFound: Boolean = false): Future[Unit] = {
    val find = query ++ BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("token" -> launchpadInviteId)
      )
    )

    val validator = if (ignoreNotFound) {
      singleUpdateValidator(launchpadInviteId, actionDesc = "updating phase3 tests", ignoreNotFound = true)
    } else {
      singleUpdateValidator(launchpadInviteId, actionDesc = "updating phase3 tests",
        CannotFindTestByLaunchpadId(s"Cannot find test group by launchpad Id: $launchpadInviteId"))
    }

    collection.update(find, update) map validator
  }

  private def defaultUpdateErrorHandler(launchpadInviteId: String) = {
    Logger.error(s"""Failed to update launchpad test: $launchpadInviteId""")
    throw CannotFindTestByLaunchpadId(s"Cannot find test group by launchpad Id: $launchpadInviteId")
  }
}
