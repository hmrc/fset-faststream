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

import model.ApplicationStatus._
import model.ApplicationStatusOrder._
import model.AssessmentScheduleCommands.{ ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult }
import model.CivilServiceExperienceType.CivilServiceExperienceType
import model.Commands._
import model.EvaluationResults._
import model.Exceptions.{ ApplicationNotFound, CannotUpdatePreview }
import model.InternshipType.InternshipType
import model.OnlineTestCommands.{ OnlineTestApplication, TestResult }
import model.ProgressStatuses.ProgressStatus
import model.SchemeType._
import model.command._
import model.persisted.{ ApplicationForNotification, Phase1TestProfile }
import model.report.{ AdjustmentReport, ApplicationForOnlineTestPassMarkReportItem, CandidateProgressReport, PassMarkReportTestResults }
import model.{ ApplicationStatus, _ }
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Format, JsNumber, JsObject }
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{ DB, QueryOpts, ReadPreference }
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand
import repositories._
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

  def create(userId: String, frameworkId: String): Future[ApplicationResponse]

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

  def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReport]]

  def onlineTestPassMarkReport(frameworkId: String): Future[List[ApplicationForOnlineTestPassMarkReportItem]]

  def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReport]]

  def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]]

  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReport]]

  def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]]

  def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]]

  def confirmAdjustment(applicationId: String, data: AdjustmentManagement): Future[Unit]

  def rejectAdjustment(applicationId: String): Future[Unit]

  def gisByApplication(applicationId: String): Future[Boolean]

  def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]]

  def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit]

  def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]]

  def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]]

  def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]]

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]]

  def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]]

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String,
                                    evaluationResult: AssessmentRuleCategoryResult, newApplicationStatus: ApplicationStatus): Future[Unit]

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]

  def addProgressStatusAndUpdateAppStatus(applicationId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit]

  def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit]
}

// scalastyle:off number.of.methods
// scalastyle:off file.size.limit
class GeneralApplicationMongoRepository(timeZoneService: TimeZoneService)(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID]("application", mongo,
    Commands.Implicits.createApplicationRequestFormats,
    ReactiveMongoFormats.objectIdFormats) with GeneralApplicationRepository with RandomSelection with CommonBSONDocuments {

  // Use the BSON collection instead of in the inbuilt JSONCollection when performance matters
  lazy val bsonCollection = mongo().collection[BSONCollection](this.collection.name)

  implicit val readerPD = bsonReader(docToReportWithPersonalDetails)
  implicit val readerTPM = bsonReader(docToOnlineTestPassMarkReport)
  implicit val readerCandidate = bsonReader(docToCandidate)
  implicit val readerCPR = bsonReader(docToCandidateProgressReport)

  override def create(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val applicationId = UUID.randomUUID().toString
    val applicationBSON = BSONDocument(
      "applicationId" -> applicationId,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> CREATED
    )
    collection.insert(applicationBSON) flatMap { _ =>
      findProgress(applicationId).map { p =>
        ApplicationResponse(applicationId, CREATED, userId, p, None)
      }
    }
  }

  private def docToCandidate(doc: BSONDocument): Candidate = {
    val userId = doc.getAs[String]("userId").getOrElse("")
    val applicationId = doc.getAs[String]("applicationId")

    val psRoot = doc.getAs[BSONDocument]("personal-details")
    val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
    val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
    val preferredName = psRoot.flatMap(_.getAs[String]("preferredName"))
    val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

    Candidate(userId, applicationId, None, firstName, lastName, preferredName, dateOfBirth, None, None, None)
  }

  def find(applicationId: String): Future[Option[Candidate]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    bsonCollection.find(query).one[Candidate]
  }

  def find(applicationIds: List[String]): Future[List[Candidate]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    bsonCollection.find(query).cursor[Candidate]().collect[List]()
  }

  // scalastyle:off method.length
  private def findProgress(document: BSONDocument, applicationId: String): ProgressResponse = {

    (document.getAs[BSONDocument]("progress-status") map { root =>

      def getProgress(key: String) = {
        // THIS IS A NASTY HACK AND SHOULD BE REMOVED ONCE THE PROGRESS STATUSES ARE SORTED
        root.getAs[Boolean](key).
          orElse(root.getAs[Boolean](key.toUpperCase)).
          orElse(root.getAs[Boolean](key.toLowerCase)).
          getOrElse(false)
      }

      def questionnaire = root.getAs[BSONDocument]("questionnaire").map { doc =>
        doc.elements.collect {
          case (name, BSONBoolean(true)) => name
        }.toList
      }.getOrElse(Nil)

      ProgressResponse(
        applicationId,
        personalDetails = getProgress(ProgressStatuses.PERSONAL_DETAILS_COMPLETED.key),
        partnerGraduateProgrammes = getProgress(ProgressStatuses.IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES_COMPLETED.key),
        schemePreferences = getProgress(ProgressStatuses.SCHEME_PREFERENCES_COMPLETED.key),
        assistanceDetails = getProgress(ProgressStatuses.IN_PROGRESS_ASSISTANCE_DETAILS_COMPLETED.key),
        preview = getProgress(ProgressStatuses.PREVIEW.key),
        questionnaire = questionnaire,
        submitted = getProgress(ProgressStatuses.SUBMITTED.key),
        withdrawn = getProgress(ProgressStatuses.WITHDRAWN.key),
        phase1ProgressResponse = Phase1ProgressResponse(
          phase1TestsInvited = getProgress(ProgressStatuses.PHASE1_TESTS_INVITED.key),
          phase1TestsFirstRemainder = getProgress(ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER.key),
          phase1TestsSecondRemainder = getProgress(ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER.key),
          phase1TestsResultsReady = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_READY.key),
          phase1TestsResultsReceived = getProgress(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED.key),
          phase1TestsStarted = getProgress(ProgressStatuses.PHASE1_TESTS_STARTED.key),
          phase1TestsCompleted = getProgress(ProgressStatuses.PHASE1_TESTS_COMPLETED.key),
          phase1TestsExpired = getProgress(ProgressStatuses.PHASE1_TESTS_EXPIRED.key),
          phase1TestsPassed = getProgress(ProgressStatuses.PHASE1_TESTS_PASSED.key),
          phase1TestsFailed = getProgress(ProgressStatuses.PHASE1_TESTS_FAILED.key)
        ),
        phase2ProgressResponse = Phase2ProgressResponse(
          phase2TestsInvited = getProgress(ProgressStatuses.PHASE2_TESTS_INVITED.key),
          phase2TestsFirstRemainder = getProgress(ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER.key),
          phase2TestsSecondRemainder = getProgress(ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER.key),
          phase2TestsResultsReady = getProgress(ProgressStatuses.PHASE2_TESTS_RESULTS_READY.key),
          phase2TestsResultsReceived = getProgress(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED.key),
          phase2TestsStarted = getProgress(ProgressStatuses.PHASE2_TESTS_STARTED.key),
          phase2TestsCompleted = getProgress(ProgressStatuses.PHASE2_TESTS_COMPLETED.key),
          phase2TestsExpired = getProgress(ProgressStatuses.PHASE2_TESTS_EXPIRED.key),
          phase2TestsPassed = getProgress(ProgressStatuses.PHASE2_TESTS_PASSED.key),
          phase2TestsFailed = getProgress(ProgressStatuses.PHASE2_TESTS_FAILED.key)
        ),
        failedToAttend = getProgress(FAILED_TO_ATTEND.toString),
        assessmentScores = AssessmentScores(getProgress(ASSESSMENT_SCORES_ENTERED.toString), getProgress(ASSESSMENT_SCORES_ACCEPTED.toString)),
        assessmentCentre = AssessmentCentre(
          getProgress(AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION.toString),
          getProgress(ASSESSMENT_CENTRE_PASSED.toString),
          getProgress(ASSESSMENT_CENTRE_FAILED.toString),
          getProgress(ASSESSMENT_CENTRE_PASSED_NOTIFIED.toString),
          getProgress(ASSESSMENT_CENTRE_FAILED_NOTIFIED.toString)
        )
      )
    }).getOrElse(ProgressResponse(applicationId))
  }
  // scalastyle:on method.length

  override def findProgress(applicationId: String): Future[ProgressResponse] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("progress-status" -> 2, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) => findProgress(document, applicationId)
      case None => throw ApplicationNotFound(applicationId)
    }
  }

  def findStatus(applicationId: String): Future[ApplicationStatusDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("applicationStatus" -> 1, "progress-status-timestamp" -> 1, "progress-status-dates" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) =>
        val applicationStatus = document.getAs[ApplicationStatus]("applicationStatus").get
        val progressStatusTimeStamp = document.getAs[BSONDocument]("progress-status-timestamp")
          .flatMap(_.getAs[DateTime](applicationStatus))
          .orElse(
            document.getAs[BSONDocument]("progress-status-dates")
              .flatMap(_.getAs[LocalDate](applicationStatus.toLowerCase).map(_.toDateTimeAtStartOfDay))
          )
        ApplicationStatusDetails(applicationStatus, progressStatusTimeStamp)

      case None => throw ApplicationNotFound(applicationId)
    }
  }

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val query = BSONDocument("userId" -> userId, "frameworkId" -> frameworkId)

    collection.find(query).one[BSONDocument] flatMap {
      case Some(document) =>
        val applicationId = document.getAs[String]("applicationId").get
        val applicationStatus = document.getAs[ApplicationStatus]("applicationStatus").get
        val fastPassReceived = document.getAs[CivilServiceExperienceDetails]("civil-service-experience-details")
        findProgress(applicationId).map { progress =>
          ApplicationResponse(applicationId, applicationStatus, userId, progress, fastPassReceived)
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

    bsonCollection.find(query).cursor[Candidate]().collect[List]()
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
              bsonDocToApplicationsForAssessmentAllocation(doc)
            }
          }.flatMap { result =>
          Future.successful(ApplicationForAssessmentAllocationResult(result, count))
        }
      }
    }
  }

  override def submit(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> applicationStatusBSON(SUBMITTED))
    collection.update(query, updateBSON, upsert = false) map { _ => }
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
        logger.error(
          s"""Failed to write assistance details for application: $applicationId ->
              |${result.writeConcernError.map(_.errmsg).mkString(",")}""".stripMargin)
        throw CannotUpdatePreview(applicationId)
      case _ => ()
    }

  }

  override def onlineTestPassMarkReport(frameworkId: String):
  Future[List[ApplicationForOnlineTestPassMarkReportItem]] = {

    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.PHASE1_TESTS_RESULTS_RECEIVED" -> true)
    ))

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "testGroups" -> "1",
      "progress-status" -> "2"
    )

    reportQueryWithProjectionsBSON[ApplicationForOnlineTestPassMarkReportItem](query, projection)
  }


  override def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReport]] =
    candidateProgressReport(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN"))
    )))

  override def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] =
    overallReportWithPersonalDetails(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN"))
    )))

  override def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReport]] =
    candidateProgressReport(BSONDocument("frameworkId" -> frameworkId))

  private def candidateProgressReport(query: BSONDocument): Future[List[CandidateProgressReport]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "civil-service-experience-details" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2"
    )

    reportQueryWithProjectionsBSON[CandidateProgressReport](query, projection)
  }

  //scalastyle:off method.length
  private def docToOnlineTestPassMarkReport(document: BSONDocument): ApplicationForOnlineTestPassMarkReportItem = {
    import config.MicroserviceAppConfig._

    val applicationId = document.getAs[String]("applicationId").getOrElse("")

    val schemesDoc = document.getAs[BSONDocument]("scheme-preferences")
    val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

    val adDoc = document.getAs[BSONDocument]("assistance-details")
    val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))
    val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
    val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
    val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)

    val testGroupsDoc = document.getAs[BSONDocument]("testGroups")
    val phase1Doc = testGroupsDoc.flatMap(_.getAs[BSONDocument]("PHASE1"))

    val phase1TestProfile = Phase1TestProfile.bsonHandler.read(phase1Doc.get)

    val situationalScheduleId = cubiksGatewayConfig.phase1Tests.scheduleIds("sjq")
    val behaviouralScheduleId = cubiksGatewayConfig.phase1Tests.scheduleIds("bq")

    def getTestResult(phase1TestProfile: Phase1TestProfile, scheduleId: Int) = {
      phase1TestProfile.activeTests.find(_.scheduleId == scheduleId).flatMap { phase1Test =>
        phase1Test.testResult.map { tr =>
          TestResult(status = tr.status, norm = tr.norm, tScore = tr.tScore, raw = tr.raw, percentile = tr.percentile, sten = tr.sten)
        }
      }
    }
    val behaviouralTestResult = getTestResult(phase1TestProfile, behaviouralScheduleId)
    val situationalTestResult = getTestResult(phase1TestProfile, situationalScheduleId)

    ApplicationForOnlineTestPassMarkReportItem(
      applicationId,
      schemes.getOrElse(List.empty[SchemeType]),
      disability,
      gis,
      onlineAdjustments,
      assessmentCentreAdjustments,
      PassMarkReportTestResults(behaviouralTestResult, situationalTestResult))
  }

  //scalastyle:on method.length

  private def docToCandidateProgressReport(document: BSONDocument): CandidateProgressReport = {
    val schemesDoc = document.getAs[BSONDocument]("scheme-preferences")
    val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

    val adDoc = document.getAs[BSONDocument]("assistance-details")
    val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
    val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
    val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
    val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview")).map(booleanTranslator)

    val fpDoc = document.getAs[BSONDocument]("civil-service-experience-details")
    val civilServiceExperienceType = (fpType: CivilServiceExperienceType) => fpDoc.map(
      _.getAs[CivilServiceExperienceType]("civilServiceExperienceType").contains(fpType)
    )
    val internshipTypes = (internshipType: InternshipType) =>
      fpDoc.map(_.getAs[List[InternshipType]]("internshipTypes").getOrElse(List.empty[InternshipType]).contains(internshipType))

    val civilServant = civilServiceExperienceType(CivilServiceExperienceType.CivilServant).map(booleanTranslator)
    val fastTrack = civilServiceExperienceType(CivilServiceExperienceType.CivilServantViaFastTrack).map(booleanTranslator)
    val edip = internshipTypes(InternshipType.EDIP).map(booleanTranslator)
    val sdipPrevious = internshipTypes(InternshipType.SDIPPreviousYear).map(booleanTranslator)
    val sdip = internshipTypes(InternshipType.SDIPCurrentYear).map(booleanTranslator)
    val fastPassCertificate = fpDoc.map(_.getAs[String]("certificateNumber").getOrElse("No"))

    val applicationId = document.getAs[String]("applicationId").getOrElse("")
    val progress: ProgressResponse = findProgress(document, applicationId)

    CandidateProgressReport(applicationId, Some(getStatus(progress)), schemes.getOrElse(List.empty[SchemeType]), disability, onlineAdjustments,
      assessmentCentreAdjustments, gis, civilServant, fastTrack, edip, sdipPrevious, sdip, fastPassCertificate)
  }

  override def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]] =
    applicationPreferences(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.${ASSESSMENT_SCORES_ACCEPTED.toLowerCase}" -> true),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> WITHDRAWN))
    )))

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

        val applicationId = document.getAs[String]("applicationId").getOrElse("")

        val pe = document.getAs[BSONDocument]("assessment-centre-passmark-evaluation")

        val ca = pe.flatMap(_.getAs[BSONDocument]("competency-average"))
        val leadingAndCommunicatingAverage = ca.flatMap(_.getAs[Double]("leadingAndCommunicatingAverage"))
        val collaboratingAndPartneringAverage = ca.flatMap(_.getAs[Double]("collaboratingAndPartneringAverage"))
        val deliveringAtPaceAverage = ca.flatMap(_.getAs[Double]("deliveringAtPaceAverage"))
        val makingEffectiveDecisionsAverage = ca.flatMap(_.getAs[Double]("makingEffectiveDecisionsAverage"))
        val changingAndImprovingAverage = ca.flatMap(_.getAs[Double]("changingAndImprovingAverage"))
        val buildingCapabilityForAllAverage = ca.flatMap(_.getAs[Double]("buildingCapabilityForAllAverage"))
        val motivationFitAverage = ca.flatMap(_.getAs[Double]("motivationFitAverage"))
        val overallScore = ca.flatMap(_.getAs[Double]("overallScore"))

        val se = pe.flatMap(_.getAs[BSONDocument]("schemes-evaluation"))
        val commercial = se.flatMap(_.getAs[String](Schemes.Commercial).map(Result(_).toPassmark))
        val digitalAndTechnology = se.flatMap(_.getAs[String](Schemes.DigitalAndTechnology).map(Result(_).toPassmark))
        val business = se.flatMap(_.getAs[String](Schemes.Business).map(Result(_).toPassmark))
        val projectDelivery = se.flatMap(_.getAs[String](Schemes.ProjectDelivery).map(Result(_).toPassmark))
        val finance = se.flatMap(_.getAs[String](Schemes.Finance).map(Result(_).toPassmark))

        val pd = document.getAs[BSONDocument]("personal-details")
        val firstName = pd.flatMap(_.getAs[String]("firstName"))
        val lastName = pd.flatMap(_.getAs[String]("lastName"))
        val preferredName = pd.flatMap(_.getAs[String]("preferredName"))
        val aLevel = pd.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
        val stemLevel = pd.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))

        ApplicationPreferencesWithTestResults(userId, applicationId, fr1FirstLocation, fr1FirstFramework,
          fr1SecondFramework, fr2FirstLocation, fr2FirstFramework, fr2SecondFramework, location, framework,
          PersonalInfo(firstName, lastName, preferredName, aLevel, stemLevel),
          CandidateScoresSummary(leadingAndCommunicatingAverage, collaboratingAndPartneringAverage,
            deliveringAtPaceAverage, makingEffectiveDecisionsAverage, changingAndImprovingAverage,
            buildingCapabilityForAllAverage, motivationFitAverage, overallScore),
          SchemeEvaluation(commercial, digitalAndTechnology, business, projectDelivery, finance))
      }
    }
  }

  // scalstyle:on method.length

  private def overallReportWithPersonalDetails(query: BSONDocument): Future[List[ReportWithPersonalDetails]] = {
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
      "personal-details.aLevel" -> "1",
      "personal-details.dateOfBirth" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.stemLevel" -> "1",
      "online-tests.cubiksUserId" -> "1",
      "assistance-details.needsAssistance" -> "1",
      "assistance-details.needsAdjustment" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2"
    )

    reportQueryWithProjectionsBSON[ReportWithPersonalDetails](query, projection)
  }

  private def docToReportWithPersonalDetails(document: BSONDocument): ReportWithPersonalDetails = {
    val fr = document.getAs[BSONDocument]("framework-preferences")
    val fr1 = fr.flatMap(_.getAs[BSONDocument]("firstLocation"))
    val fr2 = fr.flatMap(_.getAs[BSONDocument]("secondLocation"))

    def frLocation(root: Option[BSONDocument]) = extract("location")(root)
    def frScheme1(root: Option[BSONDocument]) = extract("firstFramework")(root)
    def frScheme2(root: Option[BSONDocument]) = extract("secondFramework")(root)

    val personalDetails = document.getAs[BSONDocument]("personal-details")
    val aLevel = personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
    val stemLevel = personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))
    val firstName = personalDetails.flatMap(_.getAs[String]("firstName"))
    val lastName = personalDetails.flatMap(_.getAs[String]("lastName"))
    val preferredName = personalDetails.flatMap(_.getAs[String]("preferredName"))
    val dateOfBirth = personalDetails.flatMap(_.getAs[String]("dateOfBirth"))

    val fpAlternatives = fr.flatMap(_.getAs[BSONDocument]("alternatives"))
    val location = fpAlternatives.flatMap(_.getAs[Boolean]("location").map(booleanTranslator))
    val framework = fpAlternatives.flatMap(_.getAs[Boolean]("framework").map(booleanTranslator))

    val ad = document.getAs[BSONDocument]("assistance-details")
    val needsAssistance = ad.flatMap(_.getAs[String]("needsAssistance"))
    val needsAdjustment = ad.flatMap(_.getAs[String]("needsAdjustment"))
    val guaranteedInterview = ad.flatMap(_.getAs[String]("guaranteedInterview"))

    val applicationId = document.getAs[String]("applicationId").getOrElse("")
    val userId = document.getAs[String]("userId").getOrElse("")
    val progress: ProgressResponse = findProgress(document, applicationId)

    val onlineTests = document.getAs[BSONDocument]("online-tests")
    val cubiksUserId = onlineTests.flatMap(_.getAs[Int]("cubiksUserId"))

    ReportWithPersonalDetails(
      applicationId, userId, Some(getStatus(progress)), frLocation(fr1), frScheme1(fr1), frScheme2(fr1),
      frLocation(fr2), frScheme1(fr2), frScheme2(fr2), aLevel,
      stemLevel, location, framework, needsAssistance, needsAdjustment, guaranteedInterview, firstName, lastName,
      preferredName, dateOfBirth, cubiksUserId
    )
  }

  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReport]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.CREATED)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.IN_PROGRESS)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.WITHDRAWN)),
        BSONDocument("$or" ->
          BSONArray(
            BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
            BSONDocument("assistance-details.needsSupportAtVenue" -> true),
            BSONDocument("assistance-details.guaranteedInterview" -> true)
          ))
      ))

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationStatus" -> "1",
      "applicationId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "assistance-details.hasDisability" -> "1",
      "assistance-details.needsSupportAtVenueDescription" -> "1",
      "assistance-details.needsSupportForOnlineAssessmentDescription" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "assistance-details.hasDisabilityDescription" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val personalDetails = document.getAs[BSONDocument]("personal-details")
        val userId = document.getAs[String]("userId").getOrElse("")
        val applicationId = document.getAs[String]("applicationId")
        val applicationStatus = document.getAs[String]("applicationStatus")
        val firstName = extract("firstName")(personalDetails)
        val lastName = extract("lastName")(personalDetails)
        val preferredName = extract("preferredName")(personalDetails)

        val assistance = document.getAs[BSONDocument]("assistance-details")
        val gis = assistance.flatMap(_.getAs[Boolean]("guaranteedInterview")).flatMap(b => Some(booleanTranslator(b)))
        val needsSupportForOnlineAssessmentDescription = extract("needsSupportForOnlineAssessmentDescription")(assistance)
        val needsSupportAtVenueDescription = extract("needsSupportAtVenueDescription")(assistance)
        val hasDisability = extract("hasDisability")(assistance)
        val hasDisabilityDescription = extract("hasDisabilityDescription")(assistance)

        AdjustmentReport(
          userId,
          applicationId,
          firstName,
          lastName,
          preferredName,
          None,
          None,
          gis,
          applicationStatus,
          needsSupportForOnlineAssessmentDescription,
          needsSupportAtVenueDescription,
          hasDisability,
          hasDisabilityDescription)
      }
    }
  }

  def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("applicationStatus" -> "AWAITING_ALLOCATION")
      ))

    val projection = BSONDocument(
      "userId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.dateOfBirth" -> "1",
      "framework-preferences.firstLocation.location" -> "1",
      "assistance-details.typeOfAdjustments" -> "1",
      "assistance-details.otherAdjustments" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val userId = document.getAs[String]("userId").get
        val personalDetails = document.getAs[BSONDocument]("personal-details").get
        val firstName = personalDetails.getAs[String]("firstName").get
        val lastName = personalDetails.getAs[String]("lastName").get
        val preferredName = personalDetails.getAs[String]("preferredName").get
        val dateOfBirth = personalDetails.getAs[LocalDate]("dateOfBirth").get
        val frameworkPreferences = document.getAs[BSONDocument]("framework-preferences").get
        val firstLocationDoc = frameworkPreferences.getAs[BSONDocument]("firstLocation").get
        val firstLocation = firstLocationDoc.getAs[String]("location").get

        val assistance = document.getAs[BSONDocument]("assistance-details")
        val typesOfAdjustments = assistance.flatMap(_.getAs[List[String]]("typeOfAdjustments"))

        val otherAdjustments = extract("otherAdjustments")(assistance)
        val adjustments = typesOfAdjustments.getOrElse(Nil) ::: otherAdjustments.toList
        val finalTOA = if (adjustments.isEmpty) None else Some(adjustments.mkString("|"))

        CandidateAwaitingAllocation(userId, firstName, lastName, preferredName, firstLocation, finalTOA, dateOfBirth)
      }
    }
  }

  def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)

    val projection = BSONDocument(
      "applicationId" -> "1",
      "personal-details.preferredName" -> "1",
      "userId" -> "1",
      "framework-preferences" -> "1",
      "progress-status" -> "2"
    )

    val seed = Future.successful(List.empty[(String, Boolean, PreferencesWithContactDetails)])
    reportQueryWithProjections[BSONDocument](query, projection).flatMap { lst =>
      lst.foldLeft(seed) { (applicationsFuture, document) =>
        applicationsFuture.map { applications =>
          val timeCreated = isoTimeToPrettyDateTime(getDocumentId(document).time)
          val applicationId = document.getAs[String]("applicationId").get
          val personalDetails = document.getAs[BSONDocument]("personal-details")
          val preferredName = extract("preferredName")(personalDetails)
          val userId = document.getAs[String]("userId").get
          val frameworkPreferences = document.getAs[Preferences]("framework-preferences")

          val location1 = frameworkPreferences.map(_.firstLocation.location)
          val location1Scheme1 = frameworkPreferences.map(_.firstLocation.firstFramework)
          val location1Scheme2 = frameworkPreferences.flatMap(_.firstLocation.secondFramework)

          val location2 = frameworkPreferences.flatMap(_.secondLocation.map(_.location))
          val location2Scheme1 = frameworkPreferences.flatMap(_.secondLocation.map(_.firstFramework))
          val location2Scheme2 = frameworkPreferences.flatMap(_.secondLocation.flatMap(_.secondFramework))

          val p = findProgress(document, applicationId)

          val preferences = PreferencesWithContactDetails(None, None, preferredName, None, None,
            location1, location1Scheme1, location1Scheme2,
            location2, location2Scheme1, location2Scheme2,
            Some(ApplicationStatusOrder.getStatus(p)), Some(timeCreated))

          (userId, isNonSubmittedStatus(p), preferences) +: applications
        }
      }
    }
  }

  private def getDocumentId(document: BSONDocument): BSONObjectID =
    document.get("_id").get match {
      case id: BSONObjectID => id
      case id: BSONString => BSONObjectID(id.value)
    }

  private def isoTimeToPrettyDateTime(utcMillis: Long): String =
    timeZoneService.localize(utcMillis).toString("yyyy-MM-dd HH:mm:ss")

  private def booleanTranslator(bool: Boolean) = bool match {
    case true => "Yes"
    case false => "No"
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
    assistance.flatMap(_.getAs[Boolean]("adjustments-confirmed")).getOrElse(false) match {
      case false => Some("Unconfirmed")
      case true => Some("Confirmed")
    }
  }*/

  private def reportQueryWithProjectionsBSON[A](
                                                 query: BSONDocument,
                                                 prj: BSONDocument,
                                                 upTo: Int = Int.MaxValue,
                                                 stopOnError: Boolean = true
                                               )(implicit reader: BSONDocumentReader[A]): Future[List[A]] =
    bsonCollection.find(query).projection(prj)
      .cursor[A](ReadPreference.nearest)
      .collect[List](Int.MaxValue, true)


  def confirmAdjustment(applicationId: String, data: AdjustmentManagement): Future[Unit] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val verbalAdjustment = data.timeNeeded.map(i => BSONDocument("assistance-details.verbalTimeAdjustmentPercentage" -> i))
      .getOrElse(BSONDocument.empty)
    val numericalAdjustment = data.timeNeededNum.map(i => BSONDocument("assistance-details.numericalTimeAdjustmentPercentage" -> i))
      .getOrElse(BSONDocument.empty)
    val otherAdjustments = data.otherAdjustments.map(s => BSONDocument("assistance-details.otherAdjustments" -> s))
      .getOrElse(BSONDocument.empty)

    val adjustmentsConfirmationBSON = BSONDocument("$set" -> BSONDocument(
      "assistance-details.typeOfAdjustments" -> data.adjustments.getOrElse(List.empty[String]),
      "assistance-details.adjustments-confirmed" -> true
    ).add(verbalAdjustment).add(numericalAdjustment).add(otherAdjustments))

    collection.update(query, adjustmentsConfirmationBSON, upsert = false) map { _ => }
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

  def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)
    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1"
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").getOrElse("")
        val applicationId = doc.getAs[String]("applicationId").getOrElse("")
        PersonalDetailsAdded(applicationId, userId)
      }
    }
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
    implicit val reader = bsonReader(bsonDocToApplicationForNotification)
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

  private def bsonDocToApplicationsForAssessmentAllocation(doc: BSONDocument) = {
    val userId = doc.getAs[String]("userId").get
    val applicationId = doc.getAs[String]("applicationId").get
    val personalDetails = doc.getAs[BSONDocument]("personal-details").get
    val firstName = personalDetails.getAs[String]("firstName").get
    val lastName = personalDetails.getAs[String]("lastName").get
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details").get
    val needsSupportAtVenue = assistanceDetails.getAs[Boolean]("needsSupportAtVenue").flatMap(b => Some(booleanTranslator(b))).get
    val onlineTestDetails = doc.getAs[BSONDocument]("online-tests").get
    val invitationDate = onlineTestDetails.getAs[DateTime]("invitationDate").get
    ApplicationForAssessmentAllocation(firstName, lastName, userId, applicationId, needsSupportAtVenue, invitationDate)
  }

  private def bsonDocToApplicationForNotification(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }

  private def bsonReader[T](f: BSONDocument => T): BSONDocumentReader[T] = {
    new BSONDocumentReader[T] {
      def read(bson: BSONDocument) = f(bson)
    }
  }
}
