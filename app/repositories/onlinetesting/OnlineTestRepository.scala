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
import model.Exceptions.{ ApplicationNotFound, CannotFindTestByCubiksId, CannotUpdateSchemePreferences, UnexpectedException }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.ProgressStatus
import model._
import model.exchange.CubiksTestResultReady
import model.persisted._
import org.joda.time.DateTime
import play.api.Logger
import reactivemongo.bson._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestRepository[U <: Test, T <: TestProfile[U]] extends RandomSelection with BSONHelpers with CommonBSONDocuments {
  this: ReactiveRepository[_, _] =>

  val thisApplicationStatus: ApplicationStatus
  val phaseName: String
  val dateTimeFactory: DateTimeFactory
  implicit val bsonHandler: BSONHandler[BSONDocument, T]

  def nextApplicationsReadyForOnlineTesting: Future[List[OnlineTestApplication]]

  def getTestGroup(applicationId: String, phase: String = "PHASE1"): Future[Option[T]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    phaseTestProfileByQuery(query, phase)
  }

  def getTestProfileByToken(token: String, phase: String = "PHASE1"): Future[T] = {
    val query = BSONDocument(s"testGroups.$phase.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("token" -> token)
    ))

    phaseTestProfileByQuery(query, phase).map { x =>
      x.getOrElse(cannotFindTestByToken(token))
    }
  }

  def updateTestStartTime(cubiksUserId: Int, startedTime: DateTime) = {
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.startedDateTime" -> Some(startedTime)
    ))
    findAndUpdateCubiksTest(cubiksUserId, update)
  }

  def markTestAsInactive(cubiksUserId: Int) = {
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.usedForResults" -> false
    ))
    findAndUpdateCubiksTest(cubiksUserId, update)
  }

  def insertCubiksTests[T <: CubiksTestProfile](applicationId: String, newTestProfile: T) = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )
    val update = BSONDocument("$set" -> BSONDocument(
       "$push" -> BSONDocument(
          s"testGroups.$phaseName.tests" -> newTestProfile.tests
        ),
       s"testGroups.$phaseName.expirationDate" -> newTestProfile.expirationDate
    ))
    collection.update(query, update, upsert = false) map {
      case lastError if lastError.nModified == 0 && lastError.n == 0 =>
        logger.error(s"""Failed to append cubiks tests for application: $applicationId""")
        throw ApplicationNotFound(applicationId)
      case _ => ()
    }
  }

  def updateTestCompletionTime(cubiksUserId: Int, completedTime: DateTime) = {
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(completedTime)
    ))
    findAndUpdateCubiksTest(cubiksUserId, update)
  }

  def updateTestReportReady(cubiksUserId: Int, reportReady: CubiksTestResultReady) = {
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.resultsReadyToDownload" -> (reportReady.reportStatus == "Ready"),
      s"testGroups.$phaseName.tests.$$.reportId" -> reportReady.reportId,
      s"testGroups.$phaseName.tests.$$.reportLinkURL" -> reportReady.reportLinkURL,
      s"testGroups.$phaseName.tests.$$.reportStatus" -> Some(reportReady.reportStatus)
    ))
    findAndUpdateCubiksTest(cubiksUserId, update)
  }

  def cannotFindTestByCubiksId(cubiksUserId: Int) = {
    throw CannotFindTestByCubiksId(s"Cannot find test group by cubiks Id: $cubiksUserId")
  }

  def cannotFindTestByToken(token: String) = {
    throw CannotFindTestByCubiksId(s"Cannot find test group by token: $token")
  }

  private def phaseTestProfileByQuery(query: BSONDocument, phase: String = "PHASE1"): Future[Option[T]] = {
    val projection = BSONDocument(s"testGroups.$phase" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map { optDocument =>
      optDocument.flatMap {_.getAs[BSONDocument]("testGroups")}
        .flatMap {_.getAs[BSONDocument](phase)}
        .map {x => bsonHandler.read(x)}
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

  def nextExpiringApplication(progressStatusQuery: BSONDocument, phase: String = "PHASE1"): Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> thisApplicationStatus
      ),
      BSONDocument(
        s"testGroups.$phase.expirationDate" -> BSONDocument("$lte" -> dateTimeFactory.nowLocalTimeZone) // Serialises to UTC.
      ), progressStatusQuery))

    implicit val reader = bsonReader(ExpiringOnlineTest.fromBson)
    selectOneRandom[ExpiringOnlineTest](query)
  }

  def nextTestForReminder(reminder: ReminderNotice, progressStatusQuery: BSONDocument): Future[Option[NotificationExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> thisApplicationStatus),
      BSONDocument(s"testGroups.${reminder.phase}.expirationDate" ->
        BSONDocument( "$lte" -> dateTimeFactory.nowLocalTimeZone.plusHours(reminder.hoursBeforeReminder)) // Serialises to UTC.
      ),
      progressStatusQuery
    ))

    implicit val reader = bsonReader(x => NotificationExpiringOnlineTest.fromBson(x, reminder.phase))
    selectOneRandom[NotificationExpiringOnlineTest](query)
  }


  def updateProgressStatus(appId: String, progressStatus: ProgressStatus): Future[Unit] = {
    require(progressStatus.applicationStatus == thisApplicationStatus, "Forbidden progress status update")

    val query = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> thisApplicationStatus
    )

    val update = BSONDocument("$set" -> applicationStatusBSON(progressStatus))
    collection.update(query, update, upsert = false) map ( _ => () )
  }

  private def findAndUpdateCubiksTest(cubiksUserId: Int, update: BSONDocument) = {
    val find = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
      )
    )
    collection.update(find, update, upsert = false) map {
      case lastError if lastError.nModified == 0 && lastError.n == 0 =>
        logger.error(s"""Failed to update cubiks test: $cubiksUserId""")
        throw cannotFindTestByCubiksId(cubiksUserId)
      case _ => ()
    }
  }
}
