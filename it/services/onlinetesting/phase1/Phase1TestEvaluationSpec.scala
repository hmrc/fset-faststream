package services.onlinetesting.phase1

import config.{Phase1TestsConfig, PsiTestIds}
import factories.UUIDFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Result
import model.ProgressStatuses.ProgressStatus
import model.exchange.passmarksettings.{PassMarkThreshold, Phase1PassMark, Phase1PassMarkSettingsPersistence, Phase1PassMarkThresholds}
import model.persisted.{ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult}
import model.{ApplicationRoute, ApplicationStatus, SchemeId, Schemes}
import org.mockito.Mockito.when
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor7}
import repositories.{CollectionNames, CommonRepository}
import testkit.MongoRepositorySpec

import java.time.OffsetDateTime
import scala.concurrent.Future

trait Phase1TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  val collectionName: String = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE1_PASS_MARK_SETTINGS)

  def phase1TestEvaluationService = {
    when(mockAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)

    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventoryId$idx", s"assessmentId$idx", s"reportId$idx", s"normId$idx")

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(1),
      "test2" -> testIds(2),
      "test3" -> testIds(3),
      "test4" -> testIds(4)
    )
    val mockPhase1TestsConfig: Phase1TestsConfig = mock[Phase1TestsConfig]
    when(mockOnlineTestsGatewayConfig.phase1Tests).thenReturn(mockPhase1TestsConfig)
    when(mockPhase1TestsConfig.tests).thenReturn(tests)
    when(mockPhase1TestsConfig.gis).thenReturn(List("test1", "test4"))
    when(mockPhase1TestsConfig.standard).thenReturn(List("test1", "test2", "test3", "test4"))

    new EvaluatePhase1ResultService(
      phase1EvaluationRepo,
      phase1PassMarkSettingRepo,
      mockAppConfig,
      UUIDFactory
    )
  }

  trait TestFixture extends Schemes {

    // format: OFF
    //scalastyle:off
    val phase1PassMarkSettingsTable = Table[SchemeId, Double, Double, Double, Double, Double, Double](
      ("Scheme Name",                           "Test1 Fail", "Test1 Pass", "Test2 Fail", "Test2 Pass", "Test3 Fail", "Test3 Pass"),
      (Commercial,                                20.0,         80.0,         30.0,         70.0,         20.0,         70.0),
      (DigitalDataTechnologyAndCyber,             20.001,       20.001,       20.01,        20.05,        19.0,         20.0),
      (DiplomaticAndDevelopment,                  20.01,        20.02,        20.01,        20.02,        20.0,         80.0),
      (DiplomaticAndDevelopmentEconomics,         30.0,         70.0,         30.0,         70.0,         30.0,         70.0),
      (Finance,                                   25.01,        25.02,        25.01,        25.02,        25.01,        25.02),
      (GovernmentCommunicationService,            30.0,         70.0,         30.0,         70.0,         20.0,         80.0),
      (GovernmentEconomicsService,                30.0,         70.0,         30.0,         70.0,         20.0,         80.0),
      (GovernmentOperationalResearchService,      30.0,         70.0,         30.0,         70.0,         20.0,         80.0),
      (GovernmentPolicy,                          30.0,         70.0,         30.0,         70.0,         30.0,         70.0),
      (GovernmentSocialResearchService,           30.0,         70.0,         30.0,         70.0,         20.0,         80.0),
      (GovernmentStatisticalService,              30.0,         70.0,         30.0,         70.0,         20.0,         80.0),
      (HousesOfParliament,                        30.0,         79.999,       30.0,         78.08,        20.0,         77.77),
      (HumanResources,                            30.0,         70.0,         30.0,         70.0,         20.0,         80.0),
      (OperationalDelivery,                       30.0,         30.0,         30.0,         30.0,         30.0,         30.0),
      (ProjectDelivery,                           30.0,         70.0,         30.0,         70.0,         20.0,         80.0),
      (Property,                                  40.0,         70.0,         30.0,         70.0,         30.0,         70.0),
      (ScienceAndEngineering,                     69.00,        69.00,        78.99,        78.99,        20.0,         80.0)
    )
    //scalastyle:on
    // format: ON

    val phase1PassMarkSettingWithSdipTable =
      getPassMarkSettingWithNewSettings(
        phase1PassMarkSettingsTable, (Finance, 90.00, 90.00, 90.00, 90.00, 90.00, 90.00)
      )

    var phase1PassMarkSettings: Phase1PassMarkSettingsPersistence = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    def gisApplicationEvaluation(applicationId:String, t1Score: Double, t3Score: Double, selectedSchemes: SchemeId*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults(
        applicationId, t1Score, None, t3Score, isGis = true)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue
      this
    }

    def applicationEvaluationWithPassMarks(passmarks: Phase1PassMarkSettingsPersistence, applicationId:String,
                                           t1Score: Double, t2Score: Double,
                                           t3Score: Double, selectedSchemes: SchemeId*)(
                                            implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults(
        applicationId, t1Score, Some(t2Score), t3Score, applicationRoute = applicationRoute)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, passmarks).futureValue
      this
    }

    def applicationEvaluation(applicationId: String, t1Score: Double, t2Score: Double,
                              t3Score: Double, selectedSchemes: SchemeId*)
                             (implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationEvaluationWithPassMarks(phase1PassMarkSettings, applicationId, t1Score, t2Score, t3Score, selectedSchemes:_*)
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
                                           phase1PassMarkSettingsTable: TableFor7[SchemeId,
                                             Double, Double, Double, Double, Double, Double],
                                           newSchemeSettings: (SchemeId, Double, Double, Double, Double, Double, Double)*) = {
      phase1PassMarkSettingsTable.filter(schemeSetting =>
        !newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
    }

    def applicationReEvaluationWithOverridingPassmarks(newSchemeSettings: (SchemeId, Double, Double, Double, Double,
      Double, Double)*): TestFixture = {
      val schemePassMarkSettings = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable, newSchemeSettings:_*)
      phase1PassMarkSettings = createPhase1PassMarkSettings(schemePassMarkSettings).futureValue
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue
      this
    }

    def createPhase1PassMarkSettings(phase1PassMarkSettingsTable: TableFor7[SchemeId, Double, Double, Double, Double,
      Double, Double]): Future[Phase1PassMarkSettingsPersistence] = {
      val schemeThresholds = phase1PassMarkSettingsTable.map {
        case (schemeName, t1Fail, t1Pass, t2Fail, t2Pass, t3Fail, t3Pass) =>
          Phase1PassMark(schemeName,
            Phase1PassMarkThresholds(
              PassMarkThreshold(t1Fail, t1Pass),
              PassMarkThreshold(t2Fail, t2Pass),
              PassMarkThreshold(t3Fail, t3Pass)
            ))
      }.toList

      val phase1PassMarkSettings = Phase1PassMarkSettingsPersistence(
        schemeThresholds,
        "version-1",
        OffsetDateTime.now,
        "user-1"
      )
      phase1PassMarkSettingRepo.create(phase1PassMarkSettings).flatMap { _ =>
        phase1PassMarkSettingRepo.getLatestVersion.map(_.get)
      }
    }

    val appCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

    def createUser(userId: String, appId: String) = {
      appCollection.insertOne(Document("applicationId" -> appId, "userId" -> userId,
        "applicationStatus" -> ApplicationStatus.CREATED.toBson)).toFuture().map( _ => ())
    }

    Future.sequence(List(
      createUser("user-1", "application-1"),
      createUser("user-2", "application-2"),
      createUser("user-3", "application-3"),
      createPhase1PassMarkSettings(phase1PassMarkSettingsTable).map(phase1PassMarkSettings = _)
    )).futureValue
  }
}
