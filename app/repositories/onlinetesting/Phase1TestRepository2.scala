/*
 * Copyright 2019 HM Revenue & Customs
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

/*
 * Copyright 2019 HM Revenue & Customs
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

import common.Phase1TestConcern2
import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model._
import model.persisted.{ NotificationExpiringOnlineTest, Phase1TestGroupWithUserIds2, Phase1TestProfile2 }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase1TestRepository2 extends OnlineTestRepository with Phase1TestConcern2 {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile2]]

  def insertOrUpdateTestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile2): Future[Unit]

  def getTestProfileByOrderId(orderId: String): Future[Phase1TestProfile2]

  // Replacement for getTestProfileByCubiksId
  def getTestGroupByOrderId(orderId: String): Future[Phase1TestGroupWithUserIds2]

  def nextTestGroupWithReportReady: Future[Option[Phase1TestGroupWithUserIds2]]
}

class Phase1TestMongoRepository2(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase1TestProfile2, BSONObjectID](CollectionNames.APPLICATION, mongo,
    Phase1TestProfile2.phase1TestProfile2Format, ReactiveMongoFormats.objectIdFormats
  ) with Phase1TestRepository2 {

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

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase1TestProfile2] = Phase1TestProfile2.bsonHandler

  // Needed to satisfy OnlineTestRepository trait
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

  // Needed to satisfy OnlineTestRepository trait
  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE1_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE1_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
    ))

    nextTestForReminder(reminder, progressStatusQuery)
  }

  override def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile2]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase1TestProfile: Phase1TestProfile2) = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument("$set" -> (applicationStatusBSON(PHASE1_TESTS_INVITED) ++
      BSONDocument(s"testGroups.$phaseName" -> phase1TestProfile))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "inserting test group")
    collection.update(query, update) map validator
  }

  override def getTestProfileByOrderId(orderId: String): Future[Phase1TestProfile2] = {
    getTestProfileByOrderId(orderId, phaseName)
  }

  override def getTestGroupByOrderId(orderId: String): Future[Phase1TestGroupWithUserIds2] = {
    val query = BSONDocument("testGroups.PHASE1.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("orderId" -> orderId)
    ))
    val projection = BSONDocument("applicationId" -> 1, "userId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val userId = doc.getAs[String]("userId").get
        val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phase1TestGroup = bsonPhase1.map(Phase1TestProfile2.bsonHandler.read).getOrElse(cannotFindTestByOrderId(orderId))
        Phase1TestGroupWithUserIds2(applicationId, userId, phase1TestGroup)
      case _ => cannotFindTestByOrderId(orderId)
    }
  }

  def nextTestGroupWithReportReady: Future[Option[Phase1TestGroupWithUserIds2]] = {

    implicit val reader = bsonReader { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument](phaseName).get
      Phase1TestGroupWithUserIds2(
        applicationId = doc.getAs[String]("applicationId").get,
        userId = doc.getAs[String]("userId").get,
        Phase1TestProfile2.bsonHandler.read(group)
      )
    }

    nextTestGroupWithReportReady[Phase1TestGroupWithUserIds2]
  }

}
