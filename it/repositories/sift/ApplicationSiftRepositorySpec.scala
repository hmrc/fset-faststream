package repositories.sift

import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{ Green, Red, Withdrawn }
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.PHASE3_TESTS_PASSED
import model._
import model.command.ApplicationForSift
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.TableDrivenPropertyChecks
import reactivemongo.bson.BSONDocument
import repositories.{ CollectionNames, CommonRepository }
import testkit.{ MockitoSugar, MongoRepositorySpec }

class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository
  with MockitoSugar with TableDrivenPropertyChecks {

  val collectionName: String = CollectionNames.APPLICATION

  val Commercial: SchemeId = SchemeId("Commercial")
  val Generalist: SchemeId = SchemeId("Generalist")
  val ProjectDelivery = SchemeId("Project Delivery")
  val schemeDefinitions = List(Commercial, ProjectDelivery, Generalist)

  def repository: ApplicationSiftMongoRepository = applicationSiftRepository

  "next Application for sift" must {
    "ignore applications in incorrect statuses and return only the PhaseX Passed_Notified applications that are eligible for sift" in {

      insertApplicationWithPhase3TestNotifiedResults("appId1",
        List(SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2",
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId3",
        List(SchemeEvaluationResult(DiplomaticServiceEconomists, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId4",
        List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5",
        List(SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString))).futureValue

      insertApplicationWithPhase1TestNotifiedResults("appId6",
        List(SchemeEvaluationResult(Edip, EvaluationResults.Green.toString)), appRoute = ApplicationRoute.Edip).futureValue

      insertApplicationWithPhase1TestNotifiedResults("appId7",
        List(SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString)), appRoute = ApplicationRoute.Sdip).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(SchemeEvaluationResult(DigitalAndTechnology, EvaluationResults.Green.toString))).futureValue

      /* FSET-1803 - temporarily disabled
      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(
          SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString),
          SchemeEvaluationResult(DiplomaticService, EvaluationResults.Red.toString)
        ),
        applicationRoute = ApplicationRoute.SdipFaststream
      ).futureValue */

      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift must contain theSameElementsAs List(
        ApplicationForSift("appId1", "appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString))),
        ApplicationForSift("appId3", "appId3", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(DiplomaticServiceEconomists, EvaluationResults.Green.toString))),
        ApplicationForSift("appId4", "appId4", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString))),
        ApplicationForSift("appId6", "appId6", ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(Edip, EvaluationResults.Green.toString))),
        ApplicationForSift("appId7", "appId7", ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString)))
        /* FSET-1803 - temporarily disabled
        ApplicationForSift("appId8", "appId8", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        List(SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString),
          SchemeEvaluationResult(DiplomaticService, EvaluationResults.Red.toString)))*/
      )

      appsForSift.size mustBe 5
    }

    ("return no results when there are only applications that aren't in Passed_Notified which apply for sift or don't have Green/Passed "
      + "results") in {

      insertApplicationWithPhase1TestResults("appId5", 5.5d, applicationRoute = ApplicationRoute.Edip)(Edip)
      insertApplicationWithPhase1TestResults("appId6", 5.5d, applicationRoute = ApplicationRoute.Sdip)(Sdip)

      insertApplicationWithPhase3TestResults("appId7", None,
        PassmarkEvaluation("1", None, List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString)), "1", None))(Finance)

      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(SchemeEvaluationResult(Generalist, EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId8", ApplicationStatus.PHASE3_TESTS_FAILED)
      insertApplicationWithPhase3TestNotifiedResults("appId9",
        List(SchemeEvaluationResult(Finance, EvaluationResults.Red.toString))).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId10",
        List(SchemeEvaluationResult(ProjectDelivery, EvaluationResults.Red.toString))).futureValue

      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift mustBe Nil
    }
  }

  "findApplicationsReadyForSifting" must {
    "return fast stream candidates that are ready for sifting" in {
      createSiftEligibleCandidates("appId1")
      val candidates = repository.findApplicationsReadyForSchemeSift(Commercial).futureValue
      candidates.size mustBe 1
      val candidate = candidates.head
      candidate.applicationId mustBe Some("appId1")
    }
  }

  "siftCandidate" must {
    def candidates = Table(
      ("appId", "unit", "scheme"),
      ("appId1", createSiftEligibleCandidates("appId1"), Commercial),
      ("appId2", createSdipSiftCandidates("appId2"), Sdip),
      ("appId3", createEdipSiftCandidates("appId3"), Edip)
    )

    "sift candidate as Passed" in {
      forAll(candidates) { (appId: String, _: Unit, scheme: SchemeId) =>
        repository.siftApplicationForScheme(appId, SchemeEvaluationResult(scheme, "Green"),
          Seq(BSONDocument(s"testGroups.SIFT.evaluation.dummy" -> "test"))).futureValue
        val candidatesForSift = repository.findApplicationsReadyForSchemeSift(scheme).futureValue
        play.api.Logger.error(s"\n\n$candidatesForSift - $scheme")
        candidatesForSift.size mustBe 0
      }
    }

    "eligible for other schema after sifting on one" in {
      createSiftEligibleCandidates("appId14")
      repository.siftApplicationForScheme("appId14", SchemeEvaluationResult(DiplomaticServiceEconomists, "Red")).futureValue
      val candidates = repository.findApplicationsReadyForSchemeSift(Commercial).futureValue
      candidates.size mustBe 1
    }
  }

  "next application failed at sift" must {
    "return candidates who are all red at the end of sift" in {
      val schemeStatus = List(
        SchemeEvaluationResult(Commercial, Red.toString),
        SchemeEvaluationResult(DiplomaticServiceEconomists, Withdrawn.toString),
        SchemeEvaluationResult(Generalist, Red.toString)
      )
      createSiftEligibleCandidates("appId", schemeStatus)
      applicationRepository.addProgressStatusAndUpdateAppStatus("appId", ProgressStatuses.SIFT_COMPLETED).futureValue

      whenReady(repository.nextApplicationFailedAtSift) { result =>
        result mustBe defined
        result.get mustBe ApplicationForSift("appId", "appId", ApplicationStatus.SIFT, schemeStatus)
      }
    }

    "ignore candidates who are not all red at the end of sift" in {
      val schemeStatus = List(
        SchemeEvaluationResult(Commercial, Red.toString),
        SchemeEvaluationResult(DiplomaticServiceEconomists, Green.toString),
        SchemeEvaluationResult(Generalist, Red.toString)
      )
      createSiftEligibleCandidates("appId", schemeStatus)
      applicationRepository.addProgressStatusAndUpdateAppStatus("appId", ProgressStatuses.SIFT_COMPLETED).futureValue

      whenReady(repository.nextApplicationFailedAtSift) { result =>
        result mustBe None
      }
    }
  }

  private def createSiftEligibleCandidates(appAndUserId: String, resultToSave: List[SchemeEvaluationResult] = List(
    SchemeEvaluationResult(Commercial, Green.toString),
    SchemeEvaluationResult(DiplomaticServiceEconomists, Green.toString),
    SchemeEvaluationResult(Generalist, Red.toString)
  )
  ) = {

    val phase2Test = List(model.persisted.PsiTest(inventoryId = "inventoryId",
                                                  orderId = "orderId",
                                                  usedForResults = true,
                                                  testUrl = "http://localhost",
                                                  invitationDate = now,
                                                  assessmentId = "assessmentId",
                                                  reportId = "reportId",
                                                  normId = "normId"))

    def phase2TestWithResults(testResult: model.persisted.PsiTestResult) = {
      phase2Test.map(t => t.copy(testResult = Some(testResult)))
    }

    val phase2TestWithResult = phase2TestWithResults(model.persisted.PsiTestResult(
      tScore = 20.5, rawScore = 20.5, testReportUrl = None)
    )
    val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version2-res", None)

    insertApplication2(appAndUserId,
      ApplicationStatus.PHASE3_TESTS, None, Some(phase2TestWithResult),
      Some(phase3TestWithResult),
      schemes = List(Commercial, DiplomaticServiceEconomists),
      phase2Evaluation = Some(phase2Evaluation))

    val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
      "phase3_version1-res", Some("phase2_version1-res"))
    phase3EvaluationRepo.savePassmarkEvaluation(appAndUserId, phase3Evaluation, Some(PHASE3_TESTS_PASSED)).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appAndUserId, ProgressStatuses.SIFT_READY).futureValue
  }

  private def createSdipSiftCandidates(appId: String) = createXdipSiftCandidates(ApplicationRoute.Sdip)(appId)

  private def createEdipSiftCandidates(appId: String) = createXdipSiftCandidates(ApplicationRoute.Edip)(appId)

  private def createXdipSiftCandidates(route: ApplicationRoute)(appId: String) = {
    val resultToSave = (if (route == ApplicationRoute.Sdip) {
      SchemeEvaluationResult(Sdip, Green.toString)
    } else {
      SchemeEvaluationResult(Edip, Green.toString)
    }) :: Nil

    insertApplicationWithPhase1TestNotifiedResults(appId, resultToSave, appRoute = route).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_READY).futureValue
  }
}
