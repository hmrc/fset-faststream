/*
 * Copyright 2020 HM Revenue & Customs
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

import config.PsiTestIds
import connectors.launchpadgateway.exchangeobjects.in.reviewed._
import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model._
import model.command.{ CandidateDetailsReportItem, CsvExtract, WithdrawApplication }
import model.persisted.{ FSACIndicator, SchemeEvaluationResult }
import model.persisted.fsb.ScoresAndFeedback
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{ JsObject, Json }
import reactivemongo.api.{ Cursor, DB, ReadPreference }
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONReader, BSONValue }
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import repositories.{ BSONDateTimeHandler, CollectionNames, CommonBSONDocuments, SchemeYamlRepository, withdrawHandler }
import services.reporting.SocioEconomicCalculator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//scalastyle:off
trait PreviousYearCandidatesDetailsRepository {

  implicit class RichOptionBSONDocument(doc: Option[BSONDocument]) {

    def getAs[T](key: String)(implicit reader: BSONReader[_ <: BSONValue, T]) = doc.flatMap(_.getAs[T](key))

    def getAsStr[T](key: String)(implicit reader: BSONReader[_ <: BSONValue, T]) = doc.flatMap(_.getAs[T](key)).map(_.toString)

    def getOrElseAsStr[T](key: String)(default: T)(implicit reader: BSONReader[_ <: BSONValue, T]) = {
      doc.flatMap(_.getAs[T](key).orElse(Some(default))).map(_.toString)
    }
  }

  private val appTestStatuses = "personal-details,IN_PROGRESS,scheme-preferences,assistance-details,start_questionnaire,diversity_questionnaire,education_questionnaire,occupation_questionnaire,preview,SUBMITTED,FAST_PASS_ACCEPTED,PHASE1_TESTS_INVITED,PHASE1_TESTS_FIRST_REMINDER,PHASE1_TESTS_SECOND_REMINDER,PHASE1_TESTS_STARTED,PHASE1_TESTS_COMPLETED,PHASE1_TESTS_EXPIRED,PHASE1_TESTS_RESULTS_READY," +
    "PHASE1_TESTS_RESULTS_RECEIVED,PHASE1_TESTS_PASSED,PHASE1_TESTS_PASSED_NOTIFIED,PHASE1_TESTS_FAILED,PHASE1_TESTS_FAILED_NOTIFIED,PHASE1_TESTS_FAILED_SDIP_AMBER,PHASE1_TESTS_FAILED_SDIP_GREEN," +
    "PHASE2_TESTS_INVITED,PHASE2_TESTS_FIRST_REMINDER," +
    "PHASE2_TESTS_SECOND_REMINDER,PHASE2_TESTS_STARTED,PHASE2_TESTS_COMPLETED,PHASE2_TESTS_EXPIRED,PHASE2_TESTS_RESULTS_READY," +
    "PHASE2_TESTS_RESULTS_RECEIVED,PHASE2_TESTS_PASSED,PHASE2_TESTS_FAILED,PHASE2_TESTS_FAILED_NOTIFIED,PHASE2_TESTS_FAILED_SDIP_AMBER,PHASE2_TESTS_FAILED_SDIP_GREEN,PHASE3_TESTS_INVITED,PHASE3_TESTS_FIRST_REMINDER," +
    "PHASE3_TESTS_SECOND_REMINDER,PHASE3_TESTS_STARTED,PHASE3_TESTS_COMPLETED,PHASE3_TESTS_EXPIRED,PHASE3_TESTS_RESULTS_RECEIVED," +
    "PHASE3_TESTS_PASSED_WITH_AMBER,PHASE3_TESTS_PASSED,PHASE3_TESTS_PASSED_NOTIFIED,PHASE3_TESTS_FAILED,PHASE3_TESTS_FAILED_NOTIFIED,PHASE3_TESTS_FAILED_SDIP_AMBER,PHASE3_TESTS_FAILED_SDIP_GREEN," +
    "SIFT_ENTERED,SIFT_TEST_INVITED,SIFT_TEST_STARTED,SIFT_TEST_COMPLETED,SIFT_FIRST_REMINDER,SIFT_SECOND_REMINDER,SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING," +
    "SIFT_TEST_RESULTS_READY,SIFT_TEST_RESULTS_RECEIVED,SIFT_READY,SIFT_COMPLETED,SIFT_EXPIRED,SIFT_EXPIRED_NOTIFIED,FAILED_AT_SIFT,FAILED_AT_SIFT_NOTIFIED," +
    "SDIP_FAILED_AT_SIFT,SIFT_FASTSTREAM_FAILED_SDIP_GREEN," +
    "ASSESSMENT_CENTRE_AWAITING_ALLOCATION,ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED,ASSESSMENT_CENTRE_FAILED_TO_ATTEND," +
    "ASSESSMENT_CENTRE_SCORES_ENTERED,ASSESSMENT_CENTRE_SCORES_ACCEPTED,ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION,ASSESSMENT_CENTRE_PASSED,ASSESSMENT_CENTRE_FAILED," +
    "ASSESSMENT_CENTRE_FAILED_NOTIFIED,ASSESSMENT_CENTRE_FAILED_SDIP_GREEN,ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED," +
    "FSB_AWAITING_ALLOCATION,FSB_ALLOCATION_UNCONFIRMED,FSB_ALLOCATION_CONFIRMED,FSB_FAILED_TO_ATTEND," +
    "FSB_RESULT_ENTERED,FSB_PASSED,FSB_FAILED,ALL_FSBS_AND_FSACS_FAILED,ALL_FSBS_AND_FSACS_FAILED_NOTIFIED," +
    "ELIGIBLE_FOR_JOB_OFFER,ELIGIBLE_FOR_JOB_OFFER_NOTIFIED,WITHDRAWN,"

  val fsacCompetencyHeaders = "FSAC passedMinimumCompetencyLevel," +
    "makingEffectiveDecisionsAverage,workingTogetherDevelopingSelfAndOthersAverage,communicatingAndInfluencingAverage," +
    "seeingTheBigPictureAverage,overallScore,"

  private def appTestResults(numOfSchemes: Int) = {
    val otherColumns = "result," * (numOfSchemes - 2) + "result"
    List("PHASE 1", "PHASE 2", "PHASE 3", "SIFT", "FSAC", "FSB", "Current Scheme Status").map { s =>
      s"$s result,$otherColumns"
    }.mkString(",")
  }

  def dataAnalystApplicationDetailsHeader(numOfSchemes: Int) =
    "ApplicationId,Application status,Route,All FS schemes failed SDIP not failed,Currently Civil Servant,Currently Civil Service via Fast Track,Eligible for Fast Pass," +
    "Fast Pass No,Scheme preferences,Do you have a disability," +
    appTestStatuses +
    "Final Progress Status prior to withdrawal," +
    appTestResults(numOfSchemes) +
    ",FSAC Indicator area,FSAC Indicator Assessment Centre"

  def testTitles(testName: String) = s"$testName inventoryId,orderId,normId,reportId,assessmentId,testUrl,invitationDate,startedDateTime," +
    s"completedDateTime,tScore,rawScore,testReportUrl,"

  val assistanceDetailsHeaders = "Do you have a disability,Provide more info,GIS,Extra support online tests," +
    "What adjustments will you need,Extra support f2f,What adjustments will you need,Extra support phone interview,What adjustments will you need," +
    "E-Tray time extension,E-Tray invigilated,E-Tray invigilated notes,E-Tray other notes,Video time extension,Video invigilated,Video invigilated notes," +
    "Video other notes,Additional comments,Adjustments confirmed,"

  def applicationDetailsHeader(numOfSchemes: Int) = "applicationId,userId,testAccountId,Framework ID,Application Status,Route,First name,Last name,Preferred Name,Date of Birth," +
    "Are you eligible,Terms and Conditions," +
    "Currently a Civil Servant done SDIP or EDIP,Currently Civil Servant,Currently Civil Service via Fast Track," +
    "EDIP,SDIP,Eligible for Fast Pass,Fast Pass No,Scheme preferences,Scheme names,Are you happy with order,Are you eligible," +
    assistanceDetailsHeaders +
    "I understand this wont affect application," +
    testTitles("Phase1 test1") +
    testTitles("Phase1 test2") +
    testTitles("Phase1 test3") +
    testTitles("Phase1 test4") +
    testTitles("Phase2 test1") +
    testTitles("Phase2 test2") +
    // Phase 3 test columns
    "PHASE_3 interviewId,token,candidateId,customCandidateId,comment,PHASE_3 last reviewed callback,Q1 Capability,Q1 Engagement,Q2 Capability,Q2 Engagement,Q3 Capability," +
    "Q3 Engagement,Q4 Capability,Q4 Engagement,Q5 Capability,Q5 Engagement,Q6 Capability,Q6 Engagement,Q7 Capability," +
    "Q7 Engagement,Q8 Capability,Q8 Engagement,Overall total," +
    testTitles("Sift") +
    "Fsb overall score,Fsb feedback," +
    appTestStatuses +
    fsacCompetencyHeaders +
    appTestResults(numOfSchemes) +
  ",Candidate or admin withdrawal?,Tell us why you're withdrawing,More information about your withdrawal,Admin comment," +
  "FSAC Indicator area,FSAC Indicator Assessment Centre,FSAC Indicator version"

  val contactDetailsHeader = "Email,Address line1,Address line2,Address line3,Address line4,Postcode,Outside UK,Country,Phone"

  val dataAnalystContactDetailsHeader = "Email,Postcode"

  val questionnaireDetailsHeader: String = "Gender Identity,Sexual Orientation,Ethnic Group,Live in UK between 14-18?,Home postcode at 14," +
    "Name of school 14-16,Which type of school was this?,Name of school 16-18,Eligible for free school meals?,University name,Category of degree," +
    "Lower socio-economic background?,Parent guardian completed Uni?,Parents job at 14,Employee?,Size," +
    "Supervise employees,SE 1-5,Oxbridge,Russell Group"

  val mediaHeader = "How did you hear about us?"

  val eventsDetailsHeader = "Allocated events"

  val allSchemes: List[String] = SchemeYamlRepository.schemes.map(_.id.value).toList

  val siftAnswersHeader: String = "Sift Answers status,multipleNationalities,secondNationality,nationality," +
    "undergrad degree name,classification,graduationYear,moduleDetails," +
    "postgrad degree name,classification,graduationYear,moduleDetails," + allSchemes.mkString(",")

  val dataAnalystSiftAnswersHeader: String = "Nationality,Undergrad degree name,Classification,Graduation year"

  val assessmentScoresNumericFields = Seq(
    "analysisExercise" -> "makingEffectiveDecisionsAverage,communicatingAndInfluencingAverage,seeingTheBigPictureAverage",
    "leadershipExercise" -> "workingTogetherDevelopingSelfAndOthersAverage,communicatingAndInfluencingAverage,seeingTheBigPictureAverage",
    "groupExercise" -> "makingEffectiveDecisionsAverage,communicatingAndInfluencingAverage,workingTogetherDevelopingSelfAndOthersAverage"
  )

  val assessmentScoresFeedbackFields = Seq(
    "analysisExercise" -> "makingEffectiveDecisionsFeedback,communicatingAndInfluencingFeedback,seeingTheBigPictureFeedback",
    "leadershipExercise" -> "workingTogetherDevelopingSelfAndOthersFeedback,communicatingAndInfluencingFeedback,seeingTheBigPictureFeedback",
    "groupExercise" -> "makingEffectiveDecisionsFeedback,communicatingAndInfluencingFeedback,workingTogetherDevelopingSelfAndOthersFeedback"
  )

  val assessmentScoresNumericFieldsMap: Map[String, String] = assessmentScoresNumericFields.toMap

  val assessmentScoresFeedbackFieldsMap: Map[String, String] = assessmentScoresFeedbackFields.toMap

  def assessmentScoresHeaders(assessor: String): String = {
    assessmentScoresNumericFields.map(_._1).map { exercise =>
      s"$assessor $exercise attended,updatedBy,submittedDate,${assessmentScoresNumericFieldsMap(exercise)},${assessmentScoresFeedbackFieldsMap(exercise)}"
    }.mkString(",") + s",$assessor Final feedback,updatedBy,acceptedDate"
  }

  def applicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String]): Enumerator[CandidateDetailsReportItem]

  def dataAnalystApplicationDetailsStreamPt1(numOfSchemes: Int): Enumerator[CandidateDetailsReportItem]
  def dataAnalystApplicationDetailsStreamPt2: Enumerator[CandidateDetailsReportItem]
  def dataAnalystApplicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String]): Enumerator[CandidateDetailsReportItem]

  def findApplicationsFor(appRoutes: Seq[ApplicationRoute]): Future[List[Candidate]]
  def findApplicationsFor(appRoutes: Seq[ApplicationRoute],
                          appStatuses: Seq[ApplicationStatus]): Future[List[Candidate]]

  def findDataAnalystContactDetails: Future[CsvExtract[String]]
  def findDataAnalystContactDetails(userIds: Seq[String]): Future[CsvExtract[String]]
  def findContactDetails(userIds: Seq[String]): Future[CsvExtract[String]]

  def findQuestionnaireDetails: Future[CsvExtract[String]]
  def findQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]]
  def findDataAnalystQuestionnaireDetails: Future[CsvExtract[String]]
  def findDataAnalystQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]]

  def findMediaDetails: Future[CsvExtract[String]]
  def findMediaDetails(userIds: Seq[String]): Future[CsvExtract[String]]

  def findAssessorAssessmentScores: Future[CsvExtract[String]]
  def findAssessorAssessmentScores(applicationIds: Seq[String]): Future[CsvExtract[String]]

  def findReviewerAssessmentScores: Future[CsvExtract[String]]
  def findReviewerAssessmentScores(applicationIds: Seq[String]): Future[CsvExtract[String]]

  def findEventsDetails: Future[CsvExtract[String]]
  def findEventsDetails(applicationIds: Seq[String]): Future[CsvExtract[String]]

  def findSiftAnswers: Future[CsvExtract[String]]
  def findSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]]
  def findDataAnalystSiftAnswers: Future[CsvExtract[String]]
  def findDataAnalystSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]]
}

class PreviousYearCandidatesDetailsMongoRepository()(implicit mongo: () => DB)
  extends PreviousYearCandidatesDetailsRepository with CommonBSONDocuments with DiversityQuestionsText {

  import config.MicroserviceAppConfig._

  val applicationDetailsCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION)

  val contactDetailsCollection = mongo().collection[JSONCollection](CollectionNames.CONTACT_DETAILS)

  val questionnaireCollection = mongo().collection[JSONCollection](CollectionNames.QUESTIONNAIRE)

  val mediaCollection = mongo().collection[JSONCollection](CollectionNames.MEDIA)

  val assessorAssessmentScoresCollection = mongo().collection[JSONCollection](CollectionNames.ASSESSOR_ASSESSMENT_SCORES)

  val reviewerAssessmentScoresCollection = mongo().collection[JSONCollection](CollectionNames.REVIEWER_ASSESSMENT_SCORES)

  val candidateAllocationCollection = mongo().collection[JSONCollection](CollectionNames.CANDIDATE_ALLOCATION)

  val eventCollection = mongo().collection[JSONCollection](CollectionNames.ASSESSMENT_EVENTS)

  val siftAnswersCollection = mongo().collection[JSONCollection](CollectionNames.SIFT_ANSWERS)

  private val Y = Some("Yes")
  private val N = Some("No")

  private var adsCounter = 0

  private val unlimitedMaxDocs = -1

  override def applicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String])
  : Enumerator[CandidateDetailsReportItem] = {
    adsCounter = 0

    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    val projection = Json.obj("_id" -> 0)

    import reactivemongo.play.iteratees.cursorProducer
    applicationDetailsCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .enumerator().map { doc =>

      try {
        val applicationId = doc.getAs[String]("applicationId").get
        val progressResponse = toProgressResponse(applicationId).read(doc)
        val (civilServiceExperienceType, civilServiceInternshipTypes, fastPassCertificateNo) = civilServiceExperience(doc)

        val schemePrefs: List[String] = doc.getAs[BSONDocument]("scheme-preferences").flatMap(_.getAs[List[String]]("schemes")).getOrElse(Nil)
        val schemePrefsAsString: Option[String] = Some(schemePrefs.mkString(","))
        val schemesYesNoAsString: Option[String] = Option((schemePrefs.map(_ + ": Yes") ::: allSchemes.filterNot(schemePrefs.contains).map(_ + ": No")).mkString(","))

        val onlineTestResults = onlineTests(doc)

        // Get withdrawer
        val withdrawalInfo = doc.getAs[WithdrawApplication]("withdraw")

        def maybePrefixWithdrawer(withdrawerOpt: Option[String]): Option[String] = withdrawerOpt.map { withdrawer =>
          if (withdrawer.nonEmpty && withdrawer != "Candidate") {
            "Admin (User ID: " + withdrawer + ")"
          } else {
            withdrawer
          }
        }

        val fsacIndicator = doc.getAs[FSACIndicator]("fsac-indicator")

        adsCounter += 1
        val applicationIdOpt = doc.getAs[String]("applicationId")
        val csvContent = makeRow(
          List(applicationIdOpt) :::
            List(doc.getAs[String]("userId")) :::
            List(doc.getAs[String]("testAccountId")) :::
            List(doc.getAs[String]("frameworkId")) :::
            List(doc.getAs[String]("applicationStatus")) :::
            List(doc.getAs[String]("applicationRoute")) :::
            personalDetails(doc) :::
            List(progressResponseReachedYesNo(progressResponse.personalDetails)) :::
            List(progressResponseReachedYesNo(progressResponse.personalDetails)) :::
            civilServiceExperienceCheckExpType(civilServiceExperienceType, CivilServiceExperienceType.DiversityInternship.toString) :::
            civilServiceExperienceCheckExpType(civilServiceExperienceType, CivilServiceExperienceType.CivilServant.toString) :::
            civilServiceExperienceCheckExpType(civilServiceExperienceType, CivilServiceExperienceType.CivilServantViaFastTrack.toString) :::
            civilServiceExperienceCheckInternshipType(civilServiceInternshipTypes, InternshipType.EDIP.toString) :::
            civilServiceExperienceCheckInternshipType(civilServiceInternshipTypes, InternshipType.SDIPPreviousYear.toString) :::
            civilServiceExperienceCheckInternshipType(civilServiceInternshipTypes, InternshipType.SDIPCurrentYear.toString) :::
            List(fastPassCertificateNo) ::: //Fast Pass No
            List(schemePrefsAsString) ::: //Scheme preferences
            List(schemesYesNoAsString) ::: //Scheme names
            List(progressResponseReachedYesNo(progressResponse.schemePreferences)) ::: //Are you happy with order
            List(progressResponseReachedYesNo(progressResponse.schemePreferences)) ::: //Are you eligible
            assistanceDetails(doc) :::
            List(progressResponseReachedYesNo(progressResponse.questionnaire.nonEmpty)) ::: //I understand this wont affect application

            onlineTestResults(Tests.Phase1Test1.toString) :::
            onlineTestResults(Tests.Phase1Test2.toString) :::
            onlineTestResults(Tests.Phase1Test3.toString) :::
            onlineTestResults(Tests.Phase1Test4.toString) :::
            onlineTestResults(Tests.Phase2Test1.toString) :::
            onlineTestResults(Tests.Phase2Test2.toString) :::
            videoInterview(doc) :::
            onlineTestResults(Tests.SiftTest.toString) :::

            fsbScoresAndFeedback(doc) :::
            progressStatusTimestamps(doc) :::
            fsacCompetency(doc) :::
            testEvaluations(doc, numOfSchemes) :::
            currentSchemeStatus(doc, numOfSchemes) :::
            List(maybePrefixWithdrawer(withdrawalInfo.map(_.withdrawer))) :::
            List(withdrawalInfo.map(_.reason)) :::
            List(withdrawalInfo.map(_.otherReason.getOrElse(""))) :::
            List(doc.getAs[String]("issue")) :::
            List(fsacIndicator.map(_.area)) :::
            List(fsacIndicator.map(_.assessmentCentre)) :::
            List(fsacIndicator.map(_.version))
            : _*
        )
        CandidateDetailsReportItem(
          doc.getAs[String]("applicationId").getOrElse(""),
          doc.getAs[String]("userId").getOrElse(""), csvContent
        )
      } catch {
        case ex: Throwable =>
          Logger.error("Previous year candidate report generation exception", ex)
          CandidateDetailsReportItem("", "", "ERROR LINE " + ex.getMessage)
      }
    }
  }

  private def lastProgressStatus(doc: BSONDocument): Option[String] = {
    val progressStatusTimestamps = doc.getAs[BSONDocument]("progress-status-timestamp")

    def timestampFor(progressStatus: ProgressStatuses.ProgressStatus) = {
      progressStatusTimestamps.getAs[DateTime](progressStatus.toString)
    }

    val progressStatusesWithTimestamps = List(
      ProgressStatuses.SUBMITTED, ProgressStatuses.FAST_PASS_ACCEPTED,
      ProgressStatuses.PHASE1_TESTS_INVITED, ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER, ProgressStatuses.PHASE1_TESTS_STARTED,
      ProgressStatuses.PHASE1_TESTS_COMPLETED, ProgressStatuses.PHASE1_TESTS_EXPIRED,
      ProgressStatuses.PHASE1_TESTS_RESULTS_READY, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE1_TESTS_PASSED, ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED,
      ProgressStatuses.PHASE1_TESTS_FAILED, ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED,
      ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_AMBER, ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN,

      ProgressStatuses.PHASE2_TESTS_INVITED, ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER,
      ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER, ProgressStatuses.PHASE2_TESTS_STARTED,
      ProgressStatuses.PHASE2_TESTS_COMPLETED, ProgressStatuses.PHASE2_TESTS_EXPIRED,
      ProgressStatuses.PHASE2_TESTS_RESULTS_READY, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE2_TESTS_PASSED, ProgressStatuses.PHASE2_TESTS_FAILED,
      ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED, ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_AMBER,
      ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN,

      ProgressStatuses.PHASE3_TESTS_INVITED,
      ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER, ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER,
      ProgressStatuses.PHASE3_TESTS_STARTED, ProgressStatuses.PHASE3_TESTS_COMPLETED,
      ProgressStatuses.PHASE3_TESTS_EXPIRED, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED,
      ProgressStatuses.PHASE3_TESTS_PASSED_WITH_AMBER, ProgressStatuses.PHASE3_TESTS_PASSED,
      ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED, ProgressStatuses.PHASE3_TESTS_FAILED,
      ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED, ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_AMBER,
      ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN,

      ProgressStatuses.SIFT_ENTERED, ProgressStatuses.SIFT_TEST_INVITED,
      ProgressStatuses.SIFT_TEST_STARTED, ProgressStatuses.SIFT_TEST_COMPLETED,
      ProgressStatuses.SIFT_FIRST_REMINDER, ProgressStatuses.SIFT_SECOND_REMINDER,
      ProgressStatuses.SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING, ProgressStatuses.SIFT_TEST_RESULTS_READY,
      ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED, ProgressStatuses.SIFT_READY,
      ProgressStatuses.SIFT_COMPLETED, ProgressStatuses.SIFT_EXPIRED,
      ProgressStatuses.SIFT_EXPIRED_NOTIFIED, ProgressStatuses.FAILED_AT_SIFT,
      ProgressStatuses.FAILED_AT_SIFT_NOTIFIED, ProgressStatuses.SDIP_FAILED_AT_SIFT,
      ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN,

      ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION, ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
      ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED, ProgressStatuses.ASSESSMENT_CENTRE_FAILED_TO_ATTEND,
      ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED, ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED,
      ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION, ProgressStatuses.ASSESSMENT_CENTRE_PASSED,
      ProgressStatuses.ASSESSMENT_CENTRE_FAILED, ProgressStatuses.ASSESSMENT_CENTRE_FAILED_NOTIFIED,
      ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN, ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED,

      ProgressStatuses.FSB_AWAITING_ALLOCATION, ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED,
      ProgressStatuses.FSB_ALLOCATION_CONFIRMED, ProgressStatuses.FSB_FAILED_TO_ATTEND,
      ProgressStatuses.FSB_RESULT_ENTERED, ProgressStatuses.FSB_PASSED,
      ProgressStatuses.FSB_FAILED, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED,
      ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED_NOTIFIED,

      ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED
    ).map( status => status -> timestampFor(status)).filter{ case (_ , dt) => dt.isDefined }

    val default = new DateTime(1970, 1, 1, 0, 0, 0, 0)
    val sorted = progressStatusesWithTimestamps.sortBy{ case (_, dt) => dt}(Ordering.fromLessThan(_.getOrElse(default) isAfter _.getOrElse(default)))

    if (sorted.nonEmpty) {
      val (progressStatus, _) = sorted.head
      Some(progressStatus.toString)
    } else {
      None
    }
  }

  private def lastProgressStatusPriorToWithdrawal(doc: BSONDocument): Option[String] = {
    doc.getAs[String]("applicationStatus").flatMap { status =>
      if (ApplicationStatus.WITHDRAWN == ApplicationStatus.withName(status)) {
        lastProgressStatus(doc)
      } else {
        None
      }
    }
  }

  override def dataAnalystApplicationDetailsStreamPt1(numOfSchemes: Int): Enumerator[CandidateDetailsReportItem] = {
    val query = BSONDocument.empty
    commonDataAnalystApplicationDetailsStream(numOfSchemes, query)
  }

  // Just fetch the applicationId and userId for Pt2 report
  override def dataAnalystApplicationDetailsStreamPt2: Enumerator[CandidateDetailsReportItem] = {
    val query = BSONDocument.empty
    val projection = Json.obj("_id" -> 0)

    import reactivemongo.play.iteratees.cursorProducer
    applicationDetailsCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .enumerator().map { doc =>

      try {
        val applicationIdOpt = doc.getAs[String]("applicationId")
        val csvContent = makeRow(
          List(applicationIdOpt): _*
        )
        CandidateDetailsReportItem(
          doc.getAs[String]("applicationId").getOrElse(""),
          doc.getAs[String]("userId").getOrElse(""), csvContent
        )
      } catch {
        case ex: Throwable =>
          Logger.error("Data analyst Previous year candidate report generation exception", ex)
          CandidateDetailsReportItem("", "", "ERROR LINE " + ex.getMessage)
      }
    }
  }

  override def dataAnalystApplicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String]): Enumerator[CandidateDetailsReportItem] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    commonDataAnalystApplicationDetailsStream(numOfSchemes, query)
  }

  private def commonDataAnalystApplicationDetailsStream(numOfSchemes: Int, query: BSONDocument): Enumerator[CandidateDetailsReportItem] = {

    def isSdipFsWithFsFailedAndSdipNotFailed(doc: BSONDocument) = {
      val css: Seq[SchemeEvaluationResult] = doc.getAs[Seq[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(Nil)
      val sdipSchemeId = SchemeId("Sdip")
      val sdipPresent = css.count( schemeEvaluationResult => schemeEvaluationResult.schemeId == sdipSchemeId ) == 1
      val sdipNotFailed = sdipPresent &&
        css.count( schemeEvaluationResult => schemeEvaluationResult.schemeId == sdipSchemeId && schemeEvaluationResult.result != "Red") == 1
      val fsSchemes = css.filterNot( ser => ser.schemeId == sdipSchemeId )
      val fsSchemesPresentAndAllFailed = fsSchemes.nonEmpty &&
        fsSchemes.count( schemeEvaluationResult => schemeEvaluationResult.result == "Red" ) == fsSchemes.size
      val isSdipFaststream = doc.getAs[String]("applicationRoute").contains("SdipFaststream")
      isSdipFaststream && sdipPresent && sdipNotFailed && fsSchemesPresentAndAllFailed
    }

    val projection = Json.obj("_id" -> 0)

    import reactivemongo.play.iteratees.cursorProducer
    applicationDetailsCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .enumerator().map { doc =>

      try {
        val (civilServiceExperienceType, civilServiceInternshipTypes, fastPassCertificateNo) = civilServiceExperience(doc)
        val schemePrefs: List[String] = doc.getAs[BSONDocument]("scheme-preferences").flatMap(_.getAs[List[String]]("schemes")).getOrElse(Nil)
        val schemePrefsAsString: Option[String] = Some(schemePrefs.mkString(","))
        val fsacIndicator = doc.getAs[FSACIndicator]("fsac-indicator")

        val applicationIdOpt = doc.getAs[String]("applicationId")
        val csvContent = makeRow(
          List(applicationIdOpt) :::
            List(doc.getAs[String]("applicationStatus")) :::
            List(doc.getAs[String]("applicationRoute")) :::
            List(Some(isSdipFsWithFsFailedAndSdipNotFailed(doc).toString)) :::
            civilServiceExperienceCheckExpType(civilServiceExperienceType, CivilServiceExperienceType.CivilServant.toString) :::
            civilServiceExperienceCheckExpType(civilServiceExperienceType, CivilServiceExperienceType.CivilServantViaFastTrack.toString) :::
            civilServiceExperienceCheckInternshipType(civilServiceInternshipTypes, InternshipType.SDIPCurrentYear.toString) :::
            List(fastPassCertificateNo) :::
            List(schemePrefsAsString) :::
            hasDisability(doc) :::
            progressStatusTimestamps(doc) :::
            List(lastProgressStatusPriorToWithdrawal(doc)) :::
            testEvaluations(doc, numOfSchemes) :::
            currentSchemeStatus(doc, numOfSchemes) :::
            List(fsacIndicator.map(_.area)) :::
            List(fsacIndicator.map(_.assessmentCentre))
            : _*
        )
        CandidateDetailsReportItem(
          doc.getAs[String]("applicationId").getOrElse(""),
          doc.getAs[String]("userId").getOrElse(""), csvContent
        )
      } catch {
        case ex: Throwable =>
          Logger.error("Data analyst streamed candidate report generation exception", ex)
          CandidateDetailsReportItem("", "", "ERROR LINE " + ex.getMessage)
      }
    }
  }

  private def progressStatusTimestamps(doc: BSONDocument): List[Option[String]] = {

    val statusTimestamps = doc.getAs[BSONDocument]("progress-status-timestamp")
    val progressStatus = doc.getAs[BSONDocument]("progress-status")
    val progressStatusDates = doc.getAs[BSONDocument]("progress-status-dates")

    val questionnaireStatuses = progressStatus.flatMap(_.getAs[BSONDocument]("questionnaire"))

    def timestampFor(status: ProgressStatuses.ProgressStatus) = statusTimestamps.getAsStr[DateTime](status.toString)

    def questionnaireStatus(key: String): Option[String] = questionnaireStatuses match {
      case None => Some("false")
      case Some(stat) => stat.getAs[Boolean](key).orElse(Some(false)).map(_.toString)
    }

    List(
      progressStatus.getOrElseAsStr[Boolean]("personal-details")(false),
      statusTimestamps.getAsStr[DateTime]("IN_PROGRESS").orElse(progressStatusDates.flatMap(_.getAs[String]("in_progress"))
      ),
      progressStatus.getOrElseAsStr[Boolean]("scheme-preferences")(false),
      progressStatus.getOrElseAsStr[Boolean]("assistance-details")(false),
      questionnaireStatus("start_questionnaire"),
      questionnaireStatus("diversity_questionnaire"),
      questionnaireStatus("education_questionnaire"),
      questionnaireStatus("occupation_questionnaire"),
      progressStatus.getOrElseAsStr[Boolean]("preview")(false),
      timestampFor(ProgressStatuses.SUBMITTED).orElse(progressStatusDates.getAsStr[String]("submitted")),
      timestampFor(ProgressStatuses.FAST_PASS_ACCEPTED).orElse(progressStatusDates.getAsStr[String]("FAST_PASS_ACCEPTED")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_INVITED).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_INVITED")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_FIRST_REMINDER")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_SECOND_REMINDER")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_STARTED).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_STARTED")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_COMPLETED).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_COMPLETED")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_EXPIRED).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_EXPIRED")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_RESULTS_READY).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_RESULTS_READY")),
      timestampFor(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).orElse(progressStatusDates.getAsStr[String]("PHASE1_TESTS_RESULTS_RECEIVED"))
    ) ++
      List(
        ProgressStatuses.PHASE1_TESTS_PASSED,
        ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED,
        ProgressStatuses.PHASE1_TESTS_FAILED,
        ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED,
        ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_AMBER,
        ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_GREEN,
        ProgressStatuses.PHASE2_TESTS_INVITED,
        ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER,
        ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER,
        ProgressStatuses.PHASE2_TESTS_STARTED,
        ProgressStatuses.PHASE2_TESTS_COMPLETED,
        ProgressStatuses.PHASE2_TESTS_EXPIRED,
        ProgressStatuses.PHASE2_TESTS_RESULTS_READY,
        ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED,
        ProgressStatuses.PHASE2_TESTS_PASSED,
        ProgressStatuses.PHASE2_TESTS_FAILED,
        ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED,
        ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_AMBER,
        ProgressStatuses.PHASE2_TESTS_FAILED_SDIP_GREEN,
        ProgressStatuses.PHASE3_TESTS_INVITED,
        ProgressStatuses.PHASE3_TESTS_FIRST_REMINDER,
        ProgressStatuses.PHASE3_TESTS_SECOND_REMINDER,
        ProgressStatuses.PHASE3_TESTS_STARTED,
        ProgressStatuses.PHASE3_TESTS_COMPLETED,
        ProgressStatuses.PHASE3_TESTS_EXPIRED,
        ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED,
        ProgressStatuses.PHASE3_TESTS_PASSED_WITH_AMBER,
        ProgressStatuses.PHASE3_TESTS_PASSED,
        ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED,
        ProgressStatuses.PHASE3_TESTS_FAILED,
        ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED,
        ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_AMBER,
        ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_GREEN
      ).map(timestampFor) ++
      List(
        ProgressStatuses.SIFT_ENTERED,
        ProgressStatuses.SIFT_TEST_INVITED,
        ProgressStatuses.SIFT_TEST_STARTED,
        ProgressStatuses.SIFT_TEST_COMPLETED,
        ProgressStatuses.SIFT_FIRST_REMINDER,
        ProgressStatuses.SIFT_SECOND_REMINDER,
        ProgressStatuses.SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING,
        ProgressStatuses.SIFT_TEST_RESULTS_READY,
        ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED,
        ProgressStatuses.SIFT_READY,
        ProgressStatuses.SIFT_COMPLETED,
        ProgressStatuses.SIFT_EXPIRED,
        ProgressStatuses.SIFT_EXPIRED_NOTIFIED,
        ProgressStatuses.FAILED_AT_SIFT,
        ProgressStatuses.FAILED_AT_SIFT_NOTIFIED,
        ProgressStatuses.SDIP_FAILED_AT_SIFT,
        ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN
      ).map(timestampFor) ++
      List(
        ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION,
        ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
        ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED,
        ProgressStatuses.ASSESSMENT_CENTRE_FAILED_TO_ATTEND,
        ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED,
        ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED,
        ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION,
        ProgressStatuses.ASSESSMENT_CENTRE_PASSED,
        ProgressStatuses.ASSESSMENT_CENTRE_FAILED,
        ProgressStatuses.ASSESSMENT_CENTRE_FAILED_NOTIFIED,
        ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN,
        ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED
      ).map(timestampFor) ++
      List(
        ProgressStatuses.FSB_AWAITING_ALLOCATION,
        ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED,
        ProgressStatuses.FSB_ALLOCATION_CONFIRMED,
        ProgressStatuses.FSB_FAILED_TO_ATTEND,
        ProgressStatuses.FSB_RESULT_ENTERED,
        ProgressStatuses.FSB_PASSED,
        ProgressStatuses.FSB_FAILED,
        ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED,
        ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED_NOTIFIED
      ).map(timestampFor) ++
      List(
        ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER,
        ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED
      ).map(timestampFor) ++
      List(
        ProgressStatuses.WITHDRAWN
      ).map(timestampFor)
  }

  private def civilServiceExperienceCheckExpType(civilServExperienceType: Option[String], typeToMatch: String) =
    List(if (civilServExperienceType.contains(typeToMatch)) Y else N)

  private def civilServiceExperienceCheckInternshipType(
    civilServExperienceInternshipTypes: Option[List[String]],
    typeToMatch: String
  ) = List(if (civilServExperienceInternshipTypes.exists(_.contains(typeToMatch))) Y else N)

  private def progressResponseReachedYesNo(progressResponseReached: Boolean) = if (progressResponseReached) Y else N

  override def findApplicationsFor(appRoutes: Seq[ApplicationRoute]): Future[List[Candidate]] = {
    val query = BSONDocument("applicationRoute" -> BSONDocument("$in" -> appRoutes))
    applicationDetailsCollection.find(query, projection = Option.empty[JsObject]).cursor[Candidate]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
  }

  override def findApplicationsFor(appRoutes: Seq[ApplicationRoute],
                            appStatuses: Seq[ApplicationStatus]): Future[List[Candidate]] = {
    val query = BSONDocument( "$and" -> BSONArray(
      BSONDocument("applicationRoute" -> BSONDocument("$in" -> appRoutes)),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> appStatuses))
    ))
    applicationDetailsCollection.find(query, projection = Option.empty[JsObject]).cursor[Candidate]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Candidate]]())
  }

  override def findDataAnalystContactDetails: Future[CsvExtract[String]] = {
    val query = BSONDocument.empty
    commonFindDataAnalystContactDetails(query)
  }

  override def findDataAnalystContactDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = BSONDocument("userId" -> BSONDocument("$in" -> userIds))
    commonFindDataAnalystContactDetails(query)
  }

  private def commonFindDataAnalystContactDetails(query: BSONDocument): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    contactDetailsCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      val csvRecords = docs.map { doc =>
        val contactDetails = doc.getAs[BSONDocument]("contact-details")
        val csvRecord = makeRow(
          contactDetails.getAsStr[String]("email"),
          contactDetails.getAsStr[String]("postCode")
        )
        doc.getAs[String]("userId").getOrElse("") -> csvRecord
      }
      CsvExtract(dataAnalystContactDetailsHeader, csvRecords.toMap)
    }
  }

  override def findContactDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)
    val query = BSONDocument("userId" -> BSONDocument("$in" -> userIds))

    contactDetailsCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      val csvRecords = docs.map { doc =>
        val contactDetails = doc.getAs[BSONDocument]("contact-details")
        val address = contactDetails.flatMap(_.getAs[BSONDocument]("address"))
        val csvRecord = makeRow(
          contactDetails.getAsStr[String]("email"),
          address.getAsStr[String]("line1"),
          address.getAsStr[String]("line2"),
          address.getAsStr[String]("line3"),
          address.getAsStr[String]("line4"),
          contactDetails.getAsStr[String]("postCode"),
          contactDetails.flatMap(_.getAs[Boolean]("outsideUk")).flatMap(outside => if (outside) Y else N),
          contactDetails.getAsStr[String]("country"),
          contactDetails.getAsStr[String]("phone")
        )
        doc.getAs[String]("userId").getOrElse("") -> csvRecord
      }
      CsvExtract(contactDetailsHeader, csvRecords.toMap)
    }
  }

  override def findDataAnalystQuestionnaireDetails: Future[CsvExtract[String]] = {
    val query = BSONDocument.empty
    val projection = Json.obj("_id" -> false)

    commonFindQuestionnaireDetails(query, projection)
  }

  override def findDataAnalystQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    val projection = Json.obj("_id" -> false)

    commonFindQuestionnaireDetails(query, projection)
  }

  override def findQuestionnaireDetails: Future[CsvExtract[String]] = {
    findQuestionnaireDetails(Seq.empty[String])
  }

  override def findQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    val projection = Json.obj("_id" -> 0)

    commonFindQuestionnaireDetails(query, projection)
  }

  private def commonFindQuestionnaireDetails(query: BSONDocument, projection: JsObject): Future[CsvExtract[String]] = {

    def getAnswer(question: String, doc: Option[BSONDocument]) = {
      val questionDoc = doc.flatMap(_.getAs[BSONDocument](question))
      val isUnknown = questionDoc.flatMap(_.getAs[Boolean]("unknown")).contains(true)
      if (isUnknown) {
        Some("Unknown")
      } else {
        questionDoc.flatMap(q => q.getAs[String]("answer") match {
          case None => q.getAs[String]("otherDetails")
          case Some(answer) if List("Other", "Other ethnic group").contains(answer) => q.getAs[String]("otherDetails")
          case Some(answer) => Some(answer)
        })
      }
    }

    questionnaireCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      val csvRecords = docs.map { doc =>
        val questionsDoc = doc.getAs[BSONDocument]("questions")
        val universityNameAnswer = getAnswer("What is the name of the university you received your degree from?", questionsDoc)

        val allQuestionsAndAnswers = questionsDoc.toList.flatMap(_.elements).map { bsonElement =>
          val question = bsonElement.name
          val answer = getAnswer(question, questionsDoc).getOrElse("Unknown")
          question -> answer
        }.toMap

        val csvRecord = makeRow(
          getAnswer(genderIdentity, questionsDoc),
          getAnswer(sexualOrientation, questionsDoc),
          getAnswer(ethnicGroup, questionsDoc),
          getAnswer(liveInUkAged14to18, questionsDoc),
          getAnswer(postcodeAtAge14, questionsDoc),
          getAnswer(schoolNameAged14to16, questionsDoc),
          getAnswer(schoolTypeAged14to16, questionsDoc),
          getAnswer(schoolNameAged16to18, questionsDoc),
          getAnswer(eligibleForFreeSchoolMeals, questionsDoc),
          universityNameAnswer,
          getAnswer(categoryOfDegree, questionsDoc),
          getAnswer(lowerSocioEconomicBackground, questionsDoc),
          getAnswer(parentOrGuardianQualificationsAtAge18, questionsDoc),
          getAnswer(highestEarningParentOrGuardianTypeOfWorkAtAge14, questionsDoc),
          getAnswer(employeeOrSelfEmployed, questionsDoc),
          getAnswer(sizeOfPlaceOfWork, questionsDoc),
          getAnswer(superviseEmployees, questionsDoc),
          Some(SocioEconomicCalculator.calculate(allQuestionsAndAnswers)),
          isOxbridge(universityNameAnswer),
          isRussellGroup(universityNameAnswer)
        )
        doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
      }
      CsvExtract(questionnaireDetailsHeader, csvRecords.toMap)
    }
  }

  override def findSiftAnswers: Future[CsvExtract[String]] = {
    findSiftAnswers(Seq.empty[String])
  }

  override def findSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    siftAnswersCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      val csvRecords = docs.map { doc =>
        val schemeAnswers = doc.getAs[BSONDocument]("schemeAnswers")
        val schemeTextAnswers = allSchemes.map { s =>
          schemeAnswers.getAs[BSONDocument](s).getAsStr[String]("rawText")
        }

        val generalAnswers = doc.getAs[BSONDocument]("generalAnswers")
        val undergradDegree = generalAnswers.getAs[BSONDocument]("undergradDegree")
        val postgradDegree = generalAnswers.getAs[BSONDocument]("postgradDegree")

        val csvRecord = makeRow(
          List(
            doc.getAs[String]("status"),
            generalAnswers.getAsStr[String]("multipleNationalities"),
            generalAnswers.getAsStr[String]("secondNationality"),
            generalAnswers.getAsStr[String]("nationality"),
            undergradDegree.getAsStr[String]("name"),
            undergradDegree.getAsStr[String]("classification"),
            undergradDegree.getAsStr[String]("graduationYear"),
            undergradDegree.getAsStr[String]("moduleDetails"),
            postgradDegree.getAsStr[String]("name"),
            postgradDegree.getAsStr[String]("classification"),
            postgradDegree.getAsStr[String]("graduationYear"),
            postgradDegree.getAsStr[String]("moduleDetails")
          ) ++ schemeTextAnswers: _*
        )
        doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
      }.toMap
      CsvExtract(siftAnswersHeader, csvRecords)
    }
  }

  override def findDataAnalystSiftAnswers: Future[CsvExtract[String]] = {
    val query = BSONDocument.empty
    commonFindDataAnalystSiftAnswers(query)
  }

  override def findDataAnalystSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))
    commonFindDataAnalystSiftAnswers(query)
  }

  private def commonFindDataAnalystSiftAnswers(query: BSONDocument): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    siftAnswersCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      val csvRecords = docs.map { doc =>

        val generalAnswers = doc.getAs[BSONDocument]("generalAnswers")
        val undergradDegree = generalAnswers.getAs[BSONDocument]("undergradDegree")

        val csvRecord = makeRow(
          List(
            generalAnswers.getAsStr[String]("nationality"),
            undergradDegree.getAsStr[String]("name"),
            undergradDegree.getAsStr[String]("classification"),
            undergradDegree.getAsStr[String]("graduationYear")
          ): _*
        )
        doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
      }.toMap
      CsvExtract(dataAnalystSiftAnswersHeader, csvRecords)
    }
  }

  override def findEventsDetails: Future[CsvExtract[String]] = {
    findEventsDetails(Seq.empty[String])
  }

  override def findEventsDetails(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    val allEventsFut = eventCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      docs.map { doc =>
        val id = doc.getAs[String]("id")
        id.getOrElse("") ->
          List(
            id,
            doc.getAs[String]("eventType"),
            doc.getAs[String]("description"),
            doc.getAs[BSONDocument]("location").flatMap(_.getAs[String]("name")),
            doc.getAs[BSONDocument]("venue").flatMap(_.getAs[String]("description")),
            doc.getAs[String]("date")
          ).flatten.mkString(" ")
      }.toMap
    }

    allEventsFut.flatMap { allEvents =>
      candidateAllocationCollection.find(Json.obj(), Some(projection))
        .cursor[BSONDocument](ReadPreference.nearest)
        .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
        val csvRecords = docs.map { doc =>
          val eventId = doc.getAs[String]("eventId").getOrElse("-")
          val csvRecord = makeRow(
            Some(List(
              allEvents.get(eventId).map(e => s"Event: $e"),
              doc.getAs[String]("sessionId").map(s => s"session: $eventId/$s"),
              doc.getAs[String]("status"),
              doc.getAs[String]("removeReason"),
              doc.getAs[String]("createdAt").map(d => s"on $d")
            ).flatten.mkString(" ")
            )
          )
          doc.getAs[String]("id").getOrElse("") -> csvRecord
        }.groupBy { case (id, _) => id }.map {
          case (appId, events) => appId -> events.map { case (_, eventList) => eventList }.mkString(" --- ")
        }
        CsvExtract(eventsDetailsHeader, csvRecords)
      }
    }
  }

  override def findMediaDetails: Future[CsvExtract[String]] = {
    val query = BSONDocument()
    val projection = Json.obj("_id" -> 0)

    commonFindMediaDetails(query, projection)
  }

  override def findMediaDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = BSONDocument("userId" -> BSONDocument("$in" -> userIds))
    val projection = Json.obj("_id" -> 0)

    commonFindMediaDetails(query, projection)
  }

  private def commonFindMediaDetails(query: BSONDocument, projection: JsObject) = {
    mediaCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      val csvRecords = docs.map { doc =>
        val csvRecord = makeRow(
          doc.getAs[String]("media")
        )
        doc.getAs[String]("userId").getOrElse("") -> csvRecord
      }
      CsvExtract(mediaHeader, csvRecords.toMap)
    }
  }

  override def findAssessorAssessmentScores: Future[CsvExtract[String]] =
    findAssessmentScores("Assessor", assessorAssessmentScoresCollection, Seq.empty[String])

  override def findAssessorAssessmentScores(applicationIds: Seq[String]): Future[CsvExtract[String]] =
    findAssessmentScores("Assessor", assessorAssessmentScoresCollection, applicationIds)

  override def findReviewerAssessmentScores: Future[CsvExtract[String]] =
    findAssessmentScores("Reviewer", reviewerAssessmentScoresCollection, Seq.empty[String])

  override def findReviewerAssessmentScores(applicationIds: Seq[String]): Future[CsvExtract[String]] =
    findAssessmentScores("Reviewer", reviewerAssessmentScoresCollection, applicationIds)

  private def findAssessmentScores(name: String, col: JSONCollection, applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val exerciseSections = assessmentScoresNumericFields.map(_._1)
    val projection = Json.obj("_id" -> 0)
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    col.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map { docs =>
      val csvRecords = docs.map { doc =>
        val csvStr = exerciseSections.flatMap { s =>
          val section = doc.getAs[BSONDocument](s)
          List(
            section.getAsStr[Boolean]("attended"),
            section.getAsStr[String]("updatedBy"),
            section.getAsStr[DateTime]("submittedDate")
          ) ++
            assessmentScoresNumericFieldsMap(s).split(",").map { field =>
              section.getAsStr[Double](field)
            } ++
            assessmentScoresFeedbackFieldsMap(s).split(",").map { field =>
              section.getAsStr[String](field)
            }
        } ++ {
          val section = doc.getAs[BSONDocument]("finalFeedback")
          List(
            section.getAsStr[String]("feedback"),
            section.getAsStr[String]("updatedBy"),
            section.getAsStr[DateTime]("acceptedDate")
          )
        }
        val csvRecord = makeRow(csvStr: _*)
        doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
      }
      CsvExtract(assessmentScoresHeaders(name), csvRecords.toMap)
    }
  }

  private def isOxbridge(code: Option[String]): Option[String] = {
    code match {
      case Some("O33-OXF") | Some("C05-CAM") => Y
      case Some(_) => N
      case None => None
    }
  }

  private def isRussellGroup(code: Option[String]): Option[String] = {
    val russellGroupUnis = List(
      "B32-BIRM", "B78-BRISL", "C05-CAM", "C15-CARDF", "D86-DUR", "E56-EDINB", "E81-EXCO", "G28-GLASG", "I50-IMP", "K60-KCL",
      "L23-LEEDS", "L41-LVRPL", "L72-LSE", "M20-MANU", "N21-NEWC", "N84-NOTTM", "O33-OXF", "Q75-QBELF", "S18-SHEFD",
      "S27-SOTON", "U80-UCL", "W20-WARWK", "Y50-YORK"
    )
    code.flatMap(c => if (russellGroupUnis.contains(c)) Y else N)
  }

  private def fsbScoresAndFeedback(doc: BSONDocument): List[Option[String]] = {
    val scoresAndFeedbackOpt = for {
      testGroups <- doc.getAs[BSONDocument]("testGroups")
      fsb <- testGroups.getAs[BSONDocument]("FSB")
      scoresAndFeedback <- fsb.getAs[ScoresAndFeedback]("scoresAndFeedback")
    } yield scoresAndFeedback

    scoresAndFeedbackOpt.map { saf =>
      List(Some(saf.overallScore.toString), Some(saf.feedback))
    }.getOrElse( List(None, None) )
  }

  private def videoInterview(doc: BSONDocument): List[Option[String]] = {
    val testGroups = doc.getAs[BSONDocument]("testGroups")
    val videoTestSection = testGroups.getAs[BSONDocument]("PHASE3")
    val videoTests = videoTestSection.getAs[List[BSONDocument]]("tests")
    val activeVideoTest = videoTests.map(_.filter(_.getAs[Boolean]("usedForResults").getOrElse(false)).head)
    val activeVideoTestCallbacks = activeVideoTest.getAs[BSONDocument]("callbacks")
    val activeVideoTestReviewedCallbacks = activeVideoTestCallbacks.getAs[List[BSONDocument]]("reviewed")

    val latestAVTRCallback = activeVideoTestReviewedCallbacks.flatMap {
      reviewedCallbacks =>
        reviewedCallbacks.sortWith { (r1, r2) =>
          r1.getAs[DateTime]("received").get.isAfter(r2.getAs[DateTime]("received").get)
        }.headOption.map(_.as[ReviewedCallbackRequest])
    }

    val latestReviewer = latestAVTRCallback.map {
      callback =>
        callback.reviewers.reviewer3.getOrElse(
          callback.reviewers.reviewer2.getOrElse(
            callback.reviewers.reviewer1
          )
        )
    }

    def scoreForQuestion(question: ReviewSectionQuestionRequest) = {
      BigDecimal(question.reviewCriteria1.score.getOrElse(0.0)) + BigDecimal(question.reviewCriteria2.score.getOrElse(0.0))
    }

    def totalForQuestions(reviewer: ReviewSectionReviewerRequest): BigDecimal = {
      scoreForQuestion(reviewer.question1) +
        scoreForQuestion(reviewer.question2) +
        scoreForQuestion(reviewer.question3) +
        scoreForQuestion(reviewer.question4) +
        scoreForQuestion(reviewer.question5) +
        scoreForQuestion(reviewer.question6) +
        scoreForQuestion(reviewer.question7) +
        scoreForQuestion(reviewer.question8)
    }

    List(
      activeVideoTest.getAsStr[Int]("interviewId"),
      activeVideoTest.getAsStr[String]("token"),
      activeVideoTest.getAsStr[String]("candidateId"),
      activeVideoTest.getAsStr[String]("customCandidateId"),
      latestReviewer.flatMap(_.comment),
      latestAVTRCallback.map(_.received.toString),
      latestReviewer.flatMap(_.question1.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question1.reviewCriteria2.score.map(_.toString)),
      latestReviewer.flatMap(_.question2.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question2.reviewCriteria2.score.map(_.toString)),
      latestReviewer.flatMap(_.question3.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question3.reviewCriteria2.score.map(_.toString)),
      latestReviewer.flatMap(_.question4.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question4.reviewCriteria2.score.map(_.toString)),
      latestReviewer.flatMap(_.question5.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question5.reviewCriteria2.score.map(_.toString)),
      latestReviewer.flatMap(_.question6.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question6.reviewCriteria2.score.map(_.toString)),
      latestReviewer.flatMap(_.question7.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question7.reviewCriteria2.score.map(_.toString)),
      latestReviewer.flatMap(_.question8.reviewCriteria1.score.map(_.toString)),
      latestReviewer.flatMap(_.question8.reviewCriteria2.score.map(_.toString)),
      latestReviewer.map(reviewer => totalForQuestions(reviewer).toString)
    )
  }

  private def getSchemeResults(results: List[BSONDocument]) = results.map {
    result => result.getAs[String]("schemeId").getOrElse("") + ": " + result.getAs[String]("result").getOrElse("")
  }

  private def testEvaluations(doc: BSONDocument, numOfSchemes: Int): List[Option[String]] = {
    val testGroups = doc.getAs[BSONDocument]("testGroups")

    List("PHASE1", "PHASE2", "PHASE3", "SIFT_PHASE", "FSAC", "FSB").flatMap { sectionName => {
      val testSection = testGroups.getAs[BSONDocument](sectionName)
      val testsEvaluation = testSection.getAs[BSONDocument]("evaluation")
      val testEvalResults = testsEvaluation.getAs[List[BSONDocument]]("result")
        .orElse(testsEvaluation.getAs[List[BSONDocument]]("schemes-evaluation"))
      val evalResultsMap = testEvalResults.map(getSchemeResults)
      padResults(evalResultsMap, numOfSchemes)
    }}
  }

  private def currentSchemeStatus(doc: BSONDocument, numOfSchemes: Int): List[Option[String]] = {
    val testEvalResults = doc.getAs[List[BSONDocument]]("currentSchemeStatus")
    val evalResultsMap = testEvalResults.map(getSchemeResults)
    padResults(evalResultsMap, numOfSchemes)
  }

  private def padResults(evalResultsMap: Option[List[String]], numOfSchemes: Int) = {
    val schemeResults = evalResultsMap.getOrElse(Nil)
    schemeResults.map(Option(_)) ::: (1 to (numOfSchemes - schemeResults.size)).toList.map(_ => Some(""))
  }

  private def fsacCompetency(doc: BSONDocument): List[Option[String]] = {
    val testGroups = doc.getAs[BSONDocument]("testGroups")
    val testSection = testGroups.getAs[BSONDocument]("FSAC")
    val testsEvaluation = testSection.getAs[BSONDocument]("evaluation")

    val passedMin = testsEvaluation.getAs[Boolean]("passedMinimumCompetencyLevel").map(_.toString)
    val competencyAvg = testsEvaluation.getAs[BSONDocument]("competency-average")
    passedMin :: List(
      "makingEffectiveDecisionsAverage",
      "workingTogetherDevelopingSelfAndOthersAverage",
      "communicatingAndInfluencingAverage",
      "seeingTheBigPictureAverage",
      "overallScore"
    ).map { f => competencyAvg.getAs[Double](f) map (_.toString) }
  }

  private def onlineTests(doc: BSONDocument): Map[String, List[Option[String]]] = {

    def getInventoryId(config: Map[String, PsiTestIds], testName: String, phase: String) =
      config.getOrElse(testName, throw new Exception(s"No inventoryId found for $phase $testName")).inventoryId

    def extractDataFromTest(test: Option[BSONDocument]) = {
      val testResults = test.flatMap ( _.getAs[BSONDocument]("testResult") )

      List(
        test.getAsStr[String]("inventoryId"),
        test.getAsStr[String]("orderId"),
        test.getAsStr[String]("normId"),
        test.getAsStr[String]("reportId"),
        test.getAsStr[String]("assessmentId"),
        test.getAsStr[String]("testUrl"),
        test.getAsStr[DateTime]("invitationDate"),
        test.getAsStr[DateTime]("startedDateTime"),
        test.getAsStr[DateTime]("completedDateTime"),
        testResults.getAsStr[Double]("tScore"),
        testResults.getAsStr[Double]("rawScore"),
        testResults.getAsStr[String]("testReportUrl")
      )
    }

    val testGroups = doc.getAs[BSONDocument]("testGroups")

    // Phase1 data
    val phase1TestSection = testGroups.flatMap(_.getAs[BSONDocument]("PHASE1"))
    val phase1Tests = phase1TestSection.flatMap(_.getAs[List[BSONDocument]]("tests"))

    val phase1TestConfig = testIntegrationGatewayConfig.phase1Tests.tests

    val test1InventoryId = getInventoryId(phase1TestConfig, "test1", "phase1")
    val test2InventoryId = getInventoryId(phase1TestConfig, "test2", "phase1")
    val test3InventoryId = getInventoryId(phase1TestConfig, "test3", "phase1")
    val test4InventoryId = getInventoryId(phase1TestConfig, "test4", "phase1")

    val phase1Test1 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getAs[String]("inventoryId").get == test1InventoryId && test.getAs[Boolean]("usedForResults").getOrElse(false)
      }
    )
    val phase1Test2 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getAs[String]("inventoryId").get == test2InventoryId && test.getAs[Boolean]("usedForResults").getOrElse(false)
      }
    )
    val phase1Test3 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getAs[String]("inventoryId").get == test3InventoryId && test.getAs[Boolean]("usedForResults").getOrElse(false)
      }
    )
    val phase1Test4 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getAs[String]("inventoryId").get == test4InventoryId && test.getAs[Boolean]("usedForResults").getOrElse(false)
      }
    )

    // Phase2 data
    val phase2TestConfig = testIntegrationGatewayConfig.phase2Tests.tests

    val phase2Test1InventoryId = getInventoryId(phase2TestConfig, "test1", "phase2")
    val phase2Test2InventoryId = getInventoryId(phase2TestConfig, "test2", "phase2")

    val phase2TestSection = testGroups.flatMap(_.getAs[BSONDocument]("PHASE2"))
    val phase2Tests = phase2TestSection.flatMap(_.getAs[List[BSONDocument]]("tests"))

    val phase2Test1 = phase2Tests.flatMap( tests =>
      tests.find { test =>
        test.getAs[String]("inventoryId").get == phase2Test1InventoryId && test.getAs[Boolean]("usedForResults").getOrElse(false)
      }
    )
    val phase2Test2 = phase2Tests.flatMap( tests =>
      tests.find { test =>
        test.getAs[String]("inventoryId").get == phase2Test2InventoryId && test.getAs[Boolean]("usedForResults").getOrElse(false)
      }
    )

    // Sift data
    val siftTestConfig = testIntegrationGatewayConfig.numericalTests.tests

    val siftTestInventoryId = getInventoryId(siftTestConfig, "test1", "sift")

    val siftTestSection = testGroups.flatMap(_.getAs[BSONDocument]("SIFT_PHASE"))
    val siftTests = siftTestSection.flatMap(_.getAs[List[BSONDocument]]("tests"))

    val siftTest = siftTests.flatMap( tests =>
      tests.find { test =>
        test.getAs[String]("inventoryId").get == siftTestInventoryId && test.getAs[Boolean]("usedForResults").getOrElse(false)
      }
    )

    Map(
      Tests.Phase1Test1.toString -> extractDataFromTest(phase1Test1),
      Tests.Phase1Test2.toString -> extractDataFromTest(phase1Test2),
      Tests.Phase1Test3.toString -> extractDataFromTest(phase1Test3),
      Tests.Phase1Test4.toString -> extractDataFromTest(phase1Test4),
      Tests.Phase2Test1.toString -> extractDataFromTest(phase2Test1),
      Tests.Phase2Test2.toString -> extractDataFromTest(phase2Test2),
      Tests.SiftTest.toString -> extractDataFromTest(siftTest)
    )
  }

  object Tests {
    sealed abstract class Test {
      def name: String
    }

    case object Phase1Test1 extends Test {
      override def name = "phase1test1"
    }

    case object Phase1Test2 extends Test {
      override def name = "phase1test2"
    }

    case object Phase1Test3 extends Test {
      override def name = "phase1test3"
    }

    case object Phase1Test4 extends Test {
      override def name = "phase1test4"
    }

    case object Phase2Test1 extends Test {
      override def name = "phase2test1"
    }

    case object Phase2Test2 extends Test {
      override def name = "phase2test2"
    }

    case object SiftTest extends Test {
      override def name = "siftTest"
    }
  }

  private def assistanceDetails(doc: BSONDocument): List[Option[String]] = {
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details")
    val etrayAdjustments = assistanceDetails.getAs[BSONDocument]("etray")
    val videoAdjustments = assistanceDetails.getAs[BSONDocument]("video")
    val typeOfAdjustments = assistanceDetails.getAs[List[String]]("typeOfAdjustments").getOrElse(Nil)

    List(
      assistanceDetails.getAs[String]("hasDisability"),
      assistanceDetails.getAs[String]("hasDisabilityDescription"),
      assistanceDetails.flatMap(ad => if (ad.getAs[Boolean]("guaranteedInterview").getOrElse(false)) Y else N),
      if (assistanceDetails.getAs[Boolean]("needsSupportForOnlineAssessment").getOrElse(false)) Y else N,
      assistanceDetails.getAs[String]("needsSupportForOnlineAssessmentDescription"),
      if (assistanceDetails.getAs[Boolean]("needsSupportAtVenue").getOrElse(false)) Y else N,
      assistanceDetails.getAs[String]("needsSupportAtVenueDescription"),
      if (assistanceDetails.getAs[Boolean]("needsSupportForPhoneInterview").getOrElse(false)) Y else N,
      assistanceDetails.getAs[String]("needsSupportForPhoneInterviewDescription"),
      etrayAdjustments.getAs[Int]("timeNeeded").map(_ + "%"),
      if (typeOfAdjustments.contains("etrayInvigilated")) Y else N,
      etrayAdjustments.getAs[String]("invigilatedInfo"),
      etrayAdjustments.getAs[String]("otherInfo"),
      videoAdjustments.getAs[Int]("timeNeeded").map(_ + "%"),
      if (typeOfAdjustments.contains("videoInvigilated")) Y else N,
      videoAdjustments.getAs[String]("invigilatedInfo"),
      videoAdjustments.getAs[String]("otherInfo"),
      assistanceDetails.getAs[String]("adjustmentsComment"),
      if (assistanceDetails.getAs[Boolean]("adjustmentsConfirmed").getOrElse(false)) Y else N
    )
  }

  private def hasDisability(doc: BSONDocument): List[Option[String]] = {
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details")
    List(assistanceDetails.getAs[String]("hasDisability"))
  }

  private def personalDetails(doc: BSONDocument) = {
    val personalDetails = doc.getAs[BSONDocument]("personal-details")
    List(
      personalDetails.getAs[String]("firstName"),
      personalDetails.getAs[String]("lastName"),
      personalDetails.getAs[String]("preferredName"),
      personalDetails.getAs[String]("dateOfBirth")
    )
  }

  private def civilServiceExperience(doc: BSONDocument): (Option[String], Option[List[String]], Option[String]) = {
    val csExperienceDetails = doc.getAs[BSONDocument]("civil-service-experience-details")
    (
      csExperienceDetails.getAs[String]("civilServiceExperienceType"),
      csExperienceDetails.getAs[List[String]]("internshipTypes"),
      Option(csExperienceDetails.getAs[String]("certificateNumber").getOrElse("No"))
    )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

  override def dateTimeFactory: DateTimeFactory = DateTimeFactory
}
