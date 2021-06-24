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

package repositories.sift

import config.MicroserviceAppConfig
import factories.DateTimeFactory

import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{Amber, Green, Red}
import model.Exceptions._
import model._
import model.command.{ApplicationForSift, ApplicationForSiftExpiry}
import model.persisted._
import model.persisted.sift._
import model.report.SiftPhaseReportItem
import model.sift.{FixStuckUser, FixUserStuckInSiftEntered}
import org.joda.time.DateTime
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}
import repositories.application.GeneralApplicationRepoBSONReader

import scala.util.Try
//TODO: fix
//import repositories.{ BSONDateTimeHandler, CollectionNames, CurrentSchemeStatusHelper, RandomSelection, ReactiveRepositoryHelpers }
import repositories.{ CollectionNames, CurrentSchemeStatusHelper, RandomSelection, ReactiveRepositoryHelpers }
import repositories.SchemeRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// scalastyle:off number.of.methods file.size.limit
trait ApplicationSiftRepository {

  def thisApplicationStatus: ApplicationStatus
  def dateTime: DateTimeFactory
//  def siftableSchemeIds: Seq[SchemeId]
  val phaseName = "SIFT_PHASE"

  def nextApplicationsForSiftStage(maxBatchSize: Int): Future[List[ApplicationForSift]]
  def nextApplicationsReadyForNumericTestsInvitation(batchSize: Int, numericSchemes: Seq[SchemeId]): Future[Seq[NumericalTestApplication]]
  def nextApplicationsForSiftExpiry(maxBatchSize: Int, gracePeriodInSecs: Int): Future[List[ApplicationForSiftExpiry]]
  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]]
  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]]
  def findAllResults: Future[Seq[SiftPhaseReportItem]]
  def findAllResultsByIds(applicationIds: Seq[String]): Future[Seq[SiftPhaseReportItem]]
  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]]
  //TODO: mongo no usages
  def siftResultsExistsForScheme(applicationId: String, schemeId: SchemeId): Future[Boolean]
  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult, settableFields: Seq[Document] = Nil ): Future[Unit]
  //TODO: mongo no usages
//  def update(applicationId: String, predicate: BSONDocument, update: BSONDocument, action: String): Future[Unit]
  def saveSiftExpiryDate(applicationId: String, expiryDate: DateTime): Future[Unit]
  def isSiftExpired(applicationId: String): Future[Boolean]
  def removeTestGroup(applicationId: String): Future[Unit]
  //TODO: mongo no usages
  def findAllUsersInSiftReady: Future[Seq[FixStuckUser]]
  def findAllUsersInSiftEntered: Future[Seq[FixUserStuckInSiftEntered]]
  //TODO: mongo no test
  def fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(appId: String): Future[Unit]
  //TODO: mongo no test
  def fixSchemeEvaluation(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  //TODO: mongo no test
  def fixDataByRemovingSiftEvaluation(applicationId: String): Future[Unit]
  //TODO: mongo no test HERE
  def nextApplicationForFirstSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]]
  //TODO: mongo no test HERE
  def nextApplicationForSecondSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]]
  def getTestGroup(applicationId: String): Future[Option[SiftTestGroup]]
//  def getTestGroupByCubiksId(cubiksId: Int): Future[MaybeSiftTestGroupWithAppId]
//  def getTestGroupByToken(token: String): Future[MaybeSiftTestGroupWithAppId]
  def getTestGroupByOrderId(orderId: String): Future[MaybeSiftTestGroupWithAppId]
  def updateExpiryTime(applicationId: String, expiryDateTime: DateTime): Future[Unit]
  //TODO: this is cubiks specific
  def updateTestStartTime(cubiksUserId: Int, startedTime: DateTime): Future[Unit]
  //TODO: mongo no test
  def updateTestStartTime(orderId: String, startedTime: DateTime): Future[Unit]
  //TODO cubiks specific
  def getApplicationIdForCubiksId(cubiksUserId: Int): Future[String]
  def getApplicationIdForOrderId(orderId: String): Future[String]
//  def insertNumericalTests(applicationId: String, tests: List[CubiksTest]): Future[Unit]
  // TODO: cubiks rename this without the 2
  def insertNumericalTests2(applicationId: String, tests: List[PsiTest]): Future[Unit]
  //TODO: fix
//  def findAndUpdateTest(cubiksUserId: Int, update: BSONDocument, ignoreNotFound: Boolean = false): Future[Unit]
  //TODO: Cubiks specific so now redundant
//  def updateTestCompletionTime(cubiksUserId: Int, completedTime: DateTime): Future[Unit]

  def updateTestCompletionTime(orderId: String, completedTime: DateTime): Future[Unit]
//  def updateTestReportReady(cubiksUserId: Int, reportReady: CubiksTestResultReady): Future[Unit]
//  def nextTestGroupWithReportReady: Future[Option[SiftTestGroupWithAppId]]
//  def insertCubiksTestResult(appId: String, cubiksTest: CubiksTest, testResult: TestResult): Future[Unit]
  //TODO: mongo no test
  def insertPsiTestResult(appId: String, psiTest: PsiTest, testResult: PsiTestResult): Future[Unit]
  //TODO: mongo no test
  def nextApplicationWithResultsReceived: Future[Option[String]]
  //TODO: mongo no test
  def getNotificationExpiringSift(applicationId: String): Future[Option[NotificationExpiringSift]]
  def removeEvaluation(applicationId: String): Future[Unit]
}

@Singleton
class ApplicationSiftMongoRepository @Inject() (
                                                val dateTime: DateTimeFactory,
                                                schemeRepository: SchemeRepository, //TODO:fix guice just inject the list
//                                                  @Named("siftableSchemeIds") val siftableSchemeIds: Seq[SchemeId],
                                                mongoComponent: MongoComponent,
                                                appConfig: MicroserviceAppConfig
                                    )
  extends PlayMongoRepository[ApplicationForSift](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongoComponent,
    domainFormat = ApplicationForSift.applicationForSiftFormat,
    indexes = Nil
  ) with ApplicationSiftRepository with CurrentSchemeStatusHelper with RandomSelection with ReactiveRepositoryHelpers
    with GeneralApplicationRepoBSONReader {

  val thisApplicationStatus = ApplicationStatus.SIFT
  val prevPhase = ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
  val prevTestGroup = "PHASE3"
//  private val unlimitedMaxDocs = -1

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val candidateCollection: MongoCollection[Candidate] =
    CollectionFactory.collection(
      db = mongoComponent.database,
      collectionName = CollectionNames.APPLICATION,
      domainFormat = Candidate.candidateFormat
    )

  /*
  private def applicationForSiftBsonReads(document: BSONDocument): ApplicationForSift = {
    val applicationId = document.getAs[String]("applicationId").get
    val userId = document.getAs[String]("userId").get
    val appStatus = document.getAs[ApplicationStatus]("applicationStatus").get
    val currentSchemeStatus = document.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
    ApplicationForSift(applicationId, userId, appStatus, currentSchemeStatus)
  }*/

  private def applicationForSiftBsonReads(document: Document): ApplicationForSift = {
    val applicationId = document.get("applicationId").get.asString().getValue
    val userId = document.get("userId").get.asString().getValue
    val appStatus = Codecs.fromBson[ApplicationStatus](document.get("applicationStatus").get)
    val currentSchemeStatus = document.get("currentSchemeStatus").map { bsonValue =>
      Codecs.fromBson[Seq[SchemeEvaluationResult]](bsonValue)
    }.getOrElse(Nil)

    ApplicationForSift(applicationId, userId, appStatus, currentSchemeStatus)
  }

  /*
  def updateTestStartTime(cubiksUserId: Int, startedTime: DateTime): Future[Unit] = {
    val query = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.startedDateTime" -> Some(startedTime)
    ))

    val find = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
      )
    )

    val validator = singleUpdateValidator(cubiksUserId.toString, actionDesc = s"updating $phaseName tests",
      CannotFindTestByCubiksId(s"Cannot find sift test group by cubiks Id: $cubiksUserId"))

    collection.update(ordered = false).one(find, query) map validator
  }*/
  def updateTestStartTime(cubiksUserId: Int, startedTime: DateTime): Future[Unit] = ???

  /*
  def updateTestStartTime(orderId: String, startedTime: DateTime): Future[Unit] = {
    val query = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.startedDateTime" -> Some(startedTime)
    ))

    val find = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("orderId" -> orderId)
      )
    )

    val validator = singleUpdateValidator(orderId, actionDesc = s"updating $phaseName tests",
      CannotFindTestByCubiksId(s"Cannot find sift test group by order Id: $orderId"))

    collection.update(ordered = false).one(find, query) map validator
  }*/
  def updateTestStartTime(orderId: String, startedTime: DateTime): Future[Unit] = ???

  // TODO: cubiks specific
  /*
  def updateTestReportReady(cubiksUserId: Int, reportReady: CubiksTestResultReady): Future[Unit] = {
    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.resultsReadyToDownload" -> (reportReady.reportStatus == "Ready"),
      s"testGroups.$phaseName.tests.$$.reportId" -> reportReady.reportId,
      s"testGroups.$phaseName.tests.$$.reportStatus" -> Some(reportReady.reportStatus)
    ))

    val find = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
      )
    )

    val validator = singleUpdateValidator(cubiksUserId.toString, actionDesc = s"updating $phaseName tests",
      CannotFindTestByCubiksId(s"Cannot find test group by cubiks Id: $cubiksUserId"))

    collection.update(ordered = false).one(find, update) map validator
  }*/

  // TODO: cubiks specific
  /*
  def insertCubiksTestResult(appId: String, cubiksTest: CubiksTest, testResult: TestResult): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> appId,
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksTest.cubiksUserId)
      )
    )

    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.testResult" -> TestResult.testResultBsonHandler.write(testResult)
    ))

    val validator = singleUpdateValidator(appId, actionDesc = "inserting cubiks test results")

    collection.update(ordered = false).one(query, update) map validator
  }*/

  /*
  def insertPsiTestResult(appId: String, psiTest: PsiTest, testResult: PsiTestResult): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> appId,
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("orderId" -> psiTest.orderId)
      )
    )

    val update = BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.testResult" -> PsiTestResult.testResultBsonHandler.write(testResult)
    ))

    val validator = singleUpdateValidator(appId, actionDesc = s"inserting $phaseName test results")

    collection.update(ordered = false).one(query, update) map validator
  }*/
  def insertPsiTestResult(appId: String, psiTest: PsiTest, testResult: PsiTestResult): Future[Unit] = ???

  // TODO: cubiks specific
  /*
  def nextTestGroupWithReportReady: Future[Option[SiftTestGroupWithAppId]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> thisApplicationStatus),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_TEST_RESULTS_READY}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED}" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"testGroups.$phaseName.tests" ->
        BSONDocument("$elemMatch" -> BSONDocument("resultsReadyToDownload" -> true, "testResult" -> BSONDocument("$exists" -> false)))
      )
    ))

    implicit val reader = bsonReader { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument](phaseName).get
      SiftTestGroupWithAppId(
        applicationId = doc.getAs[String]("applicationId").get,
        SiftTestGroup.bsonHandler.read(group)
      )
    }

    selectOneRandom[SiftTestGroupWithAppId](query)
  }*/

  /*
  def nextApplicationWithResultsReceived: Future[Option[String]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> thisApplicationStatus),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$ne" -> true))
    ))

    selectOneRandom[BSONDocument](query).map {
      _.map { doc =>
        doc.getAs[String]("applicationId").get
      }
    }
  }*/
  def nextApplicationWithResultsReceived: Future[Option[String]] = ???

  /*
  def getApplicationIdForCubiksId(cubiksUserId: Int): Future[String] = {
    val query = BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument("applicationId" -> true)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(doc) => doc.getAs[String]("applicationId").get
      case _ => throw CannotFindApplicationByCubiksId(s"Cannot find application by cubiks Id: $cubiksUserId")
    }
  }*/
  def getApplicationIdForCubiksId(cubiksUserId: Int): Future[String] = ???

  /*
  def getApplicationIdForOrderId(orderId: String): Future[String] = {
    val query = BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("orderId" -> orderId)
    ))
    val projection = BSONDocument("applicationId" -> true)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(doc) => doc.getAs[String]("applicationId").get
      case _ => throw CannotFindApplicationByOrderIdException(s"Cannot find application by orderId Id: $orderId")
    }
  }*/
  override def getApplicationIdForOrderId(orderId: String): Future[String] = {
    val query = Document(s"testGroups.$phaseName.tests" -> Document(
      "$elemMatch" -> Document("orderId" -> orderId)
    ))
    val projection = Projections.include("applicationId")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) => doc.get("applicationId").get.asString().getValue
      case _ => throw CannotFindApplicationByOrderIdException(s"Cannot find application by orderId Id: $orderId")
    }
  }

  /*
  def nextApplicationsForSiftStage(batchSize: Int): Future[List[ApplicationForSift]] = {
    val fsQuery = () => BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationRoute" -> ApplicationRoute.Faststream),
      BSONDocument("applicationStatus" -> prevPhase),
      BSONDocument(s"testGroups.$prevTestGroup.evaluation.result" -> BSONDocument("$elemMatch" ->
        BSONDocument("schemeId" -> BSONDocument("$in" -> schemeRepository.siftableSchemeIds),
          "result" -> EvaluationResults.Green.toString)
      ))))

    val sdipFsQuery = () => BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationRoute" -> ApplicationRoute.SdipFaststream),
      BSONDocument("applicationStatus" -> BSONDocument("$in" ->
        Seq(ApplicationStatus.PHASE1_TESTS.toString, ApplicationStatus.PHASE2_TESTS.toString,
          ApplicationStatus.PHASE3_TESTS.toString, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED.toString))
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED}" -> true)
      )),
      BSONDocument(s"currentSchemeStatus" -> BSONDocument("$elemMatch" ->
        BSONDocument("schemeId" -> BSONDocument("$in" -> schemeRepository.siftableSchemeIds),
          "result" -> EvaluationResults.Green.toString)
      ))))

    val xdipQuery = (route: ApplicationRoute) => BSONDocument(
      "applicationRoute" -> route,
      "applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED
    )

    lazy val eligibleForSiftQuery =
      if (appConfig.disableSdipFaststreamForSift) { // FSET-1803. Disable sdipfaststream in sift temporarily
        BSONDocument("$or" -> BSONArray(
          fsQuery(),
          xdipQuery(ApplicationRoute.Edip),
          xdipQuery(ApplicationRoute.Sdip)
        ))
      } else {
        BSONDocument("$or" -> BSONArray(
          fsQuery(),
          sdipFsQuery(),
          xdipQuery(ApplicationRoute.Edip),
          xdipQuery(ApplicationRoute.Sdip)
        ))
      }

    selectRandom[BSONDocument](eligibleForSiftQuery, batchSize).map {
      _.map { document => applicationForSiftBsonReads(document) }
    }
  }*/

  override def nextApplicationsForSiftStage(batchSize: Int): Future[List[ApplicationForSift]] = {
    val fsQuery = () => Document("$and" -> BsonArray(
      Document("applicationRoute" -> ApplicationRoute.Faststream.toBson),
      Document("applicationStatus" -> prevPhase.toBson),
      Document(s"testGroups.$prevTestGroup.evaluation.result" -> Document("$elemMatch" ->
        Document("schemeId" -> Document("$in" -> Codecs.toBson(schemeRepository.siftableSchemeIds)),
          "result" -> EvaluationResults.Green.toString)
      ))))

    val sdipFsQuery = () => Document("$and" -> BsonArray(
      Document("applicationRoute" -> ApplicationRoute.SdipFaststream.toBson),
      Document("applicationStatus" -> Document("$in" ->
        Seq(ApplicationStatus.PHASE1_TESTS.toString, ApplicationStatus.PHASE2_TESTS.toString,
          ApplicationStatus.PHASE3_TESTS.toString, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED.toString))
      ),
      Document("$or" -> BsonArray(
        Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN}" -> true),
        Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN}" -> true),
        Document(s"progress-status.${ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN}" -> true),
        Document(s"progress-status.${ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED}" -> true)
      )),
      Document(s"currentSchemeStatus" -> Document("$elemMatch" ->
        Document("schemeId" -> Document("$in" -> Codecs.toBson(schemeRepository.siftableSchemeIds)),
          "result" -> EvaluationResults.Green.toString)
      ))))

    val xdipQuery = (route: ApplicationRoute) => Document(
      "applicationRoute" -> route.toBson,
      "applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED.toBson
    )

    lazy val eligibleForSiftQuery =
      if (appConfig.disableSdipFaststreamForSift) { // FSET-1803. Disable sdipfaststream in sift temporarily
        Document("$or" -> BsonArray(
          fsQuery(),
          xdipQuery(ApplicationRoute.Edip),
          xdipQuery(ApplicationRoute.Sdip)
        ))
      } else {
        Document("$or" -> BsonArray(
          fsQuery(),
          sdipFsQuery(),
          xdipQuery(ApplicationRoute.Edip),
          xdipQuery(ApplicationRoute.Sdip)
        ))
      }

    // TODO: mongo temp code until we get the selectRandom migrated
    val futureResult = collection.find[Document](eligibleForSiftQuery).limit(batchSize).toFuture()
    val mappedResult = futureResult.map(_.map ( doc => applicationForSiftBsonReads(doc) ).toList)
    mappedResult
  }

  //    selectRandom[BSONDocument](eligibleForSiftQuery, batchSize).map {
  //      _.map { document => applicationForSiftBsonReads(document) }

  /*
  def nextApplicationForFirstSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_FIRST_REMINDER}" -> BSONDocument("$exists" -> false)),

      BSONDocument(s"testGroups.$phaseName.expirationDate" ->
        BSONDocument( "$lte" -> dateTime.nowLocalTimeZone.plusHours(timeInHours)) // Serialises to UTC.
      )
    ))

    implicit val reader = bsonReader(x => NotificationExpiringSift.fromBson(x, phaseName))
    selectOneRandom[NotificationExpiringSift](query)
  }*/
  def nextApplicationForFirstSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = ???

  /*
  def nextApplicationForSecondSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_FIRST_REMINDER}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_SECOND_REMINDER}" -> BSONDocument("$exists" -> false)),

      BSONDocument(s"testGroups.$phaseName.expirationDate" ->
        BSONDocument( "$lte" -> dateTime.nowLocalTimeZone.plusHours(timeInHours)) // Serialises to UTC.
      )
    ))

    implicit val reader = bsonReader(x => NotificationExpiringSift.fromBson(x, phaseName))
    selectOneRandom[NotificationExpiringSift](query)
  }*/
  def nextApplicationForSecondSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = ???

  /*
  def getNotificationExpiringSift(applicationId: String): Future[Option[NotificationExpiringSift]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.find(query, projection = Option.empty[JsObject]).one[BSONDocument] map {
      _.map(doc => NotificationExpiringSift.fromBson(doc, phaseName))
    }
  }*/
  def getNotificationExpiringSift(applicationId: String): Future[Option[NotificationExpiringSift]] = ???

  /*
  def nextApplicationsForSiftExpiry(maxBatchSize: Int, gracePeriodInSecs: Int): Future[List[ApplicationForSiftExpiry]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"testGroups.$phaseName.expirationDate" ->
        BSONDocument("$lte" -> dateTime.nowLocalTimeZone.minusSeconds(gracePeriodInSecs))
      )
    ))

    selectRandom[BSONDocument](query, maxBatchSize).map {
      _.map { doc =>
        val applicationId = doc.getAs[String]("applicationId").get
        val userId = doc.getAs[String]("userId").get
        val appStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
        ApplicationForSiftExpiry(applicationId, userId, appStatus)
      }
    }
  }*/
  def nextApplicationsForSiftExpiry(maxBatchSize: Int, gracePeriodInSecs: Int): Future[List[ApplicationForSiftExpiry]] = ???

  /*
  def isSiftExpired(applicationId: String): Future[Boolean] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(
      "_id" -> 0, "applicationId" -> 1,
      s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> 1
    )
    collection.find(query, Some(projection)).one[BSONDocument].map {
      case Some(doc) =>
        val siftExpiredStatus = doc.getAs[BSONDocument]("progress-status")
          .flatMap(_.getAs[Boolean](ProgressStatuses.SIFT_EXPIRED.toString))
          .getOrElse(false)
        siftExpiredStatus
      case _ =>
        throw ApplicationNotFound(applicationId)
    }
  }*/
  override def isSiftExpired(applicationId: String): Future[Boolean] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("applicationId", s"progress-status.${ProgressStatuses.SIFT_EXPIRED}")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        val siftExpiredStatus = Try(doc.get("progress-status")
          .map(_.asDocument().get(ProgressStatuses.SIFT_EXPIRED.toString)).exists(_.asBoolean().getValue)).getOrElse(false)
        siftExpiredStatus
      case _ =>
        throw ApplicationNotFound(applicationId)
    }
  }

  /*
  def nextApplicationsReadyForNumericTestsInvitation(batchSize: Int,
                                                     numericSchemes: Seq[SchemeId]): Future[Seq[NumericalTestApplication]] = {
    val greenNumericSchemes = numericSchemes.map { scheme =>
      BSONDocument("currentSchemeStatus" ->
        BSONDocument("$elemMatch" -> BSONDocument("schemeId" -> scheme.toString, "result" -> Green.toString)))
    }

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_TEST_INVITED}" -> BSONDocument("$exists" -> false)),
      BSONDocument("$or" -> BSONArray(greenNumericSchemes))
    ))

    selectRandom[BSONDocument](query, batchSize).map {
      _.map { doc =>
        val applicationId = doc.getAs[String]("applicationId").get
        val testAccountId = doc.getAs[String]("testAccountId").get
        val userId = doc.getAs[String]("userId").get
        val appStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
        val currentSchemeStatus = doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
        val personalDetails = doc.getAs[BSONDocument]("personal-details").get
        val preferredName = personalDetails.getAs[String]("preferredName").get
        val lastName = personalDetails.getAs[String]("lastName").get

        NumericalTestApplication(
          applicationId, userId, testAccountId, appStatus, preferredName, lastName, currentSchemeStatus)
      }
    }
  }*/

  override def nextApplicationsReadyForNumericTestsInvitation(batchSize: Int,
                                                     numericSchemes: Seq[SchemeId]): Future[Seq[NumericalTestApplication]] = {
    val greenNumericSchemes = numericSchemes.map { scheme =>
      Document("currentSchemeStatus" ->
        Document("$elemMatch" -> Document("schemeId" -> scheme.toString, "result" -> Green.toString)))
    }

    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
      Document(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      Document(s"progress-status.${ProgressStatuses.SIFT_READY}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_TEST_INVITED}" -> Document("$exists" -> false)),
      Document("$or" -> greenNumericSchemes)
    ))
/*
    selectRandom[BSONDocument](query, batchSize).map {
      _.map { doc =>
        val applicationId = doc.getAs[String]("applicationId").get
        val testAccountId = doc.getAs[String]("testAccountId").get
        val userId = doc.getAs[String]("userId").get
        val appStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
        val currentSchemeStatus = doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
        val personalDetails = doc.getAs[BSONDocument]("personal-details").get
        val preferredName = personalDetails.getAs[String]("preferredName").get
        val lastName = personalDetails.getAs[String]("lastName").get

        NumericalTestApplication(
          applicationId, userId, testAccountId, appStatus, preferredName, lastName, currentSchemeStatus)
      }
    }*/

    // TODO: mongo temp code until we get the selectRandom migrated
    collection.find[Document](query).limit(batchSize).toFuture().map {
      _.map { doc =>
        val applicationId = doc.get("applicationId").get.asString().getValue
        val testAccountId = doc.get("testAccountId").get.asString().getValue
        val userId = doc.get("userId").get.asString().getValue
        val appStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
        val currentSchemeStatus = doc.get("currentSchemeStatus").map { bsonValue =>
          Codecs.fromBson[Seq[SchemeEvaluationResult]](bsonValue)
        }.getOrElse(Nil)
        val personalDetailsRoot = doc.get("personal-details").map(_.asDocument()).get
        val preferredName = personalDetailsRoot.get("preferredName").asString().getValue
        val lastName = personalDetailsRoot.get("lastName").asString().getValue
        NumericalTestApplication(
          applicationId, userId, testAccountId, appStatus, preferredName, lastName, currentSchemeStatus)
      }
    }
  }

  /*
  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]] = {
    val predicate = BSONDocument(
      "applicationStatus" -> ApplicationStatus.SIFT,
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      "currentSchemeStatus.result" -> Red.toString,
      "currentSchemeStatus.result" -> BSONDocument("$nin" -> BSONArray(Green.toString, Amber.toString))
    )

    selectOneRandom[BSONDocument](predicate).map {
      _.map { document => applicationForSiftBsonReads(document) }
    }
  }*/
  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]] = {
    val predicate = Document(
      "applicationStatus" -> ApplicationStatus.SIFT.toBson,
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      "currentSchemeStatus.result" -> Red.toString,
      "currentSchemeStatus.result" -> Document("$nin" -> BsonArray(Green.toString, Amber.toString))
    )

//    selectOneRandom[BSONDocument](predicate).map {
//      _.map { document => applicationForSiftBsonReads(document) }
//    }
    // TODO: mongo temp code until we get the selectRandom migrated
    val futureResult = collection.find[Document](predicate).headOption()
    val mappedResult = futureResult.map(_.map ( doc => applicationForSiftBsonReads(doc) ))
    mappedResult
  }

  /*
  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]] = {
    val notSiftedOnScheme = BSONDocument(
      s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(schemeId.value))
    )

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> true),
      currentSchemeStatusGreen(schemeId),
      notSiftedOnScheme
    ))
    bsonCollection.find(query, projection = Option.empty[JsObject]).cursor[Candidate]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
  }*/
  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]] = {
    val notSiftedOnScheme = Document(
      s"testGroups.$phaseName.evaluation.result.schemeId" -> Document("$nin" -> BsonArray(schemeId.value))
    )

    val query = Document("$and" -> BsonArray(
      Document(s"applicationStatus" -> ApplicationStatus.SIFT.toBson),
      Document(s"progress-status.${ProgressStatuses.SIFT_READY}" -> true),
      currentSchemeStatusGreen(schemeId),
      notSiftedOnScheme
    ))
    candidateCollection.find(query).toFuture()
  }

  /*
  def findAllResults: Future[Seq[SiftPhaseReportItem]] = {
    findAllByQuery(BSONDocument.empty)
  }*/
  override def findAllResults: Future[Seq[SiftPhaseReportItem]] = {
    findAllByQuery(Document.empty)
  }

  /*
  def findAllResultsByIds(applicationIds: Seq[String]): Future[Seq[SiftPhaseReportItem]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    findAllByQuery(query)
  }*/
  override def findAllResultsByIds(applicationIds: Seq[String]): Future[Seq[SiftPhaseReportItem]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    findAllByQuery(query)
  }

  /*
  private def findAllByQuery(extraQuery: BSONDocument): Future[Seq[SiftPhaseReportItem]] = {
    val query = BSONDocument(s"testGroups.$phaseName.evaluation.result" -> BSONDocument("$exists" -> true)) ++ extraQuery
    val projection = BSONDocument(
      "_id" -> 0,
      "applicationId" -> 1,
      s"testGroups.$phaseName.evaluation.result" -> 1
    )

    collection.find(query, Some(projection)).cursor[BSONDocument]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[BSONDocument]]()).map {
      _.map { doc =>
        val appId = doc.getAs[String]("applicationId").get
        val phaseDoc = doc.getAs[BSONDocument](s"testGroups")
          .flatMap(_.getAs[BSONDocument](phaseName))
          .flatMap(_.getAs[BSONDocument]("evaluation"))
          .flatMap(_.getAs[Seq[SchemeEvaluationResult]]("result"))

        SiftPhaseReportItem(appId, phaseDoc)
      }
    }
  }*/

  private def findAllByQuery(extraQuery: Document): Future[Seq[SiftPhaseReportItem]] = {
    val query = Document(s"testGroups.$phaseName.evaluation.result" -> Document("$exists" -> true)) ++ extraQuery
    val projection = Projections.include("applicationId", s"testGroups.$phaseName.evaluation.result")

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map { doc =>
        val appId = doc.get("applicationId").get.asString().getValue
        val phaseDoc = doc.get("testGroups")
          .map(_.asDocument().get(phaseName))
          .map(_.asDocument().get("evaluation"))
          .map(_.asDocument().get("result"))
          .map(bson => Codecs.fromBson[Seq[SchemeEvaluationResult]](bson))

        SiftPhaseReportItem(appId, phaseDoc)
      }
    }
  }

  /*
  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult,
                               settableFields: Seq[BSONDocument] = Nil
                              ): Future[Unit] = {

    val saveEvaluationResultsDoc = BSONDocument(s"testGroups.$phaseName.evaluation.result" -> result)
    val saveSettableFieldsDoc = settableFields.foldLeft(BSONDocument.empty) { (acc, doc) => acc ++ doc }

    val update = if (saveSettableFieldsDoc.isEmpty) {
      BSONDocument("$addToSet" -> saveEvaluationResultsDoc)
    } else {
      BSONDocument(
        "$addToSet" -> saveEvaluationResultsDoc,
        "$set" -> saveSettableFieldsDoc
      )
    }

    val predicate = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
      )
    ))
    collection.update(ordered = false).one(predicate, update).map(_ => ())
  }*/
  override def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult,
                               settableFields: Seq[Document] = Nil
                              ): Future[Unit] = {

    val saveEvaluationResultsDoc = Document(s"testGroups.$phaseName.evaluation.result" -> result.toBson)
    val saveSettableFieldsDoc = settableFields.foldLeft(Document.empty) { (acc, doc) => acc ++ doc }

    val update = if (saveSettableFieldsDoc.isEmpty) {
      Document("$addToSet" -> saveEvaluationResultsDoc)
    } else {
      Document(
        "$addToSet" -> saveEvaluationResultsDoc,
        "$set" -> saveSettableFieldsDoc
      )
    }

    val predicate = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> Document("$nin" -> BsonArray(result.schemeId.value))
      )
    ))
    collection.updateOne(predicate, update).toFuture().map (_ => ())
  }

  /*
  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    val predicate = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("_id" -> 0, s"testGroups.$phaseName.evaluation.result" -> 1)

    collection.find(predicate, Some(projection)).one[BSONDocument].map(
      _.flatMap { _.getAs[BSONDocument]("testGroups") }
        .flatMap { _.getAs[BSONDocument](phaseName) }
        .flatMap { _.getAs[BSONDocument]("evaluation") }
        .flatMap { _.getAs[Seq[SchemeEvaluationResult]]("result") }
        .getOrElse(throw PassMarkEvaluationNotFound(s"Sift evaluation not found for $applicationId")))
  }*/
  override def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    val predicate = Document("applicationId" -> applicationId)
    val projection = Projections.include(s"testGroups.$phaseName.evaluation.result")
    def error = throw PassMarkEvaluationNotFound(s"Sift evaluation not found for $applicationId")

    collection.find[Document](predicate).projection(projection).headOption().map { docOpt =>
      docOpt.map { doc =>
        Try(doc.get("testGroups")
          .map(_.asDocument().get(phaseName))
          .map(_.asDocument().get("evaluation"))
          .map(_.asDocument().get("result"))
        ).getOrElse(error)
        .map( bson =>
          Try(Codecs.fromBson[Seq[SchemeEvaluationResult]](bson)).getOrElse(error)
        ).getOrElse(error)
      }.getOrElse(error)
    }
  }

  override def siftResultsExistsForScheme(applicationId: String, schemeId: SchemeId): Future[Boolean] = {
    getSiftEvaluations(applicationId).map(_.exists(_.schemeId == schemeId)).recover { case _ => false }
  }

  /*
  def update(applicationId: String, predicate: BSONDocument, update: BSONDocument, action: String): Future[Unit] = {
    val validator = singleUpdateValidator(applicationId, action)
    collection.update(ordered = false).one(predicate, update) map validator
  }*/

  /*
  def saveSiftExpiryDate(applicationId: String, expiryDate: DateTime): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$set" -> BSONDocument(s"testGroups.$phaseName.expirationDate" -> expiryDate))

    val validator = singleUpdateValidator(applicationId,
      actionDesc = s"inserting expiry date during $phaseName", ApplicationNotFound(applicationId))

    collection.update(ordered = false).one(query, update) map validator
  }*/
  override def saveSiftExpiryDate(applicationId: String, expiryDate: DateTime): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed to handle storing ISODate format
    val update = Document("$set" -> Document(s"testGroups.$phaseName.expirationDate" -> Codecs.toBson(expiryDate)))

    val validator = singleUpdateValidator(applicationId,
      actionDesc = s"inserting expiry date during $phaseName", ApplicationNotFound(applicationId))

    collection.updateOne(query, update).toFuture() map validator
  }

  /*
  def removeTestGroup(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument("$unset" -> BSONDocument(s"testGroups.$phaseName" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing test group")

    collection.update(ordered = false).one(query, update) map validator
  }*/
  override def removeTestGroup(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"testGroups.$phaseName" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing sift test group")

    collection.updateOne(query, update).toFuture() map validator
  }

  /*
  def findAllUsersInSiftReady: Future[Seq[FixStuckUser]] = {
    val query = BSONDocument("applicationStatus" -> ApplicationStatus.SIFT,
      s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$exists" -> true),
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> BSONDocument("$exists" -> false),
      s"testGroups.$phaseName" -> BSONDocument("$exists" -> true)
    )

    val projection = BSONDocument(
      "_id" -> 0,
      "applicationId" -> 1,
      s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> 1,
      s"progress-status.${ProgressStatuses.SIFT_READY}" -> 1,
      s"testGroups.$phaseName" -> 1,
      "currentSchemeStatus" -> 1
    )

    collection.find(query, Some(projection)).cursor[BSONDocument]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]())
      .map(_.map { doc =>
        val siftEvaluation = doc.getAs[BSONDocument]("testGroups")
          .flatMap { _.getAs[BSONDocument](phaseName) }
          .flatMap { _.getAs[BSONDocument]("evaluation") }
          .flatMap { _.getAs[Seq[SchemeEvaluationResult]]("result") }.getOrElse(Nil)

        val progressStatuses = doc.getAs[BSONDocument]("progress-status")
        val firstSiftTime = progressStatuses.flatMap { obj =>
          obj.getAs[DateTime](ProgressStatuses.SIFT_ENTERED.toString).orElse(obj.getAs[DateTime](ProgressStatuses.SIFT_READY.toString))
        }.getOrElse(DateTime.now())

        val css = doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").get
        val applicationId = doc.getAs[String]("applicationId").get

        FixStuckUser(
          applicationId,
          firstSiftTime,
          css,
          siftEvaluation
        )
      })
  }*/
  def findAllUsersInSiftReady: Future[Seq[FixStuckUser]] = ???

  /*
  def findAllUsersInSiftEntered: Future[Seq[FixUserStuckInSiftEntered]] = {
    val query = BSONDocument("applicationStatus" -> ApplicationStatus.SIFT,
      s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> BSONDocument("$exists" -> true),
      s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$exists" -> false),
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> BSONDocument("$exists" -> false)
    )

    val projection = BSONDocument(
      "_id" -> 0,
      "applicationId" -> 1,
      "currentSchemeStatus" -> 1
    )

    collection.find(query, Some(projection)).cursor[BSONDocument]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]())
      .map(_.map { doc =>
        val css = doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").get
        val applicationId = doc.getAs[String]("applicationId").get

        FixUserStuckInSiftEntered(applicationId, css)
      })
  }*/
  override def findAllUsersInSiftEntered: Future[Seq[FixUserStuckInSiftEntered]] = ???

  /*
  def fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(applicationId: String): Future[Unit] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("applicationId" -> applicationId),
        BSONDocument("applicationStatus" -> ApplicationStatus.FAILED_AT_SIFT)
      ))

    val updateOp = bsonCollection.updateModifier(
      BSONDocument(
        "$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
        "$unset" -> BSONDocument(s"testGroups.$phaseName.evaluation" -> "")
      )
    )

    findAndModify(query, updateOp).map{ result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }*/
  def fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(applicationId: String): Future[Unit] = ???

  /*
  def fixDataByRemovingSiftEvaluation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateOp = bsonCollection.updateModifier(
      BSONDocument(
        "$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
        "$unset" -> BSONDocument(s"testGroups.$phaseName" -> "")
      )
    )

    findAndModify(query, updateOp).map{ result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }*/
  def fixDataByRemovingSiftEvaluation(applicationId: String): Future[Unit] = ???

  /*
  def fixSchemeEvaluation(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val saveEvaluationResultsDoc = BSONDocument(s"testGroups.$phaseName.evaluation.result" -> result)

    val removeDoc = BSONDocument(
      "$pull" -> BSONDocument(s"testGroups.$phaseName.evaluation.result" -> BSONDocument("schemeId" -> result.schemeId.value))
    )
    val setDoc = BSONDocument(
      "$addToSet" -> saveEvaluationResultsDoc
    )

    val removePredicate = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$in" -> BSONArray(result.schemeId.value))
      )
    ))
    val setPredicate = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
      )
    ))

    val validator = singleUpdateValidator(applicationId, s"fixing sift results for ${result.schemeId}", ApplicationNotFound(applicationId))

    for {
      _ <- collection.update(ordered = false).one(removePredicate, removeDoc) map validator
      _ <- collection.update(ordered = false).one(setPredicate, setDoc) map validator
    } yield ()
  }*/
  def fixSchemeEvaluation(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = ???

  /*
  def getTestGroup(applicationId: String): Future[Option[SiftTestGroup]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    getTestGroupByQuery(query)
  }*/
  override def getTestGroup(applicationId: String): Future[Option[SiftTestGroup]] = {
    val query = Document("applicationId" -> applicationId)
    getTestGroupByQuery(query)
  }

  /*
  private def getTestGroupByQuery(query: BSONDocument): Future[Option[SiftTestGroup]] = {
    val projection = BSONDocument(s"testGroups.$phaseName" -> 1, "_id" -> 0)
    collection.find(query, Some(projection)).one[BSONDocument] map { optDocument =>
      optDocument.flatMap { _.getAs[BSONDocument]("testGroups") }
        .flatMap { _.getAs[BSONDocument](phaseName) }
        .map { SiftTestGroup.bsonHandler.read }
    }
  }*/

  private def getTestGroupByQuery(query: Document): Future[Option[SiftTestGroup]] = {
    val projection = Projections.include(s"testGroups.$phaseName")
    collection.find[Document](query).projection(projection).head() map { optDocument =>
      Try(optDocument.get("testGroups")
        .map(_.asDocument().get(phaseName))
        .map(bson => Codecs.fromBson[SiftTestGroup](bson))).getOrElse(None)
    }
  }

  /*
  private def getTestGroupWithAppIdByQuery(query: BSONDocument): Future[MaybeSiftTestGroupWithAppId] = {
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    val ex = CannotFindTestByOrderIdException(s"Cannot find test group for query: ${BSONDocument.pretty(query)}")
    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(doc) =>
        val appId = doc.getAs[String]("applicationId").get
        val siftDocOpt = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val siftTestGroup = siftDocOpt.map(SiftTestGroup.bsonHandler.read).getOrElse(throw ex)
        MaybeSiftTestGroupWithAppId(appId, siftTestGroup.expirationDate, siftTestGroup.tests)
      case _ => throw ex
    }
  }*/
  private def getTestGroupWithAppIdByQuery(query: Document): Future[MaybeSiftTestGroupWithAppId] = {
    val projection = Projections.include("applicationId", s"testGroups.$phaseName")

    val ex = CannotFindTestByOrderIdException(s"Cannot find test group for query: ${query.toJson}}")
    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        val appId = doc.get("applicationId").get.asString().getValue
        val siftDocOpt = doc.get("testGroups").map(_.asDocument().get(phaseName)).map(_.asDocument())
        val siftTestGroup = siftDocOpt.map { doc => Codecs.fromBson[SiftTestGroup](doc) }.getOrElse(throw ex)
        MaybeSiftTestGroupWithAppId(appId, siftTestGroup.expirationDate, siftTestGroup.tests)
      case _ => throw ex
    }
  }

  /* TODO: was already commented
  private def getTestGroupWithAppIdByQuery2(query: BSONDocument): Future[MaybeSiftTestGroupWithAppId] = {
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    val ex = CannotFindTestByOrderIdException(s"Cannot find test group for query: ${BSONDocument.pretty(query)}")
    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(doc) =>
        val appId = doc.getAs[String]("applicationId").get
        val siftDocOpt = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val siftTestGroup = siftDocOpt.map(SiftTestGroup2.bsonHandler.read).getOrElse(throw ex)
        MaybeSiftTestGroupWithAppId(appId, siftTestGroup.expirationDate, siftTestGroup.tests)
      case _ => throw ex
    }
  }*/

  /*
  def updateExpiryTime(applicationId: String, expiryDateTime: DateTime): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId, actionDesc = s"updating test group expiration date in $phaseName",
      ApplicationNotFound(applicationId))

    collection.update(ordered = false).one(query, BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.expirationDate" -> expiryDateTime
    ))) map validator
  }*/
  override def updateExpiryTime(applicationId: String, expiryDateTime: DateTime): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId, actionDesc = s"updating test group expiration date in $phaseName",
      ApplicationNotFound(applicationId))

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed to handle storing ISODate format
    collection.updateOne(query, Document("$set" -> Document(
      s"testGroups.$phaseName.expirationDate" -> Codecs.toBson(expiryDateTime)
    ))).toFuture() map validator
  }

  // TODO: cubiks specific
  /*
  def insertNumericalTests(applicationId: String, tests: List[CubiksTest]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument(
      "$push" -> BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument("$each" -> tests))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting tests during $phaseName", ApplicationNotFound(applicationId))
    collection.update(ordered = false).one(query, update) map validator
  }*/

  /*
  def insertNumericalTests2(applicationId: String, tests: List[PsiTest]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument(
      "$push" -> BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument("$each" -> tests))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting tests during $phaseName", ApplicationNotFound(applicationId))
    collection.update(ordered = false).one(query, update) map validator
  }*/
  def insertNumericalTests2(applicationId: String, tests: List[PsiTest]): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document(
      "$push" -> Document(s"testGroups.$phaseName.tests" -> Document("$each" -> Codecs.toBson(tests)))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting tests during $phaseName", ApplicationNotFound(applicationId))
    collection.updateOne(query, update).toFuture() map validator
  }

  /*
  def findAndUpdateTest(cubiksUserId: Int, update: BSONDocument, ignoreNotFound: Boolean): Future[Unit] = {
    val query = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument("$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId))
    )
    val validator = if(ignoreNotFound) {
      singleUpdateValidator(cubiksUserId.toString, actionDesc = s"updating $phaseName tests", ignoreNotFound = true)
    } else {
      singleUpdateValidator(
        cubiksUserId.toString,
        actionDesc = s"updating $phaseName tests",
        CannotFindTestByCubiksId(s"Cannot find test group by cubiks Id: $cubiksUserId")
      )
    }
    collection.update(ordered = false).one(query, update) map validator
  }*/

  //TODO: fix
  /*
  def findAndUpdateTest(orderId: String, update: BSONDocument, ignoreNotFound: Boolean): Future[Unit] = {
    val query = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument("$elemMatch" -> BSONDocument("orderId" -> orderId))
    )
    val validator = if(ignoreNotFound) {
      singleUpdateValidator(orderId, actionDesc = s"updating $phaseName tests", ignoreNotFound = true)
    } else {
      singleUpdateValidator(
        orderId,
        actionDesc = s"updating $phaseName tests",
        CannotFindTestByCubiksId(s"Cannot find test group by order Id: $orderId")
      )
    }
    collection.update(ordered = false).one(query, update) map validator
  }*/

  private def findAndUpdateTest(orderId: String, update: Document, ignoreNotFound: Boolean, actionDesc: String): Future[Unit] = {
    val query = Document(
      s"testGroups.$phaseName.tests" -> Document("$elemMatch" -> Document("orderId" -> orderId))
    )
    val validator = if(ignoreNotFound) {
      singleUpdateValidator(orderId, actionDesc, ignoreNotFound = true)
    } else {
      singleUpdateValidator(
        orderId,
        actionDesc = s"updating $phaseName tests",
        CannotFindTestByOrderIdException(s"Cannot find test group by order Id: $orderId")
      )
    }
    collection.updateOne(query, update).toFuture() map validator
  }

  /*
  def updateTestCompletionTime(cubiksUserId: Int, completedTime: DateTime): Future[Unit] = {
    val update = BSONDocument(
      "$set" -> BSONDocument(s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(completedTime))
    )
    findAndUpdateTest(cubiksUserId, update, ignoreNotFound = true)
  }*/
  // TODO: cubiks specific
//  override def updateTestCompletionTime(cubiksUserId: Int, completedTime: DateTime): Future[Unit] = ???

  /*
  def updateTestCompletionTime(orderId: String, completedTime: DateTime): Future[Unit] = {
    val update = BSONDocument(
      "$set" -> BSONDocument(s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(completedTime))
    )
    findAndUpdateTest(orderId, update, ignoreNotFound = true)
  }*/
  override def updateTestCompletionTime(orderId: String, completedTime: DateTime): Future[Unit] = {
    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed to handle storing ISODate format
    val update = Document(
      "$set" -> Document(s"testGroups.$phaseName.tests.$$.completedDateTime" -> Codecs.toBson(completedTime))
    )
    findAndUpdateTest(orderId, update, false, s"updating test completion time by orderId in $phaseName tests")
  }

  // TODO: cubiks specific
  /*
  def getTestGroupByCubiksId(cubiksUserId: Int): Future[MaybeSiftTestGroupWithAppId] = {
    val query = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument("$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId))
    )
    getTestGroupWithAppIdByQuery(query)
  }*/

  // TODO: cubiks specific
  /*
  def getTestGroupByToken(token: String): Future[MaybeSiftTestGroupWithAppId] = {
    val query = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument("$elemMatch" -> BSONDocument("token" -> token))
    )
    getTestGroupWithAppIdByQuery(query)
  }*/

  /*
  def getTestGroupByOrderId(orderId: String): Future[MaybeSiftTestGroupWithAppId] = {
    val query = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument("$elemMatch" -> BSONDocument("orderId" -> orderId))
    )
    getTestGroupWithAppIdByQuery(query)
  }*/
  override def getTestGroupByOrderId(orderId: String): Future[MaybeSiftTestGroupWithAppId] = {
    val query = Document(
      s"testGroups.$phaseName.tests" -> Document("$elemMatch" -> Document("orderId" -> orderId))
    )
    getTestGroupWithAppIdByQuery(query)
  }

  /*
  override def removeEvaluation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateOp = bsonCollection.updateModifier(
      BSONDocument(
        "$unset" -> BSONDocument(s"testGroups.$phaseName.evaluation" -> "")
      )
    )

    findAndModify(query, updateOp).map{ result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }*/
  override def removeEvaluation(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"testGroups.$phaseName.evaluation" -> ""))

    collection.updateOne(query, update).toFuture().map { result =>
      //scalastyle:off
      println(s"**** result=$result")
      //scalastyle:on
      if (result.getModifiedCount != 1) { throw new NotFoundException(s"Failed to remove sift evaluation for id $applicationId") }
      else { () }
    }
  }
}
