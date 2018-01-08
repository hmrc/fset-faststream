package services.assessmentcentre

import java.io.File

import com.typesafe.config.{ Config, ConfigFactory }
import config.AssessmentEvaluationMinimumCompetencyLevel
import model.ApplicationStatus._
import model.EvaluationResults.CompetencyAverageResult
import model._
import model.assessmentscores._
import model.exchange.passmarksettings.AssessmentCentrePassMarkSettings
import model.persisted.SchemeEvaluationResult
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import org.joda.time.DateTime
import play.Logger
import play.api.libs.json.Json
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories._
import services.evaluation.AssessmentCentreEvaluationEngine
import services.passmarksettings.PassMarkSettingsService
import testkit.MongoRepositorySpec

import scala.io.Source
import scala.util.{ Failure, Success, Try }

class AssessmentCentreServiceSpec extends MongoRepositorySpec {

  import AssessmentCentreServiceSpec._

  lazy val service = new AssessmentCentreService {
    val applicationRepo = repositories.applicationRepository
    val assessmentCentreRepo = repositories.assessmentCentreRepository
    val passmarkService = mock[PassMarkSettingsService[AssessmentCentrePassMarkSettings]]
    val assessmentScoresRepo = mock[AssessmentScoresRepository]
    val evaluationEngine = AssessmentCentreEvaluationEngine
  }

  val collectionName = CollectionNames.APPLICATION
  // Use this when debugging so the test framework only runs one test scenario. The tests will still be loaded, however
  val DebugRunTestNameOnly: Option[String] = None
//  val DebugRunTestNameOnly: Option[String] = Some("oneSchemeWithMclSuite_Amber_Scenario1")
  // Use this when debugging so the test framework only runs tests which contain the specified test suite name in their path
  // the tests will still be loaded, however
  val DebugRunTestSuitePathPatternOnly: Option[String] = None
//  val DebugRunTestSuitePathPatternOnly: Option[String] = Some("2_oneSchemeWithMclSuite/")

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
      loadSuites foreach executeSuite
    }
  }

  "Debug variables" should {
    "must be disabled" ignore {
      DebugRunTestNameOnly mustBe empty
      DebugRunTestSuitePathPatternOnly mustBe empty
    }
  }

  private def loadSuites: Array[File] = {
    val suites = new File(TestPath).listFiles sortBy(_.getName)
    require(suites.nonEmpty, s"No test suites found in $TestPath")
//    Logger.info(s"$prefix suites = ${suites.mkString(",")}")
    suites.foreach( s => Logger.info(s"$prefix test suite loaded = $s" ) )
    suites
  }

  private def executeSuite(suiteName: File) = {
    Logger.info(s"$prefix executing suite name = $suiteName...")

    // Reads the passmarkSettings.conf file
    def loadPassmarkSettings: AssessmentCentrePassMarkSettings = {
      val passmarkSettingsFile = new File(suiteName.getAbsolutePath + "/" + PassmarkSettingsFile)

      require(passmarkSettingsFile.exists(), s"File does not exist: ${passmarkSettingsFile.getAbsolutePath}")
      val passmarkSettingsJson = Json.parse(Source.fromFile(passmarkSettingsFile).getLines().mkString)
      passmarkSettingsJson.as[AssessmentCentrePassMarkSettings]
    }

    // Reads the mcl.conf file
    def loadMcl: AssessmentEvaluationMinimumCompetencyLevel = {
      val configFile = new File(suiteName.getAbsolutePath + "/" + MclSettingsFile)
      require(configFile.exists(), s"File does not exist: ${configFile.getAbsolutePath}")
      val configJson = Json.parse(Source.fromFile(configFile).getLines().mkString)
      configJson.as[AssessmentEvaluationMinimumCompetencyLevel]
    }

    // Returns all files except the config files (mcl.conf and passmarkSettings.conf)
    def loadTestCases: Array[File] = {
      val testCases = new File(s"$TestPath/${suiteName.getName}/")
        .listFiles
        .filterNot(f => ConfigFiles.contains(f.getName)) // exclude mcl.conf and passmarkSettings.conf
        .sortBy(_.getName)
      require(testCases.nonEmpty, s"No test cases found to execute in $TestPath/${suiteName.getName}/")
      testCases.sortBy(_.getName)
    }

    val passmarkSettings = loadPassmarkSettings
    Logger.info(s"$prefix pass marks loaded = $passmarkSettings")
    val mcl = loadMcl
    Logger.info(s"$prefix mcl loaded = $mcl")
    val testCases = loadTestCases
    testCases.foreach(tc => Logger.info(s"$prefix testCase loaded = $tc"))
    testCases foreach (executeTestCases(_, loadMcl, passmarkSettings))
  }

  // Execute a single test case file (which may consist of several test cases within it)
  private def executeTestCases(
    testCase: File,
    config: AssessmentEvaluationMinimumCompetencyLevel,
    passmarks: AssessmentCentrePassMarkSettings) = {
    Logger.info(s"$prefix Processing test case: ${testCase.getAbsolutePath}")

    if (DebugRunTestSuitePathPatternOnly.isEmpty || testCase.getAbsolutePath.contains(DebugRunTestSuitePathPatternOnly.get)) {
      val tests: List[AssessmentServiceTest] = loadTestCases(testCase)
      tests foreach { t =>
        logTestData(t)
        val testName = t.testName
        Logger.info(s"$prefix Loading test: $testName")
        if (DebugRunTestNameOnly.isEmpty || testName == DebugRunTestNameOnly.get) {
          Logger.info(s"$prefix Now running test case $testName...")
          val appId = t.scores.applicationId.toString()
          createApplicationInDb(appId)
          Logger.info(s"$prefix created application in db")

          val candidateData = AssessmentPassMarksSchemesAndScores(passmarks, t.schemes, t.scores)
          service.evaluateAssessmentCandidate(candidateData, config).futureValue

          val actualResult = findApplicationInDb(t.scores.applicationId.toString())
          Logger.info(s"$prefix data read from db = $actualResult")

          val expectedResult = t.expected
          assert(testCase, testName, expectedResult, actualResult)
        } else {
          Logger.info(s"$prefix --> Skipped test case")
        }
      }
      Logger.info(s"$prefix Executed test cases: ${tests.size}")
    } else {
      Logger.info(s"$prefix --> Skipped file: $testCase")
    }
  }

  // Uses the ficus library to read the config into case classes
  private def loadTestCases(testCase: File): List[AssessmentServiceTest] = {
    val tests = ConfigFactory.parseFile(new File(testCase.getAbsolutePath)).as[List[AssessmentServiceTest]]("tests")
    Logger.info(s"$prefix Found ${tests.length} ${if(tests.length == 1) "test case" else "test cases"}")
    tests
  }

  private def logTestData(data: AssessmentServiceTest) = {
    Logger.info(s"$prefix Test data read from config:")
    Logger.info(s"$prefix test case name = ${data.testName}")
    Logger.info(s"$prefix schemes: List[SchemeId] = ${data.schemes}")
    Logger.info(s"$prefix scores: AssessmentScoresAllExercises = ${data.scores}")
    Logger.info(s"$prefix expected: AssessmentScoreEvaluationTestExpectation = ${data.expected}")
  }

  // Import required for mongo db interaction
  import ImplicitBSONHandlers._
  def createApplicationInDb(appId: String) = Try(findApplicationInDb(appId)) match {
    case Success(_) =>
      val msg = s"Found application in database for applicationId $appId - this should not happen. Are you using a unique applicationId?"
      throw new IllegalStateException(msg)
    case Failure(_) =>
      Logger.info(s"$prefix creating db application")
      applicationRepository.collection.insert(
        BSONDocument(
          "applicationId" -> appId,
          "userId" -> ("user" + appId)
        )
      ).futureValue
      for {
        - <- applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED)
      } yield ()
  }

  private def findApplicationInDb(appId: String): ActualResult = {
    import com.github.nscala_time.time.OrderingImplicits.DateTimeOrdering
    import repositories.BSONDateTimeHandler

    repositories.applicationRepository.collection.find(BSONDocument("applicationId" -> appId)).one[BSONDocument].map { docOpt =>
      require(docOpt.isDefined)
      val document = docOpt.get

      val applicationStatusOpt = document.getAs[ApplicationStatus]("applicationStatus")
      val applicationStatus = applicationStatusOpt.get
      val progressStatusTimeStampDocOpt = document.getAs[BSONDocument]("progress-status-timestamp")
      val latestProgressStatusOpt = progressStatusTimeStampDocOpt.flatMap { timestamps =>
        val relevantProgressStatuses = timestamps.elements.filter(_._1.startsWith(applicationStatus))
        val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element => timestamps.getAs[DateTime](element._1).get)
        Try(ProgressStatuses.nameToProgressStatus(latestRelevantProgressStatus._1)).toOption
      }

      val evaluationDocOpt = (for {
        testGroupsOpt <- document.getAs[BSONDocument]("testGroups")
        fsacOpt <- testGroupsOpt.getAs[BSONDocument]("FSAC")
        evaluationOpt <- fsacOpt.getAs[BSONDocument]("evaluation")
      } yield evaluationOpt).get

      val passmarkVersionOpt = for {
        passmarkVersionOpt <- evaluationDocOpt.getAs[String]("passmarkVersion")
      } yield passmarkVersionOpt

      val passedMinimumCompetencyLevelOpt = for {
        passedMinimumCompetencyLevelOpt <- evaluationDocOpt.getAs[Boolean]("passedMinimumCompetencyLevel")
      } yield passedMinimumCompetencyLevelOpt

      val competencyAverageOpt = for {
        competencyAverageOpt <- evaluationDocOpt.getAs[CompetencyAverageResult]("competency-average")
      } yield competencyAverageOpt

      val schemesEvaluationOpt = for {
        schemesEvaluationOpt <- evaluationDocOpt.getAs[Seq[SchemeEvaluationResult]]("schemes-evaluation")
      } yield schemesEvaluationOpt

      ActualResult(applicationStatusOpt, latestProgressStatusOpt, passedMinimumCompetencyLevelOpt,
        passmarkVersionOpt, competencyAverageOpt, schemesEvaluationOpt)
    }.futureValue
  }

  private def assert(testCase: File, testName: String, expected: AssessmentScoreEvaluationTestExpectation, actual: ActualResult) = {

    val testMessage = s"file=${testCase.getAbsolutePath}, testName=$testName"
    val message = s"Test location: $testMessage:"

    def doIt(dataName: String)(fun: => org.scalatest.Assertion) = {
      Logger.info(s"$prefix $message $dataName check")
      // If the test fails, withClue will display a helpful message
      withClue(s"$message $dataName") {
        fun
      }
      Logger.info(s"$prefix passed")
    }

    doIt("applicationStatus"){ actual.applicationStatus mustBe expected.applicationStatus }

    doIt("progressStatus"){ actual.progressStatus mustBe expected.progressStatus }

    doIt("passmarkVersion"){ actual.passmarkVersion mustBe expected.passmarkVersion }

    doIt("minimumCompetencyLevel"){ actual.passedMinimumCompetencyLevel mustBe expected.passedMinimumCompetencyLevel }

    val actualSchemes = actual.schemesEvaluation.getOrElse(List()).map(x => (x.schemeId, x.result)).toMap
    val expectedSchemes = expected.allSchemesEvaluationExpectations.getOrElse(List()).map(x => (x.schemeId, x.result)).toMap

    val allSchemes = actualSchemes.keys ++ expectedSchemes.keys

    Logger.info(s"$prefix $message schemesEvaluation check")
    allSchemes.foreach { s =>
      withClue(s"$message schemesEvaluation for scheme: $s") {
        actualSchemes(s) mustBe expectedSchemes(s)
      }
    }
    Logger.info(s"$prefix passed")

    doIt("competencyAverage"){ actual.competencyAverageResult mustBe expected.competencyAverage }

    Logger.info(s"$prefix $message competencyAverage overallScore check")
    withClue(s"$message competencyAverage overallScore") {
      expected.overallScore.foreach { overallScore =>
        actual.competencyAverageResult.get.overallScore mustBe overallScore
      }
    }
    Logger.info(s"$prefix passed")
  }
}

object AssessmentCentreServiceSpec {

  val TestPath = "it/resources/assessmentCentreServiceSpec"
  val PassmarkSettingsFile = "passmarkSettings.conf"
  val MclSettingsFile = "mcl.conf"
  val ConfigFiles = List(PassmarkSettingsFile, MclSettingsFile)

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
    // This is only stored if mcl is enabled so expect to find it missing if mcl is disabled
    passedMinimumCompetencyLevel: Option[Boolean] = None,
    passmarkVersion: Option[String],
    competencyAverageResult: Option[CompetencyAverageResult],
    schemesEvaluation: Option[Seq[SchemeEvaluationResult]]
  )
}
