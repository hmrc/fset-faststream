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

package repositories.sift

import config.MicroserviceAppConfig
import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ Amber, Green, Red }
import model.Exceptions._
import model._
import model.command.{ ApplicationForSift, ApplicationForSiftExpiry }
import model.exchange.CubiksTestResultReady
import model.persisted.sift.{ MaybeSiftTestGroupWithAppId, NotificationExpiringSift, SiftTestGroup, SiftTestGroupWithAppId }
import model.persisted.{ CubiksTest, SchemeEvaluationResult, TestResult }
import model.report.SiftPhaseReportItem
import model.sift.{ FixStuckUser, FixUserStuckInSiftEntered }
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.application.GeneralApplicationRepoBSONReader
import repositories.{ BSONDateTimeHandler, CollectionNames, CurrentSchemeStatusHelper, RandomSelection, ReactiveRepositoryHelpers }
import repositories.adjustmentDetailHandler
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// scalastyle:off number.of.methods
trait ApplicationSiftRepository {

  def thisApplicationStatus: ApplicationStatus
  def dateTime: DateTimeFactory
  def siftableSchemeIds: Seq[SchemeId]
  val phaseName = "SIFT_PHASE"

  def nextApplicationsForSiftStage(maxBatchSize: Int): Future[List[ApplicationForSift]]
  def nextApplicationsReadyForNumericTestsInvitation(batchSize: Int): Future[Seq[NumericalTestApplication]]
  def nextApplicationsForSiftExpiry(maxBatchSize: Int): Future[List[ApplicationForSiftExpiry]]
  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]]
  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]]
  def findAllResults: Future[Seq[SiftPhaseReportItem]]
  def findAllResultsByIds(applicationIds: Seq[String]): Future[Seq[SiftPhaseReportItem]]
  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]]
  def siftResultsExistsForScheme(applicationId: String, schemeId: SchemeId): Future[Boolean]
  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult, settableFields: Seq[BSONDocument] = Nil ): Future[Unit]
  def update(applicationId: String, predicate: BSONDocument, update: BSONDocument, action: String): Future[Unit]
  def saveSiftExpiryDate(applicationId: String, expiryDate: DateTime): Future[Unit]
  def isSiftExpired(applicationId: String): Future[Boolean]
  def removeTestGroup(applicationId: String): Future[Unit]
  def findAllUsersInSiftReady: Future[Seq[FixStuckUser]]
  def findAllUsersInSiftEntered: Future[Seq[FixUserStuckInSiftEntered]]
  def fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(appId: String): Future[Unit]
  def fixSchemeEvaluation(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def fixDataByRemovingSiftEvaluation(applicationId: String): Future[Unit]
  def nextApplicationForFirstSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]]
  def nextApplicationForSecondSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]]
  def getTestGroup(applicationId: String): Future[Option[SiftTestGroup]]
  def getTestGroupByCubiksId(cubiksId: Int): Future[MaybeSiftTestGroupWithAppId]
  def getTestGroupByToken(token: String): Future[MaybeSiftTestGroupWithAppId]
  def updateExpiryTime(applicationId: String, expiryDateTime: DateTime): Future[Unit]
  def updateTestStartTime(cubiksUserId: Int, startedTime: DateTime): Future[Unit]
  def getApplicationIdForCubiksId(cubiksUserId: Int): Future[String]
  def insertNumericalTests(applicationId: String, tests: List[CubiksTest]): Future[Unit]
  def findAndUpdateTest(cubiksUserId: Int, update: BSONDocument, ignoreNotFound: Boolean = false): Future[Unit]
  def updateTestCompletionTime(cubiksUserId: Int, completedTime: DateTime): Future[Unit]
  def updateTestReportReady(cubiksUserId: Int, reportReady: CubiksTestResultReady): Future[Unit]
  def nextTestGroupWithReportReady: Future[Option[SiftTestGroupWithAppId]]
  def insertCubiksTestResult(appId: String, cubiksTest: CubiksTest, testResult: TestResult): Future[Unit]
}

class ApplicationSiftMongoRepository(
  val dateTime: DateTimeFactory,
  val siftableSchemeIds: Seq[SchemeId]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with ApplicationSiftRepository with CurrentSchemeStatusHelper with RandomSelection with ReactiveRepositoryHelpers
  with GeneralApplicationRepoBSONReader
{

  val thisApplicationStatus = ApplicationStatus.SIFT
  val prevPhase = ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
  val prevTestGroup = "PHASE3"

  private def applicationForSiftBsonReads(document: BSONDocument): ApplicationForSift = {
    val applicationId = document.getAs[String]("applicationId").get
    val userId = document.getAs[String]("userId").get
    val appStatus = document.getAs[ApplicationStatus]("applicationStatus").get
    val currentSchemeStatus = document.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
    ApplicationForSift(applicationId, userId, appStatus, currentSchemeStatus)
  }

  def updateTestStartTime(cubiksUserId: Int, startedTime: DateTime) = {
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

    collection.update(find, query) map validator
  }

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

    collection.update(find, update) map validator
  }

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

    collection.update(query, update) map validator
  }

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
  }

  def getApplicationIdForCubiksId(cubiksUserId: Int): Future[String] = {
    val query = BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument("applicationId" -> true)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) => doc.getAs[String]("applicationId").get
      case _ => throw CannotFindApplicationByCubiksId(s"Cannot find application by cubiks Id: $cubiksUserId")
    }
  }


  def nextApplicationsForSiftStage(batchSize: Int): Future[List[ApplicationForSift]] = {
    val fsQuery = (route: ApplicationRoute) => BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationRoute" -> route),
      BSONDocument("applicationStatus" -> prevPhase),
      BSONDocument(s"testGroups.$prevTestGroup.evaluation.result" -> BSONDocument("$elemMatch" ->
        BSONDocument("schemeId" -> BSONDocument("$in" -> siftableSchemeIds),
        "result" -> EvaluationResults.Green.toString)
    ))))

    val xdipQuery = (route: ApplicationRoute) => BSONDocument(
      "applicationRoute" -> route,
      "applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED
    )

    lazy val eligibleForSiftQuery =
      if (MicroserviceAppConfig.disableSdipFaststreamForSift) { // FSET-1803. Disable sdipfaststream in sift temporarily
        BSONDocument("$or" -> BSONArray(
          fsQuery(ApplicationRoute.Faststream),
          xdipQuery(ApplicationRoute.Edip),
          xdipQuery(ApplicationRoute.Sdip)
        ))
      } else {
        BSONDocument("$or" -> BSONArray(
          fsQuery(ApplicationRoute.Faststream),
          fsQuery(ApplicationRoute.SdipFaststream),
          xdipQuery(ApplicationRoute.Edip),
          xdipQuery(ApplicationRoute.Sdip)
        ))
      }

    selectRandom[BSONDocument](eligibleForSiftQuery, batchSize).map {
      _.map { document => applicationForSiftBsonReads(document) }
    }
  }

  def nextApplicationForFirstSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_FIRST_REMINDER}" -> BSONDocument("$exists" -> false)),

      BSONDocument(s"testGroups.$phaseName.expirationDate" ->
        BSONDocument( "$lte" -> dateTime.nowLocalTimeZone.plusHours(timeInHours)) // Serialises to UTC.
      )
    ))

    implicit val reader = bsonReader(x => NotificationExpiringSift.fromBson(x, phaseName))
    selectOneRandom[NotificationExpiringSift](query)
  }

  def nextApplicationForSecondSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_FIRST_REMINDER}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_SECOND_REMINDER}" -> BSONDocument("$exists" -> false)),

      BSONDocument(s"testGroups.$phaseName.expirationDate" ->
        BSONDocument( "$lte" -> dateTime.nowLocalTimeZone.plusHours(timeInHours)) // Serialises to UTC.
      )
    ))

    implicit val reader = bsonReader(x => NotificationExpiringSift.fromBson(x, phaseName))
    selectOneRandom[NotificationExpiringSift](query)
  }

  def nextApplicationsForSiftExpiry(maxBatchSize: Int): Future[List[ApplicationForSiftExpiry]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"testGroups.$phaseName.expirationDate" -> BSONDocument("$lte" -> DateTimeFactory.nowLocalTimeZone))
    ))

    selectRandom[BSONDocument](query, maxBatchSize).map {
      _.map { doc =>
        val applicationId = doc.getAs[String]("applicationId").get
        val userId = doc.getAs[String]("userId").get
        val appStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
        ApplicationForSiftExpiry(applicationId, userId, appStatus)
      }
    }
  }

  def isSiftExpired(applicationId: String): Future[Boolean] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(
      "_id" -> 0, "applicationId" -> 1,
      s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> 1
    )
    collection.find(query, projection).one[BSONDocument].map {
      case Some(doc) =>
        val siftExpiredStatus = doc.getAs[BSONDocument]("progress-status")
          .flatMap(_.getAs[Boolean](ProgressStatuses.SIFT_EXPIRED.toString))
          .getOrElse(false)
        siftExpiredStatus
      case _ =>
        throw ApplicationNotFound(applicationId)
    }
  }

  def nextApplicationsReadyForNumericTestsInvitation(batchSize: Int): Future[Seq[NumericalTestApplication]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_READY}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> BSONDocument("$exists" -> false)),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_TEST_INVITED}" -> BSONDocument("$exists" -> false))
    ))

    selectRandom[BSONDocument](query, batchSize).map {
      _.map { doc =>
        val applicationId = doc.getAs[String]("applicationId").get
        val userId = doc.getAs[String]("userId").get
        val appStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
        val currentSchemeStatus = doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)

        val assistanceDetailsRoot = doc.getAs[BSONDocument]("assistance-details").get
        val needsAdjustmentForOnlineTests = assistanceDetailsRoot.getAs[Boolean]("needsSupportForOnlineAssessment").getOrElse(false)
        val etrayAdjustments = assistanceDetailsRoot.getAs[AdjustmentDetail]("etray")
        NumericalTestApplication(applicationId, userId, appStatus, needsAdjustmentForOnlineTests, etrayAdjustments, currentSchemeStatus)
      }
    }
  }

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
  }

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
    bsonCollection.find(query).cursor[Candidate]().collect[List]()
  }

  def findAllResults: Future[Seq[SiftPhaseReportItem]] = {
    findAllByQuery(BSONDocument.empty)
  }

  def findAllResultsByIds(applicationIds: Seq[String]): Future[Seq[SiftPhaseReportItem]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    findAllByQuery(query)
  }

  private def findAllByQuery(extraQuery: BSONDocument): Future[Seq[SiftPhaseReportItem]] = {
    val query = BSONDocument(s"testGroups.$phaseName.evaluation.result" -> BSONDocument("$exists" -> true)) ++ extraQuery
    val projection = BSONDocument(
      "_id" -> 0,
      "applicationId" -> 1,
      s"testGroups.$phaseName.evaluation.result" -> 1
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[Seq]().map {
      _.map { doc =>
        val appId = doc.getAs[String]("applicationId").get
        val phaseDoc = doc.getAs[BSONDocument](s"testGroups")
          .flatMap(_.getAs[BSONDocument](phaseName))
          .flatMap(_.getAs[BSONDocument]("evaluation"))
          .flatMap(_.getAs[Seq[SchemeEvaluationResult]]("result"))

        SiftPhaseReportItem(appId, phaseDoc)
      }
    }
  }

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
    collection.update(predicate, update).map(_ => ())
  }

  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    val predicate = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("_id" -> 0, s"testGroups.$phaseName.evaluation.result" -> 1)

    collection.find(predicate, projection).one[BSONDocument].map(
       _.flatMap { _.getAs[BSONDocument]("testGroups") }
        .flatMap { _.getAs[BSONDocument](phaseName) }
        .flatMap { _.getAs[BSONDocument]("evaluation") }
        .flatMap { _.getAs[Seq[SchemeEvaluationResult]]("result") }
    .getOrElse(throw PassMarkEvaluationNotFound(s"Sift evaluation not found for $applicationId")))
  }

  def siftResultsExistsForScheme(applicationId: String, schemeId: SchemeId): Future[Boolean] = {
    getSiftEvaluations(applicationId).map(_.exists(_.schemeId == schemeId)).recover{ case _ => false }
  }

  def update(applicationId: String, predicate: BSONDocument, update: BSONDocument, action: String): Future[Unit] = {
    val validator = singleUpdateValidator(applicationId, action)
    collection.update(predicate, update) map validator
  }

  def saveSiftExpiryDate(applicationId: String, expiryDate: DateTime): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$set" -> BSONDocument(s"testGroups.$phaseName.expirationDate" -> expiryDate))

    val validator = singleUpdateValidator(applicationId,
      actionDesc = s"inserting expiry date during $phaseName", ApplicationNotFound(applicationId))

    collection.update(query, update) map validator
  }

  def removeTestGroup(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument("$unset" -> BSONDocument(s"testGroups.$phaseName" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing test group")

    collection.update(query, update) map validator
  }

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

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map(_.map { doc =>
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
  }

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

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map(_.map { doc =>
      val css = doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").get
      val applicationId = doc.getAs[String]("applicationId").get

      FixUserStuckInSiftEntered(applicationId, css)
    })
  }

  def fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(applicationId: String): Future[Unit] = {

    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("applicationId" -> applicationId),
        BSONDocument("applicationStatus" -> ApplicationStatus.FAILED_AT_SIFT)
      ))

    val updateOp = bsonCollection.updateModifier(
      BSONDocument(
        "$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
        "$unset" -> BSONDocument(s"testGroups.$phaseName" -> "")
      )
    )

    bsonCollection.findAndModify(query, updateOp).map{ result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

  def fixDataByRemovingSiftEvaluation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateOp = bsonCollection.updateModifier(
      BSONDocument(
        "$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
        "$unset" -> BSONDocument(s"testGroups.$phaseName" -> "")
      )
    )

    bsonCollection.findAndModify(query, updateOp).map{ result =>
      if (result.value.isEmpty) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

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
      _ <- collection.update(removePredicate, removeDoc) map validator
      _ <- collection.update(setPredicate, setDoc) map validator
    } yield ()
  }

  def getTestGroup(applicationId: String): Future[Option[SiftTestGroup]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    getTestGroupByQuery(query)
  }

  private def getTestGroupByQuery(query: BSONDocument): Future[Option[SiftTestGroup]] = {
    val projection = BSONDocument(s"testGroups.$phaseName" -> 1, "_id" -> 0)
    collection.find(query, projection).one[BSONDocument] map { optDocument =>
      optDocument.flatMap { _.getAs[BSONDocument]("testGroups") }
        .flatMap { _.getAs[BSONDocument](phaseName) }
        .map { SiftTestGroup.bsonHandler.read }
    }
  }

  private def getTestGroupWithAppIdByQuery(query: BSONDocument): Future[MaybeSiftTestGroupWithAppId] = {
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    val ex = CannotFindTestByCubiksId(s"Cannot find test group for query: ${BSONDocument.pretty(query)}")
    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val appId = doc.getAs[String]("applicationId").get
        val siftDocOpt = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val siftTestGroup = siftDocOpt.map(SiftTestGroup.bsonHandler.read).getOrElse(throw ex)
        MaybeSiftTestGroupWithAppId(appId, siftTestGroup.expirationDate, siftTestGroup.tests)
      case _ => throw ex
    }
  }

  def updateExpiryTime(applicationId: String, expiryDateTime: DateTime): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId, actionDesc = s"updating test group expiration date in $phaseName",
      ApplicationNotFound(applicationId))

    collection.update(query, BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.expirationDate" -> expiryDateTime
    ))) map validator
  }

  def insertNumericalTests(applicationId: String, tests: List[CubiksTest]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument(
      "$push" -> BSONDocument(s"testGroups.$phaseName.tests" -> BSONDocument("$each" -> tests))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting tests during $phaseName", ApplicationNotFound(applicationId))
    collection.update(query, update) map validator
  }

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
    collection.update(query, update) map validator
  }

  def updateTestCompletionTime(cubiksUserId: Int, completedTime: DateTime): Future[Unit] = {
    val update = BSONDocument(
      "$set" -> BSONDocument(s"testGroups.$phaseName.tests.$$.completedDateTime" -> Some(completedTime))
    )
    findAndUpdateTest(cubiksUserId, update, ignoreNotFound = true)
  }

  def getTestGroupByCubiksId(cubiksUserId: Int): Future[MaybeSiftTestGroupWithAppId] = {
    val query = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument("$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId))
    )
    getTestGroupWithAppIdByQuery(query)
  }

  def getTestGroupByToken(token: String): Future[MaybeSiftTestGroupWithAppId] = {
    val query = BSONDocument(
      s"testGroups.$phaseName.tests" -> BSONDocument("$elemMatch" -> BSONDocument("token" -> token))
    )
    getTestGroupWithAppIdByQuery(query)
  }
}
