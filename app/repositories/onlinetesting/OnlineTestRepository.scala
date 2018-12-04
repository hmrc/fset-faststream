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

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.{ ApplicationNotFound, CannotFindTestByCubiksId }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.ProgressStatus
import model._
import model.exchange.CubiksTestResultReady
import model.persisted._
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import repositories.BSONDateTimeHandler
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestRepository extends RandomSelection with ReactiveRepositoryHelpers with CommonBSONDocuments with OnlineTestCommonBSONDocuments {
  this: ReactiveRepository[_, _] =>

  val thisApplicationStatus: ApplicationStatus
  val phaseName: String
  val dateTimeFactory: DateTimeFactory
  val expiredTestQuery: BSONDocument
  val resetStatuses: List[String]
  implicit val bsonHandler: BSONHandler[BSONDocument, T]

  type U <: Test
  type T <: TestProfile[U]

  def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]]
  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

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

  def insertCubiksTests[P <: CubiksTestProfile](applicationId: String, newTestProfile: P) = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )
    val update = BSONDocument(
        "$push" -> BSONDocument(
          s"testGroups.$phaseName.tests" -> BSONDocument(
            "$each" -> newTestProfile.tests
          )),
        "$set" -> BSONDocument(
         s"testGroups.$phaseName.expirationDate" -> newTestProfile.expirationDate
        )
      )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting tests during $phaseName", ApplicationNotFound(applicationId))

    collection.update(query, update) map validator
  }

  def updateTestCompletionTime(cubiksUserId: Int, completedTime: DateTime) = {
    import repositories.BSONDateTimeHandler
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(completedTime)
    ))

    findAndUpdateCubiksTest(cubiksUserId, update, ignoreNotFound = true)
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

  private def phaseTestProfileByQuery(query: BSONDocument, phase: String): Future[Option[T]] = {
    val projection = BSONDocument(s"testGroups.$phase" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map { optDocument =>
      optDocument.flatMap {_.getAs[BSONDocument]("testGroups")}
        .flatMap {_.getAs[BSONDocument](phase)}
        .map {x => bsonHandler.read(x)}
    }
  }

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime, phase: String = "PHASE1"): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId, actionDesc = s"updating test group expiration in $phaseName",
      ApplicationNotFound(applicationId))

    collection.update(query, BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phase.expirationDate" -> expirationDate
    ))) map validator
  }

  def nextExpiringApplication(expiryTest: TestExpirationEvent): Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> thisApplicationStatus
      ),
      BSONDocument(
        s"testGroups.${expiryTest.phase}.expirationDate" -> BSONDocument("$lte" -> dateTimeFactory.nowLocalTimeZone) // Serialises to UTC.
      ), expiredTestQuery))

    implicit val reader = bsonReader(ExpiringOnlineTest.fromBson)
    selectOneRandom[ExpiringOnlineTest](query)
  }

  protected[this] def nextTestForReminder(reminder: ReminderNotice, progressStatusQuery: BSONDocument):
    Future[Option[NotificationExpiringOnlineTest]] = {
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

  def updateProgressStatus(appId: String, progressStatus: ProgressStatus): Future[Unit] =
    updateProgressStatus(appId, progressStatus, applicationStatusBSON)

  def updateProgressStatusOnly(appId: String, progressStatus: ProgressStatus): Future[Unit] =
    updateProgressStatusForSdipFaststream(appId, progressStatus, progressStatusOnlyBSON)

  private def updateProgressStatus(appId: String, progressStatus: ProgressStatus,
    updateGenerator: (ProgressStatus) => BSONDocument): Future[Unit] = {
    require(progressStatus.applicationStatus == thisApplicationStatus, "Forbidden progress status update")

    val query = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> thisApplicationStatus
    )

    val update = BSONDocument("$set" -> updateGenerator(progressStatus))
    val validator = singleUpdateValidator(appId, actionDesc = "updating progress status", ignoreNotFound = true)

    collection.update(query, update) map validator
  }

  private def updateProgressStatusForSdipFaststream(appId: String, progressStatus: ProgressStatus,
                                   updateGenerator: (ProgressStatus) => BSONDocument): Future[Unit] = {
    require(progressStatus.applicationStatus == thisApplicationStatus, "Forbidden progress status update")

    val query = BSONDocument(
      "applicationId" -> appId
    )

    val update = BSONDocument("$set" -> updateGenerator(progressStatus))
    val validator = singleUpdateValidator(appId, actionDesc = "updating progress status", ignoreNotFound = true)

    collection.update(query, update) map validator
  }

  def nextTestGroupWithReportReady[TestGroup](implicit reader: BSONDocumentReader[TestGroup]): Future[Option[TestGroup]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> thisApplicationStatus),
      BSONDocument(s"progress-status.${phaseName}_TESTS_COMPLETED" -> true),
      BSONDocument(s"progress-status.${phaseName}_TESTS_RESULTS_RECEIVED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"testGroups.$phaseName.tests" ->
        BSONDocument("$elemMatch" -> BSONDocument("resultsReadyToDownload" -> true, "testResult" -> BSONDocument("$exists" -> false)))
      )
    ))

    selectOneRandom[TestGroup](query)
  }

  private def findAndUpdateCubiksTest(cubiksUserId: Int, update: BSONDocument, ignoreNotFound: Boolean = false): Future[Unit] = {
        val find = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
      )
    )

    val validator = if (ignoreNotFound) {
      singleUpdateValidator(cubiksUserId.toString, actionDesc = s"updating $phaseName tests", ignoreNotFound = true)
    } else {
      singleUpdateValidator(cubiksUserId.toString, actionDesc = s"updating $phaseName tests",
        CannotFindTestByCubiksId(s"Cannot find test group by cubiks Id: $cubiksUserId"))
    }

    collection.update(find, update) map validator
  }

  def insertTestResult(appId: String, phase1Test: CubiksTest, testResult: TestResult): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> appId,
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> phase1Test.cubiksUserId)
      )
    )
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.testResult" -> TestResult.testResultBsonHandler.write(testResult)
    ))

    val validator = singleUpdateValidator(appId, actionDesc = s"inserting $phaseName test result")

    collection.update(query, update) map validator
  }

  def upsertTestGroupEvaluationResult(applicationId: String, passmarkEvaluation: PassmarkEvaluation): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$set" -> BSONDocument(s"testGroups.$phaseName.evaluation" -> passmarkEvaluation))

    collection.update(query, update).map(_ => ())
  }

  def resetTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty)
    require(progressStatuses forall (ps =>
      resetStatuses.contains(ps.applicationStatus.toString)), s"Cannot reset some of the $phaseName progress statuses $progressStatuses")

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> appId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> resetStatuses))
    ))

    val progressesToRemoveQueryPartial: Seq[(String, BSONValue)] = progressStatuses.flatMap(p =>
        Seq(s"progress-status.$p" -> BSONString(""),
          s"progress-status-timestamp.$p" -> BSONString(""))
      )

    val updateQuery = BSONDocument(
      "$set" -> BSONDocument("applicationStatus" -> thisApplicationStatus),
      "$unset" -> (BSONDocument(progressesToRemoveQueryPartial) ++ BSONDocument(s"testGroups.$phaseName.evaluation" -> ""))
    )

    val validator = singleUpdateValidator(appId, actionDesc = s"resetting $phaseName test progresses", ApplicationNotFound(appId))

    collection.update(query, updateQuery) map validator
  }

  // Caution - for administrative fixes only (dataconsistency)
  def removeTestGroup(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument("$unset" -> BSONDocument(s"testGroups.$phaseName" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing test group")

    collection.update(query, update) map validator
  }

  def removeTestGroupEvaluation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument("$unset" -> BSONDocument(s"testGroups.$phaseName.evaluation" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing test group evaluation")

    collection.update(query, update) map validator
  }

  def findEvaluation(applicationId: String): Future[Option[Seq[SchemeEvaluationResult]]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(
      "_id" -> false,
      s"testGroups.$phaseName.evaluation.result" -> true
    )

    collection.find(query, projection).one[BSONDocument].map { optDocument =>
      optDocument.flatMap {_.getAs[BSONDocument](s"testGroups")
        .flatMap(_.getAs[BSONDocument](phaseName))
        .flatMap(_.getAs[BSONDocument]("evaluation"))
        .flatMap(_.getAs[Seq[SchemeEvaluationResult]]("result"))
      }
    }
  }
}
