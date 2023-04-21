package services.assessmentcentre

import com.typesafe.config.{Config, ConfigFactory}
import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.EvaluationResults.CompetencyAverageResult
import model._
import model.assessmentscores._
import model.exchange.passmarksettings.{AssessmentCentrePassMarkSettings, AssessmentCentrePassMarkSettingsPersistence}
import model.persisted.SchemeEvaluationResult
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import org.joda.time.DateTime
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Logging
import play.api.libs.json.Json
import repositories._
import repositories.application.GeneralApplicationMongoRepository
import repositories.assessmentcentre.AssessmentCentreMongoRepository
import services.evaluation.AssessmentCentreEvaluationEngineImpl
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

import java.io.File
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

class AssessmentCentreServiceIntSpec extends MongoRepositorySpec with Logging {

  import AssessmentCentreServiceIntSpec._

  val schemeRepo = new SchemeYamlRepository() (app, appConfig)

  val applicationRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  // This tests the guice based class
  lazy val service = new AssessmentCentreService(
    applicationRepo,
    new AssessmentCentreMongoRepository(ITDateTimeFactoryMock, schemeRepo, mongo),
    mock[AssessmentCentrePassMarkSettingsService],
    mock[AssessmentScoresRepository],
    new AssessmentCentreEvaluationEngineImpl
  )

  val collectionName = CollectionNames.APPLICATION
  // Use this when debugging so the test framework only runs one test scenario. The tests will still be loaded, however
  val DebugRunTestNameOnly: Option[String] = None
  //    val DebugRunTestNameOnly: Option[String] = Some("multipleSchemesSuite_Mix_Scenario1")
  //    val DebugRunTestNameOnly: Option[String] = Some("oneSchemeSuite_Green_Scenario1")
  // Use this when debugging so the test framework only runs tests which contain the specified test suite name in their path
  // the tests will still be loaded, however
  val DebugRunTestSuitePathPatternOnly: Option[String] = None
  //    val DebugRunTestSuitePathPatternOnly: Option[String] = Some("1_oneSchemeSuite/")

  val prefix= "****"

  // Ficus specific ValueReaders so Ficus can read the config files into case classes
  implicit object SchemeReader extends ValueReader[SchemeId] {
    override def read(config: Config, path: String): SchemeId = {
      SchemeId(config.getString(path))
    }
  }

  implicit object UniqueIdentifierReader extends ValueReader[UniqueIdentifier] {
    override def read(config: Config, path: String): UniqueIdentifier = {
      UniqueIdentifier(config.getString(path))
    }
  }

  implicit val dateTimeReader: ValueReader[DateTime] = new ValueReader[DateTime] {
    def read(config: Config, path: String): DateTime = config.getValue(path).valueType match {
      case _ => DateTime.now()
    }
  }

  implicit object ApplicationStatusReader extends ValueReader[ApplicationStatus] {
    override def read(config: Config, path: String): ApplicationStatus = {
      ApplicationStatus.withName(config.getString(path))
    }
  }

  implicit object ProgressStatusReader extends ValueReader[ProgressStatuses.ProgressStatus] {
    override def read(config: Config, path: String): ProgressStatuses.ProgressStatus = {
      ProgressStatuses.nameToProgressStatus(config.getString(path))
    }
  }

  implicit object SchemeEvaluationResultReader extends ValueReader[SchemeEvaluationResult] {
    override def read(config: Config, path: String): SchemeEvaluationResult = {
      val localizedConfig = config.getConfig(path)
      SchemeEvaluationResult(
        schemeId = localizedConfig.as[SchemeId]("schemeId"),
        result = localizedConfig.as[String]("result")
      )
    }
  }

  "Assessment centre service" should {
    "evaluate scores for each test in the path" in  {
      locateSuites foreach executeSuite
    }
  }

  "Debug variables" should {
    "must be disabled" ignore {
      DebugRunTestNameOnly mustBe empty
      DebugRunTestSuitePathPatternOnly mustBe empty
    }
  }

  private def locateSuites: Array[File] = {
    val suites = new File(TestPath).listFiles.filterNot(_.getName == PassmarkSettingsFile).sortBy(_.getName)
    require(suites.nonEmpty, s"No test suites found in $TestPath")
    suites.foreach( s => logger.info(s"$prefix suite located = $s" ) )
    suites
  }

  private def executeSuite(suiteName: File) = {
    logger.info(s"$prefix executing suites found in directory = $suiteName...")

    // Reads the passmarkSettings.conf file
    def loadPassmarkSettings: AssessmentCentrePassMarkSettingsPersistence = {
      val passmarkSettingsFile = new File(suiteName.getAbsolutePath + "/" + PassmarkSettingsFile)

      require(passmarkSettingsFile.exists(), s"Pass mark settings file does not exist: ${passmarkSettingsFile.getAbsolutePath}")
      val passmarkSettingsJson = Json.parse(Source.fromFile(passmarkSettingsFile).getLines().mkString)
      passmarkSettingsJson.as[AssessmentCentrePassMarkSettings].toPersistence
    }

    // Returns all suite files, ignoring the config file (passmarkSettings.conf)
    def loadTestSuites: Array[File] = {
      val testSuites = new File(s"$TestPath/${suiteName.getName}/")
        .listFiles
        .filterNot(f => ConfigFiles.contains(f.getName)) // exclude passmarkSettings.conf
        .sortBy(_.getName)
      require(testSuites.nonEmpty, s"No test suites found to execute in $TestPath/${suiteName.getName}/")
      testSuites.sortBy(_.getName)
    }

    val passmarkSettings = loadPassmarkSettings
    logger.info(s"$prefix pass marks loaded = ${passmarkSettings.toString.substring(0, 600)}...<<truncated>>")
    val testSuites = loadTestSuites
    testSuites.foreach (ts => logger.info(s"$prefix testSuite loaded = $ts"))
    testSuites foreach (executeTestCases(_, passmarkSettings))
  }

  // Execute a single test suite file (which may consist of several test cases within it)
  private def executeTestCases(testSuite: File,
                               passmarks: AssessmentCentrePassMarkSettingsPersistence) = {
    logger.info(s"$prefix START: Processing test suite: ${testSuite.getAbsolutePath}")

    if (DebugRunTestSuitePathPatternOnly.isEmpty || testSuite.getAbsolutePath.contains(DebugRunTestSuitePathPatternOnly.get)) {
      val tests: List[AssessmentServiceTest] = loadTestCases(testSuite)
      tests foreach { t =>
        val testName = t.testName
        if (DebugRunTestNameOnly.isEmpty || testName == DebugRunTestNameOnly.get) {
          if (DebugRunTestNameOnly.isDefined) {
            logger.info(s"$prefix Tests are restricted to only running $testName")
          } else {
            logger.info(s"$prefix Tests are not restricted so all tests will run")
          }
          logger.info(s"$prefix Now running test case $testName...")
          logTestData(t)
          val appId = t.scores.applicationId.toString()
          createApplicationInDb(appId).futureValue

          val candidateData = AssessmentPassMarksSchemesAndScores(passmarks, t.schemes, t.scores)
          service.evaluateAssessmentCandidate(candidateData).futureValue

          val applicationId = t.scores.applicationId.toString()
          val actualResult = findApplicationInDb(t.scores.applicationId.toString()).futureValue
          logger.info(s"$prefix data read from db for appId $applicationId = $actualResult")

          val expectedResult = t.expected
          assert(testSuite, testName, expectedResult, actualResult)
        } else {
          logger.info(s"$prefix --> Skipped test case: $testName because we are only running <<${DebugRunTestNameOnly.getOrElse("")}>>")
        }
      }
      logger.info(s"$prefix END: Processed test cases: ${tests.size}")
    } else {
      logger.info(s"$prefix END: --> Skipped file: $testSuite")
    }
  }

  // Uses the ficus library to read the config into case classes
  private def loadTestCases(testCase: File): List[AssessmentServiceTest] = {
    val tests = ConfigFactory.parseFile(new File(testCase.getAbsolutePath)).as[List[AssessmentServiceTest]]("tests")
    logger.info(s"$prefix Found ${tests.length} test ${if(tests.length == 1) "case" else "cases"}")
    tests
  }

  private def logTestData(data: AssessmentServiceTest) = {
    logger.info(s"$prefix The following test data was read from config in ${data.testName}:")
    logger.info(s"$prefix schemes: List[SchemeId] = ${data.schemes}")
    logger.info(s"$prefix scores: AssessmentScoresAllExercises = ${data.scores}")
    logger.info(s"$prefix expected: AssessmentScoreEvaluationTestExpectation = ${data.expected}")
  }

  val appCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

  private def createApplicationInDb(appId: String) = Try(findApplicationInDb(appId).futureValue) match {
    case Success(_) =>
      val msg = s"Found application in database for applicationId $appId - this should not happen. Are you using a unique applicationId ?"
      throw new IllegalStateException(msg)
    case Failure(_) =>
      logger.info(s"$prefix creating db application")
      for {
        _ <- appCollection.insertOne(
          Document(
            "applicationId" -> appId,
            "userId" -> ("user" + appId)
          )
        ).toFuture()
        _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED)
      } yield ()
  }

  private def findApplicationInDb(appId: String): Future[ActualResult] = {
    import com.github.nscala_time.time.OrderingImplicits.DateTimeOrdering

    applicationRepo.collection.find[Document](Document("applicationId" -> appId)).headOption() map { docOpt =>
      require(docOpt.isDefined)
      val document = docOpt.get

      val applicationStatusOpt = document.get("applicationStatus").map { bson =>
        Codecs.fromBson[ApplicationStatus](bson)
      }
      val applicationStatus = applicationStatusOpt.get
      val progressStatusTimeStampDocOpt = document.get("progress-status-timestamp").map(_.asDocument())
      val latestProgressStatusOpt = progressStatusTimeStampDocOpt.flatMap { timestamps =>
        import scala.jdk.CollectionConverters._
        val convertedTimestamps = timestamps.entrySet().asScala.toSet
        val relevantProgressStatuses = convertedTimestamps.filter( _.getKey.startsWith(applicationStatus) )
        import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
        val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element => Codecs.fromBson[DateTime](timestamps.get(element.getKey)))
        Try(ProgressStatuses.nameToProgressStatus(latestRelevantProgressStatus.getKey)).toOption
      }

      val evaluationDocOpt = document.get("testGroups")
        .map(_.asDocument().get("FSAC"))
        .map(_.asDocument().get("evaluation"))
        .map(_.asDocument())

      val passmarkVersionOpt = evaluationDocOpt.map(_.get("passmarkVersion").asString().getValue)
      val competencyAverageOpt = evaluationDocOpt.map(bson => Codecs.fromBson[CompetencyAverageResult](bson.get("competency-average")))
      val schemesEvaluationOpt = evaluationDocOpt.map(bson => Codecs.fromBson[Seq[SchemeEvaluationResult]](bson.get("schemes-evaluation")))

      ActualResult(applicationStatusOpt, latestProgressStatusOpt, passmarkVersionOpt, competencyAverageOpt, schemesEvaluationOpt)
    }
  }

  private def assert(testCase: File, testName: String, expected: AssessmentScoreEvaluationTestExpectation, actual: ActualResult) = {

    val testMessage = s"file=${testCase.getAbsolutePath}, testName=$testName"
    val message = s"Test location: $testMessage:"
    logger.info(s"$prefix $message - now performing checks...")

    def doIt(dataName: String)(fun: => org.scalatest.Assertion) = {
      logger.info(s"$prefix $dataName check")
      // If the test fails, withClue will display a helpful message
      withClue(s"$message $dataName") {
        fun
      }
      logger.info(s"$prefix $dataName passed")
    }

    doIt("applicationStatus"){ actual.applicationStatus mustBe expected.applicationStatus }

    doIt("progressStatus"){ actual.progressStatus mustBe expected.progressStatus }

    doIt("passmarkVersion"){ actual.passmarkVersion mustBe expected.passmarkVersion }

    val actualSchemes = actual.schemesEvaluation.getOrElse(List()).map(x => (x.schemeId, x.result)).toMap
    val expectedSchemes = expected.allSchemesEvaluationExpectations.getOrElse(List()).map(x => (x.schemeId, x.result)).toMap

    val allSchemes = actualSchemes.keys ++ expectedSchemes.keys

    logger.info(s"$prefix schemesEvaluation check")
    allSchemes.foreach { s =>
      withClue(s"$message schemesEvaluation for scheme: $s") {
        actualSchemes(s) mustBe expectedSchemes(s)
      }
    }
    logger.info(s"$prefix schemesEvaluation passed")

    doIt("competencyAverage"){ actual.competencyAverageResult mustBe expected.competencyAverage }

    logger.info(s"$prefix competencyAverage overallScore check")
    withClue(s"$message competencyAverage overallScore") {
      expected.overallScore.foreach { overallScore =>
        actual.competencyAverageResult.get.overallScore mustBe overallScore
      }
    }
    logger.info(s"$prefix competencyAverage overallScore passed")
    logger.info(s"$prefix $testName passed")
  }
}

object AssessmentCentreServiceIntSpec {

  val TestPath = "it/resources/assessmentCentreServiceSpec"
  val PassmarkSettingsFile = "passmarkSettings.conf"
  val ConfigFiles = List(PassmarkSettingsFile)

  // This represents all the data read from config using the ficus library for a single test
  case class AssessmentServiceTest(
                                    testName: String,
                                    schemes: List[SchemeId],
                                    scores: AssessmentScoresAllExercises,
                                    expected: AssessmentScoreEvaluationTestExpectation
                                  )

  // Result we get back from the db after evaluation. Note that everything is an Option because
  // that is what get get back from Mongo (without calling get on the Option)
  case class ActualResult(
                           applicationStatus: Option[ApplicationStatus],
                           progressStatus: Option[ProgressStatuses.ProgressStatus],
                           passmarkVersion: Option[String],
                           competencyAverageResult: Option[CompetencyAverageResult],
                           schemesEvaluation: Option[Seq[SchemeEvaluationResult]]
                         )
}
