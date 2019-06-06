package services.onlinetesting.phase1

import config.{ OnlineTestsGatewayConfig, Phase1TestsConfig }
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Result
import model.ProgressStatuses.ProgressStatus
import model.exchange.passmarksettings.{ PassMarkThreshold, Phase1PassMark, Phase1PassMarkSettings, Phase1PassMarkThresholds }
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationRoute, ApplicationStatus, Phase, SchemeId }
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.prop.{ TableDrivenPropertyChecks, TableFor5, TableFor9 }
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONCollection
import repositories.onlinetesting.Phase1EvaluationMongoRepository
import repositories.passmarksettings.Phase1PassMarkSettingsMongoRepository
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

import scala.concurrent.Future

trait Phase1TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  import ImplicitBSONHandlers._

  val collectionName: String = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE1_PASS_MARK_SETTINGS)

  def phase1TestEvaluationService = new EvaluatePhase1ResultService {
    val evaluationRepository: Phase1EvaluationMongoRepository = phase1EvaluationRepo
    val gatewayConfig: OnlineTestsGatewayConfig = mockGatewayConfig
    val passMarkSettingsRepo: Phase1PassMarkSettingsMongoRepository = phase1PassMarkSettingRepo
    val phase1TestsConfigMock: Phase1TestsConfig = mock[Phase1TestsConfig]
    val phase = Phase.PHASE1

    when(gatewayConfig.phase1Tests).thenReturn(phase1TestsConfigMock)
    when(phase1TestsConfigMock.scheduleIds).thenReturn(Map("sjq" -> 16196, "bq" -> 16194))
  }

  trait TestFixture {

    // format: OFF
    //scalastyle:off
    val phase1PassMarkSettingsTable = Table[SchemeId, Double, Double, Double, Double, Double, Double, Double, Double](
      ("Scheme Name",                                   "Test1 Fail", "Test1 Pass", "Test2 Fail", "Test2 Pass", "Test3 Fail", "Test3 Pass", "Test4 Fail", "Test4 Pass"),
      (SchemeId("Commercial"),                            20.0,         80.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("DigitalAndTechnology"),                  20.001,       20.001,       20.01,        20.05,        20.0,         80.0,         20.0,         80.0),
      (SchemeId("DiplomaticService"),                     20.01,        20.02,        20.01,        20.02,        20.0,         80.0,         20.0,         80.0),
      (SchemeId("DiplomaticServiceEconomics"),            30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("DiplomaticServiceEuropean"),             30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("European"),                              40.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("Finance"),                               25.01,        25.02,        25.01,        25.02,        20.0,         80.0,         20.0,         80.0),
      (SchemeId("Generalist"),                            30.0,         30.0,         30.0,         30.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentCommunicationService"),        30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentEconomicsService"),            30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentOperationalResearchService"),  30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentSocialResearchService"),       30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentStatisticalService"),          30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("HousesOfParliament"),                    30.0,         79.999,       30.0,         78.08,        20.0,         80.0,         20.0,         80.0),
      (SchemeId("HumanResources"),                        30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("ProjectDelivery"),                       30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("ScienceAndEngineering"),                 69.00,        69.00,        78.99,        78.99,        20.0,         80.0,         20.0,         80.0)
    )
    //scalastyle:on
    // format: ON

    val phase1PassMarkSettingWithSdipTable =
      getPassMarkSettingWithNewSettings(
        phase1PassMarkSettingsTable, (SchemeId("Finance"), 90.00, 90.00, 90.00, 90.00, 90.00, 90.00, 90.00, 90.00)
      )

    var phase1PassMarkSettings: Phase1PassMarkSettings = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    def gisApplicationEvaluation(applicationId:String, sjqScore: Double, selectedSchemes: SchemeId*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults(applicationId, sjqScore, None, isGis = true)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue
      this
    }

    def applicationEvaluationWithPassMarks(passmarks: Phase1PassMarkSettings, applicationId:String, sjqScore: Double, bjqScore: Double,
      selectedSchemes: SchemeId*)(implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults(applicationId, sjqScore, Some(bjqScore),
        applicationRoute = applicationRoute)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, passmarks).futureValue
      this
    }

    def applicationEvaluation(applicationId: String, sjqScore: Double, bjqScore: Double, selectedSchemes: SchemeId*)
      (implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationEvaluationWithPassMarks(phase1PassMarkSettings, applicationId, sjqScore, bjqScore, selectedSchemes:_*)
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus, expProgressStatus: Option[ProgressStatus],
      expSchemeResults: (SchemeId , Result)*): TestFixture = {
      passMarkEvaluation = phase1EvaluationRepo.getPassMarkEvaluation(applicationReadyForEvaluation.applicationId).futureValue
      val applicationDetails = applicationRepository.findStatus(applicationReadyForEvaluation.applicationId).futureValue
      val applicationStatus = ApplicationStatus.withName(applicationDetails.status)
      val progressStatus = applicationDetails.latestProgressStatus

      val schemeResults = passMarkEvaluation.result.map {
        SchemeEvaluationResult.unapply(_).map {
          case (schemeType, resultStr) => schemeType -> Result(resultStr)
        }.get
      }
      phase1PassMarkSettings.version mustBe passMarkEvaluation.passmarkVersion
      applicationStatus mustBe expApplicationStatus
      progressStatus mustBe expProgressStatus
      schemeResults.size mustBe expSchemeResults.size
      schemeResults must contain theSameElementsAs expSchemeResults
      applicationReadyForEvaluation = applicationReadyForEvaluation.copy(applicationStatus = expApplicationStatus)
      this
    }

    def getPassMarkSettingWithNewSettings(
      phase1PassMarkSettingsTable: TableFor9[SchemeId, Double, Double, Double, Double, Double, Double, Double, Double],
      newSchemeSettings: (SchemeId, Double, Double, Double, Double, Double, Double, Double, Double)*) = {
      phase1PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
    }

    def applicationReEvaluationWithOverridingPassmarks(newSchemeSettings: (SchemeId, Double, Double, Double, Double,
      Double, Double, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable, newSchemeSettings:_*)
      phase1PassMarkSettings = createPhase1PassMarkSettings(schemePassMarkSettings).futureValue
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue
      this
    }

    def createPhase1PassMarkSettings(phase1PassMarkSettingsTable: TableFor9[SchemeId, Double, Double, Double, Double,
      Double, Double, Double, Double]): Future[Phase1PassMarkSettings] = {
      val schemeThresholds = phase1PassMarkSettingsTable.map {
        fields => Phase1PassMark(fields._1,
          Phase1PassMarkThresholds(
            PassMarkThreshold(fields._2, fields._3),
            PassMarkThreshold(fields._4, fields._5),
            PassMarkThreshold(fields._6, fields._7),
            PassMarkThreshold(fields._8, fields._9)
          ))
      }.toList

      val phase1PassMarkSettings = Phase1PassMarkSettings(
        schemeThresholds,
        "version-1",
        DateTime.now,
        "user-1"
      )
      phase1PassMarkSettingRepo.create(phase1PassMarkSettings).flatMap { _ =>
        phase1PassMarkSettingRepo.getLatestVersion.map(_.get)
      }
    }

    val appCollection = mongo().collection[JSONCollection](collectionName)

    def createUser(userId: String, appId: String) = {
      appCollection.insert(BSONDocument("applicationId" -> appId, "userId" -> userId,
        "applicationStatus" -> ApplicationStatus.CREATED))
    }

    Future.sequence(List(
      createUser("user-1", "application-1"),
      createUser("user-2", "application-2"),
      createUser("user-3", "application-3"),
      createPhase1PassMarkSettings(phase1PassMarkSettingsTable).map(phase1PassMarkSettings = _)
    )).futureValue
  }

}
