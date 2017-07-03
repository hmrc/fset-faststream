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

import model.CivilServiceExperienceType
import model.CivilServiceExperienceType.CivilServiceExperienceType
import model.command.{ CandidateDetailsReportItem, CsvExtract, ProgressResponse }
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONCollection
import repositories.{ CollectionNames, CommonBSONDocuments }
import reactivemongo.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PreviousYearCandidatesDetailsRepository {

  // scalastyle:off
  val applicationDetailsHeader = "Framework ID,Application Status,Route,First name,Last name,Preferred Name,Date of Birth,Are you eligible,Terms and Conditions," +
    "Currently a Civil Servant done SDIP or EDIP,Currently Civil Servant,Currently Civil Service via Fast Track," +
    "SDIP,SDIP 2016 (previous years),Fast Pass (sdip 2017),Fast Pass No,Scheme preferences,Scheme names,Are you happy with order,Are you eligible," +
    "Do you want to defer,Deferal selections,Do you have a disability,Provide more info,GIS,Extra support online tests," +
    "What adjustments will you need,Extra support f2f,What adjustments will you need,I understand this wont affect application," +
    "E-Tray time extension," +
    "E-Tray invigilated,E-Tray invigilated notes,E-Tray other notes,Video time extension,Video invigilated," +
    "Video invigilated notes,Video other notes,Additional comments,Adjustments confirmed,Behavioural T-score," +
    "Behavioural Percentile,Behavioural Raw,Behavioural STEN,Situational T-score,Situational Percentile,Situational Raw," +
    "Situational STEN,e-Tray T-score,e-Tray Raw,Q1 Capability,Q1 Engagement,Q2 Capability,Q2 Engagement,Q3 Capability," +
    "Q3 Engagement,Q4 Capability,Q4 Engagement,Q5 Capability,Q5 Engagement,Q6 Capability,Q6 Engagement,Q7 Capability," +
    "Q7 Engagement,Q8 Capability,Q8 Engagement,Overall total,applicationStatus,applicationId,userId," +
    "IN_PROGRESS,SUBMITTED,PHASE1_TESTS_INVITED,PHASE1_TESTS_STARTED,PHASE1_TESTS_COMPLETED,PHASE1_TESTS_RESULTS_READY," +
    "PHASE1_TESTS_RESULTS_RECEIVED,PHASE1_TESTS_PASSED,PHASE2_TESTS_INVITED,PHASE2_TESTS_FIRST_REMINDER," +
    "PHASE2_TESTS_SECOND_REMINDER,PHASE2_TESTS_STARTED,PHASE2_TESTS_COMPLETED,PHASE2_TESTS_RESULTS_READY," +
    "PHASE2_TESTS_RESULTS_RECEIVED,PHASE2_TESTS_PASSED,PHASE3_TESTS_INVITED,PHASE3_TESTS_FIRST_REMINDER," +
    "PHASE3_TESTS_SECOND_REMINDER,PHASE3_TESTS_STARTED,PHASE3_TESTS_COMPLETED,PHASE3_TESTS_RESULTS_RECEIVED," +
    "PHASE3_TESTS_PASSED,PHASE3_TESTS_SUCCESS_NOTIFIED,EXPORTED,PHASE1 tests scheduleId,cubiksUserId,Cubiks token," +
    "testUrl,invitationDate,participantScheduleId,startedDateTime,completedDateTime,reportId,reportLinkURL,reportId," +
    "reportLinkURL,result,result,result,result,result,result,result,result,result,result,result,result,result,result," +
    "result,result,result,PHASE_2 scheduleId,cubiksUserId,token,testUrl,participantScheduleId,reportId,reportLinkURL," +
    "result,result,result,result,result,result,result,result,result,result,result,result,result,result,result,result,result," +
    "interviewId,token,candidateId,customCandidateId,comment,result,result,result,result,result,result,result,result,result," +
    "result,result,result,result,result,result,result,result,"

  val contactDetailsHeader = "Email,Address line1,Address line2,Address line3,Address line4,Postcode,Outside UK,Country,Phone"

  val questionnaireDetailsHeader = "Sexual Orientation,Ethnic Group,Live in UK between 14-18?,Home postcode at 14," +
    "Name of school 14-16,What type of school,Name of school 16-18,University name,Category of degree," +
    "Parent guardian completed Uni?,At age 14 - parents employed?,Parents job at 14,Employee?,Size," +
    "Supervise employees,SE 1-5,Oxbridge,Russell Group,Hesa Code,"

    /*What is your gender identity?,What is your sexual orientation?,What is your ethnic group?," +
    "Between the ages of 11 to 16 in which school did you spend most of your education?," +
    "Between the ages of 16 to 18 in which school did you spend most of your education?," +
    "What was your home postcode when you were 14?,During your school years were you at any time eligible for free school meals?," +
    "Did any of your parent(s) or guardian(s) complete a university degree course or equivalent?,Parent/guardian work status," +
    "Which type of occupation did they have?,Did they work as an employee or were they self-employed?," +
    "Which size would best describe their place of work?,Did they supervise any other employees?" */

  val mediaDetailsHeader = "How did you hear about us?"

  def findApplicationDetails(): Future[CsvExtract[CandidateDetailsReportItem]]

  def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem]

  def findContactDetails(): Future[CsvExtract[String]]

  def findQuestionnaireDetails(): Future[CsvExtract[String]]
}

class PreviousYearCandidatesDetailsMongoRepository(implicit mongo: () => DB) extends PreviousYearCandidatesDetailsRepository with CommonBSONDocuments {

  val applicationDetailsCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION)

  val contactDetailsCollection = mongo().collection[JSONCollection](CollectionNames.CONTACT_DETAILS)

  val questionnaireCollection = mongo().collection[JSONCollection](CollectionNames.QUESTIONNAIRE)

  val assessmentCentersCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT)

  val assessmentScoresCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT_SCORES)

  override def findApplicationDetails(): Future[CsvExtract[CandidateDetailsReportItem]] = {
    val projection = Json.obj("_id" -> 0, "progress-status" -> 0, "progress-status-dates" -> 0)

    applicationDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val applicationId = doc.getAs[String]("applicationId").get
          val progressResponse = toProgressResponse(applicationId).read(doc)
          val civilServExperienceDetails = civilServiceExperienceDetails(doc)
          val csvContent = makeRow(
            List(doc.getAs[String]("frameworkId")) :::
              List(doc.getAs[String]("applicationStatus")) :::
              List(doc.getAs[String]("applicationRoute")) :::
              civilServiceExperienceCheckType(civilServExperienceDetails, CivilServiceExperienceType.DiversityInternship.toString) :::
              civilServiceExperienceCheckType(civilServExperienceDetails, CivilServiceExperienceType.CivilServant.toString) :::
              civilServiceExperienceCheckType(civilServExperienceDetails, CivilServiceExperienceType.CivilServantViaFastTrack.toString) :::
              personalDetails(doc) :::
              List(progressResponseReachedYesNo(progressResponse.schemePreferences)) :::
              List(progressResponseReachedYesNo(progressResponse.schemePreferences)) :::

              assistanceDetails(doc): _*
          )
          doc.getAs[String]("applicationId").getOrElse("") -> CandidateDetailsReportItem(
            doc.getAs[String]("applicationId").getOrElse(""),
            doc.getAs[String]("userId").getOrElse(""), csvContent
          )
        }
        CsvExtract(applicationDetailsHeader, csvRecords.toMap)
      }
  }

  private def civilServiceExperienceCheckType(civilServExperienceDetails: Map[String, Option[String]], typeToMatch: String) =
    List(civilServExperienceDetails("civilServiceExperienceType").map { savedType => if (savedType == typeToMatch) "Yes" else "No" })

  private def progressResponseReachedYesNo(progressResponseReached: Boolean) =
    if (progressResponseReached) { Some("Yes") } else { Some("No") }

  override def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem] = {
    val projection = Json.obj("_id" -> 0, "progress-status" -> 0, "progress-status-dates" -> 0)

    applicationDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .enumerate().map { doc =>
        val csvContent = makeRow(
          List(doc.getAs[String]("frameworkId")) :::
            List(doc.getAs[String]("applicationStatus")) :::
            personalDetails(doc) :::
            assistanceDetails(doc): _*
        )
        CandidateDetailsReportItem(
          doc.getAs[String]("applicationId").getOrElse(""),
          doc.getAs[String]("userId").getOrElse(""), csvContent
        )
      }
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
            contactDetails.flatMap(_.getAs[String]("email")),
            address.flatMap(_.getAs[String]("line1")),
            address.flatMap(_.getAs[String]("line2")),
            address.flatMap(_.getAs[String]("line3")),
            address.flatMap(_.getAs[String]("line4")),
            contactDetails.flatMap(_.getAs[String]("postCode")),
            contactDetails.flatMap(_.getAs[String]("phone"))
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
        case _ => questionDoc.flatMap(q => q.getAs[String]("answer")
          .orElse(q.getAs[String]("otherDetails")))
      }
    }

    questionnaireCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val questions = doc.getAs[BSONDocument]("questions")
          val csvRecord = makeRow(
            getAnswer("What is your gender identity?", questions),
            getAnswer("What is your sexual orientation?", questions),
            getAnswer("What is your ethnic group?", questions),
            getAnswer("Between the ages of 11 to 16, in which school did you spend most of your education?", questions),
            getAnswer("Between the ages of 16 to 18, in which school did you spend most of your education?", questions),
            getAnswer("What was your home postcode when you were 14?", questions),
            getAnswer("During your school years, were you at any time eligible for free school meals?", questions),
            getAnswer("Did any of your parent(s) or guardian(s) complete a university degree course or equivalent?", questions),
            getAnswer("Parent/guardian work status", questions),
            getAnswer("Which type of occupation did they have?", questions),
            getAnswer("Did they work as an employee or were they self-employed?", questions),
            getAnswer("Which size would best describe their place of work?", questions),
            getAnswer("Did they supervise any other employees?", questions)
          )
          doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(questionnaireDetailsHeader, csvRecords.toMap)
      }
  }

  /*
  override def findOnlineTestReports(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    def onlineTestScore(test: String, doc: BSONDocument) = {
      val scoreDoc = doc.getAs[BSONDocument](test)
      scoreDoc.flatMap(_.getAs[String]("status")) ::
        scoreDoc.flatMap(_.getAs[String]("norm")) ::
        scoreDoc.flatMap(_.getAs[Double]("tScore").map(_.toString)) ::
        scoreDoc.flatMap(_.getAs[Double]("percentile").map(_.toString)) ::
        scoreDoc.flatMap(_.getAs[Double]("raw").map(_.toString)) ::
        scoreDoc.flatMap(_.getAs[Double]("sten").map(_.toString)) ::
        Nil
    }

    onlineTestReportsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map {
          doc =>
            val csvRecord = makeRow(
              onlineTestScore("competency", doc) :::
                onlineTestScore("numerical", doc) :::
                onlineTestScore("verbal", doc) :::
                onlineTestScore("situational", doc): _*
            )
            doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(onlineTestReportHeader, csvRecords.toMap)
      }
  }*/

  private def assessmentScores(doc: BSONDocument) = {
    val leadingAndCommunicating = doc.getAs[BSONDocument]("leadingAndCommunicating")
    val deliveringAtPace = doc.getAs[BSONDocument]("deliveringAtPace")
    val makingEffectiveDecisions = doc.getAs[BSONDocument]("makingEffectiveDecisions")
    val changingAndImproving = doc.getAs[BSONDocument]("changingAndImproving")
    val buildingCapabilityForAll = doc.getAs[BSONDocument]("buildingCapabilityForAll")
    val motivationFit = doc.getAs[BSONDocument]("motivationFit")
    val feedback = doc.getAs[BSONDocument]("feedback")
    List(
      doc.getAs[Boolean]("attendancy").map(_.toString),
      doc.getAs[Boolean]("assessmentIncomplete").map(_.toString),
      leadingAndCommunicating.flatMap(_.getAs[Double]("interview").map(_.toString)),
      leadingAndCommunicating.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      leadingAndCommunicating.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      deliveringAtPace.flatMap(_.getAs[Double]("interview").map(_.toString)),
      deliveringAtPace.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      deliveringAtPace.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      makingEffectiveDecisions.flatMap(_.getAs[Double]("interview").map(_.toString)),
      makingEffectiveDecisions.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      makingEffectiveDecisions.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      changingAndImproving.flatMap(_.getAs[Double]("interview").map(_.toString)),
      changingAndImproving.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      changingAndImproving.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      buildingCapabilityForAll.flatMap(_.getAs[Double]("interview").map(_.toString)),
      buildingCapabilityForAll.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      buildingCapabilityForAll.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      motivationFit.flatMap(_.getAs[Double]("interview").map(_.toString)),
      motivationFit.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      motivationFit.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      feedback.flatMap(_.getAs[String]("interviewFeedback").map(_.toString)),
      feedback.flatMap(_.getAs[String]("groupExerciseFeedback").map(_.toString)),
      feedback.flatMap(_.getAs[String]("writtenExerciseFeedback").map(_.toString))
    )
  }

  private def assistanceDetails(doc: BSONDocument) = {
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details")
    List(
      assistanceDetails.flatMap(_.getAs[String]("needsAssistance")),
      assistanceDetails.flatMap(_.getAs[List[String]]("typeOfdisability").map(_.mkString(","))),
      assistanceDetails.flatMap(_.getAs[String]("detailsOfdisability")),
      assistanceDetails.flatMap(_.getAs[String]("guaranteedInterview")),
      assistanceDetails.flatMap(_.getAs[String]("needsAdjustment")),
      assistanceDetails.flatMap(_.getAs[List[String]]("typeOfAdjustments").map(_.mkString(","))),
      assistanceDetails.flatMap(_.getAs[String]("otherAdjustments")),
      assistanceDetails.flatMap(_.getAs[String]("campaignReferrer")),
      assistanceDetails.flatMap(_.getAs[String]("campaignOther")),
      assistanceDetails.flatMap(_.getAs[Boolean]("confirmedAdjustments").map(_.toString)),
      assistanceDetails.flatMap(_.getAs[Int]("numericalTimeAdjustmentPercentage").map(_.toString)),
      assistanceDetails.flatMap(_.getAs[Int]("verbalTimeAdjustmentPercentage").map(_.toString))
    )
  }

  private def personalDetails(doc: BSONDocument) = {
    val personalDetails = doc.getAs[BSONDocument]("personal-details")
    List(
      personalDetails.flatMap(_.getAs[String]("firstName")),
      personalDetails.flatMap(_.getAs[String]("lastName")),
      personalDetails.flatMap(_.getAs[String]("preferredName")),
      personalDetails.flatMap(_.getAs[String]("dateOfBirth"))
    )
  }

  private def civilServiceExperienceDetails(doc: BSONDocument) = {
    val personalDetails = doc.getAs[BSONDocument]("civil-service-experience-details")
    Map(
      "civilServiceExperienceType" -> personalDetails.flatMap(_.getAs[String]("civilServiceExperienceType"))
    )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

}
