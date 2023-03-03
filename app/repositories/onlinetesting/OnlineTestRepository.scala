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

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.{ApplicationNotFound, CannotFindTestByOrderIdException}
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.ProgressStatus
import model._
import model.exchange.PsiTestResultReady
import model.persisted._
import org.joda.time.DateTime
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonString, BsonValue}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import play.api.libs.json.Reads
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.util.Try
import repositories._

import scala.concurrent.{ExecutionContext, Future}

//scalastyle:off number.of.methods
trait OnlineTestRepository extends RandomSelection with ReactiveRepositoryHelpers
  with CommonBSONDocuments with OnlineTestCommonBSONDocuments {
  this: PlayMongoRepository[_] =>

  val thisApplicationStatus: ApplicationStatus
  val phaseName: String
  val dateTimeFactory: DateTimeFactory
  val expiredTestQuery: Document
  val resetStatuses: List[String]
  implicit val bsonReads: Reads[T]

  type U <: Test
  type T <: TestProfile[U]

  def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[Seq[OnlineTestApplication]]
  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

  def getTestGroup(applicationId: String, phase: String = "PHASE1")(implicit ec: ExecutionContext): Future[Option[T]] = {
    val query = BsonDocument("applicationId" -> applicationId)
    phaseTestProfileByQuery(query, phase)
  }

  def updateTestStartTime(orderId: String, startedTime: DateTime)(implicit ec: ExecutionContext): Future[Unit] = {
    val update = Document("$set" -> Document(
      s"testGroups.$phaseName.tests.$$.startedDateTime" -> Some(dateTimeToBson(startedTime))
    ))
    findAndUpdateTest(orderId, update)
  }

  def markTestAsInactive(psiOrderId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val update = Document("$set" -> Document(
      s"testGroups.$phaseName.tests.$$.usedForResults" -> false
    ))
    findAndUpdatePsiTest(psiOrderId, update)
  }

  private def findAndUpdatePsiTest(orderId: String, update: Document, ignoreNotFound: Boolean = false)(
    implicit ec: ExecutionContext): Future[Unit] = {
    val find = Document(
      s"testGroups.$phaseName.tests" -> Document(
        "$elemMatch" -> Document("orderId" -> orderId)
      )
    )

    val validator = if (ignoreNotFound) {
      singleUpdateValidator(orderId.toString, actionDesc = s"updating $phaseName tests", ignoreNotFound = true)
    } else {
      singleUpdateValidator(orderId.toString, actionDesc = s"updating $phaseName tests",
        CannotFindTestByOrderIdException(s"Cannot find test group by orderId=$orderId"))
    }

    collection.updateOne(find, update).toFuture() map validator
  }

  def insertPsiTests(applicationId: String, newTestProfile: PsiTestProfile)(implicit ec: ExecutionContext): Future[Unit] = {
    //  def insertPsiTests[P <: PsiTestProfile](applicationId: String, newTestProfile: P) = {

    val query = Document("applicationId" -> applicationId)
    val update = Document(
      "$push" -> Document(
        s"testGroups.$phaseName.tests" -> Document(
          "$each" -> Codecs.toBson(newTestProfile.tests)
        )),
      "$set" -> Document(
        s"testGroups.$phaseName.expirationDate" -> dateTimeToBson(newTestProfile.expirationDate)
      )
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting tests during $phaseName", ApplicationNotFound(applicationId))

    collection.updateOne(query, update).toFuture() map validator
  }

  def getTestProfileByOrderId(orderId: String, phase: String = "PHASE1")(implicit ec: ExecutionContext): Future[T] = {
    val query = Document(s"testGroups.$phase.tests" -> Document(
      "$elemMatch" -> Document("orderId" -> orderId)
    ))

    phaseTestProfileByQuery(query, phase).map { x =>
      x.getOrElse(cannotFindTestByOrderId(orderId))
    }
  }

  def cannotFindTestByOrderId(orderId: String) = {
    throw CannotFindTestByOrderIdException(s"Cannot find test group by orderId=$orderId")
  }

  def updateTestCompletionTime(orderId: String, completedTime: DateTime)(implicit ec: ExecutionContext): Future[Unit] = {
    val update = Document("$set" -> Document(
      s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(dateTimeToBson(completedTime))
    ))

    findAndUpdatePsiTest(orderId, update, ignoreNotFound = true)
  }

  // TODO: mongo this feature is not used and should be deleted
  def updateTestReportReady2(orderId: String, reportReady: PsiTestResultReady): Future[Unit] = ???

  def insertTestResult(appId: String, psiTest: PsiTest, testResult: PsiTestResult)(implicit ec: ExecutionContext): Future[Unit] = {
    val query = Document(
      "applicationId" -> appId,
      s"testGroups.$phaseName.tests" -> Document(
        "$elemMatch" -> Document("orderId" -> psiTest.orderId)
      )
    )
    val update = Document("$set" -> Document(
      // Turn on legacyNumbers to force Doubles with no fraction part to be stored as Doubles and not Int32
      s"testGroups.$phaseName.tests.$$.testResult" -> Codecs.toBson(testResult, legacyNumbers = true)
    ))

    val validator = singleUpdateValidator(appId, actionDesc = s"inserting $phaseName test result")

    collection.updateOne(query, update).toFuture() map validator
  }

  def getApplicationIdForOrderId(orderId: String, phase: String = "PHASE1")(implicit ec: ExecutionContext): Future[Option[String]] = {
    val projection = Projections.include("applicationId")
    val query = Document(s"testGroups.$phase.tests" -> Document(
      "$elemMatch" -> Document("orderId" -> orderId)
    ))

    collection.find[Document](query).projection(projection).headOption() map { optDocument =>
      optDocument.map( doc => doc.get("applicationId").get.asString().getValue)
    }
  }

  private def phaseTestProfileByQuery(query: Document, phase: String)(implicit reads: Reads[T], ec: ExecutionContext): Future[Option[T]] = {
    val projection = Projections.include(s"testGroups.$phase")
    collection.find[Document](query).projection(projection).headOption() map { docOpt =>
      docOpt.flatMap { doc =>
        for {
          testGroups <- subDocRoot("testGroups")(doc)
          phaseBson <- subDocRoot(phase)(testGroups)
          // Explicitly provide the reads to avoid ambiguous implicit compile error
          testProfile <- Try(Codecs.fromBson[T](phaseBson)(reads)).toOption
        } yield testProfile
      }
    }
  }

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime, phase: String = "PHASE1")(
    implicit ec: ExecutionContext): Future[Unit] = {
    val query = BsonDocument("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId, actionDesc = s"updating test group expiration in $phaseName",
      ApplicationNotFound(applicationId))

    collection.updateOne(query, BsonDocument("$set" -> BsonDocument(
      s"testGroups.$phase.expirationDate" -> dateTimeToBson(expirationDate)
    ))).toFuture() map validator
  }

  def nextExpiringApplication(expiryTest: TestExpirationEvent)(implicit ec: ExecutionContext): Future[Option[ExpiringOnlineTest]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> thisApplicationStatus.toBson),
      Document(s"testGroups.${expiryTest.phase}.expirationDate" ->
        Document("$lte" ->
          dateTimeToBson(dateTimeFactory.nowLocalTimeZone.minusSeconds(expiryTest.gracePeriodInSecs)) // Serialises to UTC.
        )
      ),
      expiredTestQuery
    ))

    selectOneRandom[ExpiringOnlineTest](query)(doc => ExpiringOnlineTest.fromBson(doc), ec)
  }

  protected[this] def nextTestForReminder(reminder: ReminderNotice, progressStatusQuery: Document)(implicit ec: ExecutionContext):
  Future[Option[NotificationExpiringOnlineTest]] = {

    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> thisApplicationStatus.toBson),
      Document(s"testGroups.${reminder.phase}.expirationDate" ->
        Document( "$lte" -> dateTimeToBson(dateTimeFactory.nowLocalTimeZone.plusHours(reminder.hoursBeforeReminder))) // Serialises to UTC.
      ),
      progressStatusQuery
    ))

    selectOneRandom[NotificationExpiringOnlineTest](query)(doc => NotificationExpiringOnlineTest.fromBson(doc, reminder.phase), ec)
  }

  def updateProgressStatus(appId: String, progressStatus: ProgressStatus)(implicit ec: ExecutionContext): Future[Unit] =
    updateProgressStatus(appId, progressStatus, applicationStatusBSON)

  def updateProgressStatusOnly(appId: String, progressStatus: ProgressStatus)(implicit ec: ExecutionContext): Future[Unit] =
      updateProgressStatusForSdipFaststream(appId, progressStatus, progressStatusOnlyBSON)

  private def updateProgressStatus(appId: String, progressStatus: ProgressStatus,
                                   updateGenerator: ProgressStatus => Document)(implicit ec: ExecutionContext): Future[Unit] = {
    require(progressStatus.applicationStatus == thisApplicationStatus,
      s"Forbidden progress status update: expected $thisApplicationStatus but was ${progressStatus.applicationStatus}")

    val query = Document(
      "applicationId" -> appId,
      "applicationStatus" -> thisApplicationStatus.toBson
    )

    val update = Document("$set" -> updateGenerator(progressStatus))
    val validator = singleUpdateValidator(appId, actionDesc = "updating progress status", ignoreNotFound = true)

    collection.updateOne(query, update).toFuture() map validator
  }

  private def updateProgressStatusForSdipFaststream(appId: String, progressStatus: ProgressStatus,
                                                    updateGenerator: ProgressStatus => Document)(implicit ec: ExecutionContext): Future[Unit] = {
    require(progressStatus.applicationStatus == thisApplicationStatus,
      s"Forbidden progress status update: expected $thisApplicationStatus but was ${progressStatus.applicationStatus}")

    val query = Document("applicationId" -> appId)

    val update = Document("$set" -> updateGenerator(progressStatus))
    val validator = singleUpdateValidator(appId, actionDesc = "updating progress status", ignoreNotFound = true)

    collection.updateOne(query, update).toFuture() map validator
  }

  private def findAndUpdateTest(orderId: String, update: Document,
                                ignoreNotFound: Boolean = false)(implicit ec: ExecutionContext): Future[Unit] = {
    val find = Document(
      s"testGroups.$phaseName.tests" -> Document(
        "$elemMatch" -> Document("orderId" -> orderId)
      )
    )

    val validator = if (ignoreNotFound) {
      singleUpdateValidator(orderId, actionDesc = s"updating $phaseName tests", ignoreNotFound = true)
    } else {
      singleUpdateValidator(orderId, actionDesc = s"updating $phaseName tests",
        CannotFindTestByOrderIdException(s"Cannot find test group by Order ID: $orderId"))
    }

    collection.updateOne(find, update).toFuture() map validator
  }

  def upsertTestGroupEvaluationResult(applicationId: String, passmarkEvaluation: PassmarkEvaluation)(
    implicit ec: ExecutionContext): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$set" -> Document(s"testGroups.$phaseName.evaluation" -> passmarkEvaluation.toBson))

    collection.updateOne(query, update).toFuture().map(_ => ())
  }

  def resetTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus], ignoreNoRecordUpdated: Boolean = false)(
    implicit ec: ExecutionContext): Future[Unit] = {
    require(progressStatuses.nonEmpty)
    require(progressStatuses forall (ps =>
      resetStatuses.contains(ps.applicationStatus.toString)), s"Cannot reset some of the $phaseName progress statuses $progressStatuses")

    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> appId),
      Document("applicationStatus" -> Document("$in" -> resetStatuses))
    ))

    val progressesToRemoveQueryPartial: Seq[(String, BsonValue)] = progressStatuses.flatMap(p =>
      Seq(s"progress-status.$p" -> BsonString(""),
        s"progress-status-timestamp.$p" -> BsonString(""))
    )

    val updateQuery = Document(
      "$set" -> Document("applicationStatus" -> thisApplicationStatus.toBson),
      "$unset" -> (Document(progressesToRemoveQueryPartial) ++ Document(s"testGroups.$phaseName.evaluation" -> ""))
    )

    val validator = singleUpdateValidator(
      appId, actionDesc = s"resetting $phaseName test progress statuses", ignoreNoRecordUpdated, ApplicationNotFound(appId)
    )

    collection.updateOne(query, updateQuery).toFuture() map validator
  }

  // Caution - for administrative fixes only (dataconsistency)
  def removeTestGroup(applicationId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"testGroups.$phaseName" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing test group")
    collection.updateOne(query, update).toFuture() map validator
  }

  def removeTestGroupEvaluation(applicationId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"testGroups.$phaseName.evaluation" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing test group evaluation")

    collection.updateOne(query, update).toFuture() map validator
  }

  def findEvaluation(applicationId: String)(implicit ec: ExecutionContext): Future[Option[Seq[SchemeEvaluationResult]]] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include(s"testGroups.$phaseName.evaluation.result")

    collection.find[Document](query).projection(projection).headOption() map { docOpt =>
      docOpt.flatMap { doc =>
        for {
          testGroups <- subDocRoot("testGroups")(doc)
          phaseBson <- subDocRoot(phaseName)(testGroups)
          evaluationBson <- subDocRoot("evaluation")(phaseBson)
          result <- Try(Codecs.fromBson[Seq[SchemeEvaluationResult]](evaluationBson.getArray("result"))).toOption
        } yield result
      }
    }
  }
}
//scalastyle:on
