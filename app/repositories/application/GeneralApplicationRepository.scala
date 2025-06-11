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

package repositories.application

import config.MicroserviceAppConfig
import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.*
import model.Exceptions.*
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{EventProgressStatuses, PREVIEW}
import model.command.*
import model.exchange.{CandidateEligibleForEvent, CandidatesEligibleForEventResponse}
import model.persisted.*
import model.persisted.eventschedules.EventType
import model.persisted.eventschedules.EventType.EventType
import model.persisted.fsb.ScoresAndFeedback
import model.{ApplicationStatus, *}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonRegularExpression}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Sorts.ascending as sortAsc
import org.mongodb.scala.model.{IndexModel, IndexOptions, Projections}
import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture, bsonDocumentToDocument}
import repositories.*
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{AddMissingPhase2ResultReceived, PassToPhase1TestPassed, PassToPhase2, ResetPhase1TestInvitedSubmitted}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

// TODO FAST STREAM
// This is far too large an interface - we should look at splitting up based on
// functional concerns.

// scalastyle:off number.of.methods
trait GeneralApplicationRepository {
  def create(userId: String, frameworkId: String, applicationRoute: ApplicationRoute): Future[ApplicationResponse]
  def find(applicationId: String): Future[Option[Candidate]]
  def findAllFileInfo: Future[Seq[CandidateFileInfo]]
  def find(applicationIds: Seq[String]): Future[List[Candidate]]
  //TODO - 22/23 campaign this is only being added during live campaign to mitigate any unwanted side effects to changing the method above
  //TODO so remove at the end of the campaign and change the one above
  def findForReport(applicationIds: Seq[String]): Future[List[Candidate]]
  def findProgress(applicationId: String): Future[ProgressResponse]
  def findStatus(applicationId: String, excludeFsacAllocationStatuses: Boolean = false): Future[ApplicationStatusDetails]
  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse]
  def findCandidateByUserId(userId: String): Future[Option[Candidate]]
  def findByCriteria(firstOrPreferredName: Option[String], lastName: Option[String],
                     dateOfBirth: Option[LocalDate], userIds: List[String] = List.empty): Future[Seq[Candidate]]
  def submit(applicationId: String): Future[Unit]
  def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit]
  def withdrawScheme(applicationId: String, schemeWithdraw: WithdrawScheme, schemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
  def preview(applicationId: String): Future[Unit]
  def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit]
//  def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit]
//  def findAdjustments(applicationId: String): Future[Option[Adjustments]]
  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit]
  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment]
  def removeAdjustmentsComment(applicationId: String): Future[Unit]
  def findAdjustmentsNeedsSupportAtFsac(applicationId: String): Future[NeedsSupportAtFsac]
  def updateAdjustmentsNeedsSupportAtFsac(applicationId: String, needsSupportAtFsac: NeedsSupportAtFsac): Future[Unit]
  def findAdjustmentsNeedsSupportAtFsb(applicationId: String): Future[NeedsSupportAtFsb]
  def updateAdjustmentsNeedsSupportAtFsb(applicationId: String, needsSupportAtFsb: NeedsSupportAtFsb): Future[Unit]
  def gisByApplication(applicationId: String): Future[Boolean]
  def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit]
  def updateApplicationStatusOnly(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit]
  def updateSubmissionDeadline(applicationId: String, newDeadline: OffsetDateTime): Future[Unit]
  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]
  def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]
  def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit]
  def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]]
  def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]]
  def getApplicationsToFix(issue: FixBatch): Future[Seq[Candidate]]
  def fix(candidate: Candidate, issue: FixBatch): Future[Option[Candidate]]
  def fixDataByRemovingETray(appId: String): Future[Unit]
  def fixDataByRemovingVideoInterviewFailed(appId: String): Future[Unit]
  def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit]
  def updateApplicationRoute(appId: String, appRoute: ApplicationRoute, newAppRoute: ApplicationRoute): Future[Unit]
  def archive(appId: String, originalUserId: String, userIdToArchiveWith: String,
              frameworkId: String, appRoute: ApplicationRoute): Future[Unit]
  def findCandidatesEligibleForEventAllocation(locations: List[String], eventType: EventType,
                                               schemeId: Option[SchemeId]): Future[CandidatesEligibleForEventResponse]
  def resetApplicationAllocationStatus(applicationId: String, eventType: EventType): Future[Unit]
  def setFailedToAttendAssessmentStatus(applicationId: String, eventType: EventType): Future[Unit]
  def findAllocatedApplications(applicationIds: List[String]): Future[CandidatesEligibleForEventResponse]
  def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]]
  def findSdipFaststreamInvitedToVideoInterview: Future[Seq[Candidate]]
  def findSdipFaststreamExpiredPhase2InvitedToSift: Future[Seq[Candidate]]
  def findSdipFaststreamExpiredPhase3InvitedToSift: Future[Seq[Candidate]]
  def getApplicationRoute(applicationId: String): Future[ApplicationRoute]
  def getLatestProgressStatuses: Future[Seq[String]]
  def countByStatus(applicationStatus: ApplicationStatus): Future[Long]
  def getProgressStatusTimestamps(applicationId: String): Future[List[(String, OffsetDateTime)]]

  // Implemented by Hmrc ReactiveRepository class - don't use until it gets fixed. Use countLong instead
//  @deprecated("At runtime throws a JsResultException: errmsg=readConcern.level must be either 'local', 'majority' or 'linearizable'", "")
//  def count(implicit ec: scala.concurrent.ExecutionContext) : Future[Int] //TODO: fix

  // Implemented in ReactiveRepositoryHelpers
  def countLong(implicit ec: scala.concurrent.ExecutionContext) : Future[Long]
  def updateCurrentSchemeStatus(applicationId: String, results: Seq[SchemeEvaluationResult]): Future[Unit]
  def removeCurrentSchemeStatus(applicationId: String): Future[Unit]
  def removeWithdrawReason(applicationId: String): Future[Unit]
  def findEligibleForJobOfferCandidatesWithFsbStatus: Future[Seq[String]]
  def listCollections: Future[Seq[String]]
  def removeCollection(name: String): Future[Either[Exception, Unit]]
  def removeCandidate(applicationId: String): Future[Unit]
  def getApplicationStatusForCandidates(applicationIds: Seq[String]): Future[Seq[(String, ApplicationStatus)]]
}

// scalastyle:off number.of.methods
// scalastyle:off file.size.limit
@Singleton
class GeneralApplicationMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                                   appConfig: MicroserviceAppConfig,
                                                   mongo: MongoComponent
                                                  )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[CreateApplicationRequest](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = CreateApplicationRequest.createApplicationRequestFormat,
    indexes = Seq(
      IndexModel(ascending("applicationId", "userId"), IndexOptions().unique(true)),
      IndexModel(ascending("userId", "frameworkId"), IndexOptions().unique(true)),
      IndexModel(ascending("applicationStatus"), IndexOptions().unique(false)),
      IndexModel(ascending("assistance-details.needsSupportAtVenue"), IndexOptions().unique(false)),
      IndexModel(ascending("assistance-details.guaranteedInterview"), IndexOptions().unique(false))
    )
  ) with GeneralApplicationRepository with RandomSelection with CommonBSONDocuments
    with GeneralApplicationRepoBSONReader with ReactiveRepositoryHelpers with CurrentSchemeStatusHelper {

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val applicationResponseCollection: MongoCollection[ApplicationResponse] = {
    CollectionFactory.collection(
      collectionName = CollectionNames.APPLICATION,
      db = mongo.database,
      domainFormat = ApplicationResponse.mongoFormat
    )
  }

  // Use this collection when using hand written bson documents
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(CollectionNames.APPLICATION)

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
  }

  def findAllFileInfo: Future[Seq[CandidateFileInfo]] = {
    val query = Document("testGroups.FSAC.tests.analysisExercise" -> Document("$exists" -> true))
    val projection = Projections.include("applicationId", "testGroups.FSAC.tests.analysisExercise")
    collection.find[Document](query).projection(projection).toFuture().map { docs =>
      docs.map { doc =>
        val testGroups = doc.get("testGroups")
        val fsac = testGroups.map(_.asDocument().get("FSAC"))
        val tests = fsac.map(_.asDocument().get("tests"))
        val analysisExercise = tests.map(_.asDocument().get("analysisExercise"))

        CandidateFileInfo(
          doc.get("applicationId").get.asString().getValue,
          analysisExercise.map(_.asDocument().get("fileId").asString().getValue).get
        )
      }
    }
  }

  def find(applicationId: String): Future[Option[Candidate]] = {
    val query = Document("applicationId" -> applicationId)
    applicationCollection.find[BsonDocument](query).headOption().map {
      _.map { doc =>
        Codecs.fromBson[Candidate](doc)
      }
    }
  }

  override def find(applicationIds: Seq[String]): Future[List[Candidate]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    collection.find[BsonDocument](query).toFuture().map { _.map { doc =>
      Codecs.fromBson[Candidate](doc)
    }.toList }
  }

  override def findForReport(applicationIds: Seq[String]): Future[List[Candidate]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    collection.find[BsonDocument](query).toFuture().map { _.map { doc =>
      // This is the only difference to the impl above and should be used as it reads more data
      Candidate.fromBson(doc)
    }.toList }
  }

  override def findProgress(applicationId: String): Future[ProgressResponse] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("progress-status")

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(document) => toProgressResponse(applicationId)(document)
      case None => throw ApplicationNotFound(s"No application found for $applicationId")
    }
  }

  override def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]] = {
    val projection = Projections.include("currentSchemeStatus")
    collection.find[Document](Document("applicationId" -> applicationId)).projection(projection).headOption().map {
      _.map { doc =>
        doc.get("currentSchemeStatus").map { bsonValue =>
          Codecs.fromBson[List[SchemeEvaluationResult]](bsonValue)
        }.getOrElse(Nil)
      }.getOrElse(Nil)
    }
  }

  def findStatus(applicationId: String, excludeFsacAllocationStatuses: Boolean = false): Future[ApplicationStatusDetails] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include(
      "applicationStatus",
      "progress-status-timestamp",
      "progress-status-dates",
      "applicationRoute",
      "submissionDeadline",
    )

    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat

    import scala.jdk.CollectionConverters.*

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
        val applicationRoute = Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").getOrElse(ApplicationRoute.Faststream.toBson))
        val progressStatusTimeStampDoc = doc.get("progress-status-timestamp").map(_.asDocument())

        val fsacAllocationProgressStatuses = Seq(
          ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED.toString,
          ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED.toString
        )

        val latest = progressStatusTimeStampDoc.map { timestamps =>
          val convertedTimestamps = timestamps.entrySet().asScala.toSet
          val relevantProgressStatuses = if (excludeFsacAllocationStatuses) {
            convertedTimestamps.filter( ps => ps.getKey.startsWith(applicationStatus) && !fsacAllocationProgressStatuses.contains(ps.getKey) )
          } else {
            convertedTimestamps.filter( _.getKey.startsWith(applicationStatus) )
          }
          val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element =>
            Codecs.fromBson[OffsetDateTime](timestamps.get(element.getKey))
          )
          val progressStatus = Try(ProgressStatuses.nameToProgressStatus(latestRelevantProgressStatus.getKey)).toOption
          val timestamp = Try(Codecs.fromBson[OffsetDateTime](timestamps.get(latestRelevantProgressStatus.getKey))).toOption
          progressStatus -> timestamp
        }

        val (latestProgressStatus, latestProgressStatusTimeStamp) = latest match {
          case Some(data) => data
          case _ => None -> None // Tuple with 2 None values
        }

        val submissionDeadline = doc.get("submissionDeadline").map( sd => Codecs.fromBson[OffsetDateTime](sd) )

        ApplicationStatusDetails(applicationStatus, applicationRoute, latestProgressStatus, latestProgressStatusTimeStamp, submissionDeadline)

      case None => throw ApplicationNotFound(applicationId)
    }
  }

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

        import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat
        val submissionDeadline = doc.get("submissionDeadline").map { bsonValue =>
          Codecs.fromBson[OffsetDateTime](bsonValue.asDateTime())
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

  def findCandidateByUserId(userId: String): Future[Option[Candidate]] = {
    val query = Document("userId" -> userId)
    collection.find[BsonDocument](query).headOption().map( _.map( bson => Candidate.fromBson(bson) ))
  }

  override def findByCriteria(firstOrPreferredNameOpt: Option[String],
                              lastNameOpt: Option[String],
                              dateOfBirthOpt: Option[LocalDate],
                              filterByUserIds: List[String]
                    ): Future[Seq[Candidate]] = {

    def matchIfSome(value: Option[String]) = value.map(v => BsonRegularExpression("^" + Pattern.quote(v) + "$", "i"))

    // If the search criteria is None then we specifically need to not include it in the filter otherwise the driver processes the None
    // value as null eg. "filter": {"personal-details.lastName": null, "personal-details.dateOfBirth": null... and no data is fetched
    val lastNameBson = matchIfSome(lastNameOpt).map( v => Document("personal-details.lastName" -> v) ).getOrElse(Document.empty)
    val dobBson = dateOfBirthOpt.map( v => Document("personal-details.dateOfBirth" -> v.toString) ).getOrElse(Document.empty)
    val firstNameBson = matchIfSome(firstOrPreferredNameOpt).map( v => Document("personal-details.firstName" -> v) ).getOrElse(Document.empty)
    val preferredNameBson = matchIfSome(firstOrPreferredNameOpt).map( v => Document("personal-details.preferredName" -> v) )
      .getOrElse(Document.empty)

    val innerQuery =
      Document("$or" -> BsonArray(
        firstNameBson,
        preferredNameBson
      )) ++
      Document("$and" -> BsonArray(
        lastNameBson,
        dobBson
      ))

    val query = if (filterByUserIds.isEmpty) {
      innerQuery
    } else {
      Document("userId" -> Document("$in" -> filterByUserIds)) ++ innerQuery
    }

    val projection = Projections.include("userId", "applicationId", "applicationRoute", "applicationStatus", "personal-details")

    applicationCollection.find[BsonDocument](query).projection(projection).toFuture().map { _.map {
      doc => Candidate.fromBson(doc)
    }}
  }

  override def findSdipFaststreamInvitedToVideoInterview: Future[Seq[Candidate]] = {
    val query = Document(
      "applicationRoute" -> ApplicationRoute.SdipFaststream.toBson,
      s"progress-status.${ProgressStatuses.PHASE3_TESTS_INVITED}" -> Document("$exists" -> true)
    )

    val projection = Projections.include("userId", "applicationId", "applicationRoute",
      "applicationStatus", "personal-details")

    applicationCollection.find[BsonDocument](query).projection(projection).toFuture().map { _.map {
      doc => Candidate.fromBson(doc)
    }}
  }

  override def findSdipFaststreamExpiredPhase2InvitedToSift: Future[Seq[Candidate]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationRoute" -> ApplicationRoute.SdipFaststream.toBson),
      Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
      Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_EXPIRED}" -> Document("$exists" -> true)),
      Document(s"testGroups.PHASE1.evaluation.result" -> Document("$elemMatch" ->
        Document("schemeId" -> "Sdip", "result" -> EvaluationResults.Green.toString)
      ))
    ))

    val projection = Projections.include("userId", "applicationId", "applicationRoute",
      "applicationStatus", "personal-details")

    applicationCollection.find[BsonDocument](query).projection(projection).toFuture().map { _.map {
      doc => Candidate.fromBson(doc)
    }}
  }

  override def findSdipFaststreamExpiredPhase3InvitedToSift: Future[Seq[Candidate]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationRoute" -> ApplicationRoute.SdipFaststream.toBson),
      Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
      Document(s"progress-status.${ProgressStatuses.PHASE3_TESTS_EXPIRED}" -> Document("$exists" -> true)),
      Document(s"testGroups.PHASE2.evaluation.result" -> Document("$elemMatch" ->
        Document("schemeId" -> "Sdip", "result" -> EvaluationResults.Green.toString)
      ))
    ))

    val projection = Projections.include("userId", "applicationId", "applicationRoute",
      "applicationStatus", "personal-details")

    applicationCollection.find[BsonDocument](query).projection(projection).toFuture().map { _.map {
      doc => Candidate.fromBson(doc)
    }}
  }

  override def submit(applicationId: String): Future[Unit] = {
    val guard = progressStatusGuardBSON(PREVIEW)
    val query = Document("applicationId" -> applicationId) ++ guard

    val updateBSON = Document("$set" -> applicationStatusBSON(SUBMITTED))

    val validator = singleUpdateValidator(applicationId, actionDesc = "submitting",
      new IllegalStateException(s"Already submitted $applicationId"))

    collection.updateOne(query, updateBSON).toFuture() map validator
  }

  override def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val applicationBSON = Document("$set" -> (Document("withdraw.application" -> reason.toBson) ++ applicationStatusBSON(WITHDRAWN)))

    val validator = singleUpdateValidator(applicationId, actionDesc = "withdrawing application")
    collection.updateOne(query, applicationBSON).toFuture() map validator
  }

  override def removeWithdrawReason(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document("withdraw.application" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing withdrawal reason")

    collection.updateOne(query, update).toFuture() map validator
  }

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

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val progressStatusBSON = Document("$set" -> Document(
      s"progress-status.questionnaire.$sectionKey" -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "update questionnaire status")

    collection.updateOne(query, progressStatusBSON).toFuture() map validator
  }

  override def preview(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val progressStatusBSON = Document("$set" -> Document(
      "progress-status.preview" -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "preview",
      CannotUpdatePreview(s"preview $applicationId"))

    collection.updateOne(query, progressStatusBSON).toFuture() map validator
  }

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
      result <- selectOneRandom[TestResultNotification](query)( doc => TestResultNotification.fromBson(doc), ec )
    } yield result
  }

  override def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationRoute" -> notificationType.applicationRoute.toBson),
      Document(s"progress-status.${notificationType.progressStatus}" -> true),
      Document(s"progress-status.${notificationType.notificationProgress}" -> Document("$ne" -> true))
    ))

    selectOneRandom[TestResultSdipFsNotification](query)( doc => TestResultSdipFsNotification.fromBson(doc), ec )
  }

  //scalastyle:off method.length
  override def getApplicationsToFix(issue: FixBatch): Future[Seq[Candidate]] = {
    issue.fix match {
      case PassToPhase2 =>
        val query = Document("$and" -> BsonArray(
          Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> true)
        ))

        selectRandom[Candidate](query, issue.batchSize)(doc => Codecs.fromBson[Candidate](doc.toBsonDocument()), ec)
      case PassToPhase1TestPassed =>
        val query = Document("$and" -> BsonArray(
          Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          Document(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> Document("$ne" -> true))
        ))

        selectRandom[Candidate](query, issue.batchSize)(doc => Codecs.fromBson[Candidate](doc.toBsonDocument()), ec)
      case ResetPhase1TestInvitedSubmitted =>
        val query = Document("$and" -> BsonArray(
          Document("applicationStatus" -> ApplicationStatus.SUBMITTED.toBson),
          Document(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
        ))

        selectRandom[Candidate](query, issue.batchSize)(doc => Codecs.fromBson[Candidate](doc.toBsonDocument()), ec)
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

        selectRandom[Candidate](query, issue.batchSize)(doc => Codecs.fromBson[Candidate](doc.toBsonDocument()), ec)
    }
  }//scalastyle:on

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
          Document(
            s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
            s"progress-status-timestamp.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
            "testGroups" -> ""
          )
        )

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
        val updateOp = Document("$set" ->
          Document(
            s"progress-status.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> true,
            s"progress-status-timestamp.${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}" -> offsetDateTimeToBson(OffsetDateTime.now)
          ))

        collection.updateOne(query, updateOp).toFuture().flatMap { _ =>
          collection.find[BsonDocument](Document("applicationId" -> application.applicationId)).headOption().map { _.map { doc =>
            Codecs.fromBson[Candidate](doc)
          }}
        }
    }
  }

  override def fixDataByRemovingETray(appId: String): Future[Unit] = {
    import ProgressStatuses.*

    val query = Document(
      "applicationId" -> appId,
      "applicationStatus" -> ApplicationStatus.PHASE2_TESTS.toBson
    )

    val updateOp =
      Document(
        "$set" -> Document("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED.toBson),
        "$unset" -> Document(
          s"progress-status.${PHASE2_TESTS_INVITED.key}" -> "",
          s"progress-status.${PHASE2_TESTS_STARTED.key}" -> "",
          s"progress-status.${PHASE2_TESTS_FIRST_REMINDER.key}" -> "",
          s"progress-status.${PHASE2_TESTS_SECOND_REMINDER.key}" -> "",
          s"progress-status.${PHASE2_TESTS_COMPLETED.key}" -> "",
          s"progress-status.${PHASE2_TESTS_EXPIRED.key}" -> "",
          s"progress-status.${PHASE2_TESTS_RESULTS_RECEIVED.key}" -> "",
          s"progress-status.${PHASE2_TESTS_PASSED.key}" -> "",
          s"progress-status.${PHASE2_TESTS_FAILED.key}" -> "",
          s"progress-status.${PHASE2_TESTS_FAILED_NOTIFIED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_INVITED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_STARTED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_FIRST_REMINDER.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_SECOND_REMINDER.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_COMPLETED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_EXPIRED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_RESULTS_RECEIVED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_PASSED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_FAILED.key}" -> "",
          s"progress-status-timestamp.${PHASE2_TESTS_FAILED_NOTIFIED.key}" -> "",
          s"testGroups.PHASE2" -> ""
        )
      )

    collection.updateOne(query, updateOp).toFuture().map(_ => ())
  }

  override def fixDataByRemovingVideoInterviewFailed(appId: String): Future[Unit] = {
    import ProgressStatuses.*

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

  def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit] = {
    val query = Document(
      "applicationId" -> appId,
      s"progress-status.$progressStatus" -> true
    )
    val updateOp = Document(
      "$unset" -> Document(
        s"progress-status.$progressStatus" -> "",
        s"progress-status-timestamp.$progressStatus" -> ""
      )
    )

    collection.updateOne(query, updateOp).toFuture().map(_ => ())
  }

  private[application] def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

  /*
  override def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val resetExerciseAdjustmentsBSON = Document("$unset" -> Document(
      "assistance-details.etray" -> "",
      "assistance-details.video" -> ""
    ))

    val adjustmentsConfirmationBSON = Document("$set" -> Document(
      "assistance-details.typeOfAdjustments" -> data.adjustments.getOrElse(List.empty[String]),
      "assistance-details.adjustmentsConfirmed" -> true,
      "assistance-details.etray" -> Codecs.toBson(data.etray),
      "assistance-details.video" -> Codecs.toBson(data.video)
    ))

    val resetValidator = singleUpdateValidator(applicationId, actionDesc = "reset adjustments")
    val adjustmentValidator = singleUpdateValidator(applicationId, actionDesc = "update adjustments")

    collection.updateOne(query, resetExerciseAdjustmentsBSON).toFuture().map(resetValidator).flatMap { _ =>
      collection.updateOne(query, adjustmentsConfirmationBSON).toFuture() map adjustmentValidator
    }
  }
   */

  /*
  override def findAdjustments(applicationId: String): Future[Option[Adjustments]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document("assistance-details" -> Document("$exists" -> true))
    ))
    val projection = Projections.include("assistance-details")

    collection.find[Document](query).projection(projection).headOption().map { docOpt =>
      docOpt.map { document =>
        val rootOpt = document.get("assistance-details")
        val adjustmentList = rootOpt.flatMap( bson => Try(Codecs.fromBson[List[String]](bson.asDocument().get("typeOfAdjustments"))).toOption )
        val adjustmentsConfirmed = rootOpt.flatMap( bson => Try(bson.asDocument.get("adjustmentsConfirmed").asBoolean().getValue).toOption )
        val etray = rootOpt.flatMap( bson => Try(Codecs.fromBson[AdjustmentDetail](bson.asDocument().get("etray")) ).toOption )
        val video = rootOpt.flatMap( bson => Try(Codecs.fromBson[AdjustmentDetail](bson.asDocument().get("video")) ).toOption )
        Adjustments(adjustmentList, adjustmentsConfirmed, etray, video)
      }
    }
  }
   */

  // Note that this should be successful as long as we match an existing document even if that document
  // does not actually have the adjustmentsComment stored within it
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

  override def findAdjustmentsNeedsSupportAtFsac(applicationId: String): Future[NeedsSupportAtFsac] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("assistance-details")

    collection.find[Document](query).projection(projection).headOption().map {
      case Some(document) =>
        val root = document.get("assistance-details").map(_.asDocument())
        root match {
          case Some(doc) =>
            val needsSupportAtVenue = Try(doc.get("needsSupportAtVenue").asBoolean().getValue).getOrElse(false)
            val needsSupportAtVenueDescription = Try(doc.get("needsSupportAtVenueDescription").asString().getValue).toOption
            NeedsSupportAtFsac(needsSupportAtVenue, needsSupportAtVenueDescription)
          case None => throw AdjustmentsNeedsSupportAtFsacNotFound(applicationId)
        }
      case None =>
        throw ApplicationNotFound(s"No application found when looking for adjustments needs support at FSAC for $applicationId")
    }
  }

  override def updateAdjustmentsNeedsSupportAtFsac(applicationId: String, needsSupportAtFsac: NeedsSupportAtFsac): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val updateBSON = needsSupportAtFsac.needsSupportAtVenue match {
      case true =>
        Document("$set" -> Document(
          "assistance-details.needsSupportAtVenue" -> needsSupportAtFsac.needsSupportAtVenue,
          "assistance-details.needsSupportAtVenueDescription" -> needsSupportAtFsac.needsSupportAtVenueDescription,
        ))
      case _ =>
        Document("$set" -> Document(
          "assistance-details.needsSupportAtVenue" -> needsSupportAtFsac.needsSupportAtVenue
        )) ++
          Document("$unset" -> Document(
            "assistance-details.needsSupportAtVenueDescription" -> ""
          ))
    }

    val validator = singleUpdateValidator(applicationId,
      actionDesc = "save adjustments needs support at venue",
      error = CannotUpdateRecord(applicationId))

    collection.updateOne(query, updateBSON).toFuture() map validator
  }

  override def findAdjustmentsNeedsSupportAtFsb(applicationId: String): Future[NeedsSupportAtFsb] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("assistance-details")

    collection.find[Document](query).projection(projection).headOption().map {
      case Some(document) =>
        val root = document.get("assistance-details").map(_.asDocument())
        root match {
          case Some(doc) =>
            val needsSupportForPhoneInterview = Try(doc.get("needsSupportForPhoneInterview").asBoolean().getValue).getOrElse(false)
            val needsSupportForPhoneInterviewDescription = Try(doc.get("needsSupportForPhoneInterviewDescription").asString().getValue).toOption
            NeedsSupportAtFsb(needsSupportForPhoneInterview, needsSupportForPhoneInterviewDescription)
          case None => throw AdjustmentsNeedsSupportAtFsbNotFound(applicationId)
        }
      case None =>
        throw ApplicationNotFound(s"No application found when looking for adjustments needs support at phone interview for $applicationId")
    }
  }

  override def updateAdjustmentsNeedsSupportAtFsb(applicationId: String, needsSupportAtFsb: NeedsSupportAtFsb): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val updateBSON = needsSupportAtFsb.needsSupportForPhoneInterview match {
      case true =>
        Document("$set" -> Document(
          "assistance-details.needsSupportForPhoneInterview" -> needsSupportAtFsb.needsSupportForPhoneInterview,
          "assistance-details.needsSupportForPhoneInterviewDescription" -> needsSupportAtFsb.needsSupportForPhoneInterviewDescription,
        ))
      case _ =>
        Document("$set" -> Document(
          "assistance-details.needsSupportForPhoneInterview" -> needsSupportAtFsb.needsSupportForPhoneInterview
        )) ++
          Document("$unset" -> Document(
            "assistance-details.needsSupportForPhoneInterviewDescription" -> ""
          ))
    }

    val validator = singleUpdateValidator(applicationId,
      actionDesc = "save adjustments needs support for phone interview",
      error = CannotUpdateRecord(applicationId))

    collection.updateOne(query, updateBSON).toFuture() map validator
  }

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

  def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating application status")

    collection.updateOne(query, Document("$set" -> applicationStatusBSON(applicationStatus))).toFuture() map validator
  }

  def updateApplicationStatusOnly(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val updateOp = Document("$set" -> Document("applicationStatus" -> applicationStatus.toBson))
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating application status")

    collection.updateOne(query, updateOp).toFuture() map validator
  }

  override def updateSubmissionDeadline(applicationId: String, newDeadline: OffsetDateTime): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating submission deadline")

    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat
    collection.updateOne(query, Document("$set" -> Document("submissionDeadline" -> Codecs.toBson(newDeadline)))).toFuture() map validator
  }

  override def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
    val query = Document("applicationId" -> appId)
    collection.find[Document](query).headOption() map {
      _.map(bsonDocToOnlineTestApplication)
    }
  }

  override def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating progress and app status")

    collection.updateOne(query, Document("$set" ->
      applicationStatusBSON(progressStatus))
    ).toFuture() map validator
  }

  override def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty, "Progress statuses to remove cannot be empty")

    val query = Document("applicationId" -> applicationId)

    val statusesToUnset = progressStatuses.map { progressStatus =>
      Map(
        s"progress-status.$progressStatus" -> "",
        s"progress-status-dates.$progressStatus" -> "", // TODO: mongo looks like an out-of-date key!!
        s"progress-status-timestamp.$progressStatus" -> ""
      )
    }

    // Fold the list of maps into one map
    val foldedStatuses = statusesToUnset.foldLeft(Map.empty[String, String])((acc, v) => acc ++ v)
    val update = Document("$unset" -> Document(foldedStatuses))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing progress statuses")
    collection.updateOne(query, update).toFuture() map validator
  }

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

  override def findAllocatedApplications(applicationIds: List[String]): Future[CandidatesEligibleForEventResponse] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.include(
      "applicationId",
      "personal-details.firstName",
      "personal-details.lastName",
      "assistance-details.needsSupportAtVenue",
      "progress-status-timestamp",
      "fsac-indicator",
      "testGroups.FSB.scoresAndFeedback"
    )

    collection.find[Document](query).projection(projection).toFuture()
      .map { _.map { doc => bsonDocToCandidatesEligibleForEvent(doc) }.toList
      }.flatMap { result =>
      Future.successful(CandidatesEligibleForEventResponse(result, -1))
    }
  }

  private def countDocuments(query: Document) = {
    collection.find(query).toFuture()
      .map( _.size )
  }

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
          "progress-status-timestamp",
          "fsac-indicator"
        )

        // Eligible candidates should be sorted based on when they passed PHASE 3
        val sort = sortAsc(s"progress-status-timestamp.${ApplicationStatus.PHASE3_TESTS_PASSED}")

        collection.find[Document](query).projection(projection).sort(sort).limit(appConfig.eventsConfig.maxNumberOfCandidates).toFuture()
          .map { _.map { doc => bsonDocToCandidatesEligibleForEvent(doc) }.toList
          }.flatMap { result =>
          Future.successful(CandidatesEligibleForEventResponse(result, count))
        }
      }
    }
  }

  override def resetApplicationAllocationStatus(applicationId: String, eventType: EventType): Future[Unit] = {
    replaceAllocationStatus(applicationId, EventProgressStatuses.get(eventType.applicationStatus).awaitingAllocation)
  }

  override def setFailedToAttendAssessmentStatus(applicationId: String, eventType: EventType): Future[Unit] = {
    replaceAllocationStatus(applicationId, EventProgressStatuses.get(eventType.applicationStatus).failedToAttend)
  }

  import ProgressStatuses.*

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

  private def bsonDocToCandidatesEligibleForEvent(doc: Document) = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val personalDetailsRoot = doc.get("personal-details").map(_.asDocument()).get
    val firstName = personalDetailsRoot.get("firstName").asString().getValue
    val lastName = personalDetailsRoot.get("lastName").asString().getValue

    val assistanceDetailsRoot = doc.get("assistance-details").map(_.asDocument()).get
    val needsSupportAtVenue = Try(assistanceDetailsRoot.get("needsSupportAtVenue").asBoolean().getValue).getOrElse(false)

    val needsAdjustment = needsSupportAtVenue

    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat
    val dateReadyOpt = doc.get("progress-status-timestamp").map{ _.asDocument().get(ApplicationStatus.PHASE3_TESTS_PASSED) }.flatMap {
      bson => Try(Codecs.fromBson[OffsetDateTime](bson)).toOption
    }

    val fsacIndicator = Codecs.fromBson[model.persisted.FSACIndicator](doc.get("fsac-indicator").get)

    val fsbOpt = Try{
      doc.get("testGroups").map( _.asDocument().get("FSB").asDocument() )
    }.toOption.flatten

    val scoresAndFeedbackOpt = fsbOpt.flatMap( fsb => Try(Codecs.fromBson[ScoresAndFeedback](fsb.get("scoresAndFeedback"))).toOption )

    val fsbScoresAndFeedbackSubmitted = scoresAndFeedbackOpt.isDefined

    CandidateEligibleForEvent(
      applicationId,
      firstName,
      lastName,
      needsAdjustment,
      fsbScoresAndFeedbackSubmitted,
      model.FSACIndicator(fsacIndicator),
      dateReadyOpt.getOrElse(OffsetDateTime.now))
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

  override def getLatestProgressStatuses: Future[Seq[String]] = {

    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat

    import scala.jdk.CollectionConverters.*

    val query = Document.empty
    val projection = Projections.include("progress-status-timestamp")
    collection.find[Document](query).projection(projection).toFuture().map {
      _.map { doc =>
        doc.get("progress-status-timestamp").map { timestamps =>
          val convertedTimestamps = timestamps.asDocument().entrySet().asScala.toSet
          convertedTimestamps.map { element =>
            element.getKey -> Codecs.fromBson[OffsetDateTime](element.getValue)
          }.toList
        }.getOrElse(Nil).sortBy(tup => tup._2).reverse.head._1
      }
    }
  }

  override def countByStatus(applicationStatus: ApplicationStatus): Future[Long] = {
    val query = Document("applicationStatus" -> applicationStatus.toString)
    collection.countDocuments(query).head()
  }

  override def getProgressStatusTimestamps(applicationId: String): Future[List[(String, OffsetDateTime)]] = {
    val query = Document("applicationId" -> applicationId)
    val projection = Projections.include("progress-status-timestamp")

    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat

    import scala.jdk.CollectionConverters.*

    collection.find[Document](query).projection(projection).headOption().map {
      case Some(doc) =>
        doc.get("progress-status-timestamp").map { timestamps =>
          val convertedTimestamps = timestamps.asDocument().entrySet().asScala.toSet
          convertedTimestamps.map { element =>
            element.getKey -> Codecs.fromBson[OffsetDateTime](element.getValue)
          }.toList
        }.getOrElse(Nil)
      case _ => Nil
    }
  }

  override def updateCurrentSchemeStatus(applicationId: String, results: Seq[SchemeEvaluationResult]): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val updateBSON = Document("$set" -> currentSchemeStatusBSON(results))

    val validator = singleUpdateValidator(applicationId, actionDesc = s"Saving currentSchemeStatus for $applicationId")
    collection.updateOne(query, updateBSON).toFuture() map validator
  }

  override def removeCurrentSchemeStatus(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(s"currentSchemeStatus" -> ""))

    val validator = singleUpdateValidator(applicationId, actionDesc = s"removing current scheme status for $applicationId")
    collection.updateOne(query, update).toFuture().map(validator)
  }

  def findEligibleForJobOfferCandidatesWithFsbStatus: Future[Seq[String]] = {
    val query = Document("$and" -> BsonArray(
      Document("applicationStatus" -> Document("$eq" -> FSB.toString)),
      Document(s"progress-status.${ELIGIBLE_FOR_JOB_OFFER.toString}" -> Document("$exists" -> true))
    ))
    val projection = Projections.include("applicationId")

    collection.find[Document](query).projection(projection).toFuture().map { docList =>
      docList.map { doc =>
        doc.get("applicationId").get.asString().getValue
      }
    }
  }

  override def listCollections: Future[Seq[String]] = {
    mongo.database.listCollectionNames().toFuture()
  }

  override def removeCollection(name: String): Future[Either[Exception, Unit]] = {
    val collectionExists = for {
      collections <- listCollections
    } yield collections.contains(name)

    collectionExists.flatMap { exists =>
      if (exists) {
        mongo.database.getCollection(name).drop().toFuture().map ( _ => Right(()) )
      } else {
        Future.successful(Left( new Exception(s"Collection not found: $name")))
      }
    }
  }

  override def removeCandidate(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val validator = singleRemovalValidator(applicationId, actionDesc = s"removing candidate $applicationId")
    collection.deleteOne(query).toFuture().map(validator)
  }
}
