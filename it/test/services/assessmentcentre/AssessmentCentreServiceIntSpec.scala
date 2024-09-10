/*
 * Copyright 2024 HM Revenue & Customs
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

package services.assessmentcentre

import com.typesafe.config.{Config, ConfigFactory}
import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.EvaluationResults.ExerciseAverageResult
import model._
import model.assessmentscores._
import model.exchange.passmarksettings.{AssessmentCentrePassMarkSettings, AssessmentCentrePassMarkSettingsPersistence}
import model.persisted.SchemeEvaluationResult
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
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
import java.time.OffsetDateTime
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
    mock[SchemeRepository],
    new AssessmentCentreEvaluationEngineImpl
  )

  val collectionName = CollectionNames.APPLICATION
  // Use this when debugging so the test framework only runs one test scenario. The tests will still be loaded, however
  val DebugRunTestNameOnly: Option[String] = None
//      val DebugRunTestNameOnly: Option[String] = Some("multipleSchemesSuite_Mix_Scenario1")
  // Use this when debugging so the test framework only runs tests which contain the specified test suite name in their path
  // the tests will still be loaded, however
  val DebugRunTestSuitePathPatternOnly: Option[String] = None
  //  val DebugRunTestSuitePathPatternOnly: Option[String] = Some("2_multipleSchemesSuite/")

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

  implicit val offsetDateTimeReader: ValueReader[OffsetDateTime] = new ValueReader[OffsetDateTime] {
    def read(config: Config, path: String): OffsetDateTime = config.getValue(path).valueType match {
      case _ => OffsetDateTime.now
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

      val source = Source.fromFile(passmarkSettingsFile)
      try {
        val passmarkSettingsJson = Json.parse(source.getLines().mkString)
        passmarkSettingsJson.as[AssessmentCentrePassMarkSettings].toPersistence
      } finally {
        source.close()
      }
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
          logger.info(s"$prefix")
          logger.info(s"$prefix Now running test case $testName...")
          logger.info(s"$prefix")
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
//    import com.github.nscala_time.time.OrderingImplicits.DateTimeOrdering

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
        import repositories.formats.MongoJavatimeFormats.Implicits._
        val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element =>
          Codecs.fromBson[OffsetDateTime](timestamps.get(element.getKey))
        )
        Try(ProgressStatuses.nameToProgressStatus(latestRelevantProgressStatus.getKey)).toOption
      }

      val evaluationDocOpt = document.get("testGroups")
        .map(_.asDocument().get("FSAC"))
        .map(_.asDocument().get("evaluation"))
        .map(_.asDocument())

      val passmarkVersionOpt = evaluationDocOpt.map(_.get("passmarkVersion").asString().getValue)
      val exerciseAverageOpt = evaluationDocOpt.map(bson => Codecs.fromBson[ExerciseAverageResult](bson.get("exercise-average")))
      val schemesEvaluationOpt = evaluationDocOpt.map(bson => Codecs.fromBson[Seq[SchemeEvaluationResult]](bson.get("schemes-evaluation")))

      ActualResult(
        applicationStatusOpt, latestProgressStatusOpt, passmarkVersionOpt, exerciseAverageOpt, schemesEvaluationOpt
      )
    }
  }

  //scalastyle:off method.length
  private def assert(testCase: File, testName: String, expected: AssessmentScoreEvaluationTestExpectation, actual: ActualResult) = {

    val testMessage = s"file=${testCase.getAbsolutePath}, testName=$testName"
    val message = s"Test location: $testMessage:"
    logger.info(s"$prefix $message - NOW PERFORMING CHECKS...")

    def performCheck(dataName: String)(fun: => org.scalatest.Assertion) = {
      logger.info(s"$prefix $dataName check")
      // If the test fails, withClue will display a helpful message
      withClue(s"$message $dataName") {
        fun
      }
      logger.info(s"$prefix $dataName passed")
    }

    performCheck("applicationStatus"){
      logger.info(s"$prefix comparing actual ${actual.applicationStatus} equals expected ${expected.applicationStatus}")
      actual.applicationStatus mustBe expected.applicationStatus
    }

    performCheck("progressStatus"){
      logger.info(s"$prefix comparing actual ${actual.progressStatus} equals expected ${expected.progressStatus}")
      actual.progressStatus mustBe expected.progressStatus
    }

    performCheck("passmarkVersion"){
      logger.info(s"$prefix comparing actual ${actual.passmarkVersion} equals expected ${expected.passmarkVersion}")
      actual.passmarkVersion mustBe expected.passmarkVersion
    }

    val actualSchemes = actual.schemesEvaluation.getOrElse(List()).map(x => (x.schemeId, x.result)).toMap
    val expectedSchemes = expected.allSchemesEvaluationExpectations.getOrElse(List()).map(x => (x.schemeId, x.result)).toMap

    val allSchemes = actualSchemes.keys ++ expectedSchemes.keys

    logger.info(s"$prefix schemesEvaluation check")
    allSchemes.foreach { s =>
      withClue(s"$message schemesEvaluation for scheme: $s") {
        logger.info(s"$prefix ${s.toString} scheme comparing actual ${actualSchemes(s)} equals expected ${expectedSchemes(s)}")
        actualSchemes(s) mustBe expectedSchemes(s)
      }
    }
    logger.info(s"$prefix schemesEvaluation passed")

    performCheck("exerciseAverages"){
      logger.info(s"$prefix comparing actual ${actual.exerciseAverageResult} equals expected ${expected.exerciseAverage}")
      actual.exerciseAverageResult mustBe expected.exerciseAverage
    }

    logger.info(s"$prefix exerciseAverage overallScore check")
    withClue(s"$message exerciseAverage overallScore") {
      expected.exerciseOverallScore.foreach { overallScore =>
        logger.info(
          s"$prefix comparing actual ${actual.exerciseAverageResult.get.overallScore} equals expected ${expected.exerciseOverallScore}"
        )
        actual.exerciseAverageResult.get.overallScore mustBe overallScore
      }
    }
    logger.info(s"$prefix exerciseAverage overallScore passed")

    logger.info(s"$prefix $testName PASSED $prefix")
  } //scalastyle:on
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
                           exerciseAverageResult: Option[ExerciseAverageResult],
                           schemesEvaluation: Option[Seq[SchemeEvaluationResult]]
                         )
}
