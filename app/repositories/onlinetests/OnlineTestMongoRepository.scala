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

package repositories.onlinetests

import controllers.OnlineTestDetails
import factories.DateTimeFactory
import model.Commands
import model.OnlineTestCommands.Phase1TestProfile
import model.PersistedObjects.{ ApplicationForNotification, ExpiringOnlineTest }
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.RandomSelection
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class OnlineTestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[OnlineTestDetails, BSONObjectID]("online-tests", mongo,
    Commands.Implicits.onlineTestDetailsFormat, ReactiveMongoFormats.objectIdFormats) with OnlineTestRepository with RandomSelection {


  override def getPhase1TestProfile(applicationId: String): Future[Option[Phase1TestProfile]] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("testGroups.PHASE1" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) => Some(
        Phase1TestProfile.phase1TestProfileHandler.read(doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument]("PHASE1").get)
      )
      case _ => None
    }
  }

  override def setTestStatusFlag(applicationId: String, testToken: String, flag: OnlineTestStatusFlags.Value): Future[Unit] ={
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("testGroups.PHASE1.tests.token" -> testToken),
      BSONDocument("testGroups.PHASE1.tests.usedForResults" -> true)
    ))

    val modifier = BSONDocument("$set" -> BSONDocument(
      "testGroups.PHASE1.tests.$.started" -> true
    ))

    collection.update(query, modifier).map( _ => () )
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    /*
    val queryTestGroup = BSONDocument("applicationId" -> applicationId)

    for {
      status <- collection.update(queryTestGroup, newExpiryTime, upsert = false)
      _ <- collection.update(queryTestGroupExpired, newStatus, upsert = false)
    } yield {
      if (status.n == 0) throw new NotFoundException(s"updateStatus didn't update anything for userId:$userId")
      if (status.n > 1) throw new UnexpectedException(s"updateStatus somehow updated more than one record for userId:$userId")
    }*/
    Future.successful(())
  }



  override def insertPhase1TestProfile(applicationId: String, phase1TestProfile: Phase1TestProfile) = {

    val doc = BSONDocument("applicationId" -> applicationId,
      "testGroups" -> BSONDocument("PHASE1" -> phase1TestProfile)
    )

    collection.insert(doc) map ( _ => () )
  }

  /*
  override def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument(
      "applicationId" -> appId
    )
    collection.find(query).one[BSONDocument] map {
      _.map(bsonDocToOnlineTestApplication)
    }
  }

  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "online-tests.expirationDate" -> BSONDocument("$lte" -> dateTime.nowLocalTimeZone) // Serialises to UTC.
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("applicationStatus" -> "ONLINE_TEST_INVITED"),
        BSONDocument("applicationStatus" -> "ONLINE_TEST_STARTED")
      ))
    ))

    selectRandom(query).map(_.map(bsonDocToExpiringOnlineTest))
  }

  def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> "ONLINE_TEST_FAILED"),
      BSONDocument("online-tests.pdfReportSaved" -> true)
    ))
    selectRandom(query).map(_.map(bsonDocToApplicationForNotification))
  }*/




  private def bsonDocToExpiringOnlineTest(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ExpiringOnlineTest(applicationId, userId, preferredName)
  }

  private def bsonDocToApplicationForNotification(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }



/*  def updateXMLReportSaved(applicationId: String): Future[Unit] = {
    updateFlag(applicationId, "online-tests.xmlReportSaved", true)
  }

  private def updateFlag(applicationId: String, flag: String, value: Boolean): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val MRAReportGenerated = BSONDocument("$set" -> BSONDocument(
      flag -> value
    ))

    for {
      status <- collection.update(query, MRAReportGenerated, upsert = false)
    } yield {
      if (status.n == 0) throw new NotFoundException(s"updateStatus didn't update anything for applicationId:$applicationId")
      if (status.n > 1) throw new UnexpectedException(s"updateStatus updated more than one record for applicationId:$applicationId")
    }
  }*/
/*
  def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]] = {
    val query =
      BSONDocument("$or" ->
        BSONArray(
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("online-tests.xmlReportSaved" -> true),
              BSONDocument("passmarkEvaluation.passmarkVersion" -> BSONDocument("$exists" -> false))
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("passmarkEvaluation.passmarkVersion" -> BSONDocument("$ne" -> currentVersion)),
              BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingOnlineTestReevaluation)
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("passmarkEvaluation.passmarkVersion" -> BSONDocument("$ne" -> currentVersion)),
              BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted)
            )
          )
        ))

    selectRandom(query).map(_.map { doc =>
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationStatus = doc.getAs[String]("applicationStatus")
        .getOrElse(throw new IllegalStateException("applicationStatus must be defined"))

      ApplicationIdWithUserIdAndStatus(applicationId, userId, applicationStatus)
    })
  }

  def savePassMarkScore(applicationId: String, version: String, p: RuleCategoryResult, applicationStatus: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = applicationStatus match {
      case ApplicationStatuses.AwaitingOnlineTestReevaluation => "awaiting_online_test_re_evaluation"
      case ApplicationStatuses.OnlineTestFailed => "online_test_failed"
      case ApplicationStatuses.AwaitingAllocation => "awaiting_online_test_allocation"
    }

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "passmarkEvaluation" ->
          BSONDocument("passmarkVersion" -> version, "location1Scheme1" -> p.location1Scheme1.toString).
          add(schemeToBSON("location1Scheme2" -> p.location1Scheme2)).
          add(schemeToBSON("location2Scheme1" -> p.location2Scheme1)).
          add(schemeToBSON("location2Scheme2" -> p.location2Scheme2)).
          add(schemeToBSON("alternativeScheme" -> p.alternativeScheme)),
        "applicationStatus" -> applicationStatus,
        s"progress-status.$progressStatus" -> true
      ))

    collection.update(query, passMarkEvaluation, upsert = false).map(checkUpdateWriteResult)
  }

  private def schemeToBSON(scheme: (String, Option[Result])) = scheme._2 match {
    case Some(s) => BSONDocument(scheme._1 -> s.toString)
    case _ => BSONDocument.empty
  }

  def savePassMarkScoreWithoutApplicationStatusUpdate(applicationId: String, version: String, p: RuleCategoryResult): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "passmarkEvaluation" ->
          BSONDocument("passmarkVersion" -> version, "location1Scheme1" -> p.location1Scheme1.toString).
            add(schemeToBSON("location1Scheme2" -> p.location1Scheme2)).
            add(schemeToBSON("location2Scheme1" -> p.location2Scheme1)).
            add(schemeToBSON("location2Scheme2" -> p.location2Scheme2)).
            add(schemeToBSON("alternativeScheme" -> p.alternativeScheme))
      ))

    collection.update(query, passMarkEvaluation, upsert = false).map(checkUpdateWriteResult)
  }

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: String, expireDate: Option[LocalDate]): Future[Unit] = {
    import ApplicationStatuses._

    require(List(AllocationConfirmed, AllocationUnconfirmed).contains(applicationStatus))

    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = applicationStatus.toLowerCase()

    val allocation = BSONDocument("$set" -> {
      def withExpireDate =
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.$progressStatus" -> true,
          s"progress-status-dates.$progressStatus" -> LocalDate.now(),
          "allocation-expire-date" -> expireDate.get
        )

      def withoutExpireDate =
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.$progressStatus" -> true,
          s"progress-status-dates.$progressStatus" -> LocalDate.now()
        )

      if (expireDate.isDefined) {
        withExpireDate
      } else {
        withoutExpireDate
      }
    })

    collection.update(query, allocation, upsert = false).map(checkUpdateWriteResult)
  }

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = "awaiting_online_test_allocation"

    val deAllocationSet = BSONDocument("$set" -> {
      BSONDocument(
        "applicationStatus" -> "AWAITING_ALLOCATION",
        s"progress-status.$progressStatus" -> true,
        s"progress-status-dates.$progressStatus" -> LocalDate.now()
      )
    })

    val deAllocationUnset = BSONDocument("$unset" -> {
      BSONDocument(
        "progress-status.allocation_confirmed" -> "",
        "progress-status.allocation_unconfirmed" -> "",
        "progress-status-dates.allocation_confirmed" -> "",
        "progress-status-dates.allocation_unconfirmed" -> "",
        "allocation-expire-date" -> ""
      )
    })

    collection.update(query, deAllocationSet, upsert = false).map(checkUpdateWriteResult).flatMap(_ =>
      collection.update(query, deAllocationUnset, upsert = false).map(checkUpdateWriteResult))
  }

  private def checkUpdateWriteResult(writeResult: UpdateWriteResult): Unit = {
    writeResult.errmsg.map(msg => throw new UnexpectedException(s"Database update failed: $msg"))
  }*/
}
