/*
 * Copyright 2017 HM Revenue & Customs
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
import config.CubiksGatewayConfig
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.Commands._
import model.EvaluationResults._
import model.Exceptions._
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.PREVIEW
import model.command._
import model.persisted._
import model.{ ApplicationStatus, _ }
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Format, JsNumber, JsObject }
import reactivemongo.api.{ DB, QueryOpts, ReadPreference }
import reactivemongo.bson.{ BSONDocument, document, _ }
import reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand
import repositories._
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{ AddMissingPhase2ResultReceived, PassToPhase1TestPassed, PassToPhase2, ResetPhase1TestInvitedSubmitted }
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

// TODO FAST STREAM
// This is far too large an interface - we should look at splitting up based on
// functional concerns.

// scalastyle:off number.of.methods
trait GeneralApplicationRepository {

  def create(userId: String, frameworkId: String, applicationRoute: ApplicationRoute): Future[ApplicationResponse]

  def find(applicationId: String): Future[Option[Candidate]]

  def find(applicationIds: List[String]): Future[List[Candidate]]

  def findProgress(applicationId: String): Future[ProgressResponse]

  def findStatus(applicationId: String): Future[ApplicationStatusDetails]

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse]

  def findCandidateByUserId(userId: String): Future[Option[Candidate]]

  def findByCriteria(firstOrPreferredName: Option[String], lastName: Option[String],
    dateOfBirth: Option[LocalDate], userIds: List[String] = List.empty): Future[List[Candidate]]

  def findApplicationIdsByLocation(location: String): Future[List[String]]

  def submit(applicationId: String): Future[Unit]

  def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit]

  def preview(applicationId: String): Future[Unit]

  def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit]

  def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit]

  def findAdjustments(applicationId: String): Future[Option[Adjustments]]

  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit]

  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment]

  def removeAdjustmentsComment(applicationId: String): Future[Unit]

  def rejectAdjustment(applicationId: String): Future[Unit]

  def gisByApplication(applicationId: String): Future[Boolean]

  def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]]

  def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit]

  def updateSubmissionDeadline(applicationId: String, newDeadline: DateTime): Future[Unit]

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]

  def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]

  def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit]

  def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]]

  def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]]

  def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]]

  def fix(candidate: Candidate, issue: FixBatch): Future[Option[Candidate]]

  def fixDataByRemovingETray(appId: String): Future[Unit]

  def fixDataByRemovingVideoInterviewFailed(appId: String): Future[Unit]

  def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit]

  def updateApplicationRoute(appId: String, appRoute: ApplicationRoute, newAppRoute: ApplicationRoute): Future[Unit]

  def archive(appId: String, originalUserId: String, userIdToArchiveWith: String,
    frameworkId: String, appRoute: ApplicationRoute): Future[Unit]
}

// scalastyle:off number.of.methods
// scalastyle:off file.size.limit
class GeneralApplicationMongoRepository(
  timeZoneService: TimeZoneService,
  gatewayConfig: CubiksGatewayConfig
)(implicit mongo: () => DB)
    extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
      Commands.Implicits.createApplicationRequestFormat,
      ReactiveMongoFormats.objectIdFormats) with GeneralApplicationRepository with RandomSelection with CommonBSONDocuments
    with GeneralApplicationRepoBSONReader with ReactiveRepositoryHelpers {

  override def create(userId: String, frameworkId: String, route: ApplicationRoute): Future[ApplicationResponse] = {
    val applicationId = UUID.randomUUID().toString
    val applicationBSON = BSONDocument(
      "applicationId" -> applicationId,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> CREATED,
      "applicationRoute" -> route
    )
    collection.insert(applicationBSON) flatMap { _ =>
      findProgress(applicationId).map { p =>
        ApplicationResponse(applicationId, CREATED, route, userId, p, None, None)
      }
    }
  }

  def find(applicationId: String): Future[Option[Candidate]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    bsonCollection.find(query).one[Candidate]
  }

  def find(applicationIds: List[String]): Future[List[Candidate]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    bsonCollection.find(query).cursor[Candidate]().collect[List]()
  }

  override def findProgress(applicationId: String): Future[ProgressResponse] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("progress-status" -> 2, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) => toProgressResponse(applicationId).read(document)
      case None => throw ApplicationNotFound(applicationId)
    }
  }

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

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) =>
        val applicationStatus = document.getAs[ApplicationStatus]("applicationStatus").get
        val applicationRoute = document.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
        val progressStatusTimeStampDoc = document.getAs[BSONDocument]("progress-status-timestamp")
        val progressStatusTimeStamp = progressStatusTimeStampDoc.flatMap { timestamps =>
          val relevantProgressStatuses = timestamps.elements.filter(_._1.startsWith(applicationStatus))

          if (relevantProgressStatuses.nonEmpty) {
            val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element => timestamps.getAs[DateTime](element._1).get)
            timestamps.getAs[DateTime](latestRelevantProgressStatus._1)
          } else {
            progressStatusDateFallback(applicationStatus, document)
          }
        }
          .orElse(
            progressStatusDateFallback(applicationStatus, document)
          )
        val submissionDeadline = document.getAs[DateTime]("submissionDeadline")
        ApplicationStatusDetails(applicationStatus, applicationRoute, progressStatusTimeStamp, submissionDeadline)

      case None => throw ApplicationNotFound(applicationId)
    }
  }

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val query = BSONDocument("userId" -> userId, "frameworkId" -> frameworkId)

    collection.find(query).one[BSONDocument] flatMap {
      case Some(document) =>
        val applicationId = document.getAs[String]("applicationId").get
        val applicationStatus = document.getAs[ApplicationStatus]("applicationStatus").get
        val applicationRoute = document.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
        val fastPassReceived = document.getAs[CivilServiceExperienceDetails]("civil-service-experience-details")
        val submissionDeadline = document.getAs[DateTime]("submissionDeadline")
        findProgress(applicationId).map { progress =>
          ApplicationResponse(applicationId, applicationStatus, applicationRoute, userId, progress, fastPassReceived, submissionDeadline)
        }
      case None => throw ApplicationNotFound(userId)
    }
  }

  def findCandidateByUserId(userId: String): Future[Option[Candidate]] = {
    val query = BSONDocument("userId" -> userId)
    bsonCollection.find(query).one[Candidate]
  }

  def findByCriteria(
    firstOrPreferredNameOpt: Option[String],
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

    bsonCollection.find(query, projection).cursor[Candidate]().collect[List]()
  }

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

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map { docList =>
      docList.map { doc =>
        doc.getAs[String]("applicationId").get
      }
    }
  }

  override def submit(applicationId: String): Future[Unit] = {
    val guard = progressStatusGuardBSON(PREVIEW)
    val query = BSONDocument("applicationId" -> applicationId) ++ guard

    val updateBSON = BSONDocument("$set" -> applicationStatusBSON(SUBMITTED))

    val validator = singleUpdateValidator(applicationId, actionDesc = "submitting",
      new IllegalStateException(s"Already submitted $applicationId"))

    collection.update(query, updateBSON) map validator
  }

  override def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationBSON = BSONDocument("$set" -> BSONDocument(
      "withdraw" -> reason
    ).add(
        applicationStatusBSON(WITHDRAWN)
      ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "withdrawing")

    collection.update(query, applicationBSON) map validator
  }

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val progressStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.questionnaire.$sectionKey" -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "update questionnaire status")

    collection.update(query, progressStatusBSON) map validator
  }

  override def preview(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val progressStatusBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.preview" -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "preview",
      CannotUpdatePreview(s"preview $applicationId"))

    collection.update(query, progressStatusBSON) map validator
  }

  override def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]] = {
    val query = Try {
      notificationType match {
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
      }
    }

    implicit val reader = bsonReader(TestResultNotification.fromBson)
    for {
      q <- Future.fromTry(query)
      result <- selectOneRandom[TestResultNotification](q)
    } yield result

  }

  def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationRoute" -> notificationType.applicationRoute),
      BSONDocument(s"progress-status.${notificationType.progressStatus}" -> true),
      BSONDocument(s"progress-status.${notificationType.notificationProgress}" -> BSONDocument("$ne" -> true))
    ))

    implicit val reader = bsonReader(TestResultSdipFsNotification.fromBson)
    selectOneRandom[TestResultSdipFsNotification](query)
  }

  // scalastyle:off method.length
  private def applicationPreferences(query: BSONDocument): Future[List[ApplicationPreferences]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "framework-preferences.alternatives.location" -> "1",
      "framework-preferences.alternatives.framework" -> "1",
      "framework-preferences.firstLocation.location" -> "1",
      "framework-preferences.secondLocation.location" -> "1",
      "framework-preferences.firstLocation.firstFramework" -> "1",
      "framework-preferences.secondLocation.firstFramework" -> "1",
      "framework-preferences.firstLocation.secondFramework" -> "1",
      "framework-preferences.secondLocation.secondFramework" -> "1",
      "assistance-details.needsAssistance" -> "1",
      "assistance-details.needsAdjustment" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "personal-details.aLevel" -> "1",
      "personal-details.stemLevel" -> "1",
      "passmarkEvaluation" -> "2",
      "applicationId" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>
        val userId = document.getAs[String]("userId").getOrElse("")

        val fr = document.getAs[BSONDocument]("framework-preferences")

        val fr1 = fr.flatMap(_.getAs[BSONDocument]("firstLocation"))
        val fr1FirstLocation = fr1.flatMap(_.getAs[String]("location"))
        val fr1FirstFramework = fr1.flatMap(_.getAs[String]("firstFramework"))
        val fr1SecondFramework = fr1.flatMap(_.getAs[String]("secondFramework"))

        val fr2 = fr.flatMap(_.getAs[BSONDocument]("secondLocation"))
        val fr2FirstLocation = fr2.flatMap(_.getAs[String]("location"))
        val fr2FirstFramework = fr2.flatMap(_.getAs[String]("firstFramework"))
        val fr2SecondFramework = fr2.flatMap(_.getAs[String]("secondFramework"))

        val frAlternatives = fr.flatMap(_.getAs[BSONDocument]("alternatives"))
        val location = frAlternatives.flatMap(_.getAs[Boolean]("location").map(booleanTranslator))
        val framework = frAlternatives.flatMap(_.getAs[Boolean]("framework").map(booleanTranslator))

        val ad = document.getAs[BSONDocument]("assistance-details")
        val needsAssistance = ad.flatMap(_.getAs[String]("needsAssistance"))
        val needsAdjustment = ad.flatMap(_.getAs[String]("needsAdjustment"))
        val guaranteedInterview = ad.flatMap(_.getAs[String]("guaranteedInterview"))

        val pd = document.getAs[BSONDocument]("personal-details")
        val aLevel = pd.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
        val stemLevel = pd.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))

        val applicationId = document.getAs[String]("applicationId").getOrElse("")

        val pe = document.getAs[BSONDocument]("passmarkEvaluation")

        val otLocation1Scheme1PassmarkEvaluation = pe.flatMap(_.getAs[String]("location1Scheme1").map(Result(_).toPassmark))
        val otLocation1Scheme2PassmarkEvaluation = pe.flatMap(_.getAs[String]("location1Scheme2").map(Result(_).toPassmark))
        val otLocation2Scheme1PassmarkEvaluation = pe.flatMap(_.getAs[String]("location2Scheme1").map(Result(_).toPassmark))
        val otLocation2Scheme2PassmarkEvaluation = pe.flatMap(_.getAs[String]("location2Scheme2").map(Result(_).toPassmark))
        val otAlternativeSchemePassmarkEvaluation = pe.flatMap(_.getAs[String]("alternativeScheme").map(Result(_).toPassmark))

        ApplicationPreferences(userId, applicationId, fr1FirstLocation, fr1FirstFramework, fr1SecondFramework,
          fr2FirstLocation, fr2FirstFramework, fr2SecondFramework, location, framework, needsAssistance,
          guaranteedInterview, needsAdjustment, aLevel, stemLevel,
          OnlineTestPassmarkEvaluationSchemes(otLocation1Scheme1PassmarkEvaluation, otLocation1Scheme2PassmarkEvaluation,
            otLocation2Scheme1PassmarkEvaluation, otLocation2Scheme2PassmarkEvaluation, otAlternativeSchemePassmarkEvaluation))
      }
    }
  }

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
            )))
        ))

        selectRandom[Candidate](query, issue.batchSize)
    }
  }

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
        bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])
      case PassToPhase1TestPassed =>
        val query = BSONDocument("$and" -> BSONArray(
          BSONDocument("applicationId" -> application.applicationId),
          BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> BSONDocument("$ne" -> true))
        ))
        val updateOp = bsonCollection.updateModifier(BSONDocument("$set" ->
          BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED)))
        bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])
      case ResetPhase1TestInvitedSubmitted =>
        val query = BSONDocument("$and" -> BSONArray(
          BSONDocument("applicationId" -> application.applicationId),
          BSONDocument("applicationStatus" -> ApplicationStatus.SUBMITTED),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
        ))
        val updateOp = bsonCollection.updateModifier(BSONDocument("$unset" ->
          BSONDocument(
            s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
            s"progress-status-timestamp.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
            "testGroups" -> ""
          )))
        bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])
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

        bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])

    }
  }

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

    bsonCollection.findAndModify(query, updateOp).map(_ => ())
  }

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

    bsonCollection.findAndModify(query, updateOp).map(_ => ())
  }

  def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> appId,
      s"progress-status.$progressStatus" -> true
    )
    val updateOp = bsonCollection.updateModifier(BSONDocument(
      "$unset" -> BSONDocument(s"progress-status.$progressStatus" -> ""),
      "$unset" -> BSONDocument(s"progress-status-timestamp.$progressStatus" -> "")
    ))

    bsonCollection.findAndModify(query, updateOp).map(_ => ())
  }

  private def applicationPreferencesWithTestResults(query: BSONDocument): Future[List[ApplicationPreferencesWithTestResults]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "framework-preferences.alternatives.location" -> "1",
      "framework-preferences.alternatives.framework" -> "1",
      "framework-preferences.firstLocation.location" -> "1",
      "framework-preferences.secondLocation.location" -> "1",
      "framework-preferences.firstLocation.firstFramework" -> "1",
      "framework-preferences.secondLocation.firstFramework" -> "1",
      "framework-preferences.firstLocation.secondFramework" -> "1",
      "framework-preferences.secondLocation.secondFramework" -> "1",
      "assessment-centre-passmark-evaluation" -> "2",
      "applicationId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.aLevel" -> "1",
      "personal-details.stemLevel" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>
        toApplicationPreferencesWithTestResults.read(document)
      }
    }
  }

  private[application] def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

  private def reportQueryWithProjections[A](
    query: BSONDocument,
    prj: BSONDocument,
    upTo: Int = Int.MaxValue,
    stopOnError: Boolean = true
  )(implicit reader: Format[A]): Future[List[A]] =
    collection.find(query).projection(prj).cursor[A](ReadPreference.nearest).collect[List](upTo, stopOnError)

  def extract(key: String)(root: Option[BSONDocument]) = root.flatMap(_.getAs[String](key))

  /*private def getAdjustmentsConfirmed(assistance: Option[BSONDocument]): Option[String] = {
    assistance.flatMap(_.getAs[Boolean]("adjustmentsConfirmed")).getOrElse(false) match {
      case false => Some("Unconfirmed")
      case true => Some("Confirmed")
    }
  }*/

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

    collection.update(query, resetExerciseAdjustmentsBSON).map(resetValidator).flatMap { _ =>
      collection.update(query, adjustmentsConfirmationBSON) map adjustmentValidator
    }
  }

  def findAdjustments(applicationId: String): Future[Option[Adjustments]] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("assistance-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument].map {
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
  }

  def removeAdjustmentsComment(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val removeBSON = BSONDocument("$unset" -> BSONDocument(
      "assistance-details.adjustmentsComment" -> ""
    ))

    val validator = singleUpdateValidator(
      applicationId,
      actionDesc = "remove adjustments comment",
      notFound = CannotRemoveAdjustmentsComment(applicationId)
    )

    collection.update(query, removeBSON) map validator
  }

  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "assistance-details.adjustmentsComment" -> adjustmentsComment.comment
    ))

    val validator = singleUpdateValidator(
      applicationId,
      actionDesc = "save adjustments comment",
      notFound = CannotUpdateAdjustmentsComment(applicationId)
    )

    collection.update(query, updateBSON) map validator
  }

  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("assistance-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument].map {
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
  }

  def rejectAdjustment(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val adjustmentRejection = BSONDocument("$set" -> BSONDocument(
      "assistance-details.typeOfAdjustments" -> List.empty[String],
      "assistance-details.needsAdjustment" -> "No"
    ))

    val validator = singleUpdateValidator(
      applicationId,
      actionDesc = "remove adjustments comment",
      notFound = CannotRemoveAdjustmentsComment(applicationId)
    )

    collection.update(query, adjustmentRejection) map validator
  }

  def gisByApplication(applicationId: String): Future[Boolean] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val projection = BSONDocument(
      "assistance-details.guaranteedInterview" -> "1"
    )

    collection.find(query, projection).one[BSONDocument].map {
      _.flatMap { doc =>
        doc.getAs[BSONDocument]("assistance-details").map(_.getAs[Boolean]("guaranteedInterview").contains(true))
      }.getOrElse(false)
    }
  }

  def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val format = DateTimeFormat.forPattern("yyyy-MM-dd")
    val projection = BSONDocument(
      "allocation-expire-date" -> "1"
    )

    collection.find(query, projection).one[BSONDocument].map {
      _.flatMap { doc =>
        doc.getAs[String]("allocation-expire-date").map(d => format.parseDateTime(d).toLocalDate)
      }
    }
  }

  def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating status")

    collection.update(query, BSONDocument("$set" -> applicationStatusBSON(applicationStatus))) map validator
  }

  def updateSubmissionDeadline(applicationId: String, newDeadline: DateTime): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating submission deadline")

    collection.update(query, BSONDocument("$set" -> BSONDocument("submissionDeadline" -> newDeadline))) map validator
  }

  override def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("applicationId" -> appId)
    collection.find(query).one[BSONDocument] map {
      _.map(bsonDocToOnlineTestApplication)
    }
  }

  override def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val validator = singleUpdateValidator(applicationId, actionDesc = "updating progress and app status")

    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(progressStatus))) map validator
  }

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

    collection.update(query, unsetDoc) map validator
  }

  override def updateApplicationRoute(appId: String, appRoute: ApplicationRoute, newAppRoute: ApplicationRoute): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> appId),
      applicationRouteCriteria(appRoute)
    ))

    val updateAppRoute = BSONDocument("$set" -> BSONDocument(
      "applicationRoute" -> newAppRoute
    ))

    val validator = singleUpdateValidator(appId, actionDesc = "updating application route")
    collection.update(query, updateAppRoute) map validator
  }

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
      ).add(
          applicationStatusBSON(ProgressStatuses.APPLICATION_ARCHIVED)
        ))

    val validator = singleUpdateValidator(appId, actionDesc = "archiving application")
    collection.update(query, updateWithArchiveUserId) map validator
  }

  private def applicationRouteCriteria(appRoute: ApplicationRoute) = appRoute match {
    case ApplicationRoute.Faststream =>
      BSONDocument("$or" -> BSONArray(
        BSONDocument("applicationRoute" -> appRoute),
        BSONDocument("applicationRoute" -> BSONDocument("$exists" -> false))
      ))
    case _ => BSONDocument("applicationRoute" -> appRoute)
  }

  private def resultToBSON(schemeName: String, result: Option[EvaluationResults.Result]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r.toString)
    case _ => BSONDocument.empty
  }

  private def booleanToBSON(schemeName: String, result: Option[Boolean]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r)
    case _ => BSONDocument.empty
  }

  private def averageToBSON(name: String, result: Option[CompetencyAverageResult]): BSONDocument = result match {
    case Some(r) => BSONDocument(name -> r)
    case _ => BSONDocument.empty
  }

  private def perSchemeToBSON(name: String, result: Option[List[PerSchemeEvaluation]]): BSONDocument = result match {
    case Some(m) =>
      val schemes = m.map(x => BSONDocument(x.schemeName -> x.result.toString))
      val schemesDoc = schemes.foldRight(BSONDocument.empty)((acc, doc) => acc.add(doc))
      BSONDocument(name -> schemesDoc)
    case _ => BSONDocument.empty
  }

}
