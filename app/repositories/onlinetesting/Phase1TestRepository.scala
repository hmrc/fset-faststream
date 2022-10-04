/*
 * Copyright 2022 HM Revenue & Customs
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
import model.EvaluationResults.{Green, Red}
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model._
import model.persisted.{NotificationExpiringOnlineTest, Phase1TestGroupWithUserIds, Phase1TestProfile}
import org.joda.time.DateTime
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories.CollectionNames
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase1TestRepository extends OnlineTestRepository with Phase1TestConcern {
  this: PlayMongoRepository[_] =>

  def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile]]
  def getTestProfileByOrderId(orderId: String): Future[Phase1TestProfile]
  def getTestGroupByOrderId(orderId: String): Future[Phase1TestGroupWithUserIds]
  def insertOrUpdateTestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile): Future[Unit]
  // Caution - for administrative fixes only (dataconsistency)
  def removeTestGroup(applicationId: String): Future[Unit]
  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit]
  def nextSdipFaststreamCandidateReadyForSdipProgression: Future[Option[Phase1TestGroupWithUserIds]]
}

@Singleton
class Phase1TestMongoRepository @Inject() (dateTime: DateTimeFactory, mongo: MongoComponent)
  extends PlayMongoRepository[Phase1TestProfile](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = Phase1TestProfile.phase1TestProfileFormat,
    indexes = Nil
  ) with Phase1TestRepository {

  override val phaseName = "PHASE1"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE1_TESTS
  override val resetStatuses = List[String](ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_FAILED)
  override val dateTimeFactory = dateTime

  override val expiredTestQuery: Document = {
    Document("$and" -> BsonArray(
      Document(s"progress-status.$PHASE1_TESTS_COMPLETED" -> Document("$ne" -> true)),
      Document(s"progress-status.$PHASE1_TESTS_EXPIRED" -> Document("$ne" -> true))
    ))
  }

  //  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase1TestProfile] = Phase1TestProfile.bsonHandler
  override implicit val bsonReads: play.api.libs.json.Reads[T] = Phase1TestProfile.phase1TestProfileFormat

  override def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile]] = {
    getTestGroup(applicationId, phaseName)
  }

  // Needed to satisfy OnlineTestRepository trait
  override def nextApplicationsReadyForOnlineTesting(batchSize: Int): Future[Seq[OnlineTestApplication]] = {
    logger.warn(s"Looking for candidates to invite to $phaseName with a batch size of $batchSize...")
    val submittedStatuses = List[String](ApplicationStatus.SUBMITTED, ApplicationStatus.SUBMITTED.toLowerCase)

    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> Document("$in" -> submittedStatuses)),
      Document("$or" -> BsonArray(
        Document("civil-service-experience-details.fastPassReceived" -> Document("$ne" -> true)),
        Document("civil-service-experience-details.fastPassAccepted" -> false)
      ))
    ))

    selectRandom[OnlineTestApplication](query, batchSize)(
      doc => repositories.bsonDocToOnlineTestApplication(doc), global
    )
  }

  override def nextSdipFaststreamCandidateReadyForSdipProgression: Future[Option[Phase1TestGroupWithUserIds]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationRoute" -> ApplicationRoute.SdipFaststream.toBson),
      Document("applicationStatus" -> thisApplicationStatus.toBson),
      Document("$and" -> BsonArray(
        Document(s"progress-status.${FailedSdipFsTestType.progressStatus}" -> Document("$ne" -> true)),
        Document(s"progress-status.${SuccessfulSdipFsTestType.progressStatus}" -> Document("$ne" -> true))
      )),
      Document("testGroups.PHASE1.evaluation.result" -> Document("$elemMatch" ->
        Document("schemeId" -> SchemeId("Sdip").toBson,
          "result" -> Document("$in" -> List(Green.toString, Red.toString)))))
    ))
    collection.find[Document](query).headOption() map {
      case Some(doc) =>
        val applicationId = doc.get("applicationId").get.asString().getValue
        val userId = doc.get("userId").get.asString().getValue
        val bsonPhase1 = doc.get("testGroups").map( _.asDocument().get(phaseName).asDocument() )
        val phase1TestGroup: Phase1TestProfile = bsonPhase1.map( Codecs.fromBson[Phase1TestProfile] ).get
        Some(Phase1TestGroupWithUserIds(applicationId, userId, phase1TestGroup))
      case _ => None
    }
  }

  override def getTestProfileByOrderId(orderId: String): Future[Phase1TestProfile] = {
    getTestProfileByOrderId(orderId, phaseName)
  }

  override def getTestGroupByOrderId(orderId: String): Future[Phase1TestGroupWithUserIds] = {
    val query = Document(s"testGroups.$phaseName.tests" -> Document(
      "$elemMatch" -> Document("orderId" -> orderId)
    ))
    val projection = Projections.include("applicationId", "userId", s"testGroups.$phaseName")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        val applicationId = doc.get("applicationId").get.asString().getValue
        val userId = doc.get("userId").get.asString().getValue
        val bsonPhase1 = doc.get("testGroups").map(_.asDocument().get(phaseName).asDocument() )
        val phase1TestGroup = bsonPhase1.map( Codecs.fromBson[Phase1TestProfile] ).getOrElse(cannotFindTestByOrderId(orderId))
        Phase1TestGroupWithUserIds(applicationId, userId, phase1TestGroup)
      case _ => cannotFindTestByOrderId(orderId)
    }
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    updateGroupExpiryTime(applicationId, expirationDate, phaseName)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val update = Document("$set" -> (applicationStatusBSON(PHASE1_TESTS_INVITED) ++
      Document(s"testGroups.$phaseName" -> phase1TestProfile.toBson))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "inserting test group")
    collection.updateOne(query, update).toFuture() map validator
  }

  // Needed to satisfy OnlineTestRepository trait
  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = Document("$and" -> BsonArray(
      Document(s"progress-status.$PHASE1_TESTS_COMPLETED" -> Document("$ne" -> true)),
      Document(s"progress-status.$PHASE1_TESTS_EXPIRED" -> Document("$ne" -> true)),
      Document(s"progress-status.${reminder.progressStatuses}" -> Document("$ne" -> true))
    ))

    nextTestForReminder(reminder, progressStatusQuery)
  }
}
