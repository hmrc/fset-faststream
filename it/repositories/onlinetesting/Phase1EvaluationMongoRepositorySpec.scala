package repositories.onlinetesting

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{Amber, Green, Red}
import model.persisted._
import model.{ApplicationRoute, ApplicationStatus, ProgressStatuses, SchemeId}
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.mongo.play.json.Codecs
import repositories.{CollectionNames, CommonRepository}
import testkit.MongoRepositorySpec

class Phase1EvaluationMongoRepositorySpec extends MongoRepositorySpec with CommonRepository with MockitoSugar {

  import Phase1EvaluationMongoRepositorySpec._

  val collectionName: String = CollectionNames.APPLICATION

  val cyberSecurity = SchemeId("CyberSecurity")

  "dynamically specified evaluation application statuses collection" should {
    "contain the expected phases that result in evaluation running" in {
      phase1EvaluationRepo.evaluationApplicationStatuses mustBe Set(
        ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_PASSED,
        ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS, ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER
      )
    }
  }

  "next Application Ready For Evaluation" should {
    "return nothing if there is no PHASE1_TESTS applications" in {
      insertApplication("appId1", ApplicationStatus.SUBMITTED)
      insertApplication("appId2", ApplicationStatus.PHASE2_TESTS)
      insertApplication("appId3", ApplicationStatus.PHASE3_TESTS)
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return nothing if application does not have online exercise results" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1Tests))
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return application in PHASE1_TESTS with results" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue

      result must not be empty

      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE1_TESTS,
        ApplicationRoute.Faststream,
        isGis = false,
        Phase1TestProfile(now, phase1TestsWithResult).activeTests,
        activeLaunchpadTest = None,
        prevPhaseEvaluation = None,
        selectedSchemes(List(SchemeId("Commercial")))
      )
    }

    "return GIS application in PHASE1_TESTS with results" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult), isGis = true)

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue

      result must not be empty
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE1_TESTS,
        ApplicationRoute.Faststream,
        isGis = true,
        Phase1TestProfile(now, phase1TestsWithResult).activeTests,
        activeLaunchpadTest = None,
        prevPhaseEvaluation = None,
        selectedSchemes(List(SchemeId("Commercial"))))
    }

    "return nothing when PHASE1_TESTS have expired" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        additionalProgressStatuses = List(ProgressStatuses.PHASE1_TESTS_EXPIRED -> true))

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "limit number of next applications to the batch size limit" in {
      val batchSizeLimit = 5
      1 to 6 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      }
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSizeLimit).futureValue
      result.size mustBe batchSizeLimit
    }

    "return fewer applications than batch size limit" in {
      val batchSizeLimit = 5
      1 to 2 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      }
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSizeLimit).futureValue
      result.size mustBe 2
    }
  }

  "save passmark evaluation" should {
    val resultToSave = List(SchemeEvaluationResult(cyberSecurity, Green.toString))

    "save result and update the status" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)

      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ProgressStatuses.PHASE1_TESTS_PASSED)).futureValue

      val resultWithAppStatus = getOnePhase1Profile("app1")

      resultWithAppStatus mustBe defined
      val (appStatus, result) = resultWithAppStatus.get
      appStatus mustBe ApplicationStatus.PHASE1_TESTS_PASSED
      result.evaluation mustBe Some(PassmarkEvaluation("version1", previousPhasePassMarkVersion = None, List(
        SchemeEvaluationResult(cyberSecurity, Green.toString)
      ), "version1-res", previousPhaseResultVersion = None))
    }

    "return nothing when candidate has been already evaluated" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return the candidate in PHASE1_TESTS if the passmark has changed" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version2", batchSize = 1).futureValue
      result must not be empty
    }

    "not return the SdipFaststream candidate in PHASE2_TESTS if the sdip is not evaluated for phase1" ignore {
      val resultToSave = List(SchemeEvaluationResult(cyberSecurity, Green.toString))

      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version2", batchSize = 1).futureValue
      result mustBe empty
    }

    "return SdipFaststream candidate in PHASE2_TESTS if sdip has not been previously evaluated for phase1" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result must not be empty
    }

    "return the SdipFaststream candidate in PHASE2_TESTS if the sdip is already evaluated to Green for phase1" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      val resultToSave = List(SchemeEvaluationResult(cyberSecurity, Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), Green.toString))
      val evaluation = PassmarkEvaluation("version1", previousPhasePassMarkVersion = None, resultToSave, "version1-res",
        previousPhaseResultVersion = None)

      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version2", batchSize = 1).futureValue
      result must not be empty
    }

    "return the SdipFaststream candidate in PHASE2_TESTS if the sdip is already evaluated to Red for phase1" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      val resultToSave = List(SchemeEvaluationResult(cyberSecurity, Red.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), Red.toString))
      val evaluation = PassmarkEvaluation("version1", previousPhasePassMarkVersion = None, resultToSave, "version1-res",
        previousPhaseResultVersion = None)

      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version2", batchSize = 1).futureValue
      result must not be empty
    }

    "return the SdipFaststream candidate in PHASE2_TESTS if the sdip is evaluated to Amber for phase1" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      val resultToSave = List(SchemeEvaluationResult(cyberSecurity, Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)

      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version2", batchSize = 1).futureValue
      result must not be empty
    }
  }

  "Add a scheme to passmark evaluation" should {
    "add sdip results to pass mark evaluation" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      val resultToSave = List(SchemeEvaluationResult(cyberSecurity, Green.toString))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue

      val sdipResult = SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)

      phase1EvaluationRepo.addSchemeResultToPassmarkEvaluation("app1", sdipResult, "version2").futureValue

      val passmarkEvaluation = phase1EvaluationRepo.getPassMarkEvaluation("app1").futureValue

      passmarkEvaluation.result must contain theSameElementsAs List(
        SchemeEvaluationResult(cyberSecurity, Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), Green.toString)
      )
    }

    "update sdip results in pass mark evaluation" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      val resultToSave = List(SchemeEvaluationResult(SchemeId("Sdip"), Amber.toString))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version1-res", None)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue

      val sdipResult = SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)

      phase1EvaluationRepo.addSchemeResultToPassmarkEvaluation("app1", sdipResult, "version2").futureValue

      val passmarkEvaluation = phase1EvaluationRepo.getPassMarkEvaluation("app1").futureValue
      passmarkEvaluation.result must contain theSameElementsAs List(
        SchemeEvaluationResult(SchemeId("Sdip"), Red.toString)
      )
    }
  }

  private def getOnePhase1Profile(appId: String) = {
    applicationCollection.find(Document("applicationId" -> appId)).headOption.map( _.map { doc =>
      val applicationStatusBsonValue = doc.get("applicationStatus").get
      val applicationStatus = Codecs.fromBson[ApplicationStatus](applicationStatusBsonValue)

      val bsonPhase1 = doc.get("testGroups").map(_.asDocument().get("PHASE1").asDocument() )
      val phase1 = bsonPhase1.map( bson => Codecs.fromBson[Phase1TestProfile](bson) ).get
      applicationStatus -> phase1
    }).futureValue
  }
}

object Phase1EvaluationMongoRepositorySpec {
  implicit val now = DateTime.now().withZone(DateTimeZone.UTC)
  val phase1Tests = List(
    model.Phase1TestExamples.firstPsiTest.copy(testResult = None),
    model.Phase1TestExamples.secondPsiTest.copy(testResult = None),
    model.Phase1TestExamples.thirdPsiTest.copy(testResult = None),
    model.Phase1TestExamples.fourthPsiTest.copy(testResult = None)
  )

  val phase1TestsWithResult = phase1TestsWithResults(PsiTestResult(tScore = 20.5d, rawScore = 10.0d, testReportUrl = None))
  def phase1TestsWithResults(testResult: PsiTestResult) = {
    phase1Tests.map(t => t.copy(testResult = Some(testResult)))
  }
}
