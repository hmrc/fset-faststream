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

import config.{MicroserviceAppConfig, PsiTestIds}
import connectors.launchpadgateway.exchangeobjects.in.reviewed._
import factories.DateTimeFactory
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model._
import model.command.{CandidateDetailsReportItem, CsvExtract, WithdrawApplication}
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{FSACIndicator, SchemeEvaluationResult}
import org.joda.time.DateTime
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonRegularExpression}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Projections
import play.api.Logging
import repositories._
import services.reporting.SocioEconomicCalculator
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

//scalastyle:off
trait PreviousYearCandidatesDetailsRepository {

  val allSchemes: List[String]
  val siftAnswersHeader: String

//  implicit class RichOptionDocument(doc: Option[Document]) {

//    def getAs[T](key: String)(implicit reader: BSONReader[_ <: BSONValue, T]) = doc.flatMap(_.getAs[T](key))

//    def getAsStr[T](key: String)(implicit reader: BSONReader[_ <: BSONValue, T]) = doc.flatMap(_.getAs[T](key)).map(_.toString)

//    def getOrElseAsStr[T](key: String)(default: T)(implicit reader: BSONReader[_ <: BSONValue, T]) = {
//      doc.flatMap(_.getAs[T](key).orElse(Some(default))).map(_.toString)
//    }
//  }

  implicit class RichOptionDocument(doc: Option[BsonDocument]) {
    def getStringOpt(key: String) = Try(doc.map(_.get(key).asString().getValue)).toOption.flatten
    def getBooleanOpt(key: String) = Try(doc.map(_.get(key).asBoolean().getValue.toString)).toOption.flatten
    def getDoubleOpt(key: String) = Try(doc.map(_.get(key).asDouble().getValue.toString)).toOption.flatten
    def getIntOpt(key: String) = Try(doc.map(_.get(key).asInt32().getValue.toString)).toOption.flatten
    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed for ISODate
    def getDateTimeOpt(key: String) =
      doc.flatMap ( doc => Try(Codecs.fromBson[DateTime](doc.get(key).asDateTime()).toString).toOption )
    def getAsBoolean(key: String) = doc.exists(ad => Try(ad.get(key).asBoolean().getValue).getOrElse(false))
    def getAsIntOpt(key: String) = Try(doc.map(_.get(key).asInt32().getValue)).toOption.flatten
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
    val otherEvaluationColumns = "result," * (numOfSchemes - 2) + "result"
    val evaluationColumns = List("PHASE 1", "PHASE 2", "PHASE 3", "SIFT", "FSAC", "FSB").map { s =>
      s"$s result,$otherEvaluationColumns"
    }.mkString(",")
    val otherCssColumns = "result," * numOfSchemes + "result"
    val currentSchemeStatusColumns = List("Current Scheme Status").map { s =>
      s"$s result,$otherCssColumns"
    }.mkString(",")
    s"$evaluationColumns,$currentSchemeStatusColumns"
  }

  val disabilityCategoriesHeaders = "Learning difference,Social/communication conditions,Long-term illness,Mental health condition," +
  "Physical impairment,Deaf,Blind,Development condition,No known impairment,Condition not listed,Prefer not to say,Other,Other description,"

  def dataAnalystApplicationDetailsHeader(numOfSchemes: Int) =
    "ApplicationId,Date of Birth,Application status,Route,All FS schemes failed SDIP not failed," +
      "Civil servant,EDIP,EDIP year,SDIP,SDIP year,Other internship,Other internship name,Other internship year,Fast Pass No," +
      "Scheme preferences," +
      "Do you have a disability,Disability impact," +
      disabilityCategoriesHeaders +
      appTestStatuses +
      "Final Progress Status prior to withdrawal," +
      appTestResults(numOfSchemes) +
      ",FSAC Indicator area,FSAC Indicator Assessment Centre"

  def testTitles(testName: String) = s"$testName inventoryId,orderId,normId,reportId,assessmentId,testUrl,invitationDate,startedDateTime," +
    s"completedDateTime,tScore,rawScore,testReportUrl,"

  val assistanceDetailsHeaders = "Do you have a disability,Disability impact," +
    disabilityCategoriesHeaders +
    "GIS,Extra support f2f,What adjustments will you need,Extra support phone interview,What adjustments will you need," +
    "Additional comments,"

  def applicationDetailsHeader(numOfSchemes: Int) = "applicationId,userId,testAccountId,Framework ID,Application Status,Route,SdipDiversity,First name,Last name," +
    "Preferred Name,Date of Birth,Are you eligible,Terms and Conditions," +
    "Civil servant,EDIP,EDIP year,SDIP,SDIP year,Other internship,Other internship name,Other internship year,Fast Pass No," +
    "Scheme preferences,Scheme names,Are you happy with order,Are you eligible," +
    assistanceDetailsHeaders +
    "I understand this wont affect application," +
    testTitles("Phase1 test1") +
    testTitles("Phase1 test2") +
    testTitles("Phase1 test3") +
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

  val questionnaireDetailsHeader: String = "Gender Identity,Sexual Orientation,Ethnic Group,Is English your 1st language?,Live in UK between 14-18?," +
    "Home postcode at 14,Name of school 14-16,Which type of school was this?,Name of school 16-18,Eligible for free school meals?,University name," +
    "Category of degree,Degree type,Postgrad university name,Postgrad category of degree,Postgrad degree type,Lower socio-economic background?," +
    "Parent guardian completed Uni?,Parents job at 14,Employee?,Size,Supervise employees,SE 1-5,Oxbridge,Russell Group"

  val mediaHeader = "How did you hear about us?"

  val eventsDetailsHeader = "Allocated events"

//  val allSchemes: List[String] = SchemeYamlRepository.schemes.map(_.id.value).toList //Broke guice DI

//  val siftAnswersHeader: String = "Sift Answers status,multipleNationalities,secondNationality,nationality," +
//    "undergrad degree name,classification,graduationYear,moduleDetails," +
//    "postgrad degree name,classification,graduationYear,moduleDetails," + allSchemes.mkString(",")

  val dataAnalystSiftAnswersHeader: String = "Nationality,Undergrad degree name,Classification,Graduation year"

  val assessmentScoresNumericFields = Seq(
    "writtenExercise" -> "makingEffectiveDecisionsAverage,communicatingAndInfluencingAverage,seeingTheBigPictureAverage",
    "leadershipExercise" -> "workingTogetherDevelopingSelfAndOthersAverage,communicatingAndInfluencingAverage,seeingTheBigPictureAverage",
    "teamExercise" -> "makingEffectiveDecisionsAverage,communicatingAndInfluencingAverage,workingTogetherDevelopingSelfAndOthersAverage"
  )

  val assessmentScoresFeedbackFields = Seq(
    "writtenExercise" -> "makingEffectiveDecisionsFeedback,communicatingAndInfluencingFeedback,seeingTheBigPictureFeedback",
    "leadershipExercise" -> "workingTogetherDevelopingSelfAndOthersFeedback,communicatingAndInfluencingFeedback,seeingTheBigPictureFeedback",
    "teamExercise" -> "makingEffectiveDecisionsFeedback,communicatingAndInfluencingFeedback,workingTogetherDevelopingSelfAndOthersFeedback"
  )

  val assessmentScoresNumericFieldsMap: Map[String, String] = assessmentScoresNumericFields.toMap

  val assessmentScoresFeedbackFieldsMap: Map[String, String] = assessmentScoresFeedbackFields.toMap

  def assessmentScoresHeaders(assessor: String): String = {
    assessmentScoresNumericFields.map(_._1).map { exercise =>
      s"$assessor $exercise attended,updatedBy,submittedDate,${assessmentScoresNumericFieldsMap(exercise)},${assessmentScoresFeedbackFieldsMap(exercise)}"
    }.mkString(",") + s",$assessor Final feedback,updatedBy,acceptedDate"
  }

  // TODO: remove this method
//  def applicationDetailsStreamLegacy(numOfSchemes: Int, applicationIds: Seq[String]): Enumerator[CandidateDetailsReportItem]
  def applicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String])(implicit mat: Materializer): Source[CandidateDetailsReportItem, _]

  // TODO: remove this method
//  def dataAnalystApplicationDetailsStreamPt1Legacy(numOfSchemes: Int): Enumerator[CandidateDetailsReportItem]
  def dataAnalystApplicationDetailsStreamPt1(numOfSchemes: Int)(implicit mat: Materializer): Source[CandidateDetailsReportItem, _]
  // TODO: remove this method
//  def dataAnalystApplicationDetailsStreamPt2Legacy: Enumerator[CandidateDetailsReportItem]
  def dataAnalystApplicationDetailsStreamPt2(implicit mat: Materializer): Source[CandidateDetailsReportItem, _]
  def dataAnalystApplicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String])(implicit mat: Materializer): Source[CandidateDetailsReportItem, _]

  def findApplicationsFor(appRoutes: Seq[ApplicationRoute]): Future[Seq[CandidateIds]]
  def findApplicationsFor(appRoutes: Seq[ApplicationRoute], appStatuses: Seq[ApplicationStatus]): Future[Seq[CandidateIds]]
  def findApplicationsFor(appRoutes: Seq[ApplicationRoute],
                          appStatuses: Seq[ApplicationStatus],
                          part: Int): Future[Seq[CandidateIds]]

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

@Singleton
class PreviousYearCandidatesDetailsMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                                              appConfig: MicroserviceAppConfig,
                                                              schemeRepository: SchemeRepository,
                                                              mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PreviousYearCandidatesDetailsRepository with CommonBSONDocuments with DiversityQuestionsText with Logging with Schemes {

  val applicationDetailsCollection = mongoComponent.database.getCollection(CollectionNames.APPLICATION)
  val contactDetailsCollection = mongoComponent.database.getCollection(CollectionNames.CONTACT_DETAILS)
  val questionnaireCollection = mongoComponent.database.getCollection(CollectionNames.QUESTIONNAIRE)
  val mediaCollection = mongoComponent.database.getCollection(CollectionNames.MEDIA)
  val assessorAssessmentScoresCollection = mongoComponent.database.getCollection(CollectionNames.ASSESSOR_ASSESSMENT_SCORES)
  val reviewerAssessmentScoresCollection = mongoComponent.database.getCollection(CollectionNames.REVIEWER_ASSESSMENT_SCORES)
  val candidateAllocationCollection = mongoComponent.database.getCollection(CollectionNames.CANDIDATE_ALLOCATION)
  val eventCollection = mongoComponent.database.getCollection(CollectionNames.ASSESSMENT_EVENTS)
  val siftAnswersCollection = mongoComponent.database.getCollection(CollectionNames.SIFT_ANSWERS)

  private val Y = Some("Yes")
  private val N = Some("No")

  // When dealing with the currentSchemeStatus, it is possible for a candidate, who is evaluated at fsb to get 2 additional schemes
  // added to the css if they are have a DiplomaticAndDevelopmentEconomics fsb. If this is the case, GovernmentEconomicsService
  // and DiplomaticAndDevelopment will be added to the css. This is why we add 2 to the numOfSchemes to handle a max number of
  // 7 scheme result columns: 5 for a SdipFaststream candidate + 2 to handle if the candidate has DiplomaticAndDevelopmentEconomics
  // as one of the schemes
  private val fsbAdditionalSchemesForEacDs = 2

  override val allSchemes: List[String] = schemeRepository.schemes.map(_.id.value).toList

  override val siftAnswersHeader: String = "Sift Answers status,multipleNationalities,secondNationality,nationality," +
    "undergrad degree name,classification,graduationYear,moduleDetails," +
    "postgrad degree name,otherDetails,graduationYear,projectDetails," + allSchemes.mkString(",")
  /*
    override def applicationDetailsStreamLegacy(numOfSchemes: Int, applicationIds: Seq[String]): Enumerator[CandidateDetailsReportItem] = {
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
              personalDetails(doc, isAnalystReport = false) :::
              List(progressResponseReachedYesNo(progressResponse.personalDetails)) :::
              List(progressResponseReachedYesNo(progressResponse.personalDetails)) :::
              repositories.getCivilServiceExperienceDetails(doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream), doc).toList :::
              List(schemePrefsAsString) ::: //Scheme preferences
              List(schemesYesNoAsString) ::: //Scheme names
              List(progressResponseReachedYesNo(progressResponse.schemePreferences)) ::: //Are you happy with order
              List(progressResponseReachedYesNo(progressResponse.schemePreferences)) ::: //Are you eligible
              assistanceDetails(doc) :::
              List(progressResponseReachedYesNo(progressResponse.questionnaire.nonEmpty)) ::: //I understand this wont affect application

              onlineTestResults(Tests.Phase1Test1.toString) :::
              onlineTestResults(Tests.Phase1Test2.toString) :::
              onlineTestResults(Tests.Phase1Test3.toString) :::
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
            logger.error("Previous year candidate report generation exception", ex)
            CandidateDetailsReportItem("", "", "ERROR LINE " + ex.getMessage)
        }
      }
    }*/

  override def applicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String])(implicit mat: Materializer): Source[CandidateDetailsReportItem, _] = {

    def processDocument(doc: Document): CandidateDetailsReportItem = {
      try {
        val applicationId = extractAppIdOpt(doc).get
        logger.debug(s"processing applicationId = $applicationId")
        val progressResponse = toProgressResponse(applicationId)(doc)

        val schemePrefs: List[String] = extractSchemes(doc).getOrElse(Nil).map(_.toString)
        val schemePrefsAsString: Option[String] = Some(schemePrefs.mkString(","))
        val schemesYesNoAsString: Option[String] = Option((schemePrefs.map(_ + ": Yes") ::: allSchemes.filterNot(schemePrefs.contains).map(_ + ": No")).mkString(","))

        val onlineTestResults = onlineTests(doc)

        // Get withdrawer
        val withdrawalInfoOpt = Try(Codecs.fromBson[WithdrawApplication](doc.get("withdraw").get)).toOption

        def maybePrefixWithdrawer(withdrawerOpt: Option[String]): Option[String] = withdrawerOpt.map { withdrawer =>
          if (withdrawer.nonEmpty && withdrawer != "Candidate") {
            "Admin (User ID: " + withdrawer + ")"
          } else {
            withdrawer
          }
        }

        val fsacIndicatorOpt = Try(Codecs.fromBson[FSACIndicator](doc.get("fsac-indicator").get)).toOption

        val csvContent = makeRow(
          List(extractAppIdOpt(doc)) :::
            List(doc.get("userId").map(_.asString().getValue)) :::
            List(doc.get("testAccountId").map(_.asString().getValue)) :::
            List(doc.get("frameworkId").map(_.asString().getValue)) :::
            List(doc.get("applicationStatus").map(_.asString().getValue)) :::
            List(doc.get("applicationRoute").map(_.asString().getValue)) :::
            List(if (Try(doc.get("sdipDiversity").map(_.asBoolean().getValue)).getOrElse(Some(false)).getOrElse(false)) Y else N) :::
            personalDetails(doc, isAnalystReport = false) :::
            List(progressResponseReachedYesNo(progressResponse.personalDetails)) :::
            List(progressResponseReachedYesNo(progressResponse.personalDetails)) :::
            repositories.getCivilServiceExperienceDetails(extractApplicationRoute(doc), doc).toList :::
            List(schemePrefsAsString) ::: //Scheme preferences
            List(schemesYesNoAsString) ::: //Scheme names
            List(progressResponseReachedYesNo(progressResponse.schemePreferences)) ::: //Are you happy with order
            List(progressResponseReachedYesNo(progressResponse.schemePreferences)) ::: //Are you eligible
            assistanceDetails(doc) :::
            List(progressResponseReachedYesNo(progressResponse.questionnaire.nonEmpty)) ::: //I understand this wont affect application
            onlineTestResults(Tests.Phase1Test1.toString) :::
            onlineTestResults(Tests.Phase1Test2.toString) :::
            onlineTestResults(Tests.Phase1Test3.toString) :::
            onlineTestResults(Tests.Phase2Test1.toString) :::
            onlineTestResults(Tests.Phase2Test2.toString) :::
            videoInterview(doc) :::
            onlineTestResults(Tests.SiftTest.toString) :::
            fsbScoresAndFeedback(doc) :::
            progressStatusTimestamps(doc) :::
            fsacCompetency(doc) :::
            testEvaluations(doc, numOfSchemes) :::

            currentSchemeStatus(doc, numOfSchemes + fsbAdditionalSchemesForEacDs) :::
            List(maybePrefixWithdrawer(withdrawalInfoOpt.map(_.withdrawer))) :::
            List(withdrawalInfoOpt.map(_.reason)) :::
            List(withdrawalInfoOpt.map(_.otherReason.getOrElse(""))) :::
            List(doc.get("issue").map(_.asString().getValue)) :::
            List(fsacIndicatorOpt.map(_.area)) :::
            List(fsacIndicatorOpt.map(_.assessmentCentre)) :::
            List(fsacIndicatorOpt.map(_.version))
            : _*
        )
        CandidateDetailsReportItem(
          extractAppId(doc),
          extractUserId(doc), csvContent
        )
      } catch {
        case ex: Throwable =>
          val appId = extractAppId(doc)
          logger.error(s"Streamed candidate report generation exception $appId", ex)
          CandidateDetailsReportItem(appId, "", s"$appId ERROR: ${ex.getMessage}")
      }
    }

    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()
    Source.fromPublisher {
      applicationDetailsCollection.find(query).projection(projection)
        .transform((doc: Document) => processDocument(doc),
                    (e: Throwable) => throw e
        )
    }
  }

  private def lastProgressStatus(doc: Document): Option[String] = {
    val progressStatusTimestamps = subDocRoot("progress-status-timestamp")(doc)

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed for ISODate
    def timestampFor(progressStatus: ProgressStatuses.ProgressStatus) = {
      progressStatusTimestamps.flatMap(doc => Try(Codecs.fromBson[DateTime](doc.get(progressStatus.toString).asDateTime())).toOption)// Handle NPE
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

  private def lastProgressStatusPriorToWithdrawal(doc: Document): Option[String] = {
    doc.get("applicationStatus").map(_.asString().getValue).flatMap { status =>
      if (ApplicationStatus.WITHDRAWN == ApplicationStatus.withName(status)) {
        lastProgressStatus(doc)
      } else {
        None
      }
    }
  }

  /*
  override def dataAnalystApplicationDetailsStreamPt1Legacy(numOfSchemes: Int): Enumerator[CandidateDetailsReportItem] = {
    val query = BSONDocument.empty
    commonDataAnalystApplicationDetailsStreamLegacy(numOfSchemes, query)
  }*/

  /*
  override def dataAnalystApplicationDetailsStreamPt2Legacy: Enumerator[CandidateDetailsReportItem] = {
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
          logger.error("Data analyst Previous year candidate report generation exception", ex)
          CandidateDetailsReportItem("", "", "ERROR LINE " + ex.getMessage)
      }
    }
  }*/

  override def dataAnalystApplicationDetailsStreamPt1(numOfSchemes: Int)(implicit mat: Materializer): Source[CandidateDetailsReportItem, _] = {
    val query = Document.empty
    commonDataAnalystApplicationDetailsStream(numOfSchemes, query)
  }

  override def dataAnalystApplicationDetailsStreamPt2(implicit mat: Materializer): Source[CandidateDetailsReportItem, _] = {

    def processDocument(doc: Document): CandidateDetailsReportItem = {
      try {
        val applicationIdOpt = extractAppIdOpt(doc)
        val csvContent = makeRow(
          List(applicationIdOpt): _*
        )
        CandidateDetailsReportItem(extractAppId(doc), extractUserId(doc), csvContent)
      } catch {
        case ex: Throwable =>
          val appId = extractAppId(doc)
          logger.error(s"Data analyst streamed candidate report generation exception $appId", ex)
          CandidateDetailsReportItem(appId, "", s"$appId ERROR: ${ex.getMessage}")
      }
    }

    val query = Document.empty
    val projection = Projections.excludeId()
    Source.fromPublisher {
      applicationDetailsCollection.find(query).projection(projection)
        .transform((doc: Document) => processDocument(doc),
          (e: Throwable) => throw e
        )
    }
  }

  override def dataAnalystApplicationDetailsStream(numOfSchemes: Int, applicationIds: Seq[String])(implicit mat: Materializer): Source[CandidateDetailsReportItem, _] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    commonDataAnalystApplicationDetailsStream(numOfSchemes, query)
  }

/*
  private def commonDataAnalystApplicationDetailsStreamLegacy(numOfSchemes: Int, query: BSONDocument): Enumerator[CandidateDetailsReportItem] = {
    val projection = Json.obj("_id" -> 0)

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

    import reactivemongo.play.iteratees.cursorProducer
    applicationDetailsCollection.find(query, Some(projection))
      .cursor[BSONDocument](ReadPreference.nearest)
      .enumerator().map { doc =>

      try {
        val schemePrefs: List[String] = doc.getAs[BSONDocument]("scheme-preferences").flatMap(_.getAs[List[String]]("schemes")).getOrElse(Nil)
        val schemePrefsAsString: Option[String] = Some(schemePrefs.mkString(","))
        val fsacIndicator = doc.getAs[FSACIndicator]("fsac-indicator")

        val applicationIdOpt = doc.getAs[String]("applicationId")
        val csvContent = makeRow(
          List(applicationIdOpt) :::
            personalDetails(doc, isAnalystReport = true) :::
            List(doc.getAs[String]("applicationStatus")) :::
            List(doc.getAs[String]("applicationRoute")) :::
            List(Some(isSdipFsWithFsFailedAndSdipNotFailed(doc).toString)) :::
            repositories.getCivilServiceExperienceDetails(doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream), doc).toList :::
            List(schemePrefsAsString) :::
            disabilityDetails(doc) :::
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
          logger.error("Data analyst streamed candidate report generation exception", ex)
          CandidateDetailsReportItem("", "", "ERROR LINE " + ex.getMessage)
      }
    }
  }*/

  // Limit the access so that test class still has access
  protected[application] def isSdipFsWithFsFailedAndSdipNotFailed(doc: Document) = {
    import scala.jdk.CollectionConverters._
    val testEvalResultsListOpt = doc.get("currentSchemeStatus").map(_.asArray().getValues.asScala.toList)
    val css = testEvalResultsListOpt.map( bsonValueList => bsonValueList.map( bsonValue => Codecs.fromBson[SchemeEvaluationResult](bsonValue)) ).getOrElse(Nil)

    val sdipSchemeId = Sdip
    val sdipPresent = css.count( schemeEvaluationResult => schemeEvaluationResult.schemeId == sdipSchemeId ) == 1
    val sdipNotFailed = sdipPresent &&
      css.count( schemeEvaluationResult =>
        schemeEvaluationResult.schemeId == sdipSchemeId
          && (schemeEvaluationResult.result == EvaluationResults.Amber.toString
          || schemeEvaluationResult.result == EvaluationResults.Green.toString)
      ) == 1
    val fsSchemes = css.filterNot( ser => ser.schemeId == sdipSchemeId )
    // For this report we regard a scheme to be failed if it is either Red or Withdrawn
    val fsSchemesPresentAndAllFailed = fsSchemes.nonEmpty &&
      fsSchemes.count( schemeEvaluationResult =>
        schemeEvaluationResult.result == EvaluationResults.Red.toString || schemeEvaluationResult.result == EvaluationResults.Withdrawn.toString
      ) == fsSchemes.size
    val isSdipFaststream = doc.get("applicationRoute").map(_.asString().getValue).contains(ApplicationRoute.SdipFaststream.toString)
    isSdipFaststream && sdipPresent && sdipNotFailed && fsSchemesPresentAndAllFailed
  }

  private def commonDataAnalystApplicationDetailsStream(numOfSchemes: Int, query: Document)(implicit mat: Materializer): Source[CandidateDetailsReportItem, _] = {

    def processDocument(doc: Document): CandidateDetailsReportItem = {
      try {
        val schemePrefs: List[String] = extractSchemes(doc).getOrElse(Nil).map(_.toString)
        val schemePrefsAsString: Option[String] = Some(schemePrefs.mkString(","))
        val fsacIndicatorOpt = Try(Codecs.fromBson[FSACIndicator](doc.get("fsac-indicator").get)).toOption

        val applicationIdOpt = extractAppIdOpt(doc)

        val csvContent = makeRow(
          List(applicationIdOpt) :::
            personalDetails(doc, isAnalystReport = true) :::
            List(doc.get("applicationStatus").map(_.asString().getValue)) :::
            List(doc.get("applicationRoute").map(_.asString().getValue)) :::

            List(Some(isSdipFsWithFsFailedAndSdipNotFailed(doc).toString)) :::
            repositories.getCivilServiceExperienceDetails(extractApplicationRoute(doc), doc).toList :::
            List(schemePrefsAsString) :::
            disabilityDetails(doc) :::
            progressStatusTimestamps(doc) :::
            List(lastProgressStatusPriorToWithdrawal(doc)) :::
            testEvaluations(doc, numOfSchemes) :::
            currentSchemeStatus(doc, numOfSchemes + fsbAdditionalSchemesForEacDs) :::
            List(fsacIndicatorOpt.map(_.area)) :::
            List(fsacIndicatorOpt.map(_.assessmentCentre))
            : _*
        )
        CandidateDetailsReportItem(extractAppId(doc), extractUserId(doc), csvContent)
      } catch {
        case ex: Throwable =>
          val appId = extractAppId(doc)
          logger.error(s"Data analyst streamed candidate report generation exception $appId", ex)
          CandidateDetailsReportItem(appId, "", s"$appId ERROR: ${ex.getMessage}")
      }
    }

    val projection = Projections.excludeId()
    Source.fromPublisher {
      applicationDetailsCollection.find(query).projection(projection)
        .transform((doc: Document) => processDocument(doc),
          (e: Throwable) => throw e
        )
    }
  }

  private def progressStatusTimestamps(doc: Document): List[Option[String]] = {

    val statusTimestampsOpt = subDocRoot("progress-status-timestamp")(doc)
    val progressStatusOpt = subDocRoot("progress-status")(doc)

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed for ISODate
    def timestampFor(status: ProgressStatuses.ProgressStatus) = {
      statusTimestampsOpt.flatMap(doc => Try(Codecs.fromBson[DateTime](doc.get(status.toString).asDateTime())).toOption.map( _.toString ))// Handle NPE
    }

    val questionnaireStatusesOpt = progressStatusOpt.flatMap( doc => subDocRoot("questionnaire")(doc) )
    def questionnaireStatus(key: String): Option[String] = questionnaireStatusesOpt match {
      case None => Some("false")
      case Some(stat) => Try(stat.get(key).asBoolean().getValue).toOption.orElse(Some(false)).map(_.toString)
    }

    val progressStatusDatesOpt = subDocRoot("progress-status-dates")(doc)

    List(
      progressStatusOpt.map ( doc => Try( doc.get("personal-details").asBoolean().getValue ).toOption.getOrElse(false).toString ),
      statusTimestampsOpt.flatMap(doc => Try(Codecs.fromBson[DateTime](doc.get("IN_PROGRESS").asDateTime())).toOption).map(_.toString).orElse(
        progressStatusDatesOpt.map( _.get("in_progress").asString.getValue )
      ),
      progressStatusOpt.map ( doc => Try( doc.get("scheme-preferences").asBoolean().getValue ).toOption.getOrElse(false).toString ),
      progressStatusOpt.map ( doc => Try( doc.get("assistance-details").asBoolean().getValue ).toOption.getOrElse(false).toString ),
      questionnaireStatus("start_questionnaire"),
      questionnaireStatus("diversity_questionnaire"),
      questionnaireStatus("education_questionnaire"),
      questionnaireStatus("occupation_questionnaire"),
      progressStatusOpt.map ( doc => Try( doc.get("preview").asBoolean().getValue ).toOption.getOrElse(false).toString ),
      timestampFor(ProgressStatuses.SUBMITTED).orElse(progressStatusDatesOpt.map( _.get("submitted").asString().getValue )),
      timestampFor(ProgressStatuses.FAST_PASS_ACCEPTED).orElse(progressStatusDatesOpt.map( _.get("FAST_PASS_ACCEPTED").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_INVITED).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_INVITED").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_FIRST_REMINDER).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_FIRST_REMINDER").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_SECOND_REMINDER).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_SECOND_REMINDER").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_STARTED).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_STARTED").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_COMPLETED).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_COMPLETED").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_EXPIRED).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_EXPIRED").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_RESULTS_READY).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_RESULTS_READY").asString().getValue )),
      timestampFor(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).orElse(progressStatusDatesOpt.map( _.get("PHASE1_TESTS_RESULTS_RECEIVED").asString().getValue ))
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

//  private def civilServiceExperienceCheckExpType(civilServExperienceType: Option[String], typeToMatch: String) =
//    List(if (civilServExperienceType.contains(typeToMatch)) Y else N)

//  private def civilServiceExperienceCheckInternshipType(
//                                                         civilServExperienceInternshipTypes: Option[List[String]],
//                                                         typeToMatch: String
//                                                       ) = List(if (civilServExperienceInternshipTypes.exists(_.contains(typeToMatch))) Y else N)

  private def progressResponseReachedYesNo(progressResponseReached: Boolean) = if (progressResponseReached) Y else N

  override def findApplicationsFor(appRoutes: Seq[ApplicationRoute]): Future[Seq[CandidateIds]] = {
    val projection = Projections.include("applicationId", "userId")
    val query = Document( "$and" -> BsonArray(
      Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson)))
    ))

    applicationDetailsCollection.find[Document](query).projection(projection).toFuture().map { docList =>
      docList.map { doc =>
        val appId = getAppId(doc)
        val userId = getUserId(doc)
        CandidateIds(userId, appId)
      }
    }
  }

  override def findApplicationsFor(appRoutes: Seq[ApplicationRoute],
                                   appStatuses: Seq[ApplicationStatus]): Future[Seq[CandidateIds]] = {
    val projection = Projections.include("applicationId", "userId")
    val query = Document( "$and" -> BsonArray(
      Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
      Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson)))
    ))

    applicationDetailsCollection.find[Document](query).projection(projection).toFuture().map { docList =>
      docList.map { doc =>
        val appId = getAppId(doc)
        val userId = getUserId(doc)
        CandidateIds(userId, appId)
      }
    }
  }

  override def findApplicationsFor(appRoutes: Seq[ApplicationRoute],
                                   appStatuses: Seq[ApplicationStatus],
                                   part: Int): Future[Seq[CandidateIds]] = {
    val projection = Projections.include("applicationId", "userId")
    val query = {
      part match {
        case 1 =>
          Document( "$and" -> BsonArray(
            Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
            Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson))),
            Document("personal-details.dateOfBirth" -> BsonRegularExpression("^[0-9]{4}-(01|05|09)-[0-9]{2}$")) // Only months 1, 5 & 9
          ))
        case 2 =>
          Document( "$and" -> BsonArray(
            Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
            Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson))),
            Document("personal-details.dateOfBirth" -> BsonRegularExpression("^[0-9]{4}-(03|07|11)-[0-9]{2}$")) // Only months 3, 7 & 11
          ))
        case 3 =>
          Document( "$and" -> BsonArray(
            Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
            Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson))),
            Document("personal-details.dateOfBirth" -> BsonRegularExpression("^[0-9]{4}-(02|06|10)-[0-9]{2}$")) // Only months 2, 6 & 10
          ))
        case 4 =>
          Document( "$and" -> BsonArray(
            Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
            Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson))),
            Document("personal-details.dateOfBirth" -> BsonRegularExpression("^[0-9]{4}-(04|08|12)-[0-9]{2}$")) // Only months 4, 8 & 12
          ))
        // Parts 1 & 2
        case 12 =>
          Document( "$and" -> BsonArray(
            Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
            Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson))),
            Document("personal-details.dateOfBirth" -> BsonRegularExpression("^[0-9]{4}-(01|05|09|03|07|11)-[0-9]{2}$")) // Only months 1, 5, 9, 3, 7 & 11
          ))
        // Parts 3 & 4
        case 34 =>
          Document( "$and" -> BsonArray(
            Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
            Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson))),
            Document("personal-details.dateOfBirth" -> BsonRegularExpression("^[0-9]{4}-(02|06|10|04|08|12)-[0-9]{2}$")) // Only months 2, 6, 10, 4, 8, 12
          ))
        case _ =>
          Document( "$and" -> BsonArray(
            Document("applicationRoute" -> Document("$in" -> appRoutes.map(_.toBson))),
            Document("applicationStatus" -> Document("$in" -> appStatuses.map(_.toBson)))
          ))
      }
    }
    applicationDetailsCollection.find[Document](query).projection(projection).toFuture().map { docList =>
      docList.map { doc =>
        val appId = getAppId(doc)
        val userId = getUserId(doc)
        CandidateIds(userId, appId)
      }
    }
  }

  override def findDataAnalystContactDetails: Future[CsvExtract[String]] = {
    val query = Document.empty
    commonFindDataAnalystContactDetails(query)
  }

  override def findDataAnalystContactDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("userId" -> Document("$in" -> userIds))
    commonFindDataAnalystContactDetails(query)
  }

  private def commonFindDataAnalystContactDetails(query: Document): Future[CsvExtract[String]] = {
    val projection = Projections.excludeId()

    contactDetailsCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>
        val contactDetailsOpt = subDocRoot("contact-details")(doc)
        val csvRecord = makeRow(
          contactDetailsOpt.map(_.get("email").asString().getValue),
          Try(contactDetailsOpt.map(_.get("postCode").asString().getValue)).toOption.flatten // Handle NPE
        )
        extractUserId(doc) -> csvRecord
      }
      CsvExtract(dataAnalystContactDetailsHeader, csvRecords.toMap)
    }
  }

  override def findContactDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val projection = Projections.excludeId()
    val query = Document("userId" -> Document("$in" -> userIds))

    contactDetailsCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>
        val contactDetailsOpt = subDocRoot("contact-details")(doc)
        val addressOpt = contactDetailsOpt.flatMap(contactDetails => subDocRoot("address")(contactDetails))

        val csvRecord = makeRow(
          contactDetailsOpt.getStringOpt("email"),
          addressOpt.getStringOpt("line1"),
          addressOpt.getStringOpt("line2"),
          addressOpt.getStringOpt("line3"),
          addressOpt.getStringOpt("line4"),
          contactDetailsOpt.getStringOpt("postCode"),
          contactDetailsOpt.map(_.get("outsideUk").asBoolean().getValue).map(outside => if (outside) "Yes" else "No"),
          contactDetailsOpt.getStringOpt("country"),
          contactDetailsOpt.getStringOpt("phone"),
        )
        extractUserId(doc) -> csvRecord
      }
      CsvExtract(contactDetailsHeader, csvRecords.toMap)
    }
  }

  override def findDataAnalystQuestionnaireDetails: Future[CsvExtract[String]] = {
    val query = Document.empty
    val projection = Projections.excludeId()

    commonFindQuestionnaireDetails(query, projection)
  }

  override def findDataAnalystQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()

    commonFindQuestionnaireDetails(query, projection)
  }

  override def findQuestionnaireDetails: Future[CsvExtract[String]] = {
    findQuestionnaireDetails(Seq.empty[String])
  }

  override def findQuestionnaireDetails(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()

    commonFindQuestionnaireDetails(query, projection)
  }

  private def commonFindQuestionnaireDetails(query: Document, projection: Bson): Future[CsvExtract[String]] = {

    def getAnswer(question: String, doc: Option[BsonDocument]) = {
      val questionDocOpt = doc.flatMap(doc => subDocRoot(question)(doc))
      val isUnknown = questionDocOpt.exists(q => Try(q.get("unknown").asBoolean().getValue).toOption.contains(true))
      if (isUnknown) {
        Some("Unknown")
      } else {
        questionDocOpt.flatMap(q => Try(q.get("answer").asString().getValue).toOption match {
          case None => Try(q.get("otherDetails").asString().getValue).toOption
          case Some(answer) if List("Other", "Other ethnic group").contains(answer) => Try(q.get("otherDetails").asString().getValue).toOption
          case Some(answer) => Some(answer)
        })
      }
    }

    questionnaireCollection.find(query).projection(projection).toFuture().map { docs =>

      val csvRecords = docs.map { doc =>
        val questionsDocOpt = subDocRoot("questions")(doc)
        val universityNameAnswer = getAnswer(universityName, questionsDocOpt)

        import scala.jdk.CollectionConverters._

        val allQuestionsAndAnswers = questionsDocOpt.map{ doc =>
          val keys = doc.keySet().asScala.toList

          keys.map{ question =>
            val answer = getAnswer(question, questionsDocOpt).getOrElse("Unknown")
            question -> answer
          }.toMap
        }.getOrElse(Map.empty[String, String])

        val csvRecord = makeRow(
          getAnswer(genderIdentity, questionsDocOpt),
          getAnswer(sexualOrientation, questionsDocOpt),
          getAnswer(ethnicGroup, questionsDocOpt),
          getAnswer(englishLanguage, questionsDocOpt),
          getAnswer(liveInUkAged14to18, questionsDocOpt),
          getAnswer(postcodeAtAge14, questionsDocOpt),
          getAnswer(schoolNameAged14to16, questionsDocOpt),
          getAnswer(schoolTypeAged14to16, questionsDocOpt),
          getAnswer(schoolNameAged16to18, questionsDocOpt),
          getAnswer(eligibleForFreeSchoolMeals, questionsDocOpt),
          universityNameAnswer,
          getAnswer(categoryOfDegree, questionsDocOpt),
          getAnswer(degreeType, questionsDocOpt),
          getAnswer(postgradUniversityName, questionsDocOpt),
          getAnswer(postgradCategoryOfDegree, questionsDocOpt),
          getAnswer(postgradDegreeType, questionsDocOpt),
          getAnswer(lowerSocioEconomicBackground, questionsDocOpt),
          getAnswer(parentOrGuardianQualificationsAtAge18, questionsDocOpt),
          getAnswer(highestEarningParentOrGuardianTypeOfWorkAtAge14, questionsDocOpt),
          getAnswer(employeeOrSelfEmployed, questionsDocOpt),
          getAnswer(sizeOfPlaceOfWork, questionsDocOpt),
          getAnswer(superviseEmployees, questionsDocOpt),
          Some(SocioEconomicCalculator.calculate(allQuestionsAndAnswers)),
          isOxbridge(universityNameAnswer),
          isRussellGroup(universityNameAnswer)
        )
        extractAppId(doc) -> csvRecord
      }
      CsvExtract(questionnaireDetailsHeader, csvRecords.toMap)
    }
  }

  override def findSiftAnswers: Future[CsvExtract[String]] = {
    findSiftAnswers(Seq.empty[String])
  }

  override def findSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()

    siftAnswersCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>
        val schemeAnswersOpt = subDocRoot("schemeAnswers")(doc)
        val schemeTextAnswers = allSchemes.map { s =>
          schemeAnswersOpt.flatMap( doc => Try(doc.get(s).asDocument()).toOption ).map( _.get("rawText").asString().getValue )
        }

        val generalAnswersOpt = subDocRoot("generalAnswers")(doc)
        val undergradDegreeOpt = generalAnswersOpt.flatMap( doc => Try(doc.get("undergradDegree").asDocument()).toOption ) // Handle NPE
        val postgradDegreeOpt = generalAnswersOpt.flatMap( doc => Try(doc.get("postgradDegree").asDocument()).toOption ) // Handle NPE

        val csvRecord = makeRow(
          List(
            Try(doc.get("status").map( _.asString().getValue )).toOption.flatten,
            generalAnswersOpt.getBooleanOpt("multipleNationalities"),
            generalAnswersOpt.getStringOpt("secondNationality"),
            generalAnswersOpt.getStringOpt("nationality"),
            undergradDegreeOpt.getStringOpt("name"),
            undergradDegreeOpt.getStringOpt("classification"),
            undergradDegreeOpt.getStringOpt("graduationYear"),
            undergradDegreeOpt.getStringOpt("moduleDetails"),
            postgradDegreeOpt.getStringOpt("name"),
            postgradDegreeOpt.getStringOpt("otherDetails"),
            postgradDegreeOpt.getStringOpt("graduationYear"),
            postgradDegreeOpt.getStringOpt("projectDetails")
          ) ++ schemeTextAnswers: _*
        )
        extractAppId(doc) -> csvRecord
      }.toMap
      CsvExtract(siftAnswersHeader, csvRecords)
    }
  }

  override def findDataAnalystSiftAnswers: Future[CsvExtract[String]] = {
    val query = Document.empty
    commonFindDataAnalystSiftAnswers(query)
  }

  override def findDataAnalystSiftAnswers(applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    commonFindDataAnalystSiftAnswers(query)
  }

  private def commonFindDataAnalystSiftAnswers(query: Document): Future[CsvExtract[String]] = {
    val projection = Projections.excludeId()

    siftAnswersCollection.find(query).projection(projection).toFuture().map { docs =>
      val csvRecords = docs.map { doc =>

        val generalAnswersOpt = subDocRoot("generalAnswers")(doc)
        val undergradDegreeOpt = generalAnswersOpt.flatMap( doc =>  Try(doc.get("undergradDegree").asDocument()).toOption ) // Handle NPE

        val csvRecord = makeRow(
          List(
            generalAnswersOpt.getStringOpt("nationality"),
            undergradDegreeOpt.getStringOpt("name"),
            undergradDegreeOpt.getStringOpt("classification"),
            undergradDegreeOpt.getStringOpt("graduationYear")
          ): _*
        )
        extractAppId(doc) -> csvRecord
      }.toMap
      CsvExtract(dataAnalystSiftAnswersHeader, csvRecords)
    }
  }

  override def findEventsDetails: Future[CsvExtract[String]] = {
    findEventsDetails(Seq.empty[String])
  }

  override def findEventsDetails(applicationIds: Seq[String]): Future[CsvExtract[String]] = {

    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    val projection = Projections.excludeId()

    val allEventsFut = eventCollection.find(query).projection(projection).toFuture().map { docs =>
      docs.map { doc =>
        val id = doc.get("id").map( _.asString().getValue )

        id.getOrElse("") ->
          List(
            id,
            doc.get("eventType").map( _.asString().getValue ),
            doc.get("description").map( _.asString().getValue ),
            subDocRoot("location")(doc).map( _.get("name").asString() ),
            subDocRoot("venue")(doc).map( _.get("description").asString() ),
            doc.get("date").map( _.asString().getValue )
          ).flatten.mkString(" ")
      }.toMap
    }

    allEventsFut.flatMap { allEvents =>
      candidateAllocationCollection.find(Document.empty).projection(projection).toFuture().map { docs =>
        val csvRecords = docs.map { doc =>
          val eventId = doc.get("eventId").map( _.asString().getValue ).getOrElse("-")
          val csvRecord = makeRow(
            Some(List(
              allEvents.get(eventId).map( e => s"Event: $e" ),
              doc.get("sessionId").map( s => s"session: $eventId/${s.asString().getValue}" ),
              doc.get("status").map( _.asString().getValue ),
              Try(doc.get("removeReason").map( _.asString().getValue )).toOption.flatten,
              doc.get("createdAt").map( d => s"on ${d.asString().getValue}" )
            ).flatten.mkString(" ")
            )
          )
          doc.get("id").map( _.asString().getValue ).getOrElse("") -> csvRecord
        }.groupBy { case (id, _) => id }.map {
          case (appId, events) => appId -> events.map { case (_, eventList) => eventList }.mkString(" --- ")
        }
        CsvExtract(eventsDetailsHeader, csvRecords)
      }
    }
  }

  override def findMediaDetails: Future[CsvExtract[String]] = {
    val query = Document.empty
    val projection = Projections.excludeId()

    commonFindMediaDetails(query, projection)
  }

  override def findMediaDetails(userIds: Seq[String]): Future[CsvExtract[String]] = {
    val query = Document("userId" -> Document("$in" -> userIds))
    val projection = Projections.excludeId()

    commonFindMediaDetails(query, projection)
  }

  private def commonFindMediaDetails(query: Document, projection: Bson) = {
    mediaCollection.find(query).projection(projection).toFuture.map { docs =>
      val csvRecords = docs.map { doc =>
        val csvRecord = makeRow(
          doc.get("media").map(_.asString().getValue)
        )
        extractUserId(doc) -> csvRecord
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

  private def findAssessmentScores(name: String, col: MongoCollection[Document], applicationIds: Seq[String]): Future[CsvExtract[String]] = {
    val exerciseSections = assessmentScoresNumericFields.map(_._1)
    val projection = Projections.excludeId()
    val query = Document("applicationId" -> Document("$in" -> applicationIds))

    col.find(query).projection(projection).toFuture.map { docs =>

      val csvRecords = docs.map { doc =>
        val csvStr = exerciseSections.flatMap { s =>
          val sectionOpt = subDocRoot(s)(doc)

          List(
            sectionOpt.getBooleanOpt("attended"),
            sectionOpt.getStringOpt("updatedBy"),
            sectionOpt.getDateTimeOpt("submittedDate")
          ) ++
            assessmentScoresNumericFieldsMap(s).split(",").map { field =>
              sectionOpt.getDoubleOpt(field)
            } ++
            assessmentScoresFeedbackFieldsMap(s).split(",").map { field =>
              sectionOpt.getStringOpt(field)
            }
        } ++ {
          val sectionOpt = subDocRoot("finalFeedback")(doc)
          List(
            sectionOpt.getStringOpt("feedback"),
            sectionOpt.getStringOpt("updatedBy"),
            sectionOpt.getDateTimeOpt("acceptedDate")
          )
        }
        val csvRecord = makeRow(csvStr: _*)
        extractAppId(doc) -> csvRecord
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
      "B32-BIRM", "B78-BRISL", "C05-CAM", "C15-CARDF", "D86-DUR", "E56-EDINB", "E84-EXETR", "G28-GLASG", "I50-IMP", "K60-KCL",
      "L23-LEEDS", "L41-LVRPL", "L72-LSE", "M20-MANU", "N21-NEWC", "N84-NOTTM", "O33-OXF", "Q75-QBELF", "S18-SHEFD",
      "S27-SOTON", "Q50-QMUL", "U80-UCL", "W20-WARWK", "Y50-YORK"
    )
    code.flatMap(c => if (russellGroupUnis.contains(c)) Y else N)
  }

  private def fsbScoresAndFeedback(doc: Document): List[Option[String]] = {
    val scoresAndFeedbackOpt = (for {
      fsb <- subDocRoot("testGroups")(doc).map(_.get("FSB"))
    } yield {
      Try(Codecs.fromBson[ScoresAndFeedback](fsb.asDocument().get("scoresAndFeedback"))).toOption
    }).flatten
    scoresAndFeedbackOpt.map { saf =>
      List(Some(saf.overallScore.toString), Some(saf.feedback))
    }.getOrElse( List(None, None) )
  }

  private def videoInterview(doc: Document): List[Option[String]] = {
    val testGroupsOpt = subDocRoot("testGroups")(doc)
    val videoTestSectionOpt = testGroupsOpt.flatMap(testGroups => subDocRoot("PHASE3")(testGroups))
    import scala.jdk.CollectionConverters._
    val videoTestsOpt = videoTestSectionOpt.flatMap{ p3 =>
      Try(p3.get("tests").asArray().getValues.asScala.toList.map ( _.asDocument() )).toOption
    }

    val activeVideoTestOpt = videoTestsOpt.map( docList => docList.filter( doc => Try(doc.get("usedForResults").asBoolean().getValue).getOrElse(false) ).head )

    val activeVideoTestCallbacksOpt = activeVideoTestOpt.flatMap( doc => subDocRoot("callbacks")(doc) )
    val activeVideoTestReviewedCallbacksOpt = activeVideoTestCallbacksOpt.map( _.get("reviewed").asArray().getValues.asScala.toList.map ( _.asDocument() ))

    val latestAVTRCallbackOpt = activeVideoTestReviewedCallbacksOpt.flatMap {
      reviewedCallbacks =>
        reviewedCallbacks.sortWith { (r1, r2) =>
          val r1Received = extractDateTime(r1, "received")
          val r2Received = extractDateTime(r2, "received")
          r1Received.isAfter(r2Received)
        }.headOption.map( Codecs.fromBson[ReviewedCallbackRequest] )
    }

    val latestReviewerOpt = latestAVTRCallbackOpt.map {
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
      activeVideoTestOpt.getIntOpt("interviewId"),
      activeVideoTestOpt.getStringOpt("token"),
      activeVideoTestOpt.getStringOpt("candidateId"),
      activeVideoTestOpt.getStringOpt("customCandidateId"),
      latestReviewerOpt.flatMap(_.comment),
      latestAVTRCallbackOpt.map(_.received.toString),
      latestReviewerOpt.flatMap(_.question1.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question1.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question2.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question2.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question3.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question3.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question4.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question4.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question5.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question5.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question6.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question6.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question7.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question7.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question8.reviewCriteria1.score.map(_.toString)),
      latestReviewerOpt.flatMap(_.question8.reviewCriteria2.score.map(_.toString)),
      latestReviewerOpt.map(reviewer => totalForQuestions(reviewer).toString)
    )
  }

  private def getSchemeResults(results: List[BsonDocument]) = results.map {
    result => Try(result.get("schemeId").asString().getValue).getOrElse("") + ": " + Try(result.get("result").asString().getValue).getOrElse("")
  }

  private def testEvaluations(doc: Document, numOfSchemes: Int): List[Option[String]] = {
    val testGroupsOpt = subDocRoot("testGroups")(doc)

    List("PHASE1", "PHASE2", "PHASE3", "SIFT_PHASE", "FSAC", "FSB").flatMap { sectionName => {
      val testSectionOpt = testGroupsOpt.flatMap( section => subDocRoot(sectionName)(section) )
      val testsEvaluationOpt = testSectionOpt.flatMap ( evaluation => subDocRoot("evaluation")(evaluation) )

      import scala.jdk.CollectionConverters._
      // Handle NPE
      val testEvalResults = testsEvaluationOpt.flatMap( eval => Try(eval.get("result").asArray().getValues.asScala.toList.map ( _.asDocument() )).toOption
        .orElse(testsEvaluationOpt.map( eval => eval.get("schemes-evaluation").asArray().getValues.asScala.toList.map ( _.asDocument() )) ))

      val evalResultsMap = testEvalResults.map(getSchemeResults)
      padResults(evalResultsMap, numOfSchemes)
    }}
  }

  private def currentSchemeStatus(doc: Document, numOfSchemes: Int): List[Option[String]] = {
    import scala.jdk.CollectionConverters._
    val testEvalResults = doc.get("currentSchemeStatus").map(_.asArray().getValues.asScala.toList.map ( _.asDocument() ))

    val evalResultsMap = testEvalResults.map(getSchemeResults)
    padResults(evalResultsMap, numOfSchemes)
  }

  private def padResults(evalResultsMap: Option[List[String]], numOfSchemes: Int) = {
    val schemeResults = evalResultsMap.getOrElse(Nil)
    schemeResults.map(Option(_)) ::: (1 to (numOfSchemes - schemeResults.size)).toList.map(_ => Some(""))
  }

  private def fsacCompetency(doc: Document): List[Option[String]] = {
    val testGroupsOpt = subDocRoot("testGroups")(doc)
    val testSectionOpt = testGroupsOpt.flatMap( testGroups => subDocRoot("FSAC")(testGroups) )
    val testsEvaluationOpt = testSectionOpt.flatMap( evaluation => subDocRoot("evaluation")(evaluation) )

    val passedMin = testsEvaluationOpt.getBooleanOpt("passedMinimumCompetencyLevel")
    val competencyAvgOpt = testsEvaluationOpt.flatMap( evaluation => subDocRoot("competency-average")(evaluation))
    passedMin :: List(
      "makingEffectiveDecisionsAverage",
      "workingTogetherDevelopingSelfAndOthersAverage",
      "communicatingAndInfluencingAverage",
      "seeingTheBigPictureAverage",
      "overallScore"
    ).map(key => competencyAvgOpt.getDoubleOpt(key) )
  }

  private def extractDateTime(doc: BsonDocument, key: String) = {
    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed for ISODate
    Codecs.fromBson[DateTime](doc.get(key).asDateTime())
  }

  private def onlineTests(doc: Document): Map[String, List[Option[String]]] = {

    def extractDataFromTest(test: Option[BsonDocument]) = {
      val testResults = test.flatMap ( doc => subDocRoot("testResult")(doc) )

      List(
        test.getStringOpt("inventoryId"),
        test.getStringOpt("orderId"),
        test.getStringOpt("normId"),
        test.getStringOpt("reportId"),
        test.getStringOpt("assessmentId"),
        test.getStringOpt("testUrl"),
        test.getDateTimeOpt("invitationDate"),
        test.getDateTimeOpt("startedDateTime"),
        test.getDateTimeOpt("completedDateTime"),
        testResults.getDoubleOpt("tScore"),
        testResults.getDoubleOpt("rawScore"),
        testResults.getStringOpt("testReportUrl")
      )
    }

    def getInventoryId(config: Map[String, PsiTestIds], testName: String, phase: String) =
      config.getOrElse(testName, throw new Exception(s"No inventoryId found for $phase $testName")).inventoryId

    // Phase1 data
    val phase1TestConfig = appConfig.onlineTestsGatewayConfig.phase1Tests.tests
    val test1InventoryId = getInventoryId(phase1TestConfig, "test1", "phase1")
    val test2InventoryId = getInventoryId(phase1TestConfig, "test2", "phase1")
    val test3InventoryId = getInventoryId(phase1TestConfig, "test3", "phase1")

    val testGroupsOpt = subDocRoot("testGroups")(doc)

    import scala.jdk.CollectionConverters._
    val phase1Tests = for {
      testGroups <- testGroupsOpt
      phase1Tests <- subDocRoot("PHASE1")(testGroups).map(_.get("tests").asArray())
    } yield phase1Tests.getValues.asScala.toList.map ( _.asDocument() )

    val phase1Test1 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == test1InventoryId && test.getBoolean("usedForResults").getValue
      }
    )
    val phase1Test2 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == test2InventoryId && test.getBoolean("usedForResults").getValue
      }
    )
    val phase1Test3 = phase1Tests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == test3InventoryId && test.getBoolean("usedForResults").getValue
      }
    )

    // Phase2 data
    val phase2TestConfig = appConfig.onlineTestsGatewayConfig.phase2Tests.tests
    val phase2Test1InventoryId = getInventoryId(phase2TestConfig, "test1", "phase2")
    val phase2Test2InventoryId = getInventoryId(phase2TestConfig, "test2", "phase2")

    val phase2Tests = for {
      testGroups <- testGroupsOpt
      phase2Tests <- subDocRoot("PHASE2")(testGroups).map(_.get("tests").asArray())
    } yield phase2Tests.getValues.asScala.toList.map ( _.asDocument() )

    val phase2Test1 = phase2Tests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == phase2Test1InventoryId && test.getBoolean("usedForResults").getValue
      }
    )
    val phase2Test2 = phase2Tests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == phase2Test2InventoryId && test.getBoolean("usedForResults").getValue
      }
    )

    // Sift data
    val siftTestConfig = appConfig.onlineTestsGatewayConfig.numericalTests.tests
    val siftTestInventoryId = getInventoryId(siftTestConfig, "test1", "sift")

    val siftTests = for {
      testGroups <- testGroupsOpt
      siftTests <- Try(subDocRoot("SIFT_PHASE")(testGroups).map(_.get("tests").asArray())).toOption.flatten // Handle NPE
    } yield siftTests.getValues.asScala.toList.map ( _.asDocument() )

    val siftTest = siftTests.flatMap( tests =>
      tests.find { test =>
        test.getString("inventoryId").getValue == siftTestInventoryId && test.getBoolean("usedForResults").getValue
      }
    )

    Map(
      Tests.Phase1Test1.toString -> extractDataFromTest(phase1Test1),
      Tests.Phase1Test2.toString -> extractDataFromTest(phase1Test2),
      Tests.Phase1Test3.toString -> extractDataFromTest(phase1Test3),
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

  private def markDisabilityCategories(disabilityCategories: List[String]) = {
    val disabilityCategoriesList = List(
      "Learning difference such as dyslexia, dyspraxia or AD(H)D",
      "Social/communication conditions such as a speech and language impairment or an autistic spectrum condition",
      "Long-term illness or health condition such as cancer, HIV, diabetes, chronic heart disease, or epilepsy",
      "Mental health condition, challenge or disorder, such as depression, schizophrenia or anxiety",
      "Physical impairment (a condition that substantially limits one or more basic physical activities such as walking, climbing stairs, lifting or carrying)",
      "D/deaf or have a hearing impairment",
      "Blind or have a visual impairment uncorrected by glasses",
      "Development condition that you have had since childhood which affects motor, cognitive, social and emotional skills, and speech and language",
      "No known impairment, health condition or learning difference",
      "An impairment, health condition or learning difference not listed above",
      "Prefer Not to Say",
      "Other"
    )
    disabilityCategoriesList.map ( dc => if (disabilityCategories.contains(dc)) Y else N )
  }

  private def assistanceDetails(doc: Document): List[Option[String]] = {
    val assistanceDetailsOpt = subDocRoot("assistance-details")(doc)
    val etrayAdjustmentsOpt = Try(assistanceDetailsOpt.map( doc => doc.get("etray").asDocument() )).toOption.flatten // Handle NPE
    val videoAdjustmentsOpt = Try(assistanceDetailsOpt.map( doc => doc.get("video").asDocument() )).toOption.flatten // Handle NPE

    import scala.jdk.CollectionConverters._
    val typeOfAdjustments = assistanceDetailsOpt.map { ad =>
      if (ad.containsKey("typeOfAdjustments"))
        ad.get("typeOfAdjustments").asArray().getValues.asScala.toList.map(_.asString().getValue)
      else Nil
    }.getOrElse(Nil)

    val markedDisabilityCategories = markDisabilityCategories(
      assistanceDetailsOpt.map { ad =>
        if (ad.containsKey("disabilityCategories"))
          ad.get("disabilityCategories").asArray().getValues.asScala.toList.map(_.asString().getValue)
        else Nil
      }.getOrElse(Nil)
    )

    List(
      assistanceDetailsOpt.getStringOpt("hasDisability"),
        assistanceDetailsOpt.getStringOpt("disabilityImpact"),
    ) ++ markedDisabilityCategories ++
      List(
        assistanceDetailsOpt.getStringOpt("otherDisabilityDescription"),
        if (assistanceDetailsOpt.getAsBoolean("guaranteedInterview")) Y else N,
        if (assistanceDetailsOpt.getAsBoolean("needsSupportAtVenue")) Y else N,
        assistanceDetailsOpt.getStringOpt("needsSupportAtVenueDescription"),
        if (assistanceDetailsOpt.getAsBoolean("needsSupportForPhoneInterview")) Y else N,
        assistanceDetailsOpt.getStringOpt("needsSupportForPhoneInterviewDescription"),
        assistanceDetailsOpt.getStringOpt("adjustmentsComment")
      )
  }

  private def disabilityDetails(doc: Document): List[Option[String]] = {
    val assistanceDetailsOpt = subDocRoot("assistance-details")(doc)

    import scala.jdk.CollectionConverters._
    val markedDisabilityCategories = markDisabilityCategories(
      assistanceDetailsOpt.map { ad =>
        if (ad.containsKey("disabilityCategories"))
          ad.get("disabilityCategories").asArray().getValues.asScala.toList.map(_.asString().getValue)
        else Nil
      }.getOrElse(Nil)
    )
    List(
      assistanceDetailsOpt.map(_.get("hasDisability").asString().getValue),
      Try(assistanceDetailsOpt.map(_.get("disabilityImpact").asString().getValue)).toOption.flatten,
    ) ++ markedDisabilityCategories ++
      List(
        Try(assistanceDetailsOpt.map(_.get("otherDisabilityDescription").asString().getValue)).toOption.flatten
      )
  }

  private def personalDetails(doc: Document, isAnalystReport: Boolean) = {
    val columns = if (isAnalystReport) { List("dateOfBirth") } else { List("firstName", "lastName","preferredName", "dateOfBirth") }
    val personalDetailsOpt = subDocRoot("personal-details")(doc)
    columns.map ( columnName => personalDetailsOpt.map(_.get(columnName).asString().getValue) )
  }


  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")
}
