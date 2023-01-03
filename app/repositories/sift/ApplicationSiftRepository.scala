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
import repositories.{dateTimeToBson, getAppId, subDocRoot}

import scala.util.Try
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

  def nextApplicationsForSiftStage(maxBatchSize: Int): Future[Seq[ApplicationForSift]]
  def nextApplicationsReadyForNumericTestsInvitation(batchSize: Int, numericSchemes: Seq[SchemeId]): Future[Seq[NumericalTestApplication]]
  def nextApplicationsForSiftExpiry(maxBatchSize: Int, gracePeriodInSecs: Int): Future[Seq[ApplicationForSiftExpiry]]
  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]]
  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Candidate]]
  def findAllResults: Future[Seq[SiftPhaseReportItem]]
  def findAllResultsByIds(applicationIds: Seq[String]): Future[Seq[SiftPhaseReportItem]]
  def getSiftEvaluations(applicationId: String): Future[Seq[SchemeEvaluationResult]]
  def siftResultsExistsForScheme(applicationId: String, schemeId: SchemeId): Future[Boolean]
  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult, settableFields: Seq[Document] = Nil ): Future[Unit]
  //TODO: mongo no usages
//  def update(applicationId: String, predicate: BSONDocument, update: BSONDocument, action: String): Future[Unit]
  def saveSiftExpiryDate(applicationId: String, expiryDate: DateTime): Future[Unit]
  def findSiftExpiryDate(applicationId: String): Future[DateTime]
  def isSiftExpired(applicationId: String): Future[Boolean]
  def removeTestGroup(applicationId: String): Future[Unit]
  //TODO: mongo no usages (commented out code)
  def findAllUsersInSiftReady: Future[Seq[FixStuckUser]]
  def findAllUsersInSiftEntered: Future[Seq[FixUserStuckInSiftEntered]]
  def fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(appId: String): Future[Unit]
  def fixSchemeEvaluation(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def fixDataByRemovingSiftEvaluation(applicationId: String): Future[Unit]
  def nextApplicationForFirstSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]]
  def nextApplicationForSecondSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]]
  def getTestGroup(applicationId: String): Future[Option[SiftTestGroup]]
  def getTestGroupByOrderId(orderId: String): Future[MaybeSiftTestGroupWithAppId]
  def updateExpiryTime(applicationId: String, expiryDateTime: DateTime): Future[Unit]
  def updateTestStartTime(orderId: String, startedTime: DateTime): Future[Unit]
  def getApplicationIdForOrderId(orderId: String): Future[String]
  def insertNumericalTests(applicationId: String, tests: List[PsiTest]): Future[Unit]
  def updateTestCompletionTime(orderId: String, completedTime: DateTime): Future[Unit]
  def insertPsiTestResult(appId: String, psiTest: PsiTest, testResult: PsiTestResult): Future[Unit]
  def nextApplicationWithResultsReceived: Future[Option[String]]
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

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val candidateCollection: MongoCollection[Candidate] =
    CollectionFactory.collection(
      db = mongoComponent.database,
      collectionName = CollectionNames.APPLICATION,
      domainFormat = Candidate.candidateFormat
    )

  private def applicationForSiftBsonReads(document: Document): ApplicationForSift = {
    val applicationId = document.get("applicationId").get.asString().getValue
    val userId = document.get("userId").get.asString().getValue
    val appStatus = Codecs.fromBson[ApplicationStatus](document.get("applicationStatus").get)
    val currentSchemeStatus = document.get("currentSchemeStatus").map { bsonValue =>
      Codecs.fromBson[Seq[SchemeEvaluationResult]](bsonValue)
    }.getOrElse(Nil)

    ApplicationForSift(applicationId, userId, appStatus, currentSchemeStatus)
  }

  override def updateTestStartTime(orderId: String, startedTime: DateTime): Future[Unit] = {
    val filter = Document(
      s"testGroups.$phaseName.tests" -> Document(
        "$elemMatch" -> Document("orderId" -> orderId)
      )
    )

    val update = Document("$set" -> Document(
      s"testGroups.$phaseName.tests.$$.startedDateTime" -> dateTimeToBson(startedTime)
    ))

    val validator = singleUpdateValidator(orderId, actionDesc = s"updating test group start time in $phaseName",
      CannotFindTestByOrderIdException(s"Cannot find sift test group by order Id: $orderId"))

    collection.updateOne(filter, update).toFuture() map validator
  }

  override def insertPsiTestResult(appId: String, psiTest: PsiTest, testResult: PsiTestResult): Future[Unit] = {
    val query = Document(
      "applicationId" -> appId,
      s"testGroups.$phaseName.tests" -> Document("$elemMatch" -> Document("orderId" -> psiTest.orderId))
    )
    val update = Document("$set" -> Document(s"testGroups.$phaseName.tests.$$.testResult" -> Codecs.toBson(testResult)))

    val validator = singleUpdateValidator(appId, actionDesc = s"inserting $phaseName test results")

    collection.updateOne(query, update).toFuture() map validator
  }

  override def nextApplicationWithResultsReceived: Future[Option[String]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> thisApplicationStatus.toBson),
      Document(s"progress-status.${ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED}" -> true),
      Document(s"progress-status.${ProgressStatuses.SIFT_READY}" -> Document("$ne" -> true)),
      Document(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> Document("$ne" -> true))
    ))

    selectOneRandom[String](query)(getAppId, global)
  }

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

  override def nextApplicationsForSiftStage(batchSize: Int): Future[Seq[ApplicationForSift]] = {
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

    selectRandom[ApplicationForSift](eligibleForSiftQuery, batchSize)(applicationForSiftBsonReads, global )
  }

  override def nextApplicationForFirstSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {

    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
      Document(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      Document(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_READY}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_FIRST_REMINDER}" -> Document("$exists" -> false)),

      Document(s"testGroups.$phaseName.expirationDate" ->
        Document( "$lte" -> dateTimeToBson(dateTime.nowLocalTimeZone.plusHours(timeInHours))) // Serialises to UTC.
      )
    ))

    selectOneRandom[NotificationExpiringSift](query)( doc => NotificationExpiringSift.fromBson(doc, phaseName), global )
  }

  override def nextApplicationForSecondSiftReminder(timeInHours: Int): Future[Option[NotificationExpiringSift]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
      Document(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      Document(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_READY}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_FIRST_REMINDER}" -> true),
      Document(s"progress-status.${ProgressStatuses.SIFT_SECOND_REMINDER}" -> Document("$exists" -> false)),

      Document(s"testGroups.$phaseName.expirationDate" ->
        Document( "$lte" -> dateTimeToBson(dateTime.nowLocalTimeZone.plusHours(timeInHours))) // Serialises to UTC.
      )
    ))

    selectOneRandom[NotificationExpiringSift](query)( doc => NotificationExpiringSift.fromBson(doc, phaseName ), global )
  }

  override def getNotificationExpiringSift(applicationId: String): Future[Option[NotificationExpiringSift]] = {
    val query = Document("applicationId" -> applicationId)
    collection.find[Document](query).headOption() map {
      _.map(doc => NotificationExpiringSift.fromBson(doc, phaseName))
    }
  }

  def nextApplicationsForSiftExpiry(maxBatchSize: Int, gracePeriodInSecs: Int): Future[Seq[ApplicationForSiftExpiry]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
      Document(s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> true),
      Document(s"progress-status.${ProgressStatuses.SIFT_READY}" -> Document("$exists" -> false)),
      Document(s"progress-status.${ProgressStatuses.SIFT_EXPIRED}" -> Document("$exists" -> false)),
      Document(s"testGroups.$phaseName.expirationDate" ->
        Document("$lte" -> dateTimeToBson(dateTime.nowLocalTimeZone.minusSeconds(gracePeriodInSecs)))
      )
    ))

    selectRandom[ApplicationForSiftExpiry](query, maxBatchSize)(ApplicationForSiftExpiry.fromBson, global)
  }

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

    implicit def processDoc(doc: Document) = {
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

    selectRandom[NumericalTestApplication](query, batchSize)
  }

  def nextApplicationFailedAtSift: Future[Option[ApplicationForSift]] = {
    val predicate = Document(
      "applicationStatus" -> ApplicationStatus.SIFT.toBson,
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      "currentSchemeStatus.result" -> Red.toString,
      "currentSchemeStatus.result" -> Document("$nin" -> BsonArray(Green.toString, Amber.toString))
    )

    selectOneRandom[ApplicationForSift](predicate)(applicationForSiftBsonReads, global)
  }

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

  override def findAllResults: Future[Seq[SiftPhaseReportItem]] = {
    findAllByQuery(Document.empty)
  }

  override def findAllResultsByIds(applicationIds: Seq[String]): Future[Seq[SiftPhaseReportItem]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    findAllByQuery(query)
  }

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

  override def saveSiftExpiryDate(applicationId: String, expiryDate: DateTime): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$set" -> Document(s"testGroups.$phaseName.expirationDate" -> dateTimeToBson(expiryDate)))

    val validator = singleUpdateValidator(applicationId,
      actionDesc = s"inserting expiry date during $phaseName", ApplicationNotFound(applicationId))

    collection.updateOne(query, update).toFuture() map validator
  }

  override def findSiftExpiryDate(applicationId: String): Future[DateTime] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Document(s"testGroups.$phaseName.expirationDate" -> true)

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits.jotDateTimeFormat // Needed for ISODate
    val error = ApplicationNotFound(s"Cannot find sift expiry date for application $applicationId")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        (for {
          testGroups <- subDocRoot("testGroups")(doc)
          phaseBson <- subDocRoot(phaseName)(testGroups)
        } yield {
          Codecs.fromBson[DateTime](phaseBson.get("expirationDate").asDateTime())
        }).getOrElse( throw error )
      case _ => throw error
    }
  }

  override def removeTestGroup(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"testGroups.$phaseName" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing sift test group")

    collection.updateOne(query, update).toFuture() map validator
  }

  def findAllUsersInSiftReady: Future[Seq[FixStuckUser]] = {
    val query = Document("applicationStatus" -> ApplicationStatus.SIFT.toBson,
      s"progress-status.${ProgressStatuses.SIFT_READY}" -> Document("$exists" -> true),
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> Document("$exists" -> false),
      s"testGroups.$phaseName" -> Document("$exists" -> true)
    )

    val projection = Projections.include(
      "applicationId",
      s"progress-status.${ProgressStatuses.SIFT_ENTERED}",
      s"progress-status.${ProgressStatuses.SIFT_READY}",
      s"testGroups.$phaseName",
      "currentSchemeStatus"
    )

    collection.find[Document](query).projection(projection).toFuture()
      .map(_.map { doc =>
        val siftEvaluation = doc.get("testGroups")
        .map(_.asDocument().get(phaseName))
        .map(_.asDocument().get("evaluation"))
        .map(_.asDocument().get("result"))
        .map(bson => Codecs.fromBson[Seq[SchemeEvaluationResult]](bson)).getOrElse(Nil)

        // TODO: mongo the original impl looks wrong to me as it is trying to get DateTime info from ProgressStatus
        // TODO: which doesn't contain that data. Leave it for now as it is a fix endpoint and may be redundant
        val progressStatuses = subDocRoot("progress-status")(doc)
        import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed for ISODate
        val firstSiftTime = progressStatuses.flatMap { obj =>
          Try(Codecs.fromBson[DateTime](obj.get(ProgressStatuses.SIFT_ENTERED.toString).asDateTime())).toOption
          .orElse(Try(Codecs.fromBson[DateTime](obj.get(ProgressStatuses.SIFT_READY.toString).asDateTime())).toOption)
        }.getOrElse(DateTime.now())

        val css = doc.get("currentSchemeStatus").map { bsonValue =>
          Codecs.fromBson[List[SchemeEvaluationResult]](bsonValue)
        }.getOrElse(Nil)
        val applicationId = getAppId(doc)

        FixStuckUser(
          applicationId,
          firstSiftTime,
          css,
          siftEvaluation
        )
      })
  }

  def findAllUsersInSiftEntered: Future[Seq[FixUserStuckInSiftEntered]] = {
    val query = Document("applicationStatus" -> ApplicationStatus.SIFT.toBson,
      s"progress-status.${ProgressStatuses.SIFT_ENTERED}" -> Document("$exists" -> true),
      s"progress-status.${ProgressStatuses.SIFT_READY}" -> Document("$exists" -> false),
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> Document("$exists" -> false)
    )

    val projection = Projections.include("applicationId", "currentSchemeStatus")

    collection.find[Document](query).projection(projection).toFuture()
      .map(_.map { doc =>
        val css = doc.get("currentSchemeStatus").map { bsonValue =>
          Codecs.fromBson[List[SchemeEvaluationResult]](bsonValue)
        }.getOrElse(Nil)
        val applicationId = getAppId(doc)

        FixUserStuckInSiftEntered(applicationId, css)
      })
  }

  override def fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(applicationId: String): Future[Unit] = {
    val query = Document(
      "applicationId" -> applicationId,
      "applicationStatus" -> ApplicationStatus.FAILED_AT_SIFT.toBson
    )

    val update = Document(
        "$set" -> Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
        "$unset" -> Document(
          s"testGroups.$phaseName.evaluation" -> "",
          s"progress-status.${ProgressStatuses.FAILED_AT_SIFT}" -> "",
          s"progress-status-timestamp.${ProgressStatuses.FAILED_AT_SIFT}" -> ""
        )
      )

    collection.updateOne(query, update).toFuture().map { result =>
      if (result.getModifiedCount != 1) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

  override def fixDataByRemovingSiftEvaluation(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document(
        "$set" -> Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
        "$unset" -> Document(s"testGroups.$phaseName.evaluation" -> "")
      )

    collection.updateOne(query, update).toFuture().map { result =>
      if (result.getModifiedCount != 1) { throw new NotFoundException(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }

  override def fixSchemeEvaluation(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val removeDoc = Document(
      "$pull" -> Document(s"testGroups.$phaseName.evaluation.result" -> Document("schemeId" -> result.schemeId.value))
    )

    val saveEvaluationResultsDoc = Document(s"testGroups.$phaseName.evaluation.result" -> Codecs.toBson(result))
    val setDoc = Document(
      "$addToSet" -> saveEvaluationResultsDoc
    )

    val removePredicate = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> Document("$in" -> BsonArray(result.schemeId.value))
      )
    ))
    val setPredicate = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document(
        s"testGroups.$phaseName.evaluation.result.schemeId" -> Document("$nin" -> BsonArray(result.schemeId.value))
      )
    ))

    val validator = singleUpdateValidator(applicationId, s"fixing sift results for ${result.schemeId}", ApplicationNotFound(applicationId))

    for {
      _ <- collection.updateOne(removePredicate, removeDoc).toFuture() map validator
      _ <- collection.updateOne(setPredicate, setDoc).toFuture() map validator
    } yield ()
  }

  override def getTestGroup(applicationId: String): Future[Option[SiftTestGroup]] = {
    val query = Document("applicationId" -> applicationId)
    getTestGroupByQuery(query)
  }

  private def getTestGroupByQuery(query: Document): Future[Option[SiftTestGroup]] = {
    val projection = Projections.include(s"testGroups.$phaseName")
    collection.find[Document](query).projection(projection).head() map { optDocument =>
      Try(optDocument.get("testGroups")
        .map(_.asDocument().get(phaseName))
        .map(bson => Codecs.fromBson[SiftTestGroup](bson))).getOrElse(None)
    }
  }

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

  override def updateExpiryTime(applicationId: String, expiryDateTime: DateTime): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId, actionDesc = s"updating test group expiration date in $phaseName",
      ApplicationNotFound(applicationId))

    collection.updateOne(query, Document("$set" -> Document(
      s"testGroups.$phaseName.expirationDate" -> dateTimeToBson(expiryDateTime)
    ))).toFuture() map validator
  }

  def insertNumericalTests(applicationId: String, tests: List[PsiTest]): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document(
      "$push" -> Document(s"testGroups.$phaseName.tests" -> Document("$each" -> Codecs.toBson(tests)))
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting tests during $phaseName", ApplicationNotFound(applicationId))
    collection.updateOne(query, update).toFuture() map validator
  }

  private def findAndUpdateTest(orderId: String, update: Document, ignoreNotFound: Boolean, actionDesc: String): Future[Unit] = {
    val query = Document(
      s"testGroups.$phaseName.tests" -> Document("$elemMatch" -> Document("orderId" -> orderId))
    )
    val validator = if (ignoreNotFound) {
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

  override def updateTestCompletionTime(orderId: String, completedTime: DateTime): Future[Unit] = {
    val update = Document(
      "$set" -> Document(s"testGroups.$phaseName.tests.$$.completedDateTime" -> dateTimeToBson(completedTime))
    )
    findAndUpdateTest(orderId, update, ignoreNotFound = false, s"updating test completion time by orderId in $phaseName tests")
  }

  override def getTestGroupByOrderId(orderId: String): Future[MaybeSiftTestGroupWithAppId] = {
    val query = Document(
      s"testGroups.$phaseName.tests" -> Document("$elemMatch" -> Document("orderId" -> orderId))
    )
    getTestGroupWithAppIdByQuery(query)
  }

  override def removeEvaluation(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"testGroups.$phaseName.evaluation" -> ""))

    collection.updateOne(query, update).toFuture().map { result =>
      if (result.getModifiedCount != 1) { throw new NotFoundException(s"Failed to remove sift evaluation for id $applicationId") }
      else { () }
    }
  }
}
