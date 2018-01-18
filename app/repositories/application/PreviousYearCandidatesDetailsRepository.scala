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

package repositories.application

import connectors.launchpadgateway.exchangeobjects.in.reviewed._
import factories.DateTimeFactory
import model.command.{ CandidateDetailsReportItem, CsvExtract }
import model.{ CivilServiceExperienceType, InternshipType, ProgressStatuses }
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONReader, BSONValue }
import reactivemongo.json.ImplicitBSONHandlers._
import reactivemongo.json.collection.JSONCollection
import repositories.{ BSONDateTimeHandler, CollectionNames, CommonBSONDocuments, SchemeYamlRepository }
import services.reporting.SocioEconomicCalculator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PreviousYearCandidatesDetailsRepository {

  implicit class RichOptionBSONDocument(doc: Option[BSONDocument]) {

    def getAs[T](key: String)(implicit reader: BSONReader[_ <: BSONValue, T]) = doc.flatMap(_.getAs[T](key))

    def getAsStr[T](key: String)(implicit reader: BSONReader[_ <: BSONValue, T]) = doc.flatMap(_.getAs[T](key)).map(_.toString)

    def getOrElseAsStr[T](key: String)(default: T)(implicit reader: BSONReader[_ <: BSONValue, T]) = {
      doc.flatMap(_.getAs[T](key).orElse(Some(default))).map(_.toString)
    }
  }

  // scalastyle:off

  private val appTestStatuses = "personal-details,IN_PROGRESS,scheme-preferences,partner-graduate-programmes,assistance-details,start_questionnaire,diversity_questionnaire,education_questionnaire,occupation_questionnaire,preview,SUBMITTED,FAST_PASS_ACCEPTED,PHASE1_TESTS_INVITED,PHASE1_TESTS_FIRST_REMINDER,PHASE1_TESTS_SECOND_REMINDER,PHASE1_TESTS_STARTED,PHASE1_TESTS_COMPLETED,PHASE1_TESTS_EXPIRED,PHASE1_TESTS_RESULTS_READY," +
    "PHASE1_TESTS_RESULTS_RECEIVED,PHASE1_TESTS_PASSED,PHASE1_TESTS_PASSED_NOTIFIED,PHASE1_TESTS_FAILED,PHASE1_TESTS_FAILED_NOTIFIED,PHASE1_TESTS_FAILED_SDIP_AMBER," +
    "PHASE2_TESTS_INVITED,PHASE2_TESTS_FIRST_REMINDER," +
    "PHASE2_TESTS_SECOND_REMINDER,PHASE2_TESTS_STARTED,PHASE2_TESTS_COMPLETED,PHASE2_TESTS_EXPIRED,PHASE2_TESTS_RESULTS_READY," +
    "PHASE2_TESTS_RESULTS_RECEIVED,PHASE2_TESTS_PASSED,PHASE2_TESTS_FAILED,PHASE2_TESTS_FAILED_NOTIFIED,PHASE2_TESTS_FAILED_SDIP_AMBER,PHASE3_TESTS_INVITED,PHASE3_TESTS_FIRST_REMINDER," +
    "PHASE3_TESTS_SECOND_REMINDER,PHASE3_TESTS_STARTED,PHASE3_TESTS_COMPLETED,PHASE3_TESTS_EXPIRED,PHASE3_TESTS_RESULTS_RECEIVED," +
    "PHASE3_TESTS_PASSED_WITH_AMBER,PHASE3_TESTS_PASSED,PHASE3_TESTS_PASSED_NOTIFIED,PHASE3_TESTS_FAILED,PHASE3_TESTS_FAILED_NOTIFIED,PHASE3_TESTS_FAILED_SDIP_AMBER," +
    "SIFT_ENTERED,SIFT_READY,SIFT_COMPLETED,FAILED_AT_SIFT,FAILED_AT_SIFT_NOTIFIED,SDIP_FAILED_AT_SIFT,SIFT_FASTSTREAM_FAILED_SDIP_GREEN," +
    "ASSESSMENT_CENTRE_AWAITING_ALLOCATION,ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED,ASSESSMENT_CENTRE_FAILED_TO_ATTEND," +
    "ASSESSMENT_CENTRE_SCORES_ENTERED,ASSESSMENT_CENTRE_SCORES_ACCEPTED,ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION,ASSESSMENT_CENTRE_PASSED,ASSESSMENT_CENTRE_FAILED," +
    "ASSESSMENT_CENTRE_FAILED_NOTIFIED,ASSESSMENT_CENTRE_FAILED_SDIP_GREEN,ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED," +
    "FSB_AWAITING_ALLOCATION,FSB_ALLOCATION_UNCONFIRMED,FSB_ALLOCATION_CONFIRMED,FSB_FAILED_TO_ATTEND," +
    "FSB_RESULT_ENTERED,FSB_PASSED,FSB_FAILED,ALL_FSBS_AND_FSACS_FAILED,ALL_FSBS_AND_FSACS_FAILED_NOTIFIED," +
    "ELIGIBLE_FOR_JOB_OFFER,ELIGIBLE_FOR_JOB_OFFER_NOTIFIED,WITHDRAWN,"


  val fsacCompetencyHeaders = "FSAC passedMinimumCompetencyLevel,analysisAndDecisionMakingAverage,buildingProductiveRelationshipsAverage,leadingAndCommunicatingAverage,strategicApproachToObjectivesAverage,overallScore,"

  private val appTestResults =
    List("PHASE 1", "PHASE 2", "PHASE 3", "SIFT", "FSAC", "FSB", "Current Scheme Status").map { s =>
      s"$s result,result,result,result,result,result,result,result,result,result,result,result,result,result,result,result,result,result"
    }
      .mkString(",")

  val applicationDetailsHeader = "applicationId,userId,Framework ID,Application Status,Route,First name,Last name,Preferred Name,Date of Birth,Are you eligible,Terms and Conditions," +
    "Currently a Civil Servant done SDIP or EDIP,Currently Civil Servant,Currently Civil Service via Fast Track," +
    "EDIP,SDIP 2016 (previous years),Fast Pass (sdip 2017),Fast Pass No,Scheme preferences,Scheme names,Are you happy with order,Are you eligible," +
    "Do you want to defer,Deferal selections,Do you have a disability,Provide more info,GIS,Extra support online tests," +
    "What adjustments will you need,Extra support f2f,What adjustments will you need,Extra support phone interview,What adjustments will you need,E-Tray time extension,E-Tray invigilated,E-Tray invigilated notes,E-Tray other notes,Video time extension,Video invigilated,Video invigilated notes,Video other notes,Additional comments,Adjustments confirmed,I understand this wont affect application," +
    "PHASE1 tests behavioural scheduleId,cubiksUserId,Cubiks token," +
    "Behavioural testUrl,invitationDate,participantScheduleId,startedDateTime,completedDateTime,reportId,reportLinkURL," +
    "Behavioural T-score," +
    "Behavioural Percentile,Behavioural Raw,Behavioural STEN,Situational scheduleId,cubiksUserId,Cubiks token," +
    "Situational testUrl,invitationDate,participantScheduleId,startedDateTime,completedDateTime,reportId," +
    "reportLinkURL," +
    "Situational T-score,Situational Percentile,Situational Raw,Situational STEN," +
    "PHASE_2 scheduleId,cubiksUserId,token,testUrl,invitiationDate,participantScheduleId,startedDateTime,completedDateTime,reportLinkURL,reportId," +
    "e-Tray T-score,e-Tray Raw,PHASE 3 interviewId,token,candidateId,customCandidateId,comment,Q1 Capability,Q1 Engagement,Q2 Capability,Q2 Engagement,Q3 Capability," +
    "Q3 Engagement,Q4 Capability,Q4 Engagement,Q5 Capability,Q5 Engagement,Q6 Capability,Q6 Engagement,Q7 Capability," +
    "Q7 Engagement,Q8 Capability,Q8 Engagement,Overall total," +
    appTestStatuses +
    fsacCompetencyHeaders +
    appTestResults

  val contactDetailsHeader = "Email,Address line1,Address line2,Address line3,Address line4,Postcode,Outside UK,Country,Phone"

  val questionnaireDetailsHeader: String = "Gender Identity,Sexual Orientation,Ethnic Group,Live in UK between 14-18?,Home postcode at 14," +
    "Name of school 14-16,Name of school 16-18,Eligible for free school meals?,University name,Category of degree," +
    "Parent guardian completed Uni?,Parents job at 14,Employee?,Size," +
    "Supervise employees,SE 1-5,Oxbridge,Russell Group"

  val mediaHeader = "How did you hear about us?"

  val eventsDetailsHeader = "Allocated events"

  val allSchemes: List[String] = SchemeYamlRepository.schemes.map(_.id.value).toList

  val siftAnswersHeader: String = "Sift Answers status,multipleNationalities,secondNationality,nationality," +
    "undergrad degree name,classification,graduationYear,moduleDetails," +
    "postgrad degree name,classification,graduationYear,moduleDetails," + allSchemes.mkString(",")

  val assessmentScoresNumericFields = Seq(
    "analysisExercise" -> "analysisAndDecisionMakingAverage,leadingAndCommunicatingAverage,strategicApproachToObjectivesAverage",
    "leadershipExercise" -> "buildingProductiveRelationshipsAverage,leadingAndCommunicatingAverage,strategicApproachToObjectivesAverage",
    "groupExercise" -> "analysisAndDecisionMakingAverage,leadingAndCommunicatingAverage,buildingProductiveRelationshipsAverage"
  )

  val assessmentScoresFeedbackFields = Seq(
    "analysisExercise" -> "analysisAndDecisionMakingFeedback,leadingAndCommunicatingFeedback,strategicApproachToObjectivesFeedback",
    "leadershipExercise" -> "buildingProductiveRelationshipsFeedback,leadingAndCommunicatingFeedback,strategicApproachToObjectivesFeedback",
    "groupExercise" -> "analysisAndDecisionMakingFeedback,leadingAndCommunicatingFeedback,buildingProductiveRelationshipsFeedback"
  )

  val assessmentScoresNumericFieldsMap: Map[String, String] = assessmentScoresNumericFields.toMap

  val assessmentScoresFeedbackFieldsMap: Map[String, String] = assessmentScoresFeedbackFields.toMap

  def assessmentScoresHeaders(assessor: String): String = {
    assessmentScoresNumericFields.map(_._1).map { exercise =>
      s"$assessor $exercise attended,updatedBy,submittedDate,${assessmentScoresNumericFieldsMap(exercise)},${assessmentScoresFeedbackFieldsMap(exercise)}"
    }.mkString(",") + s",$assessor Final feedback,updatedBy,acceptedDate"
  }


  def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem]

  def findContactDetails(): Future[CsvExtract[String]]

  def findQuestionnaireDetails(): Future[CsvExtract[String]]

  def findMediaDetails(): Future[CsvExtract[String]]

  def findAssessorAssessmentScores(): Future[CsvExtract[String]]

  def findReviewerAssessmentScores(): Future[CsvExtract[String]]

  def findEventsDetails(): Future[CsvExtract[String]]

  def findSiftAnswers(): Future[CsvExtract[String]]
}

class PreviousYearCandidatesDetailsMongoRepository()(implicit mongo: () => DB)
  extends PreviousYearCandidatesDetailsRepository with CommonBSONDocuments {

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

  override def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem] = {
    adsCounter = 0

    val projection = Json.obj("_id" -> 0)

    applicationDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .enumerate().map { doc =>

      try {
        val applicationId = doc.getAs[String]("applicationId").get
        val progressResponse = toProgressResponse(applicationId).read(doc)
        val (civilServiceExperienceType, civilServiceInternshipTypes, fastPassCertificateNo) = civilServiceExperience(doc)

        val schemePrefs: List[String] = doc.getAs[BSONDocument]("scheme-preferences").flatMap(_.getAs[List[String]]("schemes")).getOrElse(Nil)
        val schemePrefsAsString: Option[String] = Some(schemePrefs.mkString(","))
        val schemesYesNoAsString: Option[String] = Option((schemePrefs.map(_ + ": Yes") ::: allSchemes.filterNot(schemePrefs.contains).map(_ + ": No")).mkString(","))

        val onlineTestResults = onlineTests(doc)

        adsCounter += 1
        val applicationIdOpt = doc.getAs[String]("applicationId")
        val csvContent = makeRow(
          List(applicationIdOpt) :::
            List(doc.getAs[String]("userId")) :::
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
            List(fastPassCertificateNo) :::
            List(schemePrefsAsString) :::
            List(schemesYesNoAsString) :::
            List(progressResponseReachedYesNo(progressResponse.schemePreferences)) :::
            List(progressResponseReachedYesNo(progressResponse.schemePreferences)) :::
            partnerGraduateProgrammes(doc) :::
            assistanceDetails(doc) :::
            List(progressResponseReachedYesNo(progressResponse.questionnaire.nonEmpty)) :::
            onlineTestResults("bq") :::
            onlineTestResults("sjq") :::
            onlineTestResults("etray") :::
            videoInterview(doc) :::
            progressStatusTimestamps(doc) :::
            fsacCompetency(doc) :::
            testEvaluations(doc) :::
            currentSchemeStatus(doc)
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
      progressStatus.getOrElseAsStr[Boolean]("partner-graduate-programmes")(false),
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
        ProgressStatuses.PHASE3_TESTS_FAILED_SDIP_AMBER
      ).map(timestampFor) ++
      List(
        ProgressStatuses.SIFT_ENTERED,
        ProgressStatuses.SIFT_READY,
        ProgressStatuses.SIFT_COMPLETED,
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

  private def partnerGraduateProgrammes(doc: BSONDocument) = {
    val subDoc = doc.getAs[BSONDocument]("partner-graduate-programmes")
    val interested = subDoc.flatMap(_.getAs[Boolean]("interested")).getOrElse(false)

    List(
      if (interested) Y else N,
      subDoc.map(_.getAs[List[String]]("partnerGraduateProgrammes").getOrElse(Nil).mkString(","))
    )
  }

  override def findContactDetails(): Future[CsvExtract[String]] = {

    val projection = Json.obj("_id" -> 0)

    contactDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
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

  def findQuestionnaireDetails(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    def getAnswer(question: String, doc: Option[BSONDocument]) = {
      val questionDoc = doc.flatMap(_.getAs[BSONDocument](question))
      val isUnknown = questionDoc.flatMap(_.getAs[Boolean]("unknown")).contains(true)
      isUnknown match {
        case true => Some("Unknown")
        case _ => questionDoc.flatMap(q => q.getAs[String]("answer") match {
          case None => q.getAs[String]("otherDetails")
          case Some(answer) if List("Other", "Other ethnic group").contains(answer) => q.getAs[String]("otherDetails")
          case Some(answer) => Some(answer)
        })
      }
    }

    questionnaireCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
      val csvRecords = docs.map { doc =>
        val questionsDoc = doc.getAs[BSONDocument]("questions")
        val universityName = getAnswer("What is the name of the university you received your degree from?", questionsDoc)

        val allQuestionsAndAnswers = questionsDoc.toList.flatMap(_.elements).map {
          case (question, _) =>
            val answer = getAnswer(question, questionsDoc).getOrElse("Unknown")
            (question, answer)
        }.toMap

        val csvRecord = makeRow(
          getAnswer("What is your gender identity?", questionsDoc),
          getAnswer("What is your sexual orientation?", questionsDoc),
          getAnswer("What is your ethnic group?", questionsDoc),
          getAnswer("Did you live in the UK between the ages of 14 and 18?", questionsDoc),
          getAnswer("What was your home postcode when you were 14?", questionsDoc),
          getAnswer("Aged 14 to 16 what was the name of your school?", questionsDoc),
          getAnswer("Aged 16 to 18 what was the name of your school or college?", questionsDoc),
          getAnswer("Were you at any time eligible for free school meals?", questionsDoc),
          universityName,
          getAnswer("Which category best describes your degree?", questionsDoc),
          getAnswer("Do you have a parent or guardian that has completed a university degree course or equivalent?", questionsDoc),
          getAnswer("When you were 14, what kind of work did your highest-earning parent or guardian do?", questionsDoc),
          getAnswer("Did they work as an employee or were they self-employed?", questionsDoc),

          getAnswer("Which size would best describe their place of work?", questionsDoc),
          getAnswer("Did they supervise employees?", questionsDoc),
          Some(SocioEconomicCalculator.calculate(allQuestionsAndAnswers)),
          isOxbridge(universityName),
          isRussellGroup(universityName)
        )
        doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
      }
      CsvExtract(questionnaireDetailsHeader, csvRecords.toMap)
    }
  }

  def findSiftAnswers(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    siftAnswersCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
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

  def findEventsDetails(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    val allEventsFut = eventCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
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
      candidateAllocationCollection.find(Json.obj(), projection)
        .cursor[BSONDocument](ReadPreference.nearest)
        .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val eventId = doc.getAs[String]("eventId").getOrElse("-")
          val csvRecord = makeRow(
            Some(List(
              allEvents.get(eventId).map(e => s"Event: $e"),
              doc.getAs[String]("sessionId").map(s => s"session: $s"),
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

  def findMediaDetails(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    mediaCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
      val csvRecords = docs.map { doc =>
        val csvRecord = makeRow(
          doc.getAs[String]("media")
        )
        doc.getAs[String]("userId").getOrElse("") -> csvRecord
      }
      CsvExtract(mediaHeader, csvRecords.toMap)
    }
  }


  def findAssessorAssessmentScores(): Future[CsvExtract[String]] = findAssessmentScores("Assessor", assessorAssessmentScoresCollection)

  def findReviewerAssessmentScores(): Future[CsvExtract[String]] = findAssessmentScores("Reviewer", reviewerAssessmentScoresCollection)

  private def findAssessmentScores(name: String, col: JSONCollection): Future[CsvExtract[String]] = {

    val exerciseSections = assessmentScoresNumericFields.map(_._1)
    val projection = Json.obj("_id" -> 0)
    col.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
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

  val russellGroupUnis = List(
    "B32-BIRM", "B78-BRISL", "C05-CAM", "C15-CARDF", "D86-DUR", "E56-EDINB", "E81-EXCO", "G28-GLASG", "I50-IMP", "K60-KCL",
    "L23-LEEDS", "L41-LVRPL", "L72-LSE", "M20-MANU", "N21-NEWC", "N84-NOTTM", "O33-OXF", "Q75-QBELF", "S18-SHEFD",
    "S27-SOTON", "U80-UCL", "W20-WARWK", "Y50-YORK"
  )

  private def isRussellGroup(code: Option[String]): Option[String] = {
    code.flatMap(c => if (russellGroupUnis.contains(c)) Y else N)
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

  private def testEvaluations(doc: BSONDocument): List[Option[String]] = {
    val testGroups = doc.getAs[BSONDocument]("testGroups")

    List("PHASE1", "PHASE2", "PHASE3", "SIFT_PHASE", "FSAC", "FSB").flatMap { sectionName => {
      val testSection = testGroups.getAs[BSONDocument](sectionName)
      val testsEvaluation = testSection.getAs[BSONDocument]("evaluation")
      val testEvalResults = testsEvaluation.getAs[List[BSONDocument]]("result")
        .orElse(testsEvaluation.getAs[List[BSONDocument]]("schemes-evaluation"))
      val evalResultsMap = testEvalResults.map(getSchemeResults)
      val schemeResults = evalResultsMap.getOrElse(Nil)
      schemeResults.map(Option(_)) ::: (1 to (18 - schemeResults.size)).toList.map(_ => Some(""))
    }
    }
  }

  private def fsacCompetency(doc: BSONDocument): List[Option[String]] = {
    val testGroups = doc.getAs[BSONDocument]("testGroups")
    val testSection = testGroups.getAs[BSONDocument]("FSAC")
    val testsEvaluation = testSection.getAs[BSONDocument]("evaluation")


    val passedMin = testsEvaluation.getAs[Boolean]("passedMinimumCompetencyLevel").map(_.toString)
    val competencyAvg = testsEvaluation.getAs[BSONDocument]("competency-average")
    passedMin :: List(
      "analysisAndDecisionMakingAverage",
      "buildingProductiveRelationshipsAverage",
      "leadingAndCommunicatingAverage",
      "strategicApproachToObjectivesAverage",
      "overallScore"
    ).map { f => competencyAvg.getAs[Double](f) map (_.toString) }
  }

  private def currentSchemeStatus(doc: BSONDocument): List[Option[String]] = {
    val testEvalResults = doc.getAs[List[BSONDocument]]("currentSchemeStatus")
    val evalResultsMap = testEvalResults.map(getSchemeResults)
    val schemeResults = evalResultsMap.getOrElse(Nil)
    schemeResults.map(Option(_)) ::: (1 to (18 - schemeResults.size)).toList.map(_ => Some(""))
  }


  private def onlineTests(doc: BSONDocument): Map[String, List[Option[String]]] = {
    val testGroups = doc.getAs[BSONDocument]("testGroups")
    val onlineTestSection = testGroups.flatMap(_.getAs[BSONDocument]("PHASE1"))
    val onlineTests = onlineTestSection.flatMap(_.getAs[List[BSONDocument]]("tests"))
    val etrayTestSection = testGroups.flatMap(_.getAs[BSONDocument]("PHASE2"))
    val etrayTests = etrayTestSection.flatMap(_.getAs[List[BSONDocument]]("tests"))

    val bqTest = onlineTests.flatMap(_.find(test => test.getAs[Int]("scheduleId").get == cubiksGatewayConfig.phase1Tests.scheduleIds("bq") && test.getAs[Boolean]("usedForResults").getOrElse(false)))
    val bqTestResults = bqTest.flatMap {
      _.getAs[BSONDocument]("testResult")
    }

    val sjqTest = onlineTests.flatMap(_.find(test => test.getAs[Int]("scheduleId").get == cubiksGatewayConfig.phase1Tests.scheduleIds("sjq") && test.getAs[Boolean]("usedForResults").getOrElse(false)))
    val sjqTestResults = sjqTest.flatMap {
      _.getAs[BSONDocument]("testResult")
    }

    val validEtrayScheduleIds = cubiksGatewayConfig.phase2Tests.schedules.values.map(_.scheduleId).toList

    val etrayTest = etrayTests.flatMap(_.find(test => validEtrayScheduleIds.contains(test.getAs[Int]("scheduleId").get) && test.getAs[Boolean]("usedForResults").getOrElse(false)))

    val etrayResults = etrayTest.flatMap {
      _.getAs[BSONDocument]("testResult")
    }

    Map(
      "bq" ->
        List(
          bqTest.getAsStr[Int]("scheduleId"),
          bqTest.getAsStr[Int]("cubiksUserId"),
          bqTest.getAsStr[String]("token"),
          bqTest.getAsStr[String]("testUrl"),
          bqTest.getAsStr[DateTime]("invitationDate"),
          bqTest.getAsStr[Int]("participantScheduleId"),
          bqTest.getAsStr[DateTime]("startedDateTime"),
          bqTest.getAsStr[DateTime]("completedDateTime"),
          bqTest.getAsStr[Int]("reportId"),
          bqTest.getAsStr[String]("reportLinkURL"),
          bqTestResults.getAsStr[Double]("tScore"),
          bqTestResults.getAsStr[Double]("percentile"),
          bqTestResults.getAsStr[Double]("raw"),
          bqTestResults.getAsStr[Double]("sten")
        ),
      "sjq" ->
        List(
          sjqTest.getAsStr[Int]("scheduleId"),
          sjqTest.getAsStr[Int]("cubiksUserId"),
          sjqTest.getAsStr[String]("token"),
          sjqTest.getAsStr[String]("testUrl"),
          sjqTest.getAsStr[DateTime]("invitationDate"),
          sjqTest.getAsStr[Int]("participantScheduleId"),
          sjqTest.getAsStr[DateTime]("startedDateTime"),
          sjqTest.getAsStr[DateTime]("completedDateTime"),
          sjqTest.getAsStr[Int]("reportId"),
          sjqTest.getAsStr[String]("reportLinkURL"),
          sjqTestResults.getAsStr[Double]("tScore"),
          sjqTestResults.getAsStr[Double]("percentile"),
          sjqTestResults.getAsStr[Double]("raw"),
          sjqTestResults.getAsStr[Double]("sten")
        ),
      "etray" ->
        List(
          etrayTest.getAsStr[Int]("scheduleId"),
          etrayTest.getAsStr[Int]("cubiksUserId"),
          etrayTest.getAsStr[String]("token"),
          etrayTest.getAsStr[String]("testUrl"),
          etrayTest.getAsStr[DateTime]("invitationDate"),
          etrayTest.getAsStr[Int]("participantScheduleId"),
          etrayTest.getAsStr[DateTime]("startedDateTime"),
          etrayTest.getAsStr[DateTime]("completedDateTime"),
          etrayTest.getAsStr[Int]("reportId"),
          etrayTest.getAsStr[String]("reportLinkURL"),
          etrayResults.getAsStr[Double]("tScore"),
          etrayResults.getAsStr[Double]("raw")
        )
    )
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
      csExperienceDetails.getAs[String]("certificateNumber")
    )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

  override def dateTimeFactory: DateTimeFactory = DateTimeFactory
}
