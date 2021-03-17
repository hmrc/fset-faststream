package services.onlinetesting.phase2

import config.{ Phase2TestsConfig, PsiTestIds }
import factories.UUIDFactory
import model.ApplicationRoute._
import model.ApplicationStatus._
import model.EvaluationResults._
import model.ProgressStatuses.ProgressStatus
import model.exchange.passmarksettings._
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationRoute, _ }
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.prop._
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONCollection
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class Phase2TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  import ImplicitBSONHandlers._

  val collectionName: String = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE2_PASS_MARK_SETTINGS)

  def phase2TestEvaluationService = {
    when(mockAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)

    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventoryId$idx", s"assessmentId$idx", s"reportId$idx", s"normId$idx")

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(5),
      "test2" -> testIds(6)
    )

    val mockPhase2TestsConfig: Phase2TestsConfig = mock[Phase2TestsConfig]
    when(mockOnlineTestsGatewayConfig.phase2Tests).thenReturn(mockPhase2TestsConfig)
    when(mockPhase2TestsConfig.tests).thenReturn(tests)

    new EvaluatePhase2ResultService(
      phase2EvaluationRepo,
      phase2PassMarkSettingRepo,
      applicationRepository,
      mockAppConfig,
      UUIDFactory
    )
  }

  trait TestFixture {

    // format: OFF
    val phase2PassMarkSettingsTable = Table[SchemeId, Double, Double, Double, Double](
      ("Scheme Name", "Test1 Fail Threshold", "Test1 Pass Threshold", "Test2 Fail Threshold", "Test2 Pass Threshold"),
      (SchemeId("Commercial"), 20.0, 80.0, 20.0, 80.0),
      (SchemeId("DigitalAndTechnology"), 20.001, 20.001, 20.001, 20.001),
      (SchemeId("DiplomaticService"), 20.01, 20.02, 20.01, 20.02),
      (SchemeId("DiplomaticServiceEconomics"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("DiplomaticServiceEuropean"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("European"), 40.0, 70.0, 40.0, 70.0),
      (SchemeId("Finance"), 25.01, 25.02, 25.01, 25.02),
      (SchemeId("Generalist"), 30.0, 30.0, 30.0, 30.0),
      (SchemeId("GovernmentCommunicationService"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("GovernmentEconomicsService"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("GovernmentOperationalResearchService"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("GovernmentSocialResearchService"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("GovernmentStatisticalService"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("HousesOfParliament"), 30.0, 79.999, 30.0, 79.999),
      (SchemeId("HumanResources"), 30.0, 50.0, 30.0, 50.0),
      (SchemeId("ProjectDelivery"), 30.0, 70.0, 30.0, 70.0),
      (SchemeId("ScienceAndEngineering"), 69.00, 69.00, 69.00, 69.00)
    )
    // format: ON

    var phase2PassMarkSettings: Phase2PassMarkSettings = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    var phase1PassMarkEvaluation: PassmarkEvaluation = _

    def applicationEvaluation(applicationId: String, test1Score: Double, test2Score: Double, selectedSchemes: SchemeId*)
                             (implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase2TestResults(applicationId, test1Score, test2Score,
        phase1PassMarkEvaluation, applicationRoute = applicationRoute)(selectedSchemes: _*)
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus.ApplicationStatus, expProgressStatus: Option[ProgressStatus],
                     expSchemeResults: (SchemeId, Result)*): TestFixture = {
      passMarkEvaluation = phase2EvaluationRepo.getPassMarkEvaluation(applicationReadyForEvaluation.applicationId).futureValue
      val applicationDetails = applicationRepository.findStatus(applicationReadyForEvaluation.applicationId).futureValue
      val applicationStatus = ApplicationStatus.withName(applicationDetails.status)
      val progressStatus = applicationDetails.latestProgressStatus

      val schemeResults = passMarkEvaluation.result.map {
        SchemeEvaluationResult.unapply(_).map {
          case (schemeType, resultStr) => schemeType -> Result(resultStr)
        }.get
      }
      phase2PassMarkSettings.version mustBe passMarkEvaluation.passmarkVersion
      applicationStatus mustBe expApplicationStatus
      progressStatus mustBe expProgressStatus
      schemeResults must contain theSameElementsAs expSchemeResults
      passMarkEvaluation.previousPhasePassMarkVersion mustBe Some(phase1PassMarkEvaluation.passmarkVersion)
      this
    }

    def applicationReEvaluationWithSettings(newSchemeSettings: (SchemeId, Double, Double, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = phase2PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase2PassMarkSettings = createPhase2PassMarkSettings(schemePassMarkSettings).futureValue
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    private def createPhase2PassMarkSettings(phase2PassMarkSettingsTable:
                                             TableFor5[SchemeId, Double, Double, Double, Double]): Future[Phase2PassMarkSettings] = {
      val schemeThresholds = phase2PassMarkSettingsTable.map {
        case (schemeName, test1FailThreshold, test1PassThreshold, test2FailThreshold, test2PassThreshold) =>
          Phase2PassMark(
            schemeName,
            Phase2PassMarkThresholds(
              PassMarkThreshold(test1FailThreshold, test1PassThreshold),
              PassMarkThreshold(test2FailThreshold, test2PassThreshold)
            )
          )
      }.toList

      val phase2PassMarkSettings = Phase2PassMarkSettings(
        schemeThresholds,
        "version-1",
        DateTime.now,
        "user-1"
      )
      phase2PassMarkSettingRepo.create(phase2PassMarkSettings).flatMap { _ =>
        phase2PassMarkSettingRepo.getLatestVersion.map(_.get)
      }
    }

    val appCollection: JSONCollection = mongo.mongoConnector.db().collection[JSONCollection](collectionName)

    def createUser(userId: String, appId: String) = {
      appCollection.insert(ordered = false).one(BSONDocument("applicationId" -> appId, "userId" -> userId, "applicationStatus" -> CREATED))
    }

    Future.sequence(List(
      createUser("user-1", "application-1"),
      createUser("user-2", "application-2"),
      createUser("user-3", "application-3"),
      createPhase2PassMarkSettings(phase2PassMarkSettingsTable).map(phase2PassMarkSettings = _)
    )).futureValue
  }
}
