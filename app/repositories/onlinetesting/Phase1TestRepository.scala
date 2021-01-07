/*
 * Copyright 2021 HM Revenue & Customs
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
import javax.inject.{ Inject, Singleton }
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ Green, Red }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{ PHASE1_TESTS_INVITED, _ }
import model._
import model.persisted.{ NotificationExpiringOnlineTest, Phase1TestGroupWithUserIds, Phase1TestProfile }
import org.joda.time.DateTime
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.CollectionNames
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

  // Caution - for administrative fixes only (dataconsistency)
  def removeTestGroup(applicationId: String): Future[Unit]

  def nextTestGroupWithReportReady: Future[Option[Phase1TestGroupWithUserIds]]

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

  def nextSdipFaststreamCandidateReadyForSdipProgression: Future[Option[Phase1TestGroupWithUserIds]]
}

@Singleton
class Phase1TestMongoRepository @Inject() (dateTime: DateTimeFactory, mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Phase1TestProfile, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    Phase1TestProfile.phase1TestProfileFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with Phase1TestRepository {

  override val phaseName = "PHASE1"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE1_TESTS
  override val resetStatuses = List[String](ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_FAILED)
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

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] = {
    val submittedStatuses = List[String](ApplicationStatus.SUBMITTED, ApplicationStatus.SUBMITTED.toLowerCase)

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> submittedStatuses)),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("civil-service-experience-details.fastPassReceived" -> BSONDocument("$ne" -> true)),
        BSONDocument("civil-service-experience-details.fastPassAccepted" -> false)
      ))
    ))

    implicit val reader = bsonReader(repositories.bsonDocToOnlineTestApplication)
    selectRandom[OnlineTestApplication](query, maxBatchSize)
  }

  def nextSdipFaststreamCandidateReadyForSdipProgression: Future[Option[Phase1TestGroupWithUserIds]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationRoute" -> ApplicationRoute.SdipFaststream),
      BSONDocument("applicationStatus" -> thisApplicationStatus),
      BSONDocument("$and" -> BSONArray(
        BSONDocument(s"progress-status.${FailedSdipFsTestType.progressStatus}" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.${SuccessfulSdipFsTestType.progressStatus}" -> BSONDocument("$ne" -> true))
      )),
      BSONDocument("testGroups.PHASE1.evaluation.result" -> BSONDocument("$elemMatch" ->
        BSONDocument("schemeId" -> SchemeId("Sdip"),
          "result" -> BSONDocument("$in" -> List(Green.toString, Red.toString)))))
    ))
    collection.find(query, projection = Option.empty[JsObject]).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val userId = doc.getAs[String]("userId").get
        val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phase1TestGroup = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
        Some(Phase1TestGroupWithUserIds(applicationId, userId, phase1TestGroup))
      case _ => None
    }
  }

  override def getTestProfileByCubiksId(cubiksUserId: Int): Future[Phase1TestGroupWithUserIds] = {
    val query = BSONDocument("testGroups.PHASE1.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument("applicationId" -> 1, "userId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
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

  override def insertOrUpdateTestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument("$set" -> (applicationStatusBSON(PHASE1_TESTS_INVITED) ++
      BSONDocument(s"testGroups.$phaseName" -> phase1TestProfile))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "inserting test group")

    collection.update(ordered = false).one(query, update) map validator
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
}
