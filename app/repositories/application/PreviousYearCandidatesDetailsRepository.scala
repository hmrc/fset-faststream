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

import model.{ CivilServiceExperienceType, InternshipType, SchemeType }
import model.CivilServiceExperienceType.CivilServiceExperienceType
import model.InternshipType.InternshipType
import model.command.{ CandidateDetailsReportItem, CsvExtract, ProgressResponse }
import play.api.Logger
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
  val applicationDetailsHeader = "applicationId, userId,Framework ID,Application Status,Route,First name,Last name,Preferred Name,Date of Birth,Are you eligible,Terms and Conditions," +
    "Currently a Civil Servant done SDIP or EDIP,Currently Civil Servant,Currently Civil Service via Fast Track," +
    "EDIP,SDIP 2016 (previous years),Fast Pass (sdip 2017),Fast Pass No,Scheme preferences,Scheme names,Are you happy with order,Are you eligible," +
    "Do you want to defer,Deferal selections,Do you have a disability,Provide more info,GIS,Extra support online tests," +
    "What adjustments will you need,Extra support f2f,What adjustments will you need,I understand this wont affect application," +
    "Behavioural T-score," +
  "Behavioural Percentile,Behavioural Raw,Behavioural STEN,Situational T-score,Situational Percentile,Situational Raw,Situational STEN," +
    "E-Tray time extension," +
    "E-Tray invigilated,E-Tray invigilated notes,E-Tray other notes,Video time extension,Video invigilated," +
    "Video invigilated notes,Video other notes,Additional comments,Adjustments confirmed,e-Tray T-score,e-Tray Raw,Q1 Capability,Q1 Engagement,Q2 Capability,Q2 Engagement,Q3 Capability," +
    "Q3 Engagement,Q4 Capability,Q4 Engagement,Q5 Capability,Q5 Engagement,Q6 Capability,Q6 Engagement,Q7 Capability," +
    "Q7 Engagement,Q8 Capability,Q8 Engagement,Overall total," +
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
    "Supervise employees,SE 1-5,Oxbridge,Russell Group,Hesa Code"

    /*What is your gender identity?,What is your sexual orientation?,What is your ethnic group?," +
    "Between the ages of 11 to 16 in which school did you spend most of your education?," +
    "Between the ages of 16 to 18 in which school did you spend most of your education?," +
    "What was your home postcode when you were 14?,During your school years were you at any time eligible for free school meals?," +
    "Did any of your parent(s) or guardian(s) complete a university degree course or equivalent?,Parent/guardian work status," +
    "Which type of occupation did they have?,Did they work as an employee or were they self-employed?," +
    "Which size would best describe their place of work?,Did they supervise any other employees?" */

  val mediaDetailsHeader = "How did you hear about us?"

  def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem]

  def findContactDetails(): Future[CsvExtract[String]]

  def findQuestionnaireDetails(): Future[CsvExtract[String]]
}

class PreviousYearCandidatesDetailsMongoRepository(implicit mongo: () => DB) extends PreviousYearCandidatesDetailsRepository with CommonBSONDocuments {
  import config.MicroserviceAppConfig._

  val applicationDetailsCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION)

  val contactDetailsCollection = mongo().collection[JSONCollection](CollectionNames.CONTACT_DETAILS)

  val questionnaireCollection = mongo().collection[JSONCollection](CollectionNames.QUESTIONNAIRE)

  private def optYes = Some("Yes")
  private def optNo = Some("No")

  override def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem] = {
    val projection = Json.obj("_id" -> 0, "progress-status-dates" -> 0)

    applicationDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.nearest)
      .enumerate().map { doc =>

      val applicationId = doc.getAs[String]("applicationId").get
      val progressResponse = toProgressResponse(applicationId).read(doc)
      val (civilServiceExperienceType, civilServiceInternshipTypes, fastPassCertificateNo) = civilServiceExperience(doc)

      val schemePrefs: List[String] = doc.getAs[BSONDocument]("scheme-preferences").flatMap(_.getAs[List[String]]("schemes")).getOrElse(Nil)
      val schemePrefsAsString: Option[String] = Some(schemePrefs.mkString(","))
      val allSchemes: List[String] = SchemeType.values.map(_.toString).toList
      val schemesYesNoAsString: Option[String] = Option((schemePrefs.map(_ + ": Yes") ::: allSchemes.filterNot(schemePrefs.contains).map(_ + ": No")).mkString(","))

      val onlineTestResults = onlineTests(doc)

        val csvContent = makeRow(
          List(doc.getAs[String]("applicationId")) :::
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
            onlineTestResults("sjq")
            // eTray(doc) :::
            // videoInterview(doc) :::
            : _*
        )
        CandidateDetailsReportItem(
          doc.getAs[String]("applicationId").getOrElse(""),
          doc.getAs[String]("userId").getOrElse(""), csvContent
        )
      }
  }

  private def civilServiceExperienceCheckExpType(civilServExperienceType: Option[String], typeToMatch: String) =
    List(if (civilServExperienceType.contains(typeToMatch)) optYes else optNo)

  private def civilServiceExperienceCheckInternshipType(civilServExperienceInternshipTypes: Option[List[String]], typeToMatch: String) =
    List(if (civilServExperienceInternshipTypes.exists(_.contains(typeToMatch))) { optYes } else { optNo })

  private def progressResponseReachedYesNo(progressResponseReached: Boolean) =
    if (progressResponseReached) { optYes } else { optNo }

  private def partnerGraduateProgrammes(doc: BSONDocument) = {
    val subDoc = doc.getAs[BSONDocument]("partner-graduate-programmes")
    val interested = subDoc.flatMap(_.getAs[Boolean]("interested")).getOrElse(false)

    List(
      if (interested) optYes else optNo,
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

  private def onlineTests(doc: BSONDocument) = {
    val testGroups = doc.getAs[BSONDocument]("testGroups")
    val onlineTestSection = testGroups.flatMap(_.getAs[BSONDocument]("PHASE1"))
    val onlineTests = onlineTestSection.flatMap(_.getAs[List[BSONDocument]]("tests"))

    val bqTest = onlineTests.flatMap(_.find(_.getAs[Int]("scheduleId").get == cubiksGatewayConfig.phase1Tests.scheduleIds("bq")))
    val bqTestResults = bqTest.flatMap { _.getAs[BSONDocument]("testResult") }

    val sjqTest = onlineTests.flatMap(_.find(_.getAs[Int]("scheduleId").get == cubiksGatewayConfig.phase1Tests.scheduleIds("sjq")))
    val sjqTestResults = sjqTest.flatMap { _.getAs[BSONDocument]("testResult") }

    Map(
      "bq" ->
        List(
          bqTestResults.flatMap(_.getAs[Double]("tScore").map(_.toString)),
          bqTestResults.flatMap(_.getAs[Double]("percentile").map(_.toString)),
          bqTestResults.flatMap(_.getAs[Double]("raw").map(_.toString)),
          bqTestResults.flatMap(_.getAs[Double]("sten").map(_.toString))
        ),
      "sjq" ->
        List(
          sjqTestResults.flatMap(_.getAs[Double]("tScore").map(_.toString)),
          sjqTestResults.flatMap(_.getAs[Double]("percentile").map(_.toString)),
          sjqTestResults.flatMap(_.getAs[Double]("raw").map(_.toString)),
          sjqTestResults.flatMap(_.getAs[Double]("sten").map(_.toString))
        )
    )
  }

  private def assistanceDetails(doc: BSONDocument) = {
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details")
    val etrayAdjustments = assistanceDetails.flatMap(_.getAs[BSONDocument]("etray"))
    val videoAdjustments = assistanceDetails.flatMap(_.getAs[BSONDocument]("video"))
    val typeOfAdjustments = assistanceDetails.flatMap(_.getAs[List[String]]("typeOfAdjustments")).getOrElse(Nil)

    List(
      assistanceDetails.flatMap(_.getAs[String]("hasDisability")),
      assistanceDetails.flatMap(_.getAs[String]("hasDisabilityDescription")),
      assistanceDetails.map(ad => if (ad.getAs[Boolean]("guaranteedInterview").getOrElse(false)) "Yes" else "No"),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportForOnlineAssessment")),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportForOnlineAssessmentDescription")),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportAtVenue")),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportAtVenueDescription")),
      etrayAdjustments.flatMap(_.getAs[String]("timeNeeded")) + "%",
      if (typeOfAdjustments.contains("etrayInvigilated")) "Yes" else "No",
      etrayAdjustments.flatMap(_.getAs[String]("invigilatedInfo")),
      etrayAdjustments.flatMap(_.getAs[String]("otherInfo")),
      videoAdjustments.flatMap(_.getAs[String]("timeNeeded")) + "%",
      // TODO: Additional Comments
      if (assistanceDetails.flatMap(_.getAs[Boolean]("adjustmentsConfirmed")).getOrElse(false)) "Yes" else "No",
      typeOfAdjustments.contains("videoInvigilated")
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

  private def civilServiceExperience(doc: BSONDocument): (Option[String], Option[List[String]], Option[String]) = {
    val csExperienceDetails = doc.getAs[BSONDocument]("civil-service-experience-details")
    (
      csExperienceDetails.flatMap(_.getAs[String]("civilServiceExperienceType")),
      csExperienceDetails.flatMap(_.getAs[List[String]]("internshipTypes")),
      csExperienceDetails.flatMap(_.getAs[String]("certificateNumber"))
    )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

}
