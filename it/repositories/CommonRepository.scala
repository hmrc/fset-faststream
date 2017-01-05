package repositories

import config.{ CubiksGatewayConfig, LaunchpadGatewayConfig }
import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.Phase1TestExamples._
import model.Phase2TestProfileExamples._
import model.Phase3TestProfileExamples._
import model.ProgressStatuses.ProgressStatus
import model.SchemeType._
import model.persisted._
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import model.{ ApplicationRoute, ApplicationStatus, ProgressStatuses, SelectedSchemes }
import org.joda.time.{ DateTime, DateTimeZone }
import org.junit.Assert._
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.bson.BSONDocument
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.onlinetesting._
import repositories.parity.ParityExportMongoRepository
import repositories.passmarksettings._
import services.GBTimeZoneService
import testkit.MongoRepositorySpec


trait CommonRepository {
  this: MongoRepositorySpec with ScalaFutures =>

  import reactivemongo.json.ImplicitBSONHandlers._

  val mockGatewayConfig = mock[CubiksGatewayConfig]

  val mockLaunchpadConfig = mock[LaunchpadGatewayConfig]

  def applicationRepository = new GeneralApplicationMongoRepository(GBTimeZoneService, mockGatewayConfig)

  def schemePreferencesRepository = new schemepreferences.SchemePreferencesMongoRepository

  def assistanceDetailsRepository = new AssistanceDetailsMongoRepository

  def phase1TestRepository = new Phase1TestMongoRepository(DateTimeFactory)

  def phase2TestRepository = new Phase2TestMongoRepository(DateTimeFactory)

  def phase3TestRepository = new Phase3TestMongoRepository(DateTimeFactory)

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository()

  def phase2EvaluationRepo = new Phase2EvaluationMongoRepository()

  def phase3EvaluationRepo = new Phase3EvaluationMongoRepository(mockLaunchpadConfig, DateTimeFactory)

  def phase1PassMarkSettingRepo = new Phase1PassMarkSettingsMongoRepository()

  def phase2PassMarkSettingRepo = new Phase2PassMarkSettingsMongoRepository()

  def phase3PassMarkSettingRepo = new Phase3PassMarkSettingsMongoRepository()

  def parityExportMongoRepo = new ParityExportMongoRepository(DateTimeFactory)

  implicit val now = DateTime.now().withZone(DateTimeZone.UTC)

  def selectedSchemes(schemeTypes: List[SchemeType]) = SelectedSchemes(schemeTypes, orderAgreed = true, eligible = true)


  def insertApplicationWithPhase1TestResults(appId: String, sjq: Double, bq: Option[Double] = None, isGis: Boolean = false,
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  )(schemes:SchemeType*): ApplicationReadyForEvaluation = {
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(sjq), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", bq, None, None, None)))
    val phase1Tests = if(isGis) List(sjqTest) else List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(applicationRoute))
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE1_TESTS, applicationRoute, isGis,
      Phase1TestProfile(now, phase1Tests).activeTests, None, None, selectedSchemes(schemes.toList)
    )
  }

  def insertApplicationWithPhase2TestResults(appId: String, etray: Double,
    phase1PassMarkEvaluation: PassmarkEvaluation,
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  )(schemes:SchemeType*): ApplicationReadyForEvaluation = {
    assertNotNull("Phase1 pass mark evaluation must be set", phase1PassMarkEvaluation)
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val etrayTest = getEtrayTest.copy(cubiksUserId = 3, testResult = Some(TestResult("Ready", "norm", Some(etray), None, None, None)))
    val phase1Tests = List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE2_TESTS, Some(phase1Tests), Some(List(etrayTest)))
    phase1EvaluationRepo.savePassmarkEvaluation(appId, phase1PassMarkEvaluation, None)
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE2_TESTS, applicationRoute, isGis = false,
      List(etrayTest), None, Some(phase1PassMarkEvaluation), selectedSchemes(schemes.toList))
  }

  def insertApplicationWithPhase3TestResults(appId: String, videoInterviewScore: Double,
    phase2PassMarkEvaluation: PassmarkEvaluation,
    applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
  )(schemes:SchemeType*): ApplicationReadyForEvaluation = {
    assertNotNull("Phase2 pass mark evaluation must be set", phase2PassMarkEvaluation)
    val launchPadTests = phase3TestWithResults(videoInterviewScore).activeTests
    insertApplication(appId, ApplicationStatus.PHASE3_TESTS, None, None, Some(launchPadTests))
    phase2EvaluationRepo.savePassmarkEvaluation(appId, phase2PassMarkEvaluation, None)
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE3_TESTS, applicationRoute, isGis = false,
      Nil, launchPadTests.headOption, Some(phase2PassMarkEvaluation), selectedSchemes(schemes.toList))
  }

  // scalastyle:off
  def insertApplication(appId: String, applicationStatus: ApplicationStatus, phase1Tests: Option[List[CubiksTest]] = None,
                        phase2Tests: Option[List[CubiksTest]] = None, phase3Tests: Option[List[LaunchpadTest]] = None,
                        isGis: Boolean = false, schemes: List[SchemeType] = List(Commercial),
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
