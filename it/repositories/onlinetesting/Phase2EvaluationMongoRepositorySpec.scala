package repositories.onlinetesting

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.persisted._
import model.{ApplicationRoute, ApplicationStatus, ProgressStatuses, SchemeId}
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatestplus.mockito.MockitoSugar
import repositories.{CollectionNames, CommonRepository}
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

class Phase2EvaluationMongoRepositorySpec extends MongoRepositorySpec with CommonRepository with MockitoSugar {

  import Phase1EvaluationMongoRepositorySpec._
  import Phase2EvaluationMongoRepositorySpec._

  val collectionName: String = CollectionNames.APPLICATION

  "dynamically specified evaluation application statuses collection" should {
    "contain the expected phases that result in evaluation running" in {
      phase2EvaluationRepo.evaluationApplicationStatuses mustBe Set(
        ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER
      )
    }
  }

  "next Application Ready For Evaluation" should {
    val resultToSave = List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString))

    "return nothing if application does not have PHASE2_TESTS" in {
      insertApplication2("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1Tests))
      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return application in PHASE2_TESTS with results" in {
      val phase1Evaluation = PassmarkEvaluation(
        "phase1_version1", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation)
      )

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase1_version1", batchSize = 1).futureValue
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE2_TESTS,
        ApplicationRoute.Faststream,
        isGis = false,
        activePsiTests = Phase2TestGroup(now, phase2TestWithResult).activeTests,
        activeLaunchpadTest = None,
        prevPhaseEvaluation = Some(phase1Evaluation),
        selectedSchemes(List(SchemeId("Commercial")))
      )
    }

    "return application in PHASE2_TESTS with results when applicationRoute is not set" in {
      val phase1Evaluation = PassmarkEvaluation(
        "phase1_version1", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation), applicationRoute = None)

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase1_version1", batchSize = 1).futureValue

      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE2_TESTS,
        ApplicationRoute.Faststream,
        isGis = false,
        activePsiTests = Phase2TestGroup(now, phase2TestWithResult).activeTests,
        activeLaunchpadTest = None,
        prevPhaseEvaluation = Some(phase1Evaluation),
        selectedSchemes(List(SchemeId("Commercial")))
      )
    }

    "return nothing when PHASE2_TESTS are already evaluated" in {
      val phase1Evaluation = PassmarkEvaluation(
        "phase1_version1", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation))

      val phase2Evaluation = PassmarkEvaluation(
        "phase2_version1", Some("phase1_version1"), resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      phase2EvaluationRepo.savePassmarkEvaluation("app1", phase2Evaluation, newProgressStatus = None).futureValue

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return nothing when PHASE2_TESTS have expired" in {
      val phase1Evaluation = PassmarkEvaluation(
        "phase1_version1", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation),
        additionalProgressStatuses = List(ProgressStatuses.PHASE2_TESTS_EXPIRED -> true))

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase1_version1", batchSize = 1).futureValue

      result mustBe empty
    }

    "return evaluated application in PHASE2_TESTS when phase2 pass mark settings changed" in {
      val phase1Evaluation = PassmarkEvaluation(
        "phase1_version1", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation))

      val phase2Evaluation = PassmarkEvaluation(
        "phase2_version1", Some("phase1_version1"), resultToSave, "phase2-version1-res", previousPhaseResultVersion = None
      )
      phase2EvaluationRepo.savePassmarkEvaluation("app1", phase2Evaluation, newProgressStatus = None).futureValue

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version2", batchSize = 1).futureValue
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE2_TESTS,
        ApplicationRoute.Faststream,
        isGis = false,
        Phase2TestGroup(now, phase2TestWithResult).activeTests,
        activeLaunchpadTest = None,
        Some(phase1Evaluation),
        selectedSchemes(List(SchemeId("Commercial")))
      )
    }

    "return evaluated application in PHASE2_TESTS status when phase1 results are re-evaluated" in {
      val phase1Evaluation = PassmarkEvaluation(
        "phase1_version2", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation))

      val phase2Evaluation = PassmarkEvaluation(
        "phase2_version1", Some("phase1_version1"), resultToSave, "phase2-version1-res", previousPhaseResultVersion = None
      )
      phase2EvaluationRepo.savePassmarkEvaluation("app1", phase2Evaluation, newProgressStatus = None).futureValue

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version1", batchSize = 1).futureValue
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE2_TESTS,
        ApplicationRoute.Faststream,
        isGis = false,
        Phase2TestGroup(now, phase2TestWithResult).activeTests,
        activeLaunchpadTest = None,
        prevPhaseEvaluation = Some(phase1Evaluation),
        selectedSchemes(List(SchemeId("Commercial"))))
    }

    "limit number of next applications to the batch size limit" in {
      val batchSizeLimit = 5
      1 to 6 foreach { id =>
        val phase1Evaluation = PassmarkEvaluation(
          "phase1_version1", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
        )
        insertApplication2(s"app$id", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
          Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation))
      }
      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version1", batchSizeLimit).futureValue
      result.size mustBe batchSizeLimit
    }

    "return less number of applications than batch size limit" in {
      val batchSizeLimit = 5
      1 to 2 foreach { id =>
        val phase1Evaluation = PassmarkEvaluation(
          "phase1_version1", previousPhasePassMarkVersion = None, resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
        )
        insertApplication2(s"app$id", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
          Some(phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation))
      }
      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSizeLimit).futureValue
      result.size mustBe 2
    }
  }

  "save passmark evaluation" should {
    val resultToSave = List(SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString))

    "save result and update the status" in {
      insertApplication2("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult))
      val evaluation = PassmarkEvaluation(
        "version1", previousPhasePassMarkVersion = None, resultToSave, "version1-res", previousPhaseResultVersion = None
      )

      phase2EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ProgressStatuses.PHASE2_TESTS_PASSED)).futureValue

      val resultWithAppStatus = getOnePhase2Profile("app1")
      resultWithAppStatus mustBe defined

      val (appStatus, result) = resultWithAppStatus.get
      appStatus mustBe ApplicationStatus.PHASE2_TESTS_PASSED

      result.evaluation mustBe Some(PassmarkEvaluation("version1", previousPhasePassMarkVersion = None,
        List(SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString)),
        "version1-res", previousPhaseResultVersion = None
      ))
    }
  }

  private def getOnePhase2Profile(appId: String) = {
    applicationCollection.find(Document("applicationId" -> appId)).headOption.map( _.map { doc =>
      val applicationStatusBsonValue = doc.get("applicationStatus").get
      val applicationStatus = Codecs.fromBson[ApplicationStatus](applicationStatusBsonValue)

      val bsonPhase2 = doc.get("testGroups").map(_.asDocument().get("PHASE2").asDocument() )
      val phase2 = bsonPhase2.map( bson => Codecs.fromBson[Phase2TestGroup](bson) ).get
      applicationStatus -> phase2
    }).futureValue
  }
}

object Phase2EvaluationMongoRepositorySpec {
  val now = DateTime.now().withZone(DateTimeZone.UTC)
  val phase2Test = List(PsiTest(
    inventoryId = "test-inventoryId",
    orderId = "test-orderId",
    usedForResults = true, // TODO: mongo note i had to change this to true for the test to pass - investigate how it worked with false
    testUrl = "http://localhost",
    invitationDate = now,
    assessmentId = "test-assessmentId",
    reportId = "test-reportId",
    normId = "test-normId"
  ))

  val phase2TestWithResult = phase2TestWithResults(PsiTestResult(tScore = 20.5d, rawScore = 10.0d, testReportUrl = None))

  def phase2TestWithResults(testResult: PsiTestResult) = {
    phase2Test.map(t => t.copy(testResult = Some(testResult)))
  }
}
