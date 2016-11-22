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

package repositories.application

import java.util.UUID
import java.util.regex.Pattern

import config.CubiksGatewayConfig
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.AssessmentScheduleCommands.ApplicationForAssessmentAllocationResult
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
import play.api.Logger
import play.api.libs.json.{ Format, JsNumber, JsObject }
import reactivemongo.api.{ DB, QueryOpts, ReadPreference }
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand
import repositories._
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{ PassToPhase1TestPassed, PassToPhase2, ResetPhase1TestInvitedSubmitted }
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

  def findApplicationsForAssessmentAllocation(locations: List[String], start: Int, end: Int): Future[ApplicationForAssessmentAllocationResult]

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

  def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]]

  def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]]

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]]

  def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]]

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String,
                                    evaluationResult: AssessmentRuleCategoryResult, newApplicationStatus: ApplicationStatus): Future[Unit]

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]

  def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]

  def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit]

  def findFailedTestForNotification(failedTestType: FailedTestType): Future[Option[NotificationFailedTest]]

  def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]]

  def fix(candidate: Candidate, issue: FixBatch): Future[Option[Candidate]]

  def fixDataByRemovingETray(appId: String): Future[Unit]
}

// scalastyle:off number.of.methods
// scalastyle:off file.size.limit
class GeneralApplicationMongoRepository(timeZoneService: TimeZoneService,
                                        gatewayConfig: CubiksGatewayConfig)(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID]("application", mongo,
    Commands.Implicits.createApplicationRequestFormats,
    ReactiveMongoFormats.objectIdFormats) with GeneralApplicationRepository with RandomSelection with CommonBSONDocuments
    with GeneralApplicationRepoBSONReader with BSONHelpers {

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
        ApplicationResponse(applicationId, CREATED, route, userId, p, None)
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
      "_id" -> 0
    )

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) =>
        val applicationStatus = document.getAs[ApplicationStatus]("applicationStatus").get
        val applicationRoute = document.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
        val progressStatusTimeStamp = document.getAs[BSONDocument]("progress-status-timestamp")
          .flatMap(_.getAs[DateTime](applicationStatus))
          .orElse(
            document.getAs[BSONDocument]("progress-status-dates")
              .flatMap(_.getAs[LocalDate](applicationStatus.toLowerCase).map(_.toDateTimeAtStartOfDay))
          )
        ApplicationStatusDetails(applicationStatus, applicationRoute, progressStatusTimeStamp)

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
        findProgress(applicationId).map { progress =>
          ApplicationResponse(applicationId, applicationStatus, applicationRoute, userId, progress, fastPassReceived)
        }
      case None => throw ApplicationNotFound(userId)
    }
  }

  def findCandidateByUserId(userId: String): Future[Option[Candidate]] = {
    val query = BSONDocument("userId" -> userId)
    bsonCollection.find(query).one[Candidate]
  }

  def findByCriteria(firstOrPreferredNameOpt: Option[String],
                     lastNameOpt: Option[String],
                     dateOfBirth: Option[LocalDate],
                     filterToUserIds: List[String]
                    ): Future[List[Candidate]] = {

    def matchIfSome(value: Option[String]) = value.map(v => BSONRegex("^" + Pattern.quote(v) + "$", "i"))

    val innerQuery = BSONArray(
      BSONDocument("$or" -> BSONArray(
        BSONDocument("personal-details.firstName" -> matchIfSome(firstOrPreferredNameOpt)),
        BSONDocument("personal-details.preferredName" -> matchIfSome(firstOrPreferredNameOpt))
      )),
      BSONDocument("personal-details.lastName" -> matchIfSome(lastNameOpt)),
      BSONDocument("personal-details.dateOfBirth" -> dateOfBirth)
    )

    val fullQuery = if (filterToUserIds.isEmpty) {
      innerQuery
    } else {
      innerQuery ++ BSONDocument("userId" -> BSONDocument("$in" -> filterToUserIds))
    }

    val query = BSONDocument("$and" -> fullQuery)

    val projection = BSONDocument("userId" -> true, "applicationId" -> true, "applicationRoute" -> true,
    "personal-details" -> true)

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

  override def findApplicationsForAssessmentAllocation(locations: List[String], start: Int,
                                                       end: Int): Future[ApplicationForAssessmentAllocationResult] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> "AWAITING_ALLOCATION"),
      BSONDocument("framework-preferences.firstLocation.location" -> BSONDocument("$in" -> locations))
    ))

    collection.runCommand(JSONCountCommand.Count(query)).flatMap { c =>
      val count = c.count
      if (count == 0) {
        Future.successful(ApplicationForAssessmentAllocationResult(List.empty, 0))
      } else {
        val projection = BSONDocument(
          "userId" -> 1,
          "applicationId" -> 1,
          "personal-details.firstName" -> 1,
          "personal-details.lastName" -> 1,
          "assistance-details.needsSupportAtVenue" -> 1,
          "online-tests.invitationDate" -> 1
        )
        val sort = JsObject(Seq("online-tests.invitationDate" -> JsNumber(1)))

        collection.find(query, projection).sort(sort).options(QueryOpts(skipN = start)).cursor[BSONDocument]().collect[List](end - start + 1).
          map { docList =>
            docList.map { doc =>
              toApplicationsForAssessmentAllocation.read(doc)
            }
          }.flatMap { result =>
          Future.successful(ApplicationForAssessmentAllocationResult(result, count))
        }
      }
    }
  }

  override def submit(applicationId: String): Future[Unit] = {
    val guard = progressStatusGuardBSON(PREVIEW)
    val query = BSONDocument("applicationId" -> applicationId) ++ guard

    val updateBSON = BSONDocument("$set" -> applicationStatusBSON(SUBMITTED))
    collection.update(query, updateBSON, upsert = false)
      .map(validateSingleWriteOrThrow(new IllegalStateException(s"Already submitted $applicationId")))
  }

  override def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationBSON = BSONDocument("$set" -> BSONDocument(
      "withdraw" -> reason
      ).add(
        applicationStatusBSON(WITHDRAWN)
      )
    )
    collection.update(query, applicationBSON, upsert = false) map { _ => }
  }

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val progressStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.questionnaire.$sectionKey" -> true
    ))

    collection.update(query, progressStatusBSON, upsert = false) map { _ => }
  }

  override def preview(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val progressStatusBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.preview" -> true
    ))

    collection.update(query, progressStatusBSON, upsert = true) map {
      case result if result.nModified == 0 && result.n == 0 =>
        Logger.error(
          s"""Failed to write assistance details for application: $applicationId ->
              |${result.writeConcernError.map(_.errmsg).mkString(",")}""".stripMargin)
        throw CannotUpdatePreview(applicationId)
      case _ => ()
    }
  }

  override def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]] =
    applicationPreferences(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.${ASSESSMENT_SCORES_ACCEPTED.toLowerCase}" -> true),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> WITHDRAWN))
    )))

  override def findFailedTestForNotification(failedTestType: FailedTestType): Future[Option[NotificationFailedTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> failedTestType.appStatus),
      BSONDocument(s"progress-status.${failedTestType.notificationProgress}" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.${failedTestType.receiveStatus}" -> true)
    ))

    implicit val reader = bsonReader(NotificationFailedTest.fromBson)
    selectOneRandom[NotificationFailedTest](query)
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

  // scalstyle:on method.length
  override def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]] =
    applicationPreferencesWithTestResults(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.${ASSESSMENT_CENTRE_PASSED.toLowerCase}" -> true),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.WITHDRAWN))
    )))

  // scalastyle:off method.length

  override def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]] = {
    issue.fix match {
      case PassToPhase2 => {
        val query = BSONDocument("$and" -> BSONArray(
            BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
            BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
            BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> true)
          ))

        selectRandom[Candidate](query, issue.batchSize)
      }
      case PassToPhase1TestPassed =>
        val query = BSONDocument("$and" -> BSONArray(
          BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> BSONDocument("$ne" -> true))
        ))

        selectRandom[Candidate](query, issue.batchSize)
      case ResetPhase1TestInvitedSubmitted => {
        val query = BSONDocument("$and" -> BSONArray(
          BSONDocument("applicationStatus" -> ApplicationStatus.SUBMITTED),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
        ))

        selectRandom[Candidate](query, issue.batchSize)
      }
    }
  }

  override def fix(application: Candidate, issue: FixBatch): Future[Option[Candidate]] = {
    issue.fix match {
      case PassToPhase2 => {
        val query = BSONDocument("$and" -> BSONArray(
          BSONDocument("applicationId" -> application.applicationId),
          BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> true)
        ))
        val updateOp = bsonCollection.updateModifier(BSONDocument("$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS)))
        bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])
      }
      case PassToPhase1TestPassed =>
        val query = BSONDocument("$and" -> BSONArray(
          BSONDocument("applicationId" -> application.applicationId),
          BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_PASSED}" -> true),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> BSONDocument("$ne" -> true))
        ))
        val updateOp = bsonCollection.updateModifier(BSONDocument("$set" -> BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED)))
        bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])
      case ResetPhase1TestInvitedSubmitted => {
        val query = BSONDocument("$and" -> BSONArray(
          BSONDocument("applicationId" -> application.applicationId),
          BSONDocument("applicationStatus" -> ApplicationStatus.SUBMITTED),
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> true)
        ))
        val updateOp = bsonCollection.updateModifier(BSONDocument("$unset" ->
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
          s"progress-status-timestamp.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> "",
          "testGroups" -> "")))
        bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])
      }
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

    bsonCollection.findAndModify(query, updateOp).map(_.result[Candidate])
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

  private def getDocumentId(document: BSONDocument): BSONObjectID =
    document.get("_id").get match {
      case id: BSONObjectID => id
      case id: BSONString => BSONObjectID(id.value)
    }

  private def isoTimeToPrettyDateTime(utcMillis: Long): String =
    timeZoneService.localize(utcMillis).toString("yyyy-MM-dd HH:mm:ss")

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

    collection.update(query, resetExerciseAdjustmentsBSON).flatMap{ result =>
      collection.update(query, adjustmentsConfirmationBSON, upsert = false)
    } map(_ => ())
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

    collection.update(query, removeBSON).map {
      case result if result.nModified == 0 && result.n == 0 =>
        Logger.error(
          s"""Failed to remove adjustments comment for application: $applicationId ->
              |${result.writeConcernError.map(_.errmsg).mkString(",")}""".stripMargin)
        throw CannotRemoveAdjustmentsComment(applicationId)
      case _ => ()
    }
  }

  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "assistance-details.adjustmentsComment" -> adjustmentsComment.comment
    ))

    collection.update(query, updateBSON).map {
      case result if result.nModified == 0 && result.n == 0 =>
        Logger.error(
          s"""Failed to write save adjustments comment for application: $applicationId ->
             |${result.writeConcernError.map(_.errmsg).mkString(",")}""".stripMargin)
        throw CannotUpdateAdjustmentsComment(applicationId)
      case _ => ()
    }
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

    collection.update(query, adjustmentRejection, upsert = false) map { _ => }
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
    collection.update(query, BSONDocument("$set" -> applicationStatusBSON(applicationStatus))) map { _ => }
  }

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]] = {
    val query =
      BSONDocument("$or" ->
        BSONArray(
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("applicationStatus" -> ASSESSMENT_SCORES_ACCEPTED),
              BSONDocument("assessment-centre-passmark-evaluation.passmarkVersion" -> BSONDocument("$exists" -> false))
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("applicationStatus" -> AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION),
              BSONDocument("assessment-centre-passmark-evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion))
            )
          )
        ))

    implicit val reader = bsonReader { doc => doc.getAs[String]("applicationId").get }
    selectOneRandom[String](query)
  }

  def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]] = {
    val query = BSONDocument(
      "$and" -> BSONArray(
        BSONDocument("online-tests.pdfReportSaved" -> true),
        BSONDocument(
          "$or" -> BSONArray(
            BSONDocument("applicationStatus" -> ASSESSMENT_CENTRE_PASSED),
            BSONDocument("applicationStatus" -> ASSESSMENT_CENTRE_FAILED)
          )
        )
      )
    )
    selectOneRandom[ApplicationForNotification](query)
  }

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String,
                                    evaluationResult: AssessmentRuleCategoryResult, newApplicationStatus: ApplicationStatus): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        "$or" -> BSONArray(
          BSONDocument("applicationStatus" -> ASSESSMENT_SCORES_ACCEPTED),
          BSONDocument("applicationStatus" -> AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION)
        )
      )
    ))

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "assessment-centre-passmark-evaluation" -> BSONDocument("passmarkVersion" -> passmarkVersion)
          .add(booleanToBSON("passedMinimumCompetencyLevel", evaluationResult.passedMinimumCompetencyLevel))
          .add(resultToBSON("location1Scheme1", evaluationResult.location1Scheme1))
          .add(resultToBSON("location1Scheme2", evaluationResult.location1Scheme2))
          .add(resultToBSON("location2Scheme1", evaluationResult.location2Scheme1))
          .add(resultToBSON("location2Scheme2", evaluationResult.location2Scheme2))
          .add(resultToBSON("alternativeScheme", evaluationResult.alternativeScheme))
          .add(averageToBSON("competency-average", evaluationResult.competencyAverageResult))
          .add(perSchemeToBSON("schemes-evaluation", evaluationResult.schemesEvaluation))
      )
        .add(applicationStatusBSON(newApplicationStatus))
    )

    collection.update(query, passMarkEvaluation, upsert = false) map { _ => }
  }

  override def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("applicationId" -> appId)
    collection.find(query).one[BSONDocument] map {
      _.map(bsonDocToOnlineTestApplication)
    }
  }

  override def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(progressStatus))
    ) map { _ => }
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

    collection.update(query, unsetDoc)
      .map { _ => () }
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
