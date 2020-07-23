package repositories.onlinetesting

import config.{ LaunchpadGatewayConfig, Phase3TestsConfig }
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.Exceptions.PassMarkEvaluationNotFound
import model.persisted._
import model.persisted.phase3tests.Phase3TestGroup
import model.{ ApplicationStatus, ProgressStatuses, SchemeId }
import org.mockito.Mockito.when
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

class Phase3EvaluationMongoRepositorySpec extends MongoRepositorySpec with CommonRepository {

  import ImplicitBSONHandlers._
  import Phase2EvaluationMongoRepositorySpec._
  import model.Phase3TestProfileExamples._

  // Create the data with callbacks that were received 72 + 1 hours ago so they are before the 72 hour wait
  // time defined in config below. Note that this implicit is needed for the tests
  implicit val hrsBeforeLastReviewed = 72 + 1

  override val mockLaunchpadConfig = {
    LaunchpadGatewayConfig(
      url = "",
      Phase3TestsConfig(
        timeToExpireInDays = 0, invigilatedTimeToExpireInDays = 0, gracePeriodInSecs = 0, candidateCompletionRedirectUrl = "",
        interviewsByAdjustmentPercentage = Map.empty[String, Int], evaluationWaitTimeAfterResultsReceivedInHours = 72,
        verifyAllScoresArePresent = false
      )
    )
  }

  when(mockAppConfig.launchpadGatewayConfig).thenReturn(mockLaunchpadConfig)

  val collectionName: String = CollectionNames.APPLICATION

  "dynamically specified evaluation application statuses collection" should {
    "contain the expected phases that result in evaluation running" in {
      phase3EvaluationRepo.evaluationApplicationStatuses mustBe Set(
        ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER
      )
    }
  }

  "next Application Ready For Evaluation" must {
    val resultToSave = List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString))

    "return nothing if application does not have PHASE3_TESTS" in {
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, phase1Tests = None, Some(phase2Test))
      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation2("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return application in PHASE3_TESTS with results" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version1", previousPhasePassMarkVersion = None, resultToSave,
        "phase2_version1-res", previousPhaseResultVersion = None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation2("phase2_version1", batchSize = 1).futureValue
      assertApplication(result.head, phase2Evaluation)
    }

    "return nothing when PHASE3_TESTS are already evaluated" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version1-res", None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))

      val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
        "phase3_version1-res", Some("phase2_version1-res"))
      phase3EvaluationRepo.savePassmarkEvaluation("app1", phase3Evaluation, None).futureValue

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation("phase3_version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return evaluated application in PHASE3_TESTS_PASSED_WITH_AMBER when phase3 pass mark settings changed" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version1-res", None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))

      val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
        "phase3-version1-res", Some("phase2-version1-res"))
      phase3EvaluationRepo.savePassmarkEvaluation("app1", phase3Evaluation, None).futureValue

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation2("phase3_version2", batchSize = 1).futureValue
      assertApplication(result.head, phase2Evaluation, ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER)
    }

    "return evaluated application in PHASE3_TESTS when phase3 pass mark settings changed" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version1-res", None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))

      val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
        "phase3_version1-res", Some("phase2_version1-res"))
      phase3EvaluationRepo.savePassmarkEvaluation("app1", phase3Evaluation, None).futureValue

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation2("phase3_version2", batchSize = 1).futureValue
      assertApplication(result.head, phase2Evaluation)
    }

    "return nothing when phase3 test results are not reviewed before 72 hours" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version1-res", None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult(10)), phase2Evaluation = Some(phase2Evaluation))

      val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
        "phase3_version1-res", Some("phase2_version1-res"))
      phase3EvaluationRepo.savePassmarkEvaluation("app1", phase3Evaluation, None).futureValue

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation("phase3_version2", batchSize = 1).futureValue
      result mustBe empty
    }

    "return evaluated application in PHASE3_TESTS status when phase2 results are re-evaluated because pass mark versions are different" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version2", None, resultToSave, "phase2_version2-res", None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))

      val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave, "phase3_version1-res", None)
      phase3EvaluationRepo.savePassmarkEvaluation("app1", phase3Evaluation, None).futureValue

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation2("phase3_version1", batchSize = 1).futureValue
      assertApplication(result.head, phase2Evaluation)
    }

    "return evaluated application in PHASE3_TESTS status when phase2 results are re-evaluated because result versions are different" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version2-res", None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))

      val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
        "phase3_version1-res", Some("phase2_version1-res"))
      phase3EvaluationRepo.savePassmarkEvaluation("app1", phase3Evaluation, None).futureValue

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation2("phase3_version1", batchSize = 1).futureValue

      assertApplication(result.head, phase2Evaluation)
    }

    "return nothing when phase3 test results are expired" in {
      val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version1-res", None)
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
        Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation),
        additionalProgressStatuses = List(ProgressStatuses.PHASE3_TESTS_EXPIRED -> true))

      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "limit number of next applications to the batch size limit" in {
      val batchSizeLimit = 5
      1 to 6 foreach { id =>
        val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version1_res", None)
        insertApplication2(s"app$id", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
          Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))
      }
      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation("phase3_version1", batchSizeLimit).futureValue
      result.size mustBe batchSizeLimit
    }

    "return less number of applications than batch size limit" in {
      val batchSizeLimit = 5
      1 to 2 foreach { id =>
        val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version1-res", None)
        insertApplication2(s"app$id", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult),
          Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))
      }
      val result = phase3EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSizeLimit).futureValue
      result.size mustBe 2
    }
  }

  "save passmark evaluation" must {
    val resultToSave = List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))

    "save result and update the status" in {
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult), Some(phase3TestWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)

      phase3EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ProgressStatuses.PHASE3_TESTS_PASSED)).futureValue

      val resultWithAppStatus = getOnePhase3Profile("app1")
      resultWithAppStatus mustBe defined
      val (appStatus, result) = resultWithAppStatus.get
      appStatus mustBe ApplicationStatus.PHASE3_TESTS_PASSED
      result.evaluation mustBe Some(PassmarkEvaluation("version1", None, List(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString)
      ), "version1-res", None))
    }
  }

  "retrieve passmark evaluation" must {
    val resultToSave = List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))

    "return passmarks from mongo" in {
      insertApplication2("app1", ApplicationStatus.PHASE3_TESTS, phase1Tests = None, Some(phase2TestWithResult), Some(phase3TestWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)

      phase3EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ProgressStatuses.PHASE3_TESTS_PASSED)).futureValue

      val results = phase3EvaluationRepo.getPassMarkEvaluation("app1").futureValue
      results mustBe evaluation
    }

    "return an appropriate exception when no passmarks are found" in {
      val results = phase3EvaluationRepo.getPassMarkEvaluation("app2").failed.futureValue
      results mustBe a[PassMarkEvaluationNotFound]
    }
  }

  private def assertApplication(application: ApplicationReadyForEvaluation2, phase2Evaluation: PassmarkEvaluation,
                                expectedApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE3_TESTS) = {
    application.applicationId mustBe "app1"
    application.applicationStatus mustBe expectedApplicationStatus
    application.isGis mustBe false
    application.activePsiTests mustBe Nil
    application.activeLaunchpadTest.isDefined mustBe true
    application.prevPhaseEvaluation mustBe Some(phase2Evaluation)
    application.preferences mustBe selectedSchemes(List(SchemeId("Commercial")))
  }

  private def getOnePhase3Profile(appId: String) = {
    phase3EvaluationRepo.collection.find(BSONDocument("applicationId" -> appId), projection = Option.empty[JsObject])
      .one[BSONDocument].map(_.map { doc =>
      val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
      val bsonPhase3 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument]("PHASE3"))
      val phase3 = bsonPhase3.map(Phase3TestGroup.bsonHandler.read).get
      (applicationStatus, phase3)
    }).futureValue
  }
}
