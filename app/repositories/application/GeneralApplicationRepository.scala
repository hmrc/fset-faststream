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

import model.ApplicationStatusOrder._
import model.AssessmentScheduleCommands.{ ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult }
import model.Commands._
import model.EvaluationResults._
import model.Exceptions.{ ApplicationNotFound, CannotUpdatePreview }
import model.PersistedObjects.ApplicationForNotification
import model._
import model.command.{ AssessmentCentre, AssessmentScores, OnlineTestProgressResponse, ProgressResponse }
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Format, JsNumber, JsObject }
import reactivemongo.api.{ DB, QueryOpts, ReadPreference }
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand
import repositories._
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait GeneralApplicationRepository {

  def create(userId: String, frameworkId: String): Future[ApplicationResponse]

  def find(applicationIds: List[String]): Future[List[Candidate]]

  def findProgress(applicationId: String): Future[ProgressResponse]

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse]

  def findCandidateByUserId(userId: String): Future[Option[Candidate]]

  def findByCriteria(lastName: Option[String], dateOfBirth: Option[LocalDate]): Future[List[Candidate]]

  def findApplicationIdsByLocation(location: String): Future[List[String]]

  def findApplicationsForAssessmentAllocation(locations: List[String], start: Int, end: Int): Future[ApplicationForAssessmentAllocationResult]

  def submit(applicationId: String): Future[Unit]

  def withdraw(applicationId: String, reason: WithdrawApplicationRequest): Future[Unit]

  def preview(applicationId: String): Future[Unit]

  def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit]

  def overallReport(frameworkId: String): Future[List[Report]]

  def overallReportNotWithdrawn(frameworkId: String): Future[List[Report]]

  def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]]

  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReport]]

  def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]]

  def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]]

  def confirmAdjustment(applicationId: String, data: AdjustmentManagement): Future[Unit]

  def rejectAdjustment(applicationId: String): Future[Unit]

  def gisByApplication(applicationId: String): Future[Boolean]

  def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]]

  def updateStatus(applicationId: String, status: String): Future[Unit]

  def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]]

  def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]]

  def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]]

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]]

  def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]]

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String,
                                    evaluationResult: AssessmentRuleCategoryResult, newApplicationStatus: String): Future[Unit]

}

// scalastyle:off number.of.methods
// scalastyle:off file.size.limit
class GeneralApplicationMongoRepository(timeZoneService: TimeZoneService)(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID]("application", mongo,
    Commands.Implicits.createApplicationRequestFormats,
    ReactiveMongoFormats.objectIdFormats) with GeneralApplicationRepository with RandomSelection {

  override def create(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val applicationId = UUID.randomUUID().toString
    val createdStatus = "CREATED"

    val applicationBSON = BSONDocument(
      "applicationId" -> applicationId,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> createdStatus
    )

    collection.insert(applicationBSON) flatMap { _ =>
      findProgress(applicationId).map { p =>
        ApplicationResponse(applicationId, createdStatus, userId, p, None)
      }
    }
  }

  def docToCandidate(doc: BSONDocument): Candidate = {
    val userId = doc.getAs[String]("userId").getOrElse("")
    val applicationId = doc.getAs[String]("applicationId")

    val psRoot = doc.getAs[BSONDocument]("personal-details")
    val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
    val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
    val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

    Candidate(userId, applicationId, None, firstName, lastName, dateOfBirth, None, None)
  }

  def find(applicationIds: List[String]): Future[List[Candidate]] = {

    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    collection.find(query).cursor[BSONDocument]().collect[List]().map(_.map(docToCandidate))
  }

  private def findProgress(document: BSONDocument, applicationId: String): ProgressResponse = {

    (document.getAs[BSONDocument]("progress-status") map { root =>

      def getProgress(key: String) = {
        root.getAs[Boolean](key).getOrElse(false)
      }

      def questionnaire = root.getAs[BSONDocument]("questionnaire").map { doc =>
        doc.elements.collect {
          case (name, BSONBoolean(true)) => name
        }.toList
      }.getOrElse(Nil)

      ProgressResponse(
        applicationId,
        personalDetails = getProgress("personal-details"),
        partnerGraduateProgrammes = getProgress("partner-graduate-programmes"),
        schemePreferences = getProgress("scheme-preferences"),
        assistanceDetails = getProgress("assistance-details"),
        preview = getProgress("preview"),
        questionnaire = questionnaire,
        submitted = getProgress("submitted"),
        withdrawn = getProgress("withdrawn"),
        onlineTest = OnlineTestProgressResponse(onlineTestInvited = getProgress("online_test_invited"),
          onlineTestStarted = getProgress("online_test_started"),
          onlineTestCompleted = getProgress("online_test_completed"),
          onlineTestExpired = getProgress("online_test_expired"),
          onlineTestAwaitingReevaluation = getProgress("awaiting_online_test_re_evaluation"),
          onlineTestFailed = getProgress("online_test_failed"),
          onlineTestFailedNotified = getProgress("online_test_failed_notified"),
          onlineTestAwaitingAllocation = getProgress("awaiting_online_test_allocation"),
          onlineTestAllocationConfirmed = getProgress("allocation_confirmed"),
          onlineTestAllocationUnconfirmed = getProgress("allocation_unconfirmed")
          ),
        failedToAttend = getProgress("failed_to_attend"),
        assessmentScores = AssessmentScores(getProgress("assessment_scores_entered"), getProgress("assessment_scores_accepted")),
        assessmentCentre = AssessmentCentre(
          getProgress("awaiting_assessment_centre_re_evaluation"),
          getProgress("assessment_centre_passed"),
          getProgress("assessment_centre_failed"),
          getProgress("assessment_centre_passed_notified"),
          getProgress("assessment_centre_failed_notified")
        )
      )
    }).getOrElse(ProgressResponse(applicationId))
  }

  override def findProgress(applicationId: String): Future[ProgressResponse] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("progress-status" -> 2, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) => findProgress(document, applicationId)
      case None => ProgressResponse(applicationId)
    }
  }

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val query = BSONDocument("userId" -> userId, "frameworkId" -> frameworkId)

    val resp: Future[Future[ApplicationResponse]] = collection.find(query).one[BSONDocument] map {
      case Some(document) =>
        val applicationId = document.getAs[String]("applicationId").get
        val applicationStatus = document.getAs[String]("applicationStatus").get
        val fastPassReceived = document.getAs[FastPassDetails]("fastpass-details").flatMap(_.fastPassReceived)
        findProgress(applicationId).map { (p: ProgressResponse) =>
          ApplicationResponse(applicationId, applicationStatus, userId, p, fastPassReceived)
        }
      case None => throw new ApplicationNotFound(userId)
    }
    resp.flatMap(identity)
  }

  def findCandidateByUserId(userId: String): Future[Option[Candidate]] = {

    val query = BSONDocument("userId" -> userId)

    collection.find(query).one[BSONDocument].map(_.map(docToCandidate))
  }

  def findByCriteria(lastName: Option[String], dateOfBirth: Option[LocalDate]): Future[List[Candidate]] = {

    val query = BSONDocument("personal-details.lastName" -> lastName, "personal-details.dateOfBirth" -> dateOfBirth)

    collection.find(query).cursor[BSONDocument]().collect[List]().map(_.map(docToCandidate))
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
          "assistance-details.needsAdjustment" -> 1,
          "online-tests.invitationDate" -> 1
        )
        val sort = new JsObject(Seq("online-tests.invitationDate" -> JsNumber(1)))

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
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> "SUBMITTED",
      "progress-status.submitted" -> true
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case _ => ()
    }
  }

  override def withdraw(applicationId: String, reason: WithdrawApplicationRequest): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      "withdraw" -> reason,
      "applicationStatus" -> "WITHDRAWN",
      "progress-status.withdrawn" -> true
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case _ => ()
    }
  }

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.questionnaire.$sectionKey" -> true
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case _ => ()
    }
  }

  override def preview(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.preview" -> true
    ))

    collection.update(query, applicationStatusBSON, upsert = true) map {
      case result if result.nModified == 0 && result.n == 0 =>
        logger.error(
          s"""Failed to write assistance details for application: $applicationId ->
              |${result.writeConcernError.map(_.errmsg).mkString(",")}""".stripMargin)
        throw CannotUpdatePreview(applicationId)
      case _ => ()
    }

  }

  override def overallReportNotWithdrawn(frameworkId: String): Future[List[Report]] =
    overallReport(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN"))
    )))

  override def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] =
    overallReportWithPersonalDetails(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN"))
    )))

  override def overallReport(frameworkId: String): Future[List[Report]] =
    overallReport(BSONDocument("frameworkId" -> frameworkId))

  private def overallReport(query: BSONDocument): Future[List[Report]] = {
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
      "personal-details.stemLevel" -> "1",
      "assistance-details.needsAssistance" -> "1",
      "assistance-details.needsAdjustment" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "issue" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2"
    )

    reportQueryWithProjections[BSONDocument](query, projection) map { lst =>
      lst.map(docToReport)
    }
  }

  override def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]] =
    applicationPreferences(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.${ApplicationStatuses.AssessmentScoresAccepted.toLowerCase}" -> true),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Withdrawn))
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
      BSONDocument(s"progress-status.${ApplicationStatuses.AssessmentCentrePassed.toLowerCase}" -> true),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Withdrawn))
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

    reportQueryWithProjections[BSONDocument](query, projection) map { lst =>
      lst.map(docToReportWithPersonalDetails)
    }
  }

  private def docToReport(document: BSONDocument) = {
    val fr = document.getAs[BSONDocument]("framework-preferences")
    val fr1 = fr.flatMap(_.getAs[BSONDocument]("firstLocation"))
    val fr2 = fr.flatMap(_.getAs[BSONDocument]("secondLocation"))

    def frLocation(root: Option[BSONDocument]) = extract("location")(root)
    def frScheme1(root: Option[BSONDocument]) = extract("firstFramework")(root)
    def frScheme2(root: Option[BSONDocument]) = extract("secondFramework")(root)

    val personalDetails = document.getAs[BSONDocument]("personal-details")
    val aLevel = personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
    val stemLevel = personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))

    val fpAlternatives = fr.flatMap(_.getAs[BSONDocument]("alternatives"))
    val location = fpAlternatives.flatMap(_.getAs[Boolean]("location").map(booleanTranslator))
    val framework = fpAlternatives.flatMap(_.getAs[Boolean]("framework").map(booleanTranslator))

    val ad = document.getAs[BSONDocument]("assistance-details")
    val needsAssistance = ad.flatMap(_.getAs[String]("needsAssistance"))
    val needsAdjustment = ad.flatMap(_.getAs[String]("needsAdjustment"))
    val guaranteedInterview = ad.flatMap(_.getAs[String]("guaranteedInterview"))

    val applicationId = document.getAs[String]("applicationId").getOrElse("")
    val progress: ProgressResponse = findProgress(document, applicationId)

    val issue = document.getAs[String]("issue")

    Report(
      applicationId, Some(getStatus(progress)), frLocation(fr1), frScheme1(fr1), frScheme2(fr1),
      frLocation(fr2), frScheme1(fr2), frScheme2(fr2), aLevel,
      stemLevel, location, framework, needsAssistance, needsAdjustment, guaranteedInterview, issue
    )
  }

  private def docToReportWithPersonalDetails(document: BSONDocument) = {
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
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "IN_PROGRESS")),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN")),
        BSONDocument("$or" ->
          BSONArray(
            BSONDocument("assistance-details.needsAdjustment" -> "Yes"),
            BSONDocument("assistance-details.guaranteedInterview" -> "Yes")
          ))
      ))

    val projection = BSONDocument(
      "userId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "assistance-details.typeOfAdjustments" -> "1",
      "assistance-details.otherAdjustments" -> "1",
      "assistance-details.adjustments-confirmed" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val personalDetails = document.getAs[BSONDocument]("personal-details")
        val userId = document.getAs[String]("userId").getOrElse("")
        val firstName = extract("firstName")(personalDetails)
        val lastName = extract("lastName")(personalDetails)
        val preferredName = extract("preferredName")(personalDetails)

        val assistance = document.getAs[BSONDocument]("assistance-details")
        val gis = extract("guaranteedInterview")(assistance)
        val typesOfAdjustments = assistance.flatMap(_.getAs[List[String]]("typeOfAdjustments"))
        val otherAdjustments = extract("otherAdjustments")(assistance)
        val adjustmentsConfirmed = getAdjustmentsConfirmed(assistance)
        val adjustments = typesOfAdjustments.getOrElse(Nil) ::: otherAdjustments.toList
        val finalTOA = if (adjustments.isEmpty) None else Some(adjustments.mkString("|"))

        AdjustmentReport(userId, firstName, lastName, preferredName, None, None, finalTOA, gis, adjustmentsConfirmed)
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

  private def getAdjustmentsConfirmed(assistance: Option[BSONDocument]): Option[String] = {
    assistance.flatMap(_.getAs[Boolean]("adjustments-confirmed")).getOrElse(false) match {
      case false => Some("Unconfirmed")
      case true => Some("Confirmed")
    }
  }

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

    collection.update(query, adjustmentsConfirmationBSON, upsert = false) map {
      case _ => ()
    }
  }

  def rejectAdjustment(applicationId: String): Future[Unit] = {

    val query = BSONDocument("applicationId" -> applicationId)

    val adjustmentRejection = BSONDocument("$set" -> BSONDocument(
      "assistance-details.typeOfAdjustments" -> List.empty[String],
      "assistance-details.needsAdjustment" -> "No"
    ))

    collection.update(query, adjustmentRejection, upsert = false) map {
      case _ => ()
    }
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

  def updateStatus(applicationId: String, status: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val lowercaseStatus = status.toLowerCase
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> status,
      s"progress-status.$lowercaseStatus" -> true,
      s"progress-status-dates.$lowercaseStatus" -> LocalDate.now()
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case _ => ()
    }
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
              BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted),
              BSONDocument("assessment-centre-passmark-evaluation.passmarkVersion" -> BSONDocument("$exists" -> false))
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingAssessmentCentreReevaluation),
              BSONDocument("assessment-centre-passmark-evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion))
            )
          )
        ))

    selectRandom(query).map(_.map(doc => doc.getAs[String]("applicationId").get))
  }

  def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]] = {
    val query = BSONDocument(
      "$and" -> BSONArray(
        BSONDocument("online-tests.pdfReportSaved" -> true),
        BSONDocument(
          "$or" -> BSONArray(
            BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentCentrePassed),
            BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentCentreFailed)
          )
        )
      )
    )
    selectRandom(query).map(_.map(bsonDocToApplicationForNotification))
  }

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String,
                                    evaluationResult: AssessmentRuleCategoryResult, newApplicationStatus: String): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        "$or" -> BSONArray(
          BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted),
          BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingAssessmentCentreReevaluation)
        )
      )
    ))
    val progressStatus = newApplicationStatus.toLowerCase

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "applicationStatus" -> newApplicationStatus,
        s"progress-status.$progressStatus" -> true,
        s"progress-status-dates.$progressStatus" -> LocalDate.now(),
        "assessment-centre-passmark-evaluation" -> BSONDocument("passmarkVersion" -> passmarkVersion)
          .add(booleanToBSON("passedMinimumCompetencyLevel", evaluationResult.passedMinimumCompetencyLevel))
          .add(resultToBSON("location1Scheme1", evaluationResult.location1Scheme1))
          .add(resultToBSON("location1Scheme2", evaluationResult.location1Scheme2))
          .add(resultToBSON("location2Scheme1", evaluationResult.location2Scheme1))
          .add(resultToBSON("location2Scheme2", evaluationResult.location2Scheme2))
          .add(resultToBSON("alternativeScheme", evaluationResult.alternativeScheme))
          .add(averageToBSON("competency-average", evaluationResult.competencyAverageResult))
          .add(perSchemeToBSON("schemes-evaluation", evaluationResult.schemesEvaluation))
      ))

    collection.update(query, passMarkEvaluation, upsert = false) map {
      case _ => ()
    }
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
    val needsAdjustment = assistanceDetails.getAs[String]("needsAdjustment").get
    val onlineTestDetails = doc.getAs[BSONDocument]("online-tests").get
    val invitationDate = onlineTestDetails.getAs[DateTime]("invitationDate").get
    ApplicationForAssessmentAllocation(firstName, lastName, userId, applicationId, needsAdjustment, invitationDate)
  }

  private def bsonDocToApplicationForNotification(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }
}
