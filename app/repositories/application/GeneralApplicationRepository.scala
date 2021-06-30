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

package repositories.application

import java.util.UUID
import java.util.regex.Pattern
import com.github.nscala_time.time.OrderingImplicits.DateTimeOrdering
import config.MicroserviceAppConfig
import factories.DateTimeFactory

import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.Exceptions._
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{EventProgressStatuses, PREVIEW}
import model.command._
import model.exchange.{CandidateEligibleForEvent, CandidatesEligibleForEventResponse}
import model.persisted._
import model.persisted.eventschedules.EventType
import model.persisted.eventschedules.EventType.EventType
import model.persisted.fsb.ScoresAndFeedback
import model.{ApplicationStatus, _}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, LocalDate}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonRegularExpression, BsonValue}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Sorts.{ascending, descending}
import org.mongodb.scala.model.{Filters, Projections}
import play.api.libs.json.{JsNumber, JsObject, Json}
import repositories._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{ AddMissingPhase2ResultReceived, PassToPhase1TestPassed, PassToPhase2, ResetPhase1TestInvitedSubmitted }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

// TODO FAST STREAM
// This is far too large an interface - we should look at splitting up based on
// functional concerns.

// scalastyle:off number.of.methods
trait GeneralApplicationRepository {
  def create(userId: String, frameworkId: String, applicationRoute: ApplicationRoute): Future[ApplicationResponse]
  def find(applicationId: String): Future[Option[Candidate]]
//TODO: test
  def findAllFileInfo: Future[List[CandidateFileInfo]]
  def find(applicationIds: Seq[String]): Future[List[Candidate]]
  def findProgress(applicationId: String): Future[ProgressResponse]
  def findStatus(applicationId: String): Future[ApplicationStatusDetails]
  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse]
  def findCandidateByUserId(userId: String): Future[Option[Candidate]]
  def findByCriteria(firstOrPreferredName: Option[String], lastName: Option[String],
                     dateOfBirth: Option[LocalDate], userIds: List[String] = List.empty): Future[List[Candidate]]
//TODO: no usages
  def findApplicationIdsByLocation(location: String): Future[List[String]]
  def submit(applicationId: String): Future[Unit]
  def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit]
  def withdrawScheme(applicationId: String, schemeWithdraw: WithdrawScheme, schemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
  def preview(applicationId: String): Future[Unit]
  def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit]
//TODO: additional test (tricky one - do later)
  def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit]
  def findAdjustments(applicationId: String): Future[Option[Adjustments]]
//TODO: test (1 pending)
  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit]
  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment]
  def removeAdjustmentsComment(applicationId: String): Future[Unit]
//TODO: no usages
  def rejectAdjustment(applicationId: String): Future[Unit]
  def gisByApplication(applicationId: String): Future[Boolean]
//TODO: no usages
  def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]]
  def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit]
  def updateApplicationStatusOnly(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit]
  def updateSubmissionDeadline(applicationId: String, newDeadline: DateTime): Future[Unit]
  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]
  def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]
//TODO: test
  def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit]
  def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]]
//TODO: test
  def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]]
  def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]]
  def fix(candidate: Candidate, issue: FixBatch): Future[Option[Candidate]]
//TODO: test
  def fixDataByRemovingETray(appId: String): Future[Unit]
  def fixDataByRemovingVideoInterviewFailed(appId: String): Future[Unit]
//TODO: test
  def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit]
  def updateApplicationRoute(appId: String, appRoute: ApplicationRoute, newAppRoute: ApplicationRoute): Future[Unit]
  def archive(appId: String, originalUserId: String, userIdToArchiveWith: String,
              frameworkId: String, appRoute: ApplicationRoute): Future[Unit]
  def findCandidatesEligibleForEventAllocation(locations: List[String], eventType: EventType,
                                               schemeId: Option[SchemeId]): Future[CandidatesEligibleForEventResponse]
  def resetApplicationAllocationStatus(applicationId: String, eventType: EventType): Future[Unit]
//TODO: test
  def setFailedToAttendAssessmentStatus(applicationId: String, eventType: EventType): Future[Unit]
//TODO: test
  def findAllocatedApplications(applicationIds: List[String]): Future[CandidatesEligibleForEventResponse]
  def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]]
//TODO: test
  def findSdipFaststreamInvitedToVideoInterview: Future[Seq[Candidate]]
//TODO: test
  def findSdipFaststreamExpiredPhase2InvitedToSift: Future[Seq[Candidate]]
//TODO: test
  def findSdipFaststreamExpiredPhase3InvitedToSift: Future[Seq[Candidate]]
//TODO: test
  def getApplicationRoute(applicationId: String): Future[ApplicationRoute]
//TODO: test
  def getLatestProgressStatuses: Future[List[String]]
//TODO: test
  def countByStatus(applicationStatus: ApplicationStatus): Future[Long]
//TODO: test
  def getProgressStatusTimestamps(applicationId: String): Future[List[(String, DateTime)]]

  // Implemented by Hmrc ReactiveRepository class - don't use until it gets fixed. Use countLong instead
//  @deprecated("At runtime throws a JsResultException: errmsg=readConcern.level must be either 'local', 'majority' or 'linearizable'", "")
//  def count(implicit ec: scala.concurrent.ExecutionContext) : Future[Int] //TODO: fix

  //TODO: test
  // Implemented in ReactiveRespositoryHelpers
  def countLong(implicit ec: scala.concurrent.ExecutionContext) : Future[Long]
  def updateCurrentSchemeStatus(applicationId: String, results: Seq[SchemeEvaluationResult]): Future[Unit]
  def removeCurrentSchemeStatus(applicationId: String): Future[Unit]
//TODO: test
  def removeWithdrawReason(applicationId: String): Future[Unit]
//TODO: test
  def findEligibleForJobOfferCandidatesWithFsbStatus: Future[Seq[String]]
//TODO: test
  def listCollections: Future[List[String]]
  def removeCollection(name: String): Future[Unit]
  def removeCandidate(applicationId: String): Future[Unit]
  def getApplicationStatusForCandidates(applicationIds: Seq[String]): Future[Seq[(String, ApplicationStatus)]]
}

// scalastyle:off number.of.methods
// scalastyle:off file.size.limit
/*
@Singleton
class GeneralApplicationMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                                   appConfig: MicroserviceAppConfig,
                                                   mongoComponent: ReactiveMongoComponent
                                                  )
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    CreateApplicationRequest.createApplicationRequestFormat,
    ReactiveMongoFormats.objectIdFormats) with GeneralApplicationRepository with RandomSelection with CommonBSONDocuments
    with GeneralApplicationRepoBSONReader with ReactiveRepositoryHelpers with CurrentSchemeStatusHelper {*/

@Singleton
class GeneralApplicationMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                                   appConfig: MicroserviceAppConfig,
                                                   mongo: MongoComponent
                                                  )
  extends PlayMongoRepository[CreateApplicationRequest](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = CreateApplicationRequest.createApplicationRequestFormat,
    indexes = Nil
  ) with GeneralApplicationRepository with RandomSelection with CommonBSONDocuments
    with GeneralApplicationRepoBSONReader with ReactiveRepositoryHelpers with CurrentSchemeStatusHelper {

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val applicationResponseCollection: MongoCollection[ApplicationResponse2] = {
    CollectionFactory.collection(
      collectionName = CollectionNames.APPLICATION,
      db = mongo.database,
      domainFormat = ApplicationResponse2.mongoFormat
    )
  }

  // Use this collection when using hand written bson documents
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(CollectionNames.APPLICATION)

  //TODO: test that these indexes are created as expected
  /*
  override def indexes: Seq[Index] = Seq(
    Index(Seq(("applicationId", Ascending), ("userId", Ascending)), unique = true),
    Index(Seq(("userId", Ascending), ("frameworkId", Ascending)), unique = true),
    Index(Seq(("applicationStatus", Ascending)), unique = false),
    Index(Seq(("assistance-details.needsSupportForOnlineAssessment", Ascending)), unique = false),
    Index(Seq(("assistance-details.needsSupportAtVenue", Ascending)), unique = false),
    Index(Seq(("assistance-details.guaranteedInterview", Ascending)), unique = false)
  )*/
  /*
  override def getApplicationStatusForCandidates(applicationIds: Seq[String]): Future[Seq[(String, ApplicationStatus)]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    val projection = BSONDocument(
      "applicationId" -> true,
      "applicationStatus" -> true
    )

    collection.find(query, Some(projection)).cursor[BSONDocument]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]())
      .map { docList =>
        docList.map { doc =>
          val applicationId = doc.getAs[String]("applicationId").get
          val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
          applicationId -> applicationStatus
        }
      }
  }*/
  override def getApplicationStatusForCandidates(applicationIds: Seq[String]): Future[Seq[(String, ApplicationStatus)]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.include("applicationId", "applicationStatus")

    collection.find[Document](query).projection(projection).toFuture()
      .map { docList =>
        docList.map { doc =>
          val applicationId = doc.get("applicationId").get.asString().getValue
          val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
          applicationId -> applicationStatus
        }
      }
  }

  /*
  override def countLong(implicit ec: ExecutionContext): Future[Long] =
    collection.withReadPreference(ReadPreference.primary).count(
      selector = Option.empty[JsObject],
      limit = None,
      skip = 0,
      hint =  None,
      readConcern = ReadConcern.Local
    )
*/
  override def countLong(implicit ec: ExecutionContext): Future[Long] = ???

  /*
  override def create(userId: String, frameworkId: String, route: ApplicationRoute): Future[ApplicationResponse] = {
    val applicationId = UUID.randomUUID().toString
    val testAccountId = UUID.randomUUID().toString
    val applicationBSON = BSONDocument(
      "applicationId" -> applicationId,
      "userId" -> userId,
      "testAccountId" -> testAccountId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> CREATED,
      "applicationRoute" -> route
    )
    collection.insert(ordered = false).one(applicationBSON) flatMap { _ =>
      findProgress(applicationId).map { p =>
        ApplicationResponse(applicationId, CREATED, route, userId, testAccountId, p, None, None)
      }
    }
  }*/
  override def create(userId: String, frameworkId: String, route: ApplicationRoute): Future[ApplicationResponse] = {
    val applicationId = UUID.randomUUID().toString
    val testAccountId = UUID.randomUUID().toString
    val applicationBSON = Document(
      "applicationId" -> applicationId,
      "userId" -> userId,
      "testAccountId" -> testAccountId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> CREATED.toBson,
      "applicationRoute" -> route.toBson
    )

    applicationCollection.insertOne(applicationBSON).toFuture() flatMap { _ =>
      findProgress(applicationId).map { p =>
        ApplicationResponse(
          applicationId, CREATED, route, userId, testAccountId, p, civilServiceExperienceDetails = None, overriddenSubmissionDeadline = None
        )
      }
    }
/*
    collection.insertOne(applicationBSON).toFuture() map { _ =>
      findProgress(applicationId).map { p =>
        ApplicationResponse(
          applicationId, CREATED, route, userId, testAccountId, p, civilServiceExperienceDetails = None, overriddenSubmissionDeadline = None
        )
      }
    }*/
  }

  /*
  def findAllFileInfo: Future[List[CandidateFileInfo]] = {
    val query = BSONDocument("testGroups.FSAC.tests.analysisExercise" -> BSONDocument("$exists" -> true))
    val projection = BSONDocument("_id" -> 0, "applicationId" -> 1, "testGroups.FSAC.tests.analysisExercise" -> 1)
    bsonCollection.find(query, Some(projection)).cursor[BSONDocument]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      docs.map { doc =>
        val testGroups = doc.getAs[BSONDocument]("testGroups")
        val fsac = testGroups.flatMap(_.getAs[BSONDocument]("FSAC"))
        val tests = fsac.flatMap(_.getAs[BSONDocument]("tests"))
        val analysisExercise = tests.flatMap(_.getAs[BSONDocument]("analysisExercise"))

        CandidateFileInfo(
          doc.getAs[String]("applicationId").get,
          analysisExercise.flatMap(_.getAs[String]("fileId")).get
        )
      }
    }
  }*/
  def findAllFileInfo: Future[List[CandidateFileInfo]] = ???

  /*
  def find(applicationId: String): Future[Option[Candidate]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    bsonCollection.find(query, projection = Option.empty[JsObject]).one[Candidate]
  }*/
  def find(applicationId: String): Future[Option[Candidate]] = {
    val query = Document("applicationId" -> applicationId)
    applicationCollection.find[BsonDocument](query).headOption().map {
      _.map { doc =>
        Codecs.fromBson[Candidate](doc)
      }
    }
  }

  /*
  def find(applicationIds: Seq[String]): Future[List[Candidate]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    bsonCollection.find(query, projection = Option.empty[JsObject]).cursor[Candidate]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
  }*/
  override def find(applicationIds: Seq[String]): Future[List[Candidate]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    collection.find[BsonDocument](query).toFuture().map { _.map { doc =>
      Codecs.fromBson[Candidate](doc)
    }.toList }
  }

  /*
  override def findProgress(applicationId: String): Future[ProgressResponse] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("progress-status" -> 2, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(document) => toProgressResponse(applicationId).read(document)
      case None => throw ApplicationNotFound(s"No application found for $applicationId")
    }
  }*/
  override def findProgress(applicationId: String): Future[ProgressResponse] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("progress-status")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(document) => toProgressResponse(applicationId)(document)
      case None => throw ApplicationNotFound(s"No application found for $applicationId")
    }
  }

  /*
  def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    collection.find(
      BSONDocument("applicationId" -> applicationId),
      Some(BSONDocument("_id" -> 0, "currentSchemeStatus" -> 1))
    ).one[BSONDocument].map(_.flatMap{ doc =>
      doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus")
    }.getOrElse(Nil))
  }*/
//  def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]] = ???

  override def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    val projection = Projections.include("currentSchemeStatus")
    collection.find[Document](Document("applicationId" -> applicationId)).projection(projection).headOption().map {
      _.map { doc =>
        doc.get("currentSchemeStatus").map { bsonValue =>
          Codecs.fromBson[Seq[SchemeEvaluationResult]](bsonValue)
        }.getOrElse(Nil)
      }.getOrElse(Nil)
    }
  }

  /*
  def findStatus(applicationId: String): Future[ApplicationStatusDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(
      "applicationStatus" -> 1,
      "progress-status-timestamp" -> 1,
      "progress-status-dates" -> 1,
      "applicationRoute" -> 1,
      "submissionDeadline" -> 1,
      "_id" -> 0
    )

    def progressStatusDateFallback(applicationStatus: ApplicationStatus, document: BSONDocument) = {
      document.getAs[BSONDocument]("progress-status-dates")
        .flatMap(_.getAs[LocalDate](applicationStatus.toLowerCase).map(_.toDateTimeAtStartOfDay))
    }

    collection.find(query, Some(projection)).one[BSONDocument] map {
      case Some(document) =>
        val applicationStatus = document.getAs[ApplicationStatus]("applicationStatus").get
        val applicationRoute = document.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
        val progressStatusTimeStampDoc = document.getAs[BSONDocument]("progress-status-timestamp")
        val latestProgressStatus = progressStatusTimeStampDoc.flatMap { timestamps =>
          val relevantProgressStatuses = timestamps.elements.filter(_.name.startsWith(applicationStatus))
          val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element => timestamps.getAs[DateTime](element.name).get)
          Try(ProgressStatuses.nameToProgressStatus(latestRelevantProgressStatus.name)).toOption
        }

        val progressStatusTimeStamp = progressStatusTimeStampDoc.flatMap { timestamps =>
          val relevantProgressStatuses = timestamps.elements.filter(_.name.startsWith(applicationStatus))
          val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element => timestamps.getAs[DateTime](element.name).get)
          timestamps.getAs[DateTime](latestRelevantProgressStatus.name)
        }
          .orElse(
            progressStatusDateFallback(applicationStatus, document)
          )
        val submissionDeadline = document.getAs[DateTime]("submissionDeadline")
        ApplicationStatusDetails(applicationStatus, applicationRoute, latestProgressStatus, progressStatusTimeStamp, submissionDeadline)

      case None => throw ApplicationNotFound(applicationId)
    }
  }*/

  def findStatus(applicationId: String): Future[ApplicationStatusDetails] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include(
      "applicationStatus",
      "progress-status-timestamp",
      "progress-status-dates",
      "applicationRoute",
      "submissionDeadline",
    )

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
    import scala.collection.JavaConverters._

    def progressStatusDateFallback(applicationStatus: ApplicationStatus, document: Document) = {
      val docOpt = document.get("progress-status-dates").map( _.asDocument() )
      docOpt.flatMap { doc =>
        Try(Codecs.fromBson[LocalDate](doc.get(applicationStatus.toLowerCase))).toOption.map( _.toDateTimeAtStartOfDay )
      }
    }

  collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
        val applicationRoute = Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").getOrElse(ApplicationRoute.Faststream.toBson))
        val progressStatusTimeStampDoc = doc.get("progress-status-timestamp").map(_.asDocument())

        val latestProgressStatus = progressStatusTimeStampDoc.flatMap { timestamps =>
          val convertedTimestamps = timestamps.entrySet().asScala.toSet
          val relevantProgressStatuses = convertedTimestamps.filter( _.getKey.startsWith(applicationStatus) )
          val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element => Codecs.fromBson[DateTime](timestamps.get(element.getKey)))
          Try(ProgressStatuses.nameToProgressStatus(latestRelevantProgressStatus.getKey)).toOption
        }

        val progressStatusTimeStamp = progressStatusTimeStampDoc.flatMap { timestamps =>
          val convertedTimestamps = timestamps.entrySet().asScala.toSet
          val relevantProgressStatuses = convertedTimestamps.filter( _.getKey.startsWith(applicationStatus) )
          val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element => Codecs.fromBson[DateTime](timestamps.get(element.getKey)))
          Try(Codecs.fromBson[DateTime](timestamps.get(latestRelevantProgressStatus.getKey))).toOption
        }
          .orElse(
            progressStatusDateFallback(applicationStatus, doc)
          )
        val submissionDeadline = doc.get("submissionDeadline").map( ss => Codecs.fromBson[DateTime](ss) )
        ApplicationStatusDetails(applicationStatus, applicationRoute, latestProgressStatus, progressStatusTimeStamp, submissionDeadline)

      case None => throw ApplicationNotFound(applicationId)
    }
  }

/*
def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = {
  val query = BSONDocument("userId" -> userId, "frameworkId" -> frameworkId)

  collection.find(query, projection = Option.empty[JsObject]).one[BSONDocument] flatMap {
    case Some(document) =>
      val applicationId = document.getAs[String]("applicationId").get
      val testAccountId = document.getAs[String]("testAccountId").get
      val applicationStatus = document.getAs[ApplicationStatus]("applicationStatus").get
      val applicationRoute = document.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
      val fastPassReceived = document.getAs[CivilServiceExperienceDetails]("civil-service-experience-details")
      val submissionDeadline = document.getAs[DateTime]("submissionDeadline")
      findProgress(applicationId).map { progress =>
        ApplicationResponse(
          applicationId, applicationStatus, applicationRoute, userId, testAccountId,
          progress, fastPassReceived, submissionDeadline
        )
      }
    case None => throw ApplicationNotFound(userId)
  }
}*/

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val query = Document("userId" -> userId, "frameworkId" -> frameworkId)

    collection.find[Document](query).headOption() flatMap {
      case Some(doc) =>
        val applicationId = doc.get("applicationId").get.asString().getValue
        val testAccountId = doc.get("testAccountId").get.asString().getValue
        val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
        val applicationRoute = Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").getOrElse(ApplicationRoute.Faststream.toBson))
        val civilServiceExperienceDetails = doc.get("civil-service-experience-details").map(_.asDocument()).map { doc =>
          Codecs.fromBson[CivilServiceExperienceDetails](doc)
        }

        import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
        val submissionDeadline = doc.get("submissionDeadline").map { bsonValue =>
          Codecs.fromBson[DateTime](bsonValue.asDateTime())
        }

        findProgress(applicationId).map { progress =>
          ApplicationResponse(
            applicationId, applicationStatus, applicationRoute, userId, testAccountId,
            progress, civilServiceExperienceDetails, submissionDeadline
          )
        }
      case None => throw ApplicationNotFound(userId)
    }
  }

/*
def findCandidateByUserId(userId: String): Future[Option[Candidate]] = {
  val query = BSONDocument("userId" -> userId)
  bsonCollection.find(query, projection = Option.empty[JsObject]).one[Candidate]
}*/
  def findCandidateByUserId(userId: String): Future[Option[Candidate]] = {
    val query = Document("userId" -> userId)
    collection.find[BsonDocument](query).headOption().map( _.map( bson => Codecs.fromBson[Candidate](bson) ))
  }

/*
def findByCriteria(firstOrPreferredNameOpt: Option[String],
                   lastNameOpt: Option[String],
                   dateOfBirthOpt: Option[LocalDate],
                   filterToUserIds: List[String]
                  ): Future[List[Candidate]] = {

  def matchIfSome(value: Option[String]) = value.map(v => BSONRegex("^" + Pattern.quote(v) + "$", "i"))

  val innerQuery = BSONArray(
    BSONDocument("$or" -> BSONArray(
      BSONDocument("personal-details.firstName" -> matchIfSome(firstOrPreferredNameOpt)),
      BSONDocument("personal-details.preferredName" -> matchIfSome(firstOrPreferredNameOpt))
    )),
    BSONDocument("personal-details.lastName" -> matchIfSome(lastNameOpt)),
    BSONDocument("personal-details.dateOfBirth" -> dateOfBirthOpt)
  )

  val fullQuery = if (filterToUserIds.isEmpty) {
    innerQuery
  } else {
    innerQuery ++ BSONDocument("userId" -> BSONDocument("$in" -> filterToUserIds))
  }

  val query = BSONDocument("$and" -> fullQuery)

  val projection = BSONDocument("userId" -> true, "applicationId" -> true, "applicationRoute" -> true,
    "applicationStatus" -> true, "personal-details" -> true)

  bsonCollection.find(query, Some(projection)).cursor[Candidate]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
}*/

  //scalastyle:off
  override def findByCriteria(firstOrPreferredNameOpt: Option[String],
                              lastNameOpt: Option[String],
                              dateOfBirthOpt: Option[LocalDate],
                              filterByUserIds: List[String]
                    ): Future[List[Candidate]] = {

    def matchIfSome(value: Option[String]) = value.map(v => BsonRegularExpression("^" + Pattern.quote(v) + "$", "i"))

    // If the search criteria is None then we specifically need to not include it in the filter otherwise the driver processes the None
    // value as null eg. "filter": {"personal-details.lastName": null, "personal-details.dateOfBirth": null... and no data is fetched
    val lastNameBson = matchIfSome(lastNameOpt).map( v => Document("personal-details.lastName" -> v) ).getOrElse(Document.empty)
    val dobBson = dateOfBirthOpt.map( v => Document("personal-details.dateOfBirth" -> v.toString) ).getOrElse(Document.empty)
    val firstNameBson = matchIfSome(firstOrPreferredNameOpt).map( v => Document("personal-details.firstName" -> v) ).getOrElse(Document.empty)
    val preferredNameBson = matchIfSome(firstOrPreferredNameOpt).map( v => Document("personal-details.preferredName" -> v) )
      .getOrElse(Document.empty)

    val innerQuery =
      Document("$and" -> BsonArray(
        lastNameBson,
        dobBson
      )) ++
      Document("$or" -> BsonArray(
        firstNameBson,
        preferredNameBson
      )
    )

    val query = if (filterByUserIds.isEmpty) {
      innerQuery
    } else {
      Document("userId" -> Document("$in" -> filterByUserIds)) ++ innerQuery
    }

    val projection = Projections.include("userId", "applicationId", "applicationRoute", "applicationStatus", "personal-details")

    applicationCollection.find[BsonDocument](query).projection(projection).toFuture().map { _.map { doc =>
      Codecs.fromBson[Candidate](doc)
    }.toList }
  }

/*
override def findApplicationIdsByLocation(location: String): Future[List[String]] = {
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "CREATED")),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN")),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "IN_PROGRESS"))
    )),
    BSONDocument("$or" -> BSONArray(
      BSONDocument("framework-preferences.firstLocation.location" -> location),
      BSONDocument("framework-preferences.secondLocation.location" -> location)
    ))
  ))

  val projection = BSONDocument("applicationId" -> 1)

  collection.find(query, Some(projection)).cursor[BSONDocument]()
    .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docList =>
    docList.map { doc =>
      doc.getAs[String]("applicationId").get
    }
  }
}*/
override def findApplicationIdsByLocation(location: String): Future[List[String]] = ???

/*
override def findSdipFaststreamInvitedToVideoInterview: Future[Seq[Candidate]] = {
  val query = BSONDocument(
    "applicationRoute" -> ApplicationRoute.SdipFaststream,
    s"progress-status.${ProgressStatuses.PHASE3_TESTS_INVITED}" -> BSONDocument("$exists" -> true)
  )

  val projection = BSONDocument("userId" -> true, "applicationId" -> true, "applicationRoute" -> true,
    "applicationStatus" -> true, "personal-details" -> true)

  bsonCollection.find(query, Some(projection)).cursor[Candidate]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
}*/
override def findSdipFaststreamInvitedToVideoInterview: Future[Seq[Candidate]] = ???

/*
override def findSdipFaststreamExpiredPhase2InvitedToSift: Future[Seq[Candidate]] = {
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationRoute" -> ApplicationRoute.SdipFaststream),
    BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
    BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_EXPIRED}" -> BSONDocument("$exists" -> true)),
    BSONDocument(s"testGroups.PHASE1.evaluation.result" -> BSONDocument("$elemMatch" ->
      BSONDocument("schemeId" -> "Sdip", "result" -> EvaluationResults.Green.toString)
    ))
  ))

  val projection = BSONDocument("userId" -> true, "applicationId" -> true, "applicationRoute" -> true,
    "applicationStatus" -> true, "personal-details" -> true)

  bsonCollection.find(query, Some(projection)).cursor[Candidate]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
}*/
override def findSdipFaststreamExpiredPhase2InvitedToSift: Future[Seq[Candidate]] = ???

/*
override def findSdipFaststreamExpiredPhase3InvitedToSift: Future[Seq[Candidate]] = {
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationRoute" -> ApplicationRoute.SdipFaststream),
    BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
    BSONDocument(s"progress-status.${ProgressStatuses.PHASE3_TESTS_EXPIRED}" -> BSONDocument("$exists" -> true)),
    BSONDocument(s"testGroups.PHASE2.evaluation.result" -> BSONDocument("$elemMatch" ->
      BSONDocument("schemeId" -> "Sdip", "result" -> EvaluationResults.Green.toString)
    ))
  ))

  val projection = BSONDocument("userId" -> true, "applicationId" -> true, "applicationRoute" -> true,
    "applicationStatus" -> true, "personal-details" -> true)

  bsonCollection.find(query, Some(projection)).cursor[Candidate]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
}*/
override def findSdipFaststreamExpiredPhase3InvitedToSift: Future[Seq[Candidate]] = ???

/*
override def submit(applicationId: String): Future[Unit] = {
  val guard = progressStatusGuardBSON(PREVIEW)
  val query = BSONDocument("applicationId" -> applicationId) ++ guard

  val updateBSON = BSONDocument("$set" -> applicationStatusBSON(SUBMITTED))

  val validator = singleUpdateValidator(applicationId, actionDesc = "submitting",
    new IllegalStateException(s"Already submitted $applicationId"))

  collection.update(ordered = false).one(query, updateBSON) map validator
}*/
  override def submit(applicationId: String): Future[Unit] = {
    val guard = progressStatusGuardBSON(PREVIEW)
    val query = Document("applicationId" -> applicationId) ++ guard

    val updateBSON = Document("$set" -> applicationStatusBSON2(SUBMITTED))

    val validator = singleUpdateValidator(applicationId, actionDesc = "submitting",
      new IllegalStateException(s"Already submitted $applicationId"))

    collection.updateOne(query, updateBSON).toFuture() map validator
  }

/*
override def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val applicationBSON = BSONDocument("$set" -> BSONDocument(
    "withdraw" -> reason
    ).merge(
      applicationStatusBSON(WITHDRAWN)
    )
  )

  val validator = singleUpdateValidator(applicationId, actionDesc = "withdrawing")

  collection.update(ordered = false).one(query, applicationBSON) map validator
}*/
  override def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val applicationBSON = Document("$set" -> (Document("withdraw" -> reason.toBson) ++ applicationStatusBSON2(WITHDRAWN)))

    val validator = singleUpdateValidator(applicationId, actionDesc = "withdrawing application")
    collection.updateOne(query, applicationBSON).toFuture() map validator
  }

/*
override def removeWithdrawReason(applicationId: String): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val update = BSONDocument("$unset" -> BSONDocument(
    "withdraw" -> ""
  ))

  val validator = singleUpdateValidator(applicationId, actionDesc = "removing withdrawal reason")

  collection.update(ordered = false).one(query, update) map validator
}*/
override def removeWithdrawReason(applicationId: String): Future[Unit] = ???

/*
override def withdrawScheme(applicationId: String, withdrawScheme: WithdrawScheme, schemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {

  val update = BSONDocument("$set" -> BSONDocument(
    s"withdraw.schemes.${withdrawScheme.schemeId}" -> withdrawScheme.reason
  ).merge(currentSchemeStatusBSON(schemeStatus)))

  val predicate = BSONDocument(
    "applicationId" -> applicationId
  )

  collection.update(ordered = false).one(predicate, update).map(_ => ())
}*/
  override def withdrawScheme(applicationId: String, withdrawScheme: WithdrawScheme, schemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {
    // Note that the info about who performed the withdraw operation is not persisted even though it is contained
    // in the WithdrawScheme class
    val update = Document("$set" -> (Document(
      s"withdraw.schemes.${withdrawScheme.schemeId}" -> withdrawScheme.reason
    ) ++ (currentSchemeStatusBSON(schemeStatus))))

    val predicate = Document(
      "applicationId" -> applicationId
    )

    collection.updateOne(predicate, update).toFuture().map(_ => ())
  }

/*
override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val progressStatusBSON = BSONDocument("$set" -> BSONDocument(
    s"progress-status.questionnaire.$sectionKey" -> true
  ))

  val validator = singleUpdateValidator(applicationId, actionDesc = "update questionnaire status")

  collection.update(ordered = false).one(query, progressStatusBSON) map validator
}*/
  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val progressStatusBSON = Document("$set" -> Document(
      s"progress-status.questionnaire.$sectionKey" -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "update questionnaire status")

    collection.updateOne(query, progressStatusBSON).toFuture() map validator
  }

/*
override def preview(applicationId: String): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val progressStatusBSON = BSONDocument("$set" -> BSONDocument(
    "progress-status.preview" -> true
  ))

  val validator = singleUpdateValidator(applicationId, actionDesc = "preview",
    CannotUpdatePreview(s"preview $applicationId"))

  collection.update(ordered = false).one(query, progressStatusBSON) map validator
}*/
  override def preview(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val progressStatusBSON = Document("$set" -> Document(
      "progress-status.preview" -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "preview",
      CannotUpdatePreview(s"preview $applicationId"))

    collection.updateOne(query, progressStatusBSON).toFuture() map validator
  }

/*
override def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]] = {
  val query = Try{ notificationType match {
    case s: SuccessTestType if s.applicationRoutes.isEmpty =>
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> s.appStatus),
        BSONDocument(s"progress-status.${s.notificationProgress}" -> BSONDocument("$ne" -> true))
      ))
    case s: SuccessTestType if s.applicationRoutes.nonEmpty =>
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> s.appStatus),
        BSONDocument(s"progress-status.${s.notificationProgress}" -> BSONDocument("$ne" -> true)),
        BSONDocument("applicationRoute" -> BSONDocument("$in" -> s.applicationRoutes))
      ))
    case f: FailedTestType =>
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> f.appStatus),
        BSONDocument(s"progress-status.${f.notificationProgress}" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.${f.receiveStatus}" -> true)
      ))
    case unknown => throw new RuntimeException(s"Unsupported NotificationTestType: $unknown")
  }}

  implicit val reader = bsonReader(TestResultNotification.fromBson)
  for {
    q <- Future.fromTry(query)
    result <- selectOneRandom[TestResultNotification](q)
  } yield result
}*/
//override def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]] = ???

  override def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]] = {
    val query = Try{ notificationType match {
      case s: SuccessTestType if s.applicationRoutes.isEmpty =>
        Document("$and" -> BsonArray(
          Document("applicationStatus" -> s.appStatus.toBson),
          Document(s"progress-status.${s.notificationProgress}" -> Document("$ne" -> true))
        ))
      case s: SuccessTestType if s.applicationRoutes.nonEmpty =>
        Document("$and" -> BsonArray(
          Document("applicationStatus" -> s.appStatus.toBson),
          Document(s"progress-status.${s.notificationProgress}" -> Document("$ne" -> true)),
          Document("applicationRoute" -> Document("$in" -> Codecs.toBson(s.applicationRoutes)))
        ))
      case f: FailedTestType =>
        Document("$and" -> BsonArray(
          Document("applicationStatus" -> f.appStatus.toBson),
          Document(s"progress-status.${f.notificationProgress}" -> Document("$ne" -> true)),
          Document(s"progress-status.${f.receiveStatus}" -> true)
        ))
      case unknown => throw new RuntimeException(s"Unsupported NotificationTestType: $unknown")
    }}

    for {
      query <- Future.fromTry(query)
//      result <- selectOneRandom[TestResultNotification](q)
      // TODO: mongo temp code until we get the selectRandom migrated
      result <- collection.find[BsonDocument](query).headOption().map(_.map ( doc => TestResultNotification.fromBson(doc) ))
    } yield result
  }

/*
def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]] = {
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationRoute" -> notificationType.applicationRoute),
    BSONDocument(s"progress-status.${notificationType.progressStatus}" -> true),
    BSONDocument(s"progress-status.${notificationType.notificationProgress}" -> BSONDocument("$ne" -> true))
  ))

  implicit val reader = bsonReader(TestResultSdipFsNotification.fromBson)
  selectOneRandom[TestResultSdipFsNotification](query)
}*/
def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]] = ???

/*
override def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]] = {
  issue.fix match {
    case PassToPhase2 =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> true)
      ))

      selectRandom[Candidate](query, issue.batchSize)
    case PassToPhase1TestPassed =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> BSONDocument("$ne" -> true))
      ))

      selectRandom[Candidate](query, issue.batchSize)
    case ResetPhase1TestInvitedSubmitted =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.SUBMITTED),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
      ))

      selectRandom[Candidate](query, issue.batchSize)
    case AddMissingPhase2ResultReceived =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_READY}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"testGroups.PHASE2.tests" ->
          BSONDocument("$elemMatch" -> BSONDocument(
            "usedForResults" -> true, "testResult" -> BSONDocument("$exists" -> true)
          ))
        )
      ))

      selectRandom[Candidate](query, issue.batchSize)
  }
}*/

  //scalastyle:off method.length
  override def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]] = {
    issue.fix match {
      case PassToPhase2 =>
        val query = Document("$and" -> BsonArray(
          Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> true)
        ))

//        selectRandom[Candidate](query, issue.batchSize)
        // TODO: mongo temp code until we get the selectRandom migrated
        val futureResult = collection.find[BsonDocument](query).limit(issue.batchSize).toFuture()
        futureResult.map(_.map ( doc => Codecs.fromBson[Candidate](doc) ).toList)

      case PassToPhase1TestPassed =>
        val query = Document("$and" -> BsonArray(
          Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> Document("$ne" -> true))
        ))

//        selectRandom[Candidate](query, issue.batchSize)
        // TODO: mongo temp code until we get the selectRandom migrated
        val futureResult = collection.find[BsonDocument](query).limit(issue.batchSize).toFuture()
        futureResult.map(_.map ( doc => Codecs.fromBson[Candidate](doc) ).toList)

      case ResetPhase1TestInvitedSubmitted =>
        val query = Document("$and" -> BsonArray(
          Document("applicationStatus" -> ApplicationStatus.SUBMITTED.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
        ))

//        selectRandom[Candidate](query, issue.batchSize)
        // TODO: mongo temp code until we get the selectRandom migrated
        val futureResult = collection.find[BsonDocument](query).limit(issue.batchSize).toFuture()
        futureResult.map(_.map ( doc => Codecs.fromBson[Candidate](doc) ).toList)

      case AddMissingPhase2ResultReceived =>
        val query = Document("$and" -> BsonArray(
          Document("applicationStatus" -> ApplicationStatus.PHASE2_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_READY}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> Document("$ne" -> true)),
          Document(s"testGroups.PHASE2.tests" ->
            Document("$elemMatch" -> Document(
              "usedForResults" -> true, "testResult" -> Document("$exists" -> true)
            ))
          )
        ))

//        selectRandom[Candidate](query, issue.batchSize)
        // TODO: mongo temp code until we get the selectRandom migrated
        val futureResult = collection.find[BsonDocument](query).limit(issue.batchSize).toFuture()
        futureResult.map(_.map ( doc => Codecs.fromBson[Candidate](doc) ).toList)
    }
  }//scalastyle:on

/*
override def fix(application: Candidate, issue: FixBatch): Future[Option[Candidate]] = {
  issue.fix match {
    case PassToPhase2 =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationId" -> application.applicationId),
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> true)
      ))
      val updateOp = bsonCollection.updateModifier(BSONDocument("$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS)))
      findAndModify(query, updateOp).map(_.result[Candidate])
    case PassToPhase1TestPassed =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationId" -> application.applicationId),
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> BSONDocument("$ne" -> true))
      ))
      val updateOp = bsonCollection.updateModifier(BSONDocument("$set" ->
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED)))
      findAndModify(query, updateOp).map(_.result[Candidate])
    case ResetPhase1TestInvitedSubmitted =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationId" -> application.applicationId),
        BSONDocument("applicationStatus" -> ApplicationStatus.SUBMITTED),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
      ))
      val updateOp = bsonCollection.updateModifier(BSONDocument("$unset" ->
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
          s"progress-status-timestamp.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
          "testGroups" -> "")))
      findAndModify(query, updateOp).map(_.result[Candidate])
    case AddMissingPhase2ResultReceived =>
      val query = BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationId" -> application.applicationId),
        BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_READY}" -> true),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> BSONDocument("$ne" -> true))
      ))
      val updateOp = bsonCollection.updateModifier(BSONDocument("$set" ->
        BSONDocument(
          s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> true,
          s"progress-status-timestamp.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> DateTime.now()
        )))

      findAndModify(query, updateOp).map(_.result[Candidate])
  }
}*/
//override def fix(application: Candidate, issue: FixBatch): Future[Option[Candidate]] = ???

  override def fix(application: Candidate, issue: FixBatch): Future[Option[Candidate]] = {
    issue.fix match {
      case PassToPhase2 =>
        val query = Document("$and" -> BsonArray(
          Document("applicationId" -> application.applicationId),
          Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> true)
        ))
        val updateOp = Document("$set" -> Document("applicationStatus" -> ApplicationStatus.PHASE2_TESTS.toBson))

        collection.updateOne(query, updateOp).toFuture().flatMap { _ =>
          collection.find[BsonDocument](Document("applicationId" -> application.applicationId)).headOption().map { _.map { doc =>
            Codecs.fromBson[Candidate](doc)
          }}
        }
      case PassToPhase1TestPassed =>
        val query = Document("$and" -> BsonArray(
          Document("applicationId" -> application.applicationId),
          Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> Document("$ne" -> true))
        ))
        val updateOp = Document("$set" -> Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED.toBson))

        collection.updateOne(query, updateOp).toFuture().flatMap { _ =>
          collection.find[BsonDocument](Document("applicationId" -> application.applicationId)).headOption().map { _.map { doc =>
            Codecs.fromBson[Candidate](doc)
          }}
        }
      case ResetPhase1TestInvitedSubmitted =>
        val query = Document("$and" -> BsonArray(
          Document("applicationId" -> application.applicationId),
          Document("applicationStatus" -> ApplicationStatus.SUBMITTED.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
        ))
        val updateOp = Document("$unset" ->
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
            s"progress-status-timestamp.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
            "testGroups" -> ""))

        collection.updateOne(query, updateOp).toFuture().flatMap { _ =>
          collection.find[BsonDocument](Document("applicationId" -> application.applicationId)).headOption().map { _.map { doc =>
            Codecs.fromBson[Candidate](doc)
          }}
        }
      case AddMissingPhase2ResultReceived =>
        val query = Document("$and" -> BsonArray(
          Document("applicationId" -> application.applicationId),
          Document("applicationStatus" -> ApplicationStatus.PHASE2_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_READY}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> Document("$ne" -> true))
        ))
        import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
        val updateOp = Document("$set" ->
          Document(
            s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> true,
            s"progress-status-timestamp.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> Codecs.toBson(DateTime.now())
          ))

        collection.updateOne(query, updateOp).toFuture().flatMap { _ =>
          collection.find[BsonDocument](Document("applicationId" -> application.applicationId)).headOption().map { _.map { doc =>
            Codecs.fromBson[Candidate](doc)
          }}
        }
    }
  }
/*
def fixDataByRemovingETray(appId: String): Future[Unit] = {
  import ProgressStatuses._

  val query = BSONDocument("$and" ->
    BSONArray(
      BSONDocument("applicationId" -> appId),
      BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS)
    ))

  val updateOp = bsonCollection.updateModifier(
    BSONDocument(
      "$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_INVITED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_STARTED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_FIRST_REMINDER.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_SECOND_REMINDER.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_COMPLETED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_EXPIRED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_RESULTS_RECEIVED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_PASSED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_FAILED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE2_TESTS_FAILED_NOTIFIED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_INVITED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_STARTED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_FIRST_REMINDER.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_SECOND_REMINDER.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_COMPLETED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_EXPIRED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_RESULTS_RECEIVED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_PASSED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_FAILED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE2_TESTS_FAILED_NOTIFIED.key}" -> ""),
      "$unset" -> BSONDocument(s"testGroups.PHASE2" -> "")
    )
  )

  findAndModify(query, updateOp).map(_ => ())
}*/
def fixDataByRemovingETray(appId: String): Future[Unit] = ???

/*
def fixDataByRemovingVideoInterviewFailed(appId: String): Future[Unit] = {
  import ProgressStatuses._

  val query = BSONDocument("$and" ->
    BSONArray(
      BSONDocument("applicationId" -> appId),
      BSONDocument("applicationStatus" -> ApplicationStatus.PHASE3_TESTS_FAILED)
    ))

  val updateOp = bsonCollection.updateModifier(
    BSONDocument(
      "$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.PHASE3_TESTS),
      "$unset" -> BSONDocument(s"progress-status.${PHASE3_TESTS_FAILED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status.${PHASE3_TESTS_FAILED_NOTIFIED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE3_TESTS_FAILED.key}" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.${PHASE3_TESTS_FAILED_NOTIFIED.key}" -> ""),
      "$unset" -> BSONDocument(s"testGroups.PHASE3.evaluation" -> "")
    )
  )

  findAndModify(query, updateOp).map(_ => ())
}*/
  override def fixDataByRemovingVideoInterviewFailed(appId: String): Future[Unit] = {
    import ProgressStatuses._

    val query = Document("$and" ->
      BsonArray(
        Document("applicationId" -> appId),
        Document("applicationStatus" -> ApplicationStatus.PHASE3_TESTS_FAILED.toBson)
      ))

    val updateOp = Document(
      "$set" -> Document("applicationStatus" -> ApplicationStatus.PHASE3_TESTS.toBson),
      "$unset" -> Document(
        s"progress-status.${PHASE3_TESTS_FAILED.key}" -> "",
        s"progress-status.${PHASE3_TESTS_FAILED_NOTIFIED.key}" -> "",
        s"progress-status-timestamp.${PHASE3_TESTS_FAILED.key}" -> "",
        s"progress-status-timestamp.${PHASE3_TESTS_FAILED_NOTIFIED.key}" -> "",
        s"testGroups.PHASE3.evaluation" -> ""
      )
    )

    collection.updateOne(query, updateOp).toFuture().map(_ => ())
  }

/*
def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit] = {
  val query = BSONDocument(
    "applicationId" -> appId,
    s"progress-status.$progressStatus" -> true
  )
  val updateOp = bsonCollection.updateModifier(BSONDocument(
    "$unset" -> BSONDocument(s"progress-status.$progressStatus" -> ""),
    "$unset" -> BSONDocument(s"progress-status-timestamp.$progressStatus" -> "")
  ))

  findAndModify(query, updateOp).map(_ => ())
}*/
def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit] = ???

private[application] def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
  val isNotSubmitted = !progress.submitted
  val isNotWithdrawn = !progress.withdrawn
  isNotWithdrawn && isNotSubmitted
}

//TODO: fix
//  def extract(key: String)(root: Option[BSONDocument]): Option[String] = root.flatMap(_.getAs[String](key))

/*private def getAdjustmentsConfirmed(assistance: Option[BSONDocument]): Option[String] = {
  assistance.flatMap(_.getAs[Boolean]("adjustmentsConfirmed")).getOrElse(false) match {
    case false => Some("Unconfirmed")
    case true => Some("Confirmed")
  }
}*/
/*
def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit] = {

  val query = BSONDocument("applicationId" -> applicationId)

  val resetExerciseAdjustmentsBSON = BSONDocument("$unset" -> BSONDocument(
    "assistance-details.etray" -> "",
    "assistance-details.video" -> ""
  ))

  val adjustmentsConfirmationBSON = BSONDocument("$set" -> BSONDocument(
    "assistance-details.typeOfAdjustments" -> data.adjustments.getOrElse(List.empty[String]),
    "assistance-details.adjustmentsConfirmed" -> true,
    "assistance-details.etray" -> data.etray,
    "assistance-details.video" -> data.video
  ))

  val resetValidator = singleUpdateValidator(applicationId, actionDesc = "reset")
  val adjustmentValidator = singleUpdateValidator(applicationId, actionDesc = "updateAdjustments")

  collection.update(ordered = false).one(query, resetExerciseAdjustmentsBSON).map(resetValidator).flatMap { _ =>
    collection.update(ordered = false).one(query, adjustmentsConfirmationBSON) map adjustmentValidator
  }
}*/
  //scalastyle:off
  override def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val resetExerciseAdjustmentsBSON = Document("$unset" -> Document(
      "assistance-details.etray" -> "",
      "assistance-details.video" -> ""
    ))

    val adjustmentsConfirmationBSON = Document("$set" -> (Document(
      "assistance-details.typeOfAdjustments" -> data.adjustments.getOrElse(List.empty[String]),
      "assistance-details.adjustmentsConfirmed" -> true//,
//      "assistance-details.etray" -> data.etray.map( etray => Codecs.toBson(etray) ).getOrElse(Document.empty),
//      "assistance-details.video" -> data.video.map( video => Codecs.toBson(video) ).getOrElse(Document.empty)
    ) ++ data.etray.map( adjustment => Document("assistance-details.etray" -> Codecs.toBson(adjustment) )).getOrElse(Document.empty)
      ++ data.video.map( adjustment => Document("assistance-details.video" -> Codecs.toBson(adjustment) )).getOrElse(Document.empty)
    ))

    val resetValidator = singleUpdateValidator(applicationId, actionDesc = "resetting adjustments")
    val adjustmentValidator = singleUpdateValidator(applicationId, actionDesc = "updating adjustments")

//    collection.updateOne(query, resetExerciseAdjustmentsBSON).toFuture().map(resetValidator).flatMap { _ =>
//      collection.updateOne(query, adjustmentsConfirmationBSON).toFuture() map adjustmentValidator
//    }
//    collection.updateOne(query, adjustmentsConfirmationBSON).toFuture() map adjustmentValidator

    collection.updateOne(query, resetExerciseAdjustmentsBSON).toFuture().map { _ =>
      println("**** reset successful")
      collection.updateOne(query, adjustmentsConfirmationBSON).toFuture() map { _ =>
        println("**** update successful")
        ()
      }
    }
  }

/*
def findAdjustments(applicationId: String): Future[Option[Adjustments]] = {

  val query = BSONDocument("applicationId" -> applicationId)
  val projection = BSONDocument("assistance-details" -> 1, "_id" -> 0)

  collection.find(query, Some(projection)).one[BSONDocument].map {
    _.flatMap { document =>
      val rootOpt = document.getAs[BSONDocument]("assistance-details")
      rootOpt.map { root =>
        val adjustmentList = root.getAs[List[String]]("typeOfAdjustments")
        val adjustmentsConfirmed = root.getAs[Boolean]("adjustmentsConfirmed")
        val etray = root.getAs[AdjustmentDetail]("etray")
        val video = root.getAs[AdjustmentDetail]("video")
        Adjustments(adjustmentList, adjustmentsConfirmed, etray, video)
      }
    }
  }
}*/

  override def findAdjustments(applicationId: String): Future[Option[Adjustments]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document("assistance-details" -> Document("$exists" -> true))
    ))
    val projection = Projections.include("assistance-details")

    collection.find[Document](query).projection(projection).headOption().map { docOpt =>
      docOpt.map { document =>
        val rootOpt = document.get("assistance-details")
        val adjustmentList = rootOpt.map( bson => Codecs.fromBson[List[String]](bson.asDocument().get("typeOfAdjustments")) )
        val adjustmentsConfirmed = rootOpt.map( _.asDocument().get("adjustmentsConfirmed").asBoolean().getValue )
        val etray = rootOpt.map( bson => Codecs.fromBson[AdjustmentDetail](bson.asDocument().get("etray")) )
        val video = rootOpt.map( bson => Codecs.fromBson[AdjustmentDetail](bson.asDocument().get("video")) )
        Adjustments(adjustmentList, adjustmentsConfirmed, etray, video)
      }
    }
  }

/*
def removeAdjustmentsComment(applicationId: String): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)

  val removeBSON = BSONDocument("$unset" -> BSONDocument(
    "assistance-details.adjustmentsComment" -> ""
  ))

  val validator = singleUpdateValidator(applicationId,
    actionDesc = "remove adjustments comment",
    notFound = CannotRemoveAdjustmentsComment(applicationId))

  collection.update(ordered = false).one(query, removeBSON) map validator
}*/
  def removeAdjustmentsComment(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val removeBSON = Document("$unset" -> Document(
      "assistance-details.adjustmentsComment" -> ""
    ))

    val validator = singleUpdateValidator(applicationId,
      actionDesc = "remove adjustments comment",
      error = CannotRemoveAdjustmentsComment(applicationId))

    collection.updateOne(query, removeBSON).toFuture() map validator
  }

/*
def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)

  val updateBSON = BSONDocument("$set" -> BSONDocument(
    "assistance-details.adjustmentsComment" -> adjustmentsComment.comment
  ))

  val validator = singleUpdateValidator(applicationId,
    actionDesc = "save adjustments comment",
    notFound = CannotUpdateAdjustmentsComment(applicationId))

  collection.update(ordered = false).one(query, updateBSON) map validator
}*/
//def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = ???
  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val updateBSON = Document("$set" -> Document(
      "assistance-details.adjustmentsComment" -> adjustmentsComment.comment
    ))

    val validator = singleUpdateValidator(applicationId,
      actionDesc = "save adjustments comment",
      error = CannotUpdateAdjustmentsComment(applicationId))

    collection.updateOne(query, updateBSON).toFuture() map validator
  }

/*
def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val projection = BSONDocument("assistance-details" -> 1, "_id" -> 0)

  collection.find(query, Some(projection)).one[BSONDocument].map {
    case Some(document) =>
      val root = document.getAs[BSONDocument]("assistance-details")
      root match {
        case Some(doc) =>
          doc.getAs[String]("adjustmentsComment") match {
            case Some(comment) => AdjustmentsComment(comment)
            case None => throw AdjustmentsCommentNotFound(applicationId)
          }
        case None => throw AdjustmentsCommentNotFound(applicationId)
      }
    case None => throw ApplicationNotFound(applicationId)
  }
}*/
  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("assistance-details")

    collection.find[Document](query).projection(projection).headOption().map {
      case Some(document) =>
        val root = document.get("assistance-details").map(_.asDocument())
        root match {
          case Some(doc) =>
            val comment = Try(doc.get("adjustmentsComment").asString().getValue).getOrElse(throw AdjustmentsCommentNotFound(applicationId))
            AdjustmentsComment(comment)
          case None => throw AdjustmentsCommentNotFound(applicationId)
        }
      case None =>
        throw ApplicationNotFound(s"No application found when looking for adjustments comment for $applicationId")
    }
  }

/*
def rejectAdjustment(applicationId: String): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)

  val adjustmentRejection = BSONDocument("$set" -> BSONDocument(
    "assistance-details.typeOfAdjustments" -> List.empty[String],
    "assistance-details.needsAdjustment" -> "No"
  ))

  val validator = singleUpdateValidator(applicationId,
    actionDesc = "remove adjustments comment",
    notFound = CannotRemoveAdjustmentsComment(applicationId))

  collection.update(ordered = false).one(query, adjustmentRejection) map validator
}*/
def rejectAdjustment(applicationId: String): Future[Unit] = ???

/*
def gisByApplication(applicationId: String): Future[Boolean] = {
  val query = BSONDocument("applicationId" -> applicationId)

  val projection = BSONDocument(
    "assistance-details.guaranteedInterview" -> "1"
  )

  collection.find(query, Some(projection)).one[BSONDocument].map {
    _.flatMap { doc =>
      doc.getAs[BSONDocument]("assistance-details").map(_.getAs[Boolean]("guaranteedInterview").contains(true))
    }.getOrElse(false)
  }
}*/
  def gisByApplication(applicationId: String): Future[Boolean] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("assistance-details.guaranteedInterview")

    collection.find[Document](query).projection(projection).headOption().map {
      _.exists { doc =>
        val assistanceDetailsRoot = doc.get("assistance-details").map(_.asDocument()).get
        Try(assistanceDetailsRoot.get("guaranteedInterview").asBoolean().getValue).getOrElse(false)
      }
    }
  }

/*
def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val format = DateTimeFormat.forPattern("yyyy-MM-dd")
  val projection = BSONDocument(
    "allocation-expire-date" -> "1"
  )

  collection.find(query, Some(projection)).one[BSONDocument].map {
    _.flatMap { doc =>
      doc.getAs[String]("allocation-expire-date").map(d => format.parseDateTime(d).toLocalDate)
    }
  }
}*/
def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = ???

/*
def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val validator = singleUpdateValidator(applicationId, actionDesc = "updating status")

  collection.update(ordered = false).one(query, BSONDocument("$set" -> applicationStatusBSON(applicationStatus))) map validator
}*/
  def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating status")

    collection.updateOne(query, Document("$set" -> applicationStatusBSON2(applicationStatus))).toFuture() map validator
  }

/*
def updateApplicationStatusOnly(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val updateOp = BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus.toString))
  val validator = singleUpdateValidator(applicationId, actionDesc = "updating application status")

  collection.update(ordered = false).one(query, updateOp) map validator
}*/
  def updateApplicationStatusOnly(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val updateOp = Document("$set" -> Document("applicationStatus" -> applicationStatus.toBson))
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating application status")

    collection.updateOne(query, updateOp).toFuture() map validator
  }

/*
def updateSubmissionDeadline(applicationId: String, newDeadline: DateTime): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val validator = singleUpdateValidator(applicationId, actionDesc = "updating submission deadline")

  collection.update(ordered = false).one(query, BSONDocument("$set" -> BSONDocument("submissionDeadline" -> newDeadline))) map validator
}*/
  override def updateSubmissionDeadline(applicationId: String, newDeadline: DateTime): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating submission deadline")

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
    collection.updateOne(query, Document("$set" -> Document("submissionDeadline" -> Codecs.toBson(newDeadline)))).toFuture() map validator
  }

/*
override def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
  val query = BSONDocument("applicationId" -> appId)
  collection.find(query, projection = Option.empty[JsObject]).one[BSONDocument] map {
    _.map(bsonDocToOnlineTestApplication)
  }
}*/
  override def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
    val query = Document("applicationId" -> appId)
    collection.find[Document](query).headOption() map {
      _.map(bsonDocToOnlineTestApplication)
    }
  }

/*
override def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val validator = singleUpdateValidator(applicationId, actionDesc = "updating progress and app status")

  collection.update(ordered = false).one(query, BSONDocument("$set" ->
    applicationStatusBSON(progressStatus))
  ) map validator
}*/

  override def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating progress and app status")

    collection.updateOne(query, Document("$set" ->
      applicationStatusBSON(progressStatus))
    ).toFuture() map validator
  }

/*
override def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = {
  require(progressStatuses.nonEmpty, "Progress statuses to remove cannot be empty")

  val query = BSONDocument("applicationId" -> applicationId)

  val statusesToUnset = progressStatuses.flatMap { progressStatus =>
    Map(
      s"progress-status.$progressStatus" -> BSONString(""),
      s"progress-status-dates.$progressStatus" -> BSONString(""),
      s"progress-status-timestamp.$progressStatus" -> BSONString("")
    )
  }

  val unsetDoc = BSONDocument("$unset" -> BSONDocument(statusesToUnset))

  val validator = singleUpdateValidator(applicationId, actionDesc = "removing progress and app status")

  collection.update(ordered = false).one(query, unsetDoc) map validator
}*/
override def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = ???
  /*
  override def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty, "Progress statuses to remove cannot be empty")

    val query = Document("applicationId" -> applicationId)

    val statusesToUnset = progressStatuses.map { progressStatus =>
      Map(
        s"progress-status.$progressStatus" -> "",
        s"progress-status-dates.$progressStatus" -> "",
        s"progress-status-timestamp.$progressStatus" -> ""
      )
    }

    val unsetDoc = Document("$unset" -> Document(statusesToUnset))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing progress statuses")

    collection.updateOne(query, unsetDoc).toFuture() map validator
  }*/

/*
override def updateApplicationRoute(appId: String, appRoute:ApplicationRoute, newAppRoute: ApplicationRoute): Future[Unit] = {
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationId" -> appId),
    applicationRouteCriteria(appRoute)
  ))

  val updateAppRoute = BSONDocument("$set" -> BSONDocument(
    "applicationRoute" -> newAppRoute
  ))

  val validator = singleUpdateValidator(appId, actionDesc = "updating application route")
  collection.update(ordered = false).one(query, updateAppRoute) map validator
}*/
  override def updateApplicationRoute(appId: String, appRoute:ApplicationRoute, newAppRoute: ApplicationRoute): Future[Unit] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> appId),
      applicationRouteCriteria(appRoute)
    ))

    val updateAppRoute = Document("$set" -> Document(
      "applicationRoute" -> newAppRoute.toBson
    ))

    val validator = singleUpdateValidator(appId, actionDesc = "updating application route")
    collection.updateOne(query, updateAppRoute).toFuture() map validator
  }

/*
override def archive(appId: String, originalUserId: String, userIdToArchiveWith: String,
                     frameworkId: String, appRoute: ApplicationRoute): Future[Unit] = {
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationId" -> appId),
    applicationRouteCriteria(appRoute)
  ))

  val updateWithArchiveUserId = BSONDocument("$set" ->
    BSONDocument(
      "originalUserId" -> originalUserId,
      "userId" -> userIdToArchiveWith
    ).merge(
      applicationStatusBSON(ProgressStatuses.APPLICATION_ARCHIVED)
    )
  )

  val validator = singleUpdateValidator(appId, actionDesc = "archiving application")
  collection.update(ordered = false).one(query, updateWithArchiveUserId) map validator
}*/
  override def archive(appId: String, originalUserId: String, userIdToArchiveWith: String,
                       frameworkId: String, appRoute: ApplicationRoute): Future[Unit] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> appId),
      applicationRouteCriteria(appRoute)
    ))

    val updateWithArchiveUserId = Document("$set" ->
      (Document("originalUserId" -> originalUserId, "userId" -> userIdToArchiveWith)
        ++ applicationStatusBSON(ProgressStatuses.APPLICATION_ARCHIVED))
    )

    val validator = singleUpdateValidator(appId, actionDesc = "archiving application")
    collection.updateOne(query, updateWithArchiveUserId).toFuture() map validator
  }

/*
override def findAllocatedApplications(applicationIds: List[String]): Future[CandidatesEligibleForEventResponse] = {
  val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
  val projection = BSONDocument(
    "applicationId" -> true,
    "personal-details.firstName" -> true,
    "personal-details.lastName" -> true,
    "assistance-details.needsSupportAtVenue" -> true,
    "assistance-details.needsSupportForOnlineAssessment" -> true,
    "progress-status-timestamp" -> true,
    "fsac-indicator" -> true,
    "testGroups.FSB.scoresAndFeedback" -> true
  )

  collection.find(query, Some(projection)).cursor[BSONDocument]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]())
    .map { docList =>
      docList.map { doc =>
        bsonDocToCandidatesEligibleForEvent(doc)
      }
    }.flatMap { result =>
    Future.successful(CandidatesEligibleForEventResponse(result, -1))
  }
}*/
override def findAllocatedApplications(applicationIds: List[String]): Future[CandidatesEligibleForEventResponse] = ???

  private def countDocuments(query: Document) = {
    collection.find(query).toFuture()
      .map( _.size )
  }

/*
override def findCandidatesEligibleForEventAllocation(locations: List[String],
                                                      eventType: EventType,
                                                      schemeId: Option[SchemeId]
                                                     ): Future[CandidatesEligibleForEventResponse] = {
  logger.info("Finding candidates eligible for event allocation with " +
    s"maxNumberOfCandidates = ${appConfig.eventsConfig.maxNumberOfCandidates}")
  val appStatus = eventType.applicationStatus
  val status = EventProgressStatuses.get(appStatus)
  val awaitingAllocation = status.awaitingAllocation.key
  val confirmedAllocation = status.allocationConfirmed.key
  val unconfirmedAllocation = status.allocationUnconfirmed.key
  val fsacConditions = BSONDocument("fsac-indicator.assessmentCentre" -> BSONDocument("$in" -> locations))
  val fsbConditions = schemeId.map { s => isFirstResidualPreference(s) }
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationStatus" -> appStatus),
    if (eventType == EventType.FSAC) fsacConditions else fsbConditions,
    BSONDocument(s"progress-status.$awaitingAllocation" -> true),
    BSONDocument(s"progress-status.$confirmedAllocation" -> BSONDocument("$exists" -> false)),
    BSONDocument(s"progress-status.$unconfirmedAllocation" -> BSONDocument("$exists" -> false))
  ))
  countDocuments(query).flatMap { count =>
    if (count == 0) {
      Future.successful(CandidatesEligibleForEventResponse(List.empty, 0))
    } else {
      val projection = BSONDocument(
        "applicationId" -> true,
        "personal-details.firstName" -> true,
        "personal-details.lastName" -> true,
        "assistance-details.needsSupportAtVenue" -> true,
        "assistance-details.needsSupportForOnlineAssessment" -> true,
        "progress-status-timestamp" -> true,
        "fsac-indicator" -> true
      )
      val ascending = JsNumber(1)
      // Eligible candidates should be sorted based on when they passed PHASE 3
      val sort = new JsObject(Map(s"progress-status-timestamp.${ApplicationStatus.PHASE3_TESTS_PASSED}" -> ascending))
      collection.find(query, Some(projection)).sort(sort).cursor[BSONDocument]()
        .collect[List](appConfig.eventsConfig.maxNumberOfCandidates, Cursor.FailOnError[List[BSONDocument]]())
        .map { docList =>
          docList.map { doc =>
            bsonDocToCandidatesEligibleForEvent(doc)
          }
        }.flatMap { result =>
        Future.successful(CandidatesEligibleForEventResponse(result, count))
      }
    }
  }
}*/
//override def findCandidatesEligibleForEventAllocation(locations: List[String],
//                                                      eventType: EventType,
//                                                      schemeId: Option[SchemeId]
//                                                     ): Future[CandidatesEligibleForEventResponse] = ???

  override def findCandidatesEligibleForEventAllocation(locations: List[String],
                                                        eventType: EventType,
                                                        schemeId: Option[SchemeId]
                                                       ): Future[CandidatesEligibleForEventResponse] = {
    logger.info("Finding candidates eligible for event allocation with " +
      s"maxNumberOfCandidates = ${appConfig.eventsConfig.maxNumberOfCandidates}")
    val appStatus = eventType.applicationStatus
    val status = EventProgressStatuses.get(appStatus)
    val awaitingAllocation = status.awaitingAllocation.key
    val confirmedAllocation = status.allocationConfirmed.key
    val unconfirmedAllocation = status.allocationUnconfirmed.key
    val fsacConditions = Document("fsac-indicator.assessmentCentre" -> Document("$in" -> locations))
    val fsbConditions = schemeId.map { s => isFirstResidualPreference(s) }
    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> appStatus.toBson),
      if (eventType == EventType.FSAC) fsacConditions else fsbConditions,
      Document(s"progress-status.$awaitingAllocation" -> true),
      Document(s"progress-status.$confirmedAllocation" -> Document("$exists" -> false)),
      Document(s"progress-status.$unconfirmedAllocation" -> Document("$exists" -> false))
    ))

    countDocuments(query).flatMap { count =>
      if (count == 0) {
        Future.successful(CandidatesEligibleForEventResponse(List.empty, 0))
      } else {
        val projection = Projections.include(
          "applicationId",
          "personal-details.firstName",
          "personal-details.lastName",
          "assistance-details.needsSupportAtVenue",
          "assistance-details.needsSupportForOnlineAssessment",
          "progress-status-timestamp",
          "fsac-indicator"
        )

        // Eligible candidates should be sorted based on when they passed PHASE 3
        val sort = ascending(s"progress-status-timestamp.${ApplicationStatus.PHASE3_TESTS_PASSED}")

        collection.find[Document](query).projection(projection).sort(sort).limit(appConfig.eventsConfig.maxNumberOfCandidates).toFuture()
          .map { docList =>
            docList.map { doc =>
              bsonDocToCandidatesEligibleForEvent(doc)
            }.toList
          }.flatMap { result =>
          Future.successful(CandidatesEligibleForEventResponse(result, count))
        }
      }
    }
  }

/*
override def resetApplicationAllocationStatus(applicationId: String, eventType: EventType): Future[Unit] = {
  replaceAllocationStatus(applicationId, EventProgressStatuses.get(eventType.applicationStatus).awaitingAllocation)
}*/
  override def resetApplicationAllocationStatus(applicationId: String, eventType: EventType): Future[Unit] = {
    replaceAllocationStatus(applicationId, EventProgressStatuses.get(eventType.applicationStatus).awaitingAllocation)
  }

/*
override def setFailedToAttendAssessmentStatus(applicationId: String, eventType: EventType): Future[Unit] = {
  replaceAllocationStatus(applicationId, EventProgressStatuses.get(eventType.applicationStatus).failedToAttend)
}*/
  override def setFailedToAttendAssessmentStatus(applicationId: String, eventType: EventType): Future[Unit] = ???

  import ProgressStatuses._

  private val progressStatuses = Map(
    ASSESSMENT_CENTRE -> List(
      ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED,
      ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
      ASSESSMENT_CENTRE_AWAITING_ALLOCATION,
      ASSESSMENT_CENTRE_FAILED_TO_ATTEND),
    FSB -> List(
      FSB_ALLOCATION_CONFIRMED,
      FSB_ALLOCATION_UNCONFIRMED,
      FSB_AWAITING_ALLOCATION,
      FSB_FAILED_TO_ATTEND
    )
  )

  private def replaceAllocationStatus(applicationId: String, newStatus: ProgressStatuses.ProgressStatus) = {
    val query = Document("applicationId" -> applicationId)
    val statusesToRemove = progressStatuses(newStatus.applicationStatus)
      .filter(_ != newStatus).map(p => s"progress-status.${p.key}" -> "")

    val updateQuery = Document(
      "$unset" -> Document(statusesToRemove),
      "$set" -> Document(s"progress-status.${newStatus.key}" -> true)
    )
    collection.updateOne(query, updateQuery).toFuture().map(_ => ())
  }

  /*
  private def bsonDocToCandidatesEligibleForEvent(doc: Document) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val personalDetails = doc.getAs[BSONDocument]("personal-details").get
    val firstName = personalDetails.getAs[String]("firstName").get
    val lastName = personalDetails.getAs[String]("lastName").get
    val needsSupportAtVenue = doc.getAs[BSONDocument]("assistance-details").flatMap(_.getAs[Boolean]("needsSupportAtVenue")).getOrElse(false)
    val needsSupportForOnlineTests = doc.getAs[BSONDocument]("assistance-details")
      .flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).getOrElse(false)
    val needsAdjustment = needsSupportAtVenue || needsSupportForOnlineTests
    val dateReady = doc.getAs[BSONDocument]("progress-status-timestamp").flatMap(_.getAs[DateTime](ApplicationStatus.PHASE3_TESTS_PASSED))
    val fsacIndicator = doc.getAs[model.persisted.FSACIndicator]("fsac-indicator").get
    val scoresAndFeedbackOpt = for {
      testGroups <- doc.getAs[BSONDocument]("testGroups")
      fsb <- testGroups.getAs[BSONDocument]("FSB")
      scoresAndFeedback <- fsb.getAs[ScoresAndFeedback]("scoresAndFeedback")
    } yield scoresAndFeedback
    val fsbScoresAndFeedbackSubmitted = scoresAndFeedbackOpt match {
      case Some(scoresAndFeedback) => true
      case _ => false
    }

    CandidateEligibleForEvent(
      applicationId,
      firstName,
      lastName,
      needsAdjustment,
      fsbScoresAndFeedbackSubmitted,
      model.FSACIndicator(fsacIndicator),
      dateReady.getOrElse(DateTime.now()))
  }*/

  private def bsonDocToCandidatesEligibleForEvent(doc: Document) = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val personalDetailsRoot = doc.get("personal-details").map(_.asDocument()).get
    val firstName = personalDetailsRoot.get("firstName").asString().getValue
    val lastName = personalDetailsRoot.get("lastName").asString().getValue

    val assistanceDetailsRoot = doc.get("assistance-details").map(_.asDocument()).get
    val needsSupportAtVenue = Try(assistanceDetailsRoot.get("needsSupportAtVenue").asBoolean().getValue).getOrElse(false)
    val needsSupportForOnlineTests = Try(assistanceDetailsRoot.get("needsSupportForOnlineAssessment").asBoolean().getValue).getOrElse(false)

    val needsAdjustment = needsSupportAtVenue || needsSupportForOnlineTests

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
    val dateReadyOpt = doc.get("progress-status-timestamp").map{ _.asDocument().get(ApplicationStatus.PHASE3_TESTS_PASSED) }.flatMap {
      bson => Try(Codecs.fromBson[DateTime](bson)).toOption
    }

    val fsacIndicator = Codecs.fromBson[model.persisted.FSACIndicator](doc.get("fsac-indicator").get)

    val fsbOpt = doc.get("testGroups").map(_.asDocument().get("FSB").asDocument())
    val scoresAndFeedbackOpt = fsbOpt.map( fsb => Codecs.fromBson[ScoresAndFeedback](fsb.get("scoresAndFeedback")) )

    val fsbScoresAndFeedbackSubmitted = scoresAndFeedbackOpt.isDefined

    CandidateEligibleForEvent(
      applicationId,
      firstName,
      lastName,
      needsAdjustment,
      fsbScoresAndFeedbackSubmitted,
      model.FSACIndicator(fsacIndicator),
      dateReadyOpt.getOrElse(DateTime.now()))
  }

  private def applicationRouteCriteria(appRoute: ApplicationRoute) = appRoute match {
    case ApplicationRoute.Faststream =>
      Document("$or" -> BsonArray(
        Document("applicationRoute" -> appRoute.toBson),
        Document("applicationRoute" -> Document("$exists" -> false))
      ))
    case _ => Document("applicationRoute" -> appRoute.toBson)
  }

  override def getApplicationRoute(applicationId: String): Future[ApplicationRoute] = {
    def error = throw ApplicationNotFound(s"No application found for $applicationId")
    val predicate = Document("applicationId" -> applicationId)
    val projection = Projections.include("applicationRoute")
    collection.find[Document](predicate).projection(projection).headOption().map {
      _.map { doc =>
        Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").getOrElse(error))
      }.getOrElse(error)
    }
  }

/*
def getLatestProgressStatuses: Future[List[String]] = {
  val projection = BSONDocument("_id" -> false, "progress-status-timestamp" -> 2)
  val query = BSONDocument()

  collection.find(query, Some(projection)).cursor[BSONDocument]()
    .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { doc =>
    doc.flatMap { item =>
      item.getAs[BSONDocument]("progress-status-timestamp").map {
        _.elements.toList.map { progressStatus =>
          progressStatus.name -> progressStatus.value.toString
        }.sortBy(tup => tup._2).reverse.head._1
      }
    }
  }
}*/
def getLatestProgressStatuses: Future[List[String]] = ???

/*
override def countByStatus(applicationStatus: ApplicationStatus): Future[Long] = {
  val query = Json.obj("applicationStatus" -> applicationStatus.toString)
  collection.count(
    selector = Some(query),
    limit = None,
    skip = 0,
    hint = None,
    readConcern = reactivemongo.api.ReadConcern.Local)
}*/
override def countByStatus(applicationStatus: ApplicationStatus): Future[Long] = ???

/*
def getProgressStatusTimestamps(applicationId: String): Future[List[(String, DateTime)]] = {

  //TODO Ian mongo 3.2 -> 3.4
  implicit object BSONDateTimeHandler extends BSONReader[BSONValue, DateTime] {
    def read(time: BSONValue) = time match {
      case BSONDateTime(value) => new DateTime(value, org.joda.time.DateTimeZone.UTC)
      case _ => throw new RuntimeException("Error trying to read date time value when processing progress status time stamps")
    }
  }

  val projection = BSONDocument("_id" -> false, "progress-status-timestamp" -> true)
  val query = BSONDocument("applicationId" -> applicationId)

  collection.find(query, Some(projection)).one[BSONDocument].map {
    case Some(doc) =>
      doc.getAs[BSONDocument]("progress-status-timestamp").map { timestamps =>
        timestamps.elements.toList.map { bsonElement =>
          bsonElement.name -> bsonElement.value.as[DateTime]
        }
      }.getOrElse(Nil)
    case _ => Nil
  }
}*/
def getProgressStatusTimestamps(applicationId: String): Future[List[(String, DateTime)]] = ???

/*
def updateCurrentSchemeStatus(applicationId: String, results: Seq[SchemeEvaluationResult]): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val updateBSON = BSONDocument("$set" -> currentSchemeStatusBSON(results))

  val validator = singleUpdateValidator(applicationId, actionDesc = s"Saving currentSchemeStatus for $applicationId")
  collection.update(ordered = false).one(query, updateBSON).map(validator)
}*/
//def updateCurrentSchemeStatus(applicationId: String, results: Seq[SchemeEvaluationResult]): Future[Unit] = ???
  override def updateCurrentSchemeStatus(applicationId: String, results: Seq[SchemeEvaluationResult]): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val updateBSON = Document("$set" -> currentSchemeStatusBSON(results))

    val validator = singleUpdateValidator(applicationId, actionDesc = s"Saving currentSchemeStatus for $applicationId")
    collection.updateOne(query, updateBSON).toFuture() map validator
  }

/*
override def removeCurrentSchemeStatus(applicationId: String): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  val update = BSONDocument("$unset" -> BSONDocument(s"currentSchemeStatus" -> ""))

  val validator = singleUpdateValidator(applicationId, actionDesc = s"removing current scheme status for $applicationId")
  collection.update(ordered = false).one(query, update).map(validator)
}*/
  override def removeCurrentSchemeStatus(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"currentSchemeStatus" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = s"removing current scheme status for $applicationId")
    collection.updateOne(query, update).toFuture().map(validator)
  }

/*
def findEligibleForJobOfferCandidatesWithFsbStatus: Future[Seq[String]] = {
  val query = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationStatus" -> BSONDocument("$eq" -> FSB.toString)),
    BSONDocument(s"progress-status.${ELIGIBLE_FOR_JOB_OFFER.toString}" -> BSONDocument("$exists" -> true))
  ))

  val projection = BSONDocument("applicationId" -> 1)

  collection.find(query, Some(projection)).cursor[BSONDocument]()
    .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docList =>
    docList.map { doc =>
      doc.getAs[String]("applicationId").get
    }
  }
}*/
def findEligibleForJobOfferCandidatesWithFsbStatus: Future[Seq[String]] = ???

/*
override def listCollections: Future[List[String]] = {
  mongoComponent.mongoConnector.db().collectionNames
}*/
override def listCollections: Future[List[String]] = ???

/*
override def removeCollection(name: String): Future[Unit] = {
  mongo().collection[JSONCollection](name).drop(failIfNotFound = true).map(_ => {})
}*/
override def removeCollection(name: String): Future[Unit] = ???

/*
override def removeCandidate(applicationId: String): Future[Unit] = {
  val query = BSONDocument("applicationId" -> applicationId)
  collection.delete().one(query, limit = Some(1)).map(_ => ())
}*/
  override def removeCandidate(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleRemovalValidator(applicationId, actionDesc = s"removing application")
    collection.deleteOne(query).toFuture().map(validator)
  }
}
