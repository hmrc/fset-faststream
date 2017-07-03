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

import model.command. { CandidateDetailsReportItem, CsvExtract }
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONCollection
import repositories.CollectionNames
import reactivemongo.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PreviousYearCandidatesDetailsRepository {

  val applicationDetailsHeader = "FrameworkId,Application status,First name,Last name,Preferred name,Date of birth," +
    "A level,Stem level,First location region,First location,First location first framework,First location second framework," +
    "Second location region,Second location,Second location first framework,Second location second framework,second location intended," +
    "Alternative location,Alternative framework, Needs assistance,Type of disability, Details of disability, Guaranteed interview," +
    "Needs adjustment,Type of adjustments,Other adjustments,Campaign referrer,Campaign other,Confirm adjustments," +
    "Percentage of numerical time adjustment,Percentage of verbal time adjustment"

  val contactDetailsHeader = "Email,Address line1,Address line2,Address line3,Address line4,Postcode,Phone"

  val questionnaireDetailsHeader = "What is your gender identity?,What is your sexual orientation?,What is your ethnic group?," +
    "Between the ages of 11 to 16 in which school did you spend most of your education?," +
    "Between the ages of 16 to 18 in which school did you spend most of your education?," +
    "What was your home postcode when you were 14?,During your school years were you at any time eligible for free school meals?," +
    "Did any of your parent(s) or guardian(s) complete a university degree course or equivalent?,Parent/guardian work status," +
    "Which type of occupation did they have?,Did they work as an employee or were they self-employed?," +
    "Which size would best describe their place of work?,Did they supervise any other employees?"

  val onlineTestReportHeader = "Competency status,Competency norm,Competency tscore,Competency percentile,Competency raw,Competency sten," +
    "Numerical status,Numerical norm,Numerical tscore,Numerical percentile,Numerical raw,Numerical sten," +
    "Verbal status,Verbal norm,Verbal tscore,Verbal percentile,Verbal raw,Verbal sten," +
    "Situational status,Situational norm,Situational tscore,Situational percentile,Situational raw,Situational sten"

  val assessmentCenterDetailsHeader = "Assessment venue,Assessment date,Assessment session,Assessment slot,Assessment confirmed"

  val assessmentScoresHeader = "Assessment attended,Assessment incomplete,Leading and communicating interview," +
    "Leading and communicating group exercise,Leading and communicating written exercise,Delivering at pace interview," +
    "Delivering at pace group exercise,Delivering at pace written exercise,Making effective decisions interview," +
    "Making effective decisions group exercise,Making effective decisions written exercise,Changing and improving interview," +
    "Changing and improving group exercise,Changing and improving written exercise,Building capability for all interview," +
    "Building capability for all group exercise,Building capability for all written exercise,Motivation fit interview," +
    "Motivation fit group exercise,Motivation fit written exercise,Interview feedback,Group exercise feedback," +
    "Written exercise feedback"

  def findApplicationDetails(): Future[CsvExtract[CandidateDetailsReportItem]]

  def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem]

  def findContactDetails(): Future[CsvExtract[String]]

  def findAssessmentCenterDetails(): Future[CsvExtract[String]]

  def findAssessmentScores(): Future[CsvExtract[String]]

  def findQuestionnaireDetails(): Future[CsvExtract[String]]

}

class PreviousYearCandidatesDetailsMongoRepository(implicit mongo: () => DB) extends PreviousYearCandidatesDetailsRepository {

  val applicationDetailsCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION)

  val contactDetailsCollection = mongo().collection[JSONCollection](CollectionNames.CONTACT_DETAILS)

  val questionnaireCollection = mongo().collection[JSONCollection](CollectionNames.QUESTIONNAIRE)

  val assessmentCentersCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT)

  val assessmentScoresCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT_SCORES)

  override def findApplicationDetails(): Future[CsvExtract[CandidateDetailsReportItem]] = {
    val projection = Json.obj("_id" -> 0, "progress-status" -> 0, "progress-status-dates" -> 0)

    applicationDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val csvContent = makeRow(
            List(doc.getAs[String]("frameworkId")) :::
              List(doc.getAs[String]("applicationStatus")) :::
              personalDetails(doc) ::: frameworkPreferences(doc) :::
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

  override def applicationDetailsStream(): Enumerator[CandidateDetailsReportItem] = {
    val projection = Json.obj("_id" -> 0, "progress-status" -> 0, "progress-status-dates" -> 0)

    applicationDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .enumerate().map { doc =>
        val csvContent = makeRow(
          List(doc.getAs[String]("frameworkId")) :::
            List(doc.getAs[String]("applicationStatus")) :::
            personalDetails(doc) ::: frameworkPreferences(doc) :::
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
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
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
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
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

  override def findAssessmentCenterDetails(): Future[CsvExtract[String]] = {

    val projection = Json.obj("_id" -> 0)

    assessmentCentersCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map {
          doc =>
            val csvRecord = makeRow(
              doc.getAs[String]("venue"),
              doc.getAs[String]("date"),
              doc.getAs[String]("session"),
              doc.getAs[Int]("slot").map(_.toString),
              doc.getAs[Boolean]("confirmed").map(_.toString)
            )
            doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(assessmentCenterDetailsHeader, csvRecords.toMap)
      }
  }

  override def findAssessmentScores(): Future[CsvExtract[String]] = {

    val projection = Json.obj("_id" -> 0)

    assessmentScoresCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val csvRecord = makeRow(assessmentScores(doc): _*)
          doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(assessmentScoresHeader, csvRecords.toMap)
      }
  }

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

  private def frameworkPreferences(doc: BSONDocument) = {
    val frameworkPrefs = doc.getAs[BSONDocument]("framework-preferences")
    val firstLocation = frameworkPrefs.flatMap(_.getAs[BSONDocument]("firstLocation"))
    val secondLocation = frameworkPrefs.flatMap(_.getAs[BSONDocument]("secondLocation"))
    val alternatives = frameworkPrefs.flatMap(_.getAs[BSONDocument]("alternatives"))
    List(
      firstLocation.flatMap(_.getAs[String]("region")),
      firstLocation.flatMap(_.getAs[String]("location")),
      firstLocation.flatMap(_.getAs[String]("firstFramework")),
      firstLocation.flatMap(_.getAs[String]("secondFramework")),
      secondLocation.flatMap(_.getAs[String]("region")),
      secondLocation.flatMap(_.getAs[String]("location")),
      secondLocation.flatMap(_.getAs[String]("firstFramework")),
      secondLocation.flatMap(_.getAs[String]("secondFramework")),
      frameworkPrefs.flatMap(_.getAs[Boolean]("secondLocationIntended").map(_.toString)),
      alternatives.flatMap(_.getAs[Boolean]("location").map(_.toString)),
      alternatives.flatMap(_.getAs[Boolean]("framework").map(_.toString))
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
      personalDetails.flatMap(_.getAs[String]("dateOfBirth")),
      personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(_.toString)),
      personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(_.toString))
    )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

}
