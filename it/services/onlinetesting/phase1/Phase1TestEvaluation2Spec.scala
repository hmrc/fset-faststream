package services.onlinetesting.phase1

import config.{ Phase1TestsConfig2, PsiTestIds, TestIntegrationGatewayConfig }
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Result
import model.ProgressStatuses.ProgressStatus
import model.exchange.passmarksettings.{ PassMarkThreshold, Phase1PassMark, Phase1PassMarkSettings, Phase1PassMarkThresholds }
import model.persisted.{ ApplicationReadyForEvaluation2, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationRoute, ApplicationStatus, Phase, SchemeId }
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.prop.{ TableDrivenPropertyChecks, TableFor9 }
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONCollection
import repositories.onlinetesting.Phase1EvaluationMongoRepository
import repositories.passmarksettings.Phase1PassMarkSettingsMongoRepository
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

import scala.concurrent.Future

trait Phase1TestEvaluation2Spec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  import ImplicitBSONHandlers._

  val collectionName: String = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE1_PASS_MARK_SETTINGS)

  def phase1TestEvaluationService = new EvaluatePhase1ResultService2 {
    val evaluationRepository: Phase1EvaluationMongoRepository = phase1EvaluationRepo
    val gatewayConfig: TestIntegrationGatewayConfig = mockTestIntegrationGatewayConfig
    val passMarkSettingsRepo: Phase1PassMarkSettingsMongoRepository = phase1PassMarkSettingRepo
    val phase1TestsConfigMock: Phase1TestsConfig2 = mock[Phase1TestsConfig2]
    val phase = Phase.PHASE1

    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventoryId$idx", s"assessmentId$idx", s"reportId$idx", s"normId$idx")

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(1),
      "test2" -> testIds(2),
      "test3" -> testIds(3),
      "test4" -> testIds(4)
    )

    when(gatewayConfig.phase1Tests).thenReturn(phase1TestsConfigMock)
    when(phase1TestsConfigMock.tests).thenReturn(tests)
    when(phase1TestsConfigMock.gis).thenReturn(List("test1", "test4"))
    when(phase1TestsConfigMock.standard).thenReturn(List("test1", "test2", "test3", "test4"))
  }

  trait TestFixture {

    // format: OFF
    //scalastyle:off
    val phase1PassMarkSettingsTable = Table[SchemeId, Double, Double, Double, Double, Double, Double, Double, Double](
      ("Scheme Name",                                   "Test1 Fail", "Test1 Pass", "Test2 Fail", "Test2 Pass", "Test3 Fail", "Test3 Pass", "Test4 Fail", "Test4 Pass"),
      (SchemeId("Commercial"),                            20.0,         80.0,         30.0,         70.0,         30.0,         70.0,         20.0,         70.0),
      (SchemeId("DigitalAndTechnology"),                  20.001,       20.001,       20.01,        20.05,        19.0,         20.0,         19.0,         20.0),
      (SchemeId("DiplomaticService"),                     20.01,        20.02,        20.01,        20.02,        20.0,         80.0,         20.0,         80.0),
      (SchemeId("DiplomaticServiceEconomics"),            30.0,         70.0,         30.0,         70.0,         30.0,         70.0,         30.0,         70.0),
      (SchemeId("DiplomaticServiceEuropean"),             30.0,         70.0,         30.0,         70.0,         30.0,         70.0,         30.0,         70.0),
      (SchemeId("European"),                              40.0,         70.0,         30.0,         70.0,         30.0,         70.0,         30.0,         70.0),
      (SchemeId("Finance"),                               25.01,        25.02,        25.01,        25.02,        25.01,        25.02,        25.01,        25.02),
      (SchemeId("Generalist"),                            30.0,         30.0,         30.0,         30.0,         30.0,         30.0,         30.0,         30.0),
      (SchemeId("GovernmentCommunicationService"),        30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentEconomicsService"),            30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentOperationalResearchService"),  30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentSocialResearchService"),       30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("GovernmentStatisticalService"),          30.0,         70.0,         30.0,         70.0,         20.0,         80.0,         20.0,         80.0),
      (SchemeId("HousesOfParliament"),                    30.0,         79.999,       30.0,         78.08,        20.0,         77.77,        20.0,         76.66),
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

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation2 = _

    var passMarkEvaluation: PassmarkEvaluation = _

    def gisApplicationEvaluation(applicationId:String, t1Score: Double, t4Score: Double, selectedSchemes: SchemeId*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults2(
        applicationId, t1Score, None, None, t4Score, isGis = true)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue
      this
    }

    def applicationEvaluationWithPassMarks(passmarks: Phase1PassMarkSettings, applicationId:String,
                                           t1Score: Double, t2Score: Double,
                                           t3Score: Double, t4Score: Double, selectedSchemes: SchemeId*)(
      implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults2(
        applicationId, t1Score, Some(t2Score), Some(t3Score), t4Score, applicationRoute = applicationRoute)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, passmarks).futureValue
      this
    }

    def applicationEvaluation(applicationId: String, t1Score: Double, t2Score: Double,
                              t3Score: Double, t4Score: Double, selectedSchemes: SchemeId*)
      (implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationEvaluationWithPassMarks(phase1PassMarkSettings, applicationId, t1Score, t2Score, t3Score, t4Score, selectedSchemes:_*)
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
        case (schemeName, t1Fail, t1Pass, t2Fail, t2Pass, t3Fail, t3Pass, t4Fail, t4Pass) =>
          Phase1PassMark(schemeName,
          Phase1PassMarkThresholds(
            PassMarkThreshold(t1Fail, t1Pass),
            PassMarkThreshold(t2Fail, t2Pass),
            PassMarkThreshold(t3Fail, t3Pass),
            PassMarkThreshold(t4Fail, t4Pass)
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
