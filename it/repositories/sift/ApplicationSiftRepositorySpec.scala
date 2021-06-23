package repositories.sift

import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{Green, Red, Withdrawn}
import model.Exceptions.{ApplicationNotFound, CannotFindTestByOrderIdException, CannotUpdateRecord, NotFoundException, PassMarkEvaluationNotFound}
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.PHASE3_TESTS_PASSED
import model._
import model.command.ApplicationForSift
import model.persisted.sift.{MaybeSiftTestGroupWithAppId, SiftTestGroup}
import model.persisted.{PassmarkEvaluation, PsiTest, SchemeEvaluationResult}
import model.report.SiftPhaseReportItem
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.Logging
import repositories.{CollectionNames, CommonRepository}
import testkit.{MockitoSugar, MongoRepositorySpec}

class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository
  with MockitoSugar with TableDrivenPropertyChecks with Logging {

  val collectionName: String = CollectionNames.APPLICATION

  val Commercial: SchemeId = SchemeId("Commercial")
  val Generalist: SchemeId = SchemeId("Generalist")
  val ProjectDelivery = SchemeId("Project Delivery")
  val schemeDefinitions = List(Commercial, ProjectDelivery, Generalist)

  def repository: ApplicationSiftMongoRepository = applicationSiftRepository

  "next application for sift" must {
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

    "return no results when there are only applications that aren't in Passed_Notified which apply for sift " +
      "or don't have Green/Passed results" in {

      insertApplicationWithPhase1TestResults2("appId5", 5.5d, None, None, 5.5d, applicationRoute = ApplicationRoute.Edip)(Edip)
      insertApplicationWithPhase1TestResults2("appId6", 5.5d, None, None, 5.5d, applicationRoute = ApplicationRoute.Sdip)(Sdip)

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
        repository.siftApplicationForScheme(appId, SchemeEvaluationResult(scheme, Green.toString),
          Seq(Document(s"testGroups.SIFT.evaluation.dummy" -> "test"))).futureValue
        val candidatesForSift = repository.findApplicationsReadyForSchemeSift(scheme).futureValue
        logger.error(s"\n\n$candidatesForSift - $scheme")
        candidatesForSift.size mustBe 0
      }
    }

    "eligible for other schema after sifting on one" in {
      createSiftEligibleCandidates("appId14")
      repository.siftApplicationForScheme("appId14", SchemeEvaluationResult(DiplomaticServiceEconomists, Red.toString)).futureValue
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
        result mustBe Some(ApplicationForSift("appId", "appId", ApplicationStatus.SIFT, schemeStatus))
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

  "remove evaluation" must {
    "throw an exception if no record is updated" in {
      val result = repository.removeEvaluation("appId").failed.futureValue
      result mustBe a[NotFoundException]
    }

    "update a record" in {
      val appId = "appId1"
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Green.toString)
      createSiftEligibleCandidates(appId)
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue

      val beforeRemoval = repository.getSiftEvaluations(appId).futureValue
      beforeRemoval mustBe Seq(schemeEvaluationResult)
      repository.removeEvaluation(appId).futureValue
      val afterRemoval = repository.getSiftEvaluations(appId).failed.futureValue
      afterRemoval mustBe a[PassMarkEvaluationNotFound]
    }
  }

  "get test group by order id" must {
    "throw an exception if no record is found" in {
      val result = repository.getTestGroupByOrderId("orderId").failed.futureValue
      result mustBe a[CannotFindTestByOrderIdException]
    }

    "return a record" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "orderUuid", assessmentId = "assessmentUuid",
        reportId = "reportUuid", normId = "normUuid", usedForResults = true,
        testUrl = "http://testUrl.com", invitationDate = now)
      repository.insertNumericalTests2(appId, List(test))

      val result = repository.getTestGroupByOrderId("orderUuid").futureValue
      result mustBe MaybeSiftTestGroupWithAppId(appId, now, Some(List(test)))
    }
  }

  "update test completion time" must {
    "throw an exception if no record is found" in {
      val result = repository.updateTestCompletionTime("orderId", now).failed.futureValue
      result mustBe a[CannotFindTestByOrderIdException]
    }

    "return a record" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "orderUuid", assessmentId = "assessmentUuid",
        reportId = "reportUuid", normId = "normUuid", usedForResults = true,
        testUrl = "http://testUrl.com", invitationDate = now)
      repository.insertNumericalTests2(appId, List(test))

      val completedTime = DateTime.now(DateTimeZone.UTC)
      repository.updateTestCompletionTime("orderUuid", completedTime).futureValue

      val result = repository.getTestGroupByOrderId("orderUuid").futureValue
      result mustBe MaybeSiftTestGroupWithAppId(appId, now, Some(List(test.copy(completedDateTime = Some(completedTime)))))
    }
  }

  "update expiry time" must {
    "throw an exception if no record is found" in {
      val result = repository.updateTestCompletionTime("orderId", now).failed.futureValue
      result mustBe a[CannotFindTestByOrderIdException]
    }

    "update the expiry time" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue

      val newExpiryTime = DateTime.now(DateTimeZone.UTC)
      repository.updateExpiryTime(appId, newExpiryTime).futureValue

      val result = repository.getTestGroup(appId).futureValue
      result mustBe Some(SiftTestGroup(newExpiryTime, None))
    }
  }

  "remove test group" must {
    "throw an exception if no record is updated" in {
      val result = repository.removeTestGroup("appId").failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }

    "update a record" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue

      val beforeRemoval = repository.getTestGroup(appId).futureValue
      beforeRemoval mustBe Some(SiftTestGroup(now, tests = None))
      repository.removeTestGroup(appId).futureValue
      val afterRemoval = repository.getTestGroup(appId).futureValue
      afterRemoval mustBe None
    }
  }

  "find all results" must {
    "return an empty list if there is no data" in {
      val result = repository.findAllResults.futureValue
      result mustBe Nil
    }

    "return data" in {
      val appId = "appId1"
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Green.toString)
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue

      val result = repository.findAllResults.futureValue
      result mustBe Seq(SiftPhaseReportItem(appId, Some(Seq(schemeEvaluationResult))))
    }
  }

  "find all results by ids" must {
    "return an empty list if there is no data" in {
      val result = repository.findAllResultsByIds(Seq(AppId)).futureValue
      result mustBe Nil
    }

    "return data" in {
      val appId = "appId1"
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Green.toString)
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue

      val result = repository.findAllResultsByIds(Seq(appId)).futureValue
      result mustBe Seq(SiftPhaseReportItem(appId, Some(Seq(schemeEvaluationResult))))
    }
  }

  "next application for sift numeric test invitation" must {
    "fetch applications that are eligible for the sift numeric test" in {
      insertApplicationWithSiftEntered("appId1",
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))

      val appsForSift = repository.nextApplicationsReadyForNumericTestsInvitation(10, Seq(Commercial)).futureValue
      appsForSift must contain theSameElementsAs List(
        NumericalTestApplication("appId1", "appId1", "testAccountId", ApplicationStatus.SIFT, "preferredName", "lastName",
          List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      )
    }

    "return no applications when none are eligible" in {
      insertApplicationWithSiftEntered("appId1",
        List(SchemeEvaluationResult(Generalist, EvaluationResults.Green.toString)))

      val appsForSift = repository.nextApplicationsReadyForNumericTestsInvitation(10, Seq(Commercial)).futureValue
      appsForSift mustBe Nil
    }
  }

  "is sift expired" must {
    "reeturn true true when the application is expired" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId,
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_EXPIRED).futureValue

      val result = repository.isSiftExpired(appId).futureValue
      result mustBe true
    }

    "return an exception when the application does not exist" in {
      val result = repository.isSiftExpired("appId").failed.futureValue
      result mustBe an[ApplicationNotFound]
    }

    "return false when the application is not expired" in {
      insertApplicationWithSiftEntered("appId1",
        List(SchemeEvaluationResult(Generalist, EvaluationResults.Green.toString)))

      val result = repository.isSiftExpired("appId1").futureValue
      result mustBe false
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
