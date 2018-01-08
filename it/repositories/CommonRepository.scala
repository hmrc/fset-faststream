package repositories

import common.FutureEx
import config.{ CubiksGatewayConfig, LaunchpadGatewayConfig }
import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.Phase1TestExamples._
import model.Phase2TestProfileExamples._
import model.Phase3TestProfileExamples._
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_AWAITING_ALLOCATION, ProgressStatus }
import model.persisted._
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.junit.Assert._
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.bson.{ BSONArray, BSONDocument }
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import repositories.assessmentcentre.AssessmentCentreMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.onlinetesting._
import repositories.passmarksettings._
import repositories.sift.{ ApplicationSiftMongoRepository, ApplicationSiftRepository }
import services.GBTimeZoneService
import services.sift.ApplicationSiftService
import testkit.MongoRepositorySpec

import scala.concurrent.Future

trait CommonRepository extends CurrentSchemeStatusHelper {
  this: MongoRepositorySpec with ScalaFutures =>

  import reactivemongo.json.ImplicitBSONHandlers._

  val mockGatewayConfig = mock[CubiksGatewayConfig]

  val mockLaunchpadConfig = mock[LaunchpadGatewayConfig]

  val European: SchemeId = SchemeId("European")
  val Finance = SchemeId("Finance")
  val DiplomaticService = SchemeId("Diplomatic Service")
  val Sdip = SchemeId("Sdip")
  val Edip = SchemeId("Edip")
  val siftableSchemeDefinitions = List(European, Finance, DiplomaticService, Edip, Sdip)

  def applicationRepository = new GeneralApplicationMongoRepository(DateTimeFactory, mockGatewayConfig)

  def schemePreferencesRepository = new schemepreferences.SchemePreferencesMongoRepository

  def assistanceDetailsRepository = new AssistanceDetailsMongoRepository

  def phase1TestRepository = new Phase1TestMongoRepository(DateTimeFactory)

  def phase2TestRepository = new Phase2TestMongoRepository(DateTimeFactory)

  def phase3TestRepository = new Phase3TestMongoRepository(DateTimeFactory)

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository(DateTimeFactory)

  def phase2EvaluationRepo = new Phase2EvaluationMongoRepository(DateTimeFactory)

  def phase3EvaluationRepo = new Phase3EvaluationMongoRepository(mockLaunchpadConfig, DateTimeFactory)

  def phase1PassMarkSettingRepo = new Phase1PassMarkSettingsMongoRepository()

  def phase2PassMarkSettingRepo = new Phase2PassMarkSettingsMongoRepository()

  def phase3PassMarkSettingRepo = new Phase3PassMarkSettingsMongoRepository()

  def applicationSiftRepository = new ApplicationSiftMongoRepository(DateTimeFactory, siftableSchemeDefinitions)

  def assessmentCentreRepository = new AssessmentCentreMongoRepository(DateTimeFactory, siftableSchemeDefinitions)

  implicit val now: DateTime = DateTime.now().withZone(DateTimeZone.UTC)

  def selectedSchemes(schemeTypes: List[SchemeId]) = SelectedSchemes(schemeTypes, orderAgreed = true, eligible = true)


  def insertApplicationWithPhase1TestResults(appId: String, sjq: Double, bq: Option[Double] = None, isGis: Boolean = false,
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  )(schemes: SchemeId*): ApplicationReadyForEvaluation = {
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(sjq), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", bq, None, None, None)))
    val phase1Tests = if(isGis) List(sjqTest) else List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(applicationRoute))
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE1_TESTS, applicationRoute, isGis,
      Phase1TestProfile(now, phase1Tests).activeTests, None, None, selectedSchemes(schemes.toList)
    )
  }

  def insertApplicationWithPhase1TestNotifiedResults(appId: String, results: List[SchemeEvaluationResult],
    appRoute: ApplicationRoute = ApplicationRoute.Sdip
  ): Future[Unit] = {
    val phase1PassMarkEvaluation = PassmarkEvaluation("", Some(""), results, "", Some(""))

    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val phase1Tests = List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(appRoute))

    phase1EvaluationRepo.savePassmarkEvaluation(appId, phase1PassMarkEvaluation, None).futureValue

    updateApplicationStatus(appId, ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED)
  }

  def insertApplicationWithPhase2TestResults(appId: String, etray: Double,
    phase1PassMarkEvaluation: PassmarkEvaluation,
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  )(schemes: SchemeId*): ApplicationReadyForEvaluation = {
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val etrayTest = getEtrayTest.copy(cubiksUserId = 3, testResult = Some(TestResult("Ready", "norm", Some(etray), None, None, None)))
    val phase1Tests = List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE2_TESTS, Some(phase1Tests), Some(List(etrayTest)))
    phase1EvaluationRepo.savePassmarkEvaluation(appId, phase1PassMarkEvaluation, None).futureValue
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE2_TESTS, applicationRoute, isGis = false,
      List(etrayTest), None, Some(phase1PassMarkEvaluation), selectedSchemes(schemes.toList))
  }

  def insertApplicationWithPhase3TestResults(appId: String, videoInterviewScore: Option[Double],
    phase2PassMarkEvaluation: PassmarkEvaluation,
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  )(schemes: SchemeId*): ApplicationReadyForEvaluation = {
    val launchPadTests = phase3TestWithResults(videoInterviewScore).activeTests
    insertApplication(appId, ApplicationStatus.PHASE3_TESTS, None, None, Some(launchPadTests))
    phase2EvaluationRepo.savePassmarkEvaluation(appId, phase2PassMarkEvaluation, None).futureValue
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE3_TESTS, applicationRoute, isGis = false,
      Nil, launchPadTests.headOption, Some(phase2PassMarkEvaluation), selectedSchemes(schemes.toList))
  }

  def insertApplicationWithPhase3TestNotifiedResults(appId: String, results: List[SchemeEvaluationResult],
                             videoInterviewScore: Option[Double] = None,
                             applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                            ): Future[Unit] = {
    val schemes = results.map(_.schemeId)
    val phase3PassMarkEvaluation = PassmarkEvaluation("", Some(""), results, "", Some(""))

    val launchPadTests = phase3TestWithResults(videoInterviewScore).activeTests
    insertApplication(appId, ApplicationStatus.PHASE3_TESTS, None, None, Some(launchPadTests), applicationRoute = Some(applicationRoute),
      schemes = schemes
    )

    phase3EvaluationRepo.savePassmarkEvaluation(appId, phase3PassMarkEvaluation, None).futureValue

    updateApplicationStatus(appId, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED)
  }

  def insertApplicationWithSiftComplete(appId: String, results: Seq[SchemeEvaluationResult],
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  ) = {
    insertApplicationWithPhase3TestNotifiedResults(appId, results.toList, applicationRoute = applicationRoute).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_ENTERED).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
  }

  def insertApplicationAtFsbWithStatus(appId: String, results: Seq[SchemeEvaluationResult], progressStatus: ProgressStatus,
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  ) = {
    insertApplicationWithSiftComplete(appId, results, applicationRoute)
    FutureEx.traverseSerial(results) { result => fsbRepository.saveResult(appId, result) }.futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, progressStatus).futureValue
  }

  def updateApplicationStatus(appId: String, newStatus: ApplicationStatus): Future[Unit] = {
    val application = BSONDocument("applicationId" -> appId)
    val update = BSONDocument(
      "$set" -> BSONDocument(s"applicationStatus" -> newStatus)
    )
    applicationRepository.collection.update(application, update).map {_ => ()}
  }

  // scalastyle:off
  def insertApplication(appId: String, applicationStatus: ApplicationStatus, phase1Tests: Option[List[CubiksTest]] = None,
                        phase2Tests: Option[List[CubiksTest]] = None, phase3Tests: Option[List[LaunchpadTest]] = None,
                        isGis: Boolean = false, schemes: List[SchemeId] = List(SchemeId("Commercial")),
                        phase1Evaluation: Option[PassmarkEvaluation] = None,
                        phase2Evaluation: Option[PassmarkEvaluation] = None,
                        additionalProgressStatuses: List[(ProgressStatus, Boolean)] = List.empty,
    applicationRoute: Option[ApplicationRoute] = Some(ApplicationRoute.Faststream)
  ): Unit = {
    val gis = if (isGis) Some(true) else None
    applicationRepository.collection.insert(
      BSONDocument(
      "applicationId" -> appId,
      "userId" -> appId,
      "applicationStatus" -> applicationStatus,
      "progress-status" -> progressStatus(additionalProgressStatuses)
    ) ++ {
        if (applicationRoute.isDefined) {
          BSONDocument("applicationRoute" -> applicationRoute.get)
        } else {
          BSONDocument.empty
        }
      }
    ).futureValue

    val ad = AssistanceDetails("No", None, gis, needsSupportForOnlineAssessment = Some(false), None,
      needsSupportAtVenue = Some(false), None, needsSupportForPhoneInterview = None, needsSupportForPhoneInterviewDescription = None)
    assistanceDetailsRepository.update(appId, appId, ad).futureValue

    schemePreferencesRepository.save(appId, selectedSchemes(schemes)).futureValue
    insertPhase1Tests(appId, phase1Tests, phase1Evaluation)
    insertPhase2Tests(appId, phase2Tests, phase2Evaluation)
    phase3Tests.foreach { t =>
      phase3TestRepository.insertOrUpdateTestGroup(appId, Phase3TestGroup(now, t)).futureValue
      if(t.headOption.exists(_.callbacks.reviewed.nonEmpty)) {
        phase3TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
    applicationRepository.collection.update(
      BSONDocument("applicationId" -> appId),
      BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus))).futureValue
  }
  // scalastyle:on

  def insertPhase2Tests(appId: String, phase2Tests: Option[List[CubiksTest]], phase2Evaluation: Option[PassmarkEvaluation]): Unit = {
    phase2Tests.foreach { t =>
      phase2TestRepository.insertOrUpdateTestGroup(appId, Phase2TestGroup(now, t, phase2Evaluation)).futureValue
      if (t.exists(_.testResult.isDefined)) {
        phase2TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
  }

  def insertPhase1Tests(appId: String, phase1Tests: Option[List[CubiksTest]], phase1Evaluation: Option[PassmarkEvaluation]) = {
    phase1Tests.foreach { t =>
      phase1TestRepository.insertOrUpdateTestGroup(appId, Phase1TestProfile(now, t, phase1Evaluation)).futureValue
      if (t.exists(_.testResult.isDefined)) {
        phase1TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
  }

  private def questionnaire() = {
    BSONDocument(
      "start_questionnaire" -> true,
      "diversity_questionnaire" -> true,
      "education_questionnaire" -> true,
      "occupation_questionnaire" -> true
    )
  }

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): BSONDocument = {
    val baseDoc = BSONDocument(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "partner-graduate-programmes" -> true,
      "assistance-details" -> true,
      "questionnaire" -> questionnaire(),
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc.++(v._1.toString -> v._2))
  }
}
