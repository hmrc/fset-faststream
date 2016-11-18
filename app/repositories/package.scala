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

import factories.DateTimeFactory
import model.CandidateScoresCommands.{ CandidateScoreFeedback, CandidateScores, CandidateScoresAndFeedback }
import model.Commands._
import model.EvaluationResults._
import model.FlagCandidatePersistedObject.FlagCandidate
import model.OnlineTestCommands.OnlineTestApplication
import model.PassmarkPersistedObjects._
import model.PersistedObjects.{ ContactDetails, PersistedAnswer, PersonalDetails }
import model.command.WithdrawApplication
import model.persisted.AssistanceDetails
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import play.modules.reactivemongo.ReactiveMongoPlugin
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson._
import repositories.application._
import repositories.onlinetesting._
import services.GBTimeZoneService
import services.reporting.SocioEconomicScoreCalculator
import config.MicroserviceAppConfig._
import model.{ AdjustmentDetail, ApplicationRoute }
import model.ApplicationRoute.ApplicationRoute
import play.api.libs.json._
import repositories.EventMongoRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsMongoRepository
import repositories.passmarksettings.{ Phase1PassMarkSettingsMongoRepository, Phase2PassMarkSettingsMongoRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

package object repositories {
  private val timeZoneService = GBTimeZoneService
  private implicit val connection = {
    import play.api.Play.current
    ReactiveMongoPlugin.mongoConnector.db
  }

  lazy val faststreamPersonalDetailsRepository = new personaldetails.PersonalDetailsMongoRepository()
  lazy val faststreamContactDetailsRepository = new contactdetails.ContactDetailsMongoRepository()
  lazy val schemePreferencesRepository = new schemepreferences.SchemePreferencesMongoRepository
  lazy val civilServiceExperienceDetailsRepository = new CivilServiceExperienceDetailsMongoRepository()
  lazy val faststreamPartnerGraduateProgrammesRepository = new partnergraduateprogrammes.PartnerGraduateProgrammesMongoRepository()
  lazy val faststreamAssistanceDetailsRepository = new assistancedetails.AssistanceDetailsMongoRepository()
  lazy val faststreamPhase1EvaluationRepository = new onlinetesting.Phase1EvaluationMongoRepository()
  lazy val faststreamPhase2EvaluationRepository = new onlinetesting.Phase2EvaluationMongoRepository()
  lazy val schoolsRepository = SchoolsCSVRepository
  lazy val questionnaireRepository = new QuestionnaireMongoRepository(new SocioEconomicScoreCalculator {})
  lazy val mediaRepository = new MediaMongoRepository()
  lazy val applicationRepository = new GeneralApplicationMongoRepository(timeZoneService,
    cubiksGatewayConfig,
    GeneralApplicationRepoBSONToModelHelper)
  lazy val reportingRepository = new ReportingMongoRepository(timeZoneService, ReportingRepoBSONToModelHelper)

  // Below repositories will be deleted as they are valid only for Fasttrack
  lazy val personalDetailsRepository = new PersonalDetailsMongoRepository()
  lazy val contactDetailsRepository = new ContactDetailsMongoRepository()
  lazy val frameworkRepository = new FrameworkYamlRepository()
  lazy val frameworkPreferenceRepository = new FrameworkPreferenceMongoRepository()
  lazy val phase1TestRepository = new Phase1TestMongoRepository(DateTimeFactory)
  lazy val phase2TestRepository = new Phase2TestMongoRepository(DateTimeFactory)
  lazy val phase3TestRepository = new Phase3TestMongoRepository(DateTimeFactory)
  lazy val testReportRepository = new TestReportMongoRepository()
  lazy val phase1PassMarkSettingsRepository = new Phase1PassMarkSettingsMongoRepository()
  lazy val phase2PassMarkSettingsRepository = new Phase2PassMarkSettingsMongoRepository()
  lazy val assessmentCentrePassMarkSettingsRepository = new AssessmentCentrePassMarkSettingsMongoRepository()
  lazy val applicationAssessmentRepository = new ApplicationAssessmentMongoRepository()
  lazy val candidateAllocationMongoRepository = new CandidateAllocationMongoRepository(DateTimeFactory)
  lazy val diagnosticReportRepository = new DiagnosticReportingMongoRepository
  lazy val applicationAssessmentScoresRepository = new ApplicationAssessmentScoresMongoRepository(DateTimeFactory)
  lazy val flagCandidateRepository = new FlagCandidateMongoRepository
  lazy val eventMongoRepository = new EventMongoRepository

  /** Create indexes */
  Await.result(Future.sequence(List(
    applicationRepository.collection.indexesManager.create(Index(Seq(("applicationId", Ascending), ("userId", Ascending)), unique = true)),
    applicationRepository.collection.indexesManager.create(Index(Seq(("userId", Ascending), ("frameworkId", Ascending)), unique = true)),
    applicationRepository.collection.indexesManager.create(Index(Seq(("applicationStatus", Ascending)), unique = false)),
    applicationRepository.collection.indexesManager.create(Index(
      Seq(("assistance-details.needsSupportForOnlineAssessment", Ascending)), unique = false)),
    applicationRepository.collection.indexesManager.create(Index(Seq(("assistance-details.needsSupportAtVenue", Ascending)), unique = false)),
    applicationRepository.collection.indexesManager.create(Index(Seq(("assistance-details.guaranteedInterview", Ascending)), unique = false)),

    contactDetailsRepository.collection.indexesManager.create(Index(Seq(("userId", Ascending)), unique = true)),
    faststreamContactDetailsRepository.collection.indexesManager.create(Index(Seq(("userId", Ascending)), unique = true)),

    phase1PassMarkSettingsRepository.collection.indexesManager.create(Index(Seq(("createDate", Ascending)), unique = true)),

    phase2PassMarkSettingsRepository.collection.indexesManager.create(Index(Seq(("createDate", Ascending)), unique = true)),

    assessmentCentrePassMarkSettingsRepository.collection.indexesManager.create(Index(Seq(("info.createDate", Ascending)), unique = true)),

    applicationAssessmentRepository.collection.indexesManager.create(Index(Seq(("venue", Ascending), ("date", Ascending),
      ("session", Ascending), ("slot", Ascending)), unique = true)),
    applicationAssessmentRepository.collection.indexesManager.create(Index(Seq(("applicationId", Ascending)), unique = true)),

    applicationAssessmentScoresRepository.collection.indexesManager.create(Index(Seq(("applicationId", Ascending)), unique = true))

  )), 20 seconds)

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(time: BSONDateTime) = new DateTime(time.value, DateTimeZone.UTC)

    def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
  }

  /** Implicit transformation for the DateTime **/
  implicit object BSONLocalDateHandler extends BSONHandler[BSONString, LocalDate] {
    def read(time: BSONString) = LocalDate.parse(time.value)

    def write(jdtime: LocalDate) = BSONString(jdtime.toString("yyyy-MM-dd"))
  }

  /** Implicit transformation for the PersistedPersonalDetails **/
  @deprecated("fasttrack version", "ages ago")
  implicit object BSONPersistedPersonalDetailsHandler extends BSONHandler[BSONDocument, PersonalDetails] {
    def read(doc: BSONDocument): PersonalDetails = {
      val root = doc.getAs[BSONDocument]("personal-details").get
      val firstName = root.getAs[String]("firstName").get
      val lastName = root.getAs[String]("lastName").get
      val preferredName = root.getAs[String]("preferredName").get
      val dateOfBirth = doc.getAs[LocalDate]("dateOfBirth").get

      PersonalDetails(firstName, lastName, preferredName, dateOfBirth, aLevel = false, stemLevel = false)
    }

    def write(psDoc: PersonalDetails) = BSONDocument(
      "firstName" -> psDoc.firstName,
      "lastName" -> psDoc.lastName,
      "preferredName" -> psDoc.preferredName,
      "dateOfBirth" -> psDoc.dateOfBirth
    )
  }

  def toCandidate(doc: BSONDocument): Candidate = {
    val userId = doc.getAs[String]("userId").getOrElse("")
    val applicationId = doc.getAs[String]("applicationId")
    // If the application does not have applicationRoute, it is legacy data
    // as it needs to be interpreted as Faststream
    val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)

    val psRoot = doc.getAs[BSONDocument]("personal-details")
    val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
    val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
    val preferredName = psRoot.flatMap(_.getAs[String]("preferredName"))
    val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

    Candidate(userId, applicationId, None, firstName, lastName, preferredName, dateOfBirth, None, None, None, Some(applicationRoute))
  }

  implicit object BSONMapHandler extends BSONHandler[BSONDocument, Map[String, Int]] {
    override def write(map: Map[String, Int]): BSONDocument = {
      val elements = map.toStream.map { tuple =>
        tuple._1 -> BSONInteger(tuple._2)
      }
      BSONDocument(elements)
    }

    override def read(bson: BSONDocument): Map[String, Int] = {
      val elements = bson.elements.map { tuple =>
        // assume that all values in the document are BSONDocuments
        tuple._1 -> tuple._2.seeAsTry[Int].get
      }
      elements.toMap
    }
  }

implicit object OFormatHelper {
  def oFormat[T](implicit format:Format[T]) : OFormat[T] = {
    val oFormat: OFormat[T] = new OFormat[T](){
      override def writes(o: T): JsObject = format.writes(o).as[JsObject]
      override def reads(json: JsValue): JsResult[T] = format.reads(json)
    }
    oFormat
  }
}

  implicit val withdrawHandler: BSONHandler[BSONDocument, WithdrawApplication] = Macros.handler[WithdrawApplication]
  implicit val cdHandler: BSONHandler[BSONDocument, ContactDetails] = Macros.handler[ContactDetails]
  implicit val assistanceDetailsHandler: BSONHandler[BSONDocument, AssistanceDetails] = Macros.handler[AssistanceDetails]
  implicit val answerHandler: BSONHandler[BSONDocument, PersistedAnswer] = Macros.handler[PersistedAnswer]
  implicit val candidateScoresHandler: BSONHandler[BSONDocument, CandidateScores] = Macros.handler[CandidateScores]
  implicit val candidateScoreFeedback: BSONHandler[BSONDocument, CandidateScoreFeedback] = Macros.handler[CandidateScoreFeedback]
  implicit val candidateScoresAndFeedback: BSONHandler[BSONDocument, CandidateScoresAndFeedback] = Macros.handler[CandidateScoresAndFeedback]
  implicit val passMarkSchemeThresholdHandler: BSONHandler[BSONDocument, PassMarkSchemeThreshold] =
    Macros.handler[PassMarkSchemeThreshold]
  implicit val assessmentCentrePassMarkInfoHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkInfo] =
    Macros.handler[AssessmentCentrePassMarkInfo]
  implicit val assessmentCentrePassMarkSchemeHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkScheme] =
    Macros.handler[AssessmentCentrePassMarkScheme]
  implicit val assessmentCentrePassMarkSettingsHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkSettings] =
    Macros.handler[AssessmentCentrePassMarkSettings]
  implicit val competencyAverageResultHandler: BSONHandler[BSONDocument, CompetencyAverageResult] =
    Macros.handler[CompetencyAverageResult]
  implicit val flagCandidateHandler: BSONHandler[BSONDocument, FlagCandidate] = Macros.handler[FlagCandidate]
  implicit val adjustmentDetailHandler: BSONHandler[BSONDocument, AdjustmentDetail] = Macros.handler[AdjustmentDetail]

  def bsonDocToOnlineTestApplication(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val userId = doc.getAs[String]("userId").get

    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    val lastName = personalDetailsRoot.getAs[String]("lastName").get

    val assistanceDetailsRoot = doc.getAs[BSONDocument]("assistance-details").get
    val guaranteedInterview = assistanceDetailsRoot.getAs[Boolean]("guaranteedInterview").getOrElse(false)
    val needsAdjustmentForOnlineTests = assistanceDetailsRoot.getAs[Boolean]("needsSupportForOnlineAssessment").getOrElse(false)
    val needsAdjustmentsAtVenue = assistanceDetailsRoot.getAs[Boolean]("needsSupportAtVenue").getOrElse(false)

    val etrayAdjustments = assistanceDetailsRoot.getAs[AdjustmentDetail]("etray")
    val videoInterviewAdjustments = assistanceDetailsRoot.getAs[AdjustmentDetail]("video")

    OnlineTestApplication(applicationId, applicationStatus, userId, guaranteedInterview, needsAdjustmentForOnlineTests,
      needsAdjustmentsAtVenue, preferredName, lastName, etrayAdjustments, videoInterviewAdjustments)
  }
}
