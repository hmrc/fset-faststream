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

package repositories.sift

import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{Green, Red, Withdrawn}
import model.Exceptions._
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.PHASE3_TESTS_PASSED
import model._
import model.command.ApplicationForSift
import model.persisted.sift.{MaybeSiftTestGroupWithAppId, SiftTestGroup}
import model.persisted.{PassmarkEvaluation, PsiTest, PsiTestResult, SchemeEvaluationResult}
import model.report.SiftPhaseReportItem
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.Logging
import repositories.{CollectionNames, CommonRepository}
import testkit.{MockitoSugar, MongoRepositorySpec}

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneId}

class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository
  with MockitoSugar with TableDrivenPropertyChecks with Logging {

  val collectionName: String = CollectionNames.APPLICATION

  val schemeDefinitions = List(Commercial, ProjectDelivery, OperationalDelivery)

  def repository: ApplicationSiftMongoRepository = applicationSiftRepository

  "next application for sift" must {
    "ignore Commercial and Finance as they no longer have a sift requirement from campaign 2022/23" in {

      insertApplicationWithPhase3TestNotifiedResults("appId1",
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2",
        List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString))).futureValue

      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift mustBe Nil
    }

    "ignore applications in incorrect statuses and return only the PhaseX Passed_Notified applications that are eligible for sift" in {

      insertApplicationWithPhase3TestNotifiedResults("appId1",
        List(SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2",
        List(SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId3",
        List(SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId4",
        List(SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5",
        List(SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Red.toString))).futureValue

      insertApplicationWithPhase1TestNotifiedResults("appId6",
        List(SchemeEvaluationResult(Edip, EvaluationResults.Green.toString)), appRoute = ApplicationRoute.Edip).futureValue

      // The Sdip application should not be selected for campaign 2024/25 as it no longer goes via Sift
      insertApplicationWithPhase1TestNotifiedResults("appId7",
        List(SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString)), appRoute = ApplicationRoute.Sdip).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(SchemeEvaluationResult(Digital, EvaluationResults.Green.toString))).futureValue

      /* FSET-1803 - temporarily disabled
      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(
          SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString),
          SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Red.toString)
        ),
        applicationRoute = ApplicationRoute.SdipFaststream
      ).futureValue */

      // The Sdip application should not be selected for campaign 2024/25 as it no longer goes via Sift
      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift must contain theSameElementsAs List(
        ApplicationForSift("appId1", "appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(GovernmentEconomicsService, EvaluationResults.Green.toString))),
        ApplicationForSift("appId3", "appId3", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, EvaluationResults.Green.toString))),
        ApplicationForSift("appId4", "appId4", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Green.toString))),
        ApplicationForSift("appId6", "appId6", ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(Edip, EvaluationResults.Green.toString))),
        /* FSET-1803 - temporarily disabled
        ApplicationForSift("appId8", "appId8", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        List(SchemeEvaluationResult(Sdip, EvaluationResults.Green.toString),
          SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Red.toString)))*/
      )

      appsForSift.size mustBe 4
    }

    "return no results when there are only applications that aren't in Passed_Notified which apply for sift " +
      "or don't have Green/Passed results" in {

      insertApplicationWithPhase1TestResults("appId5", 5.5d, Some(5.5d), applicationRoute = ApplicationRoute.Edip)(Edip)
      insertApplicationWithPhase1TestResults("appId6", 5.5d, Some(5.5d), applicationRoute = ApplicationRoute.Sdip)(Sdip)

      insertApplicationWithPhase3TestResults("appId7", None,
        PassmarkEvaluation("1", None, List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString)), "1", None))(Finance)

      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString))).futureValue
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

    "be eligible for other schema after sifting on one" in {
      createSiftEligibleCandidates("appId14")
      repository.siftApplicationForScheme("appId14", SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Red.toString)).futureValue
      val candidates = repository.findApplicationsReadyForSchemeSift(Commercial).futureValue
      candidates.size mustBe 1
    }
  }

  "next application failed at sift" must {
    "return candidates who are all red at the end of sift" in {
      val schemeStatus = List(
        SchemeEvaluationResult(Commercial, Red.toString),
        SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Withdrawn.toString),
        SchemeEvaluationResult(OperationalDelivery, Red.toString)
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
        SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Green.toString),
        SchemeEvaluationResult(OperationalDelivery, Red.toString)
      )
      createSiftEligibleCandidates("appId", schemeStatus)
      applicationRepository.addProgressStatusAndUpdateAppStatus("appId", ProgressStatuses.SIFT_COMPLETED).futureValue

      whenReady(repository.nextApplicationFailedAtSift) { result =>
        result mustBe None
      }
    }
  }

  "sift expiry date" must {
    "throw an exception if there is no expiry date when looking for one" in {
      createSiftEligibleCandidates("appId")
      val result = repository.findSiftExpiryDate("appId").failed.futureValue
      result mustBe a[ApplicationNotFound]
    }

    "return the expiry date when one is stored" in {
      createSiftEligibleCandidates("appId")
      repository.saveSiftExpiryDate("appId", now).futureValue
      val result = repository.findSiftExpiryDate("appId").futureValue
      result mustBe now
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
      repository.insertNumericalTests(appId, List(test)).futureValue

      val result = repository.getTestGroupByOrderId("orderUuid").futureValue
      result mustBe MaybeSiftTestGroupWithAppId(appId, now, Some(List(test)))
    }
  }

  "update test started time" must {
    "throw an exception if no record is found" in {
      val result = repository.updateTestStartTime("orderId", now).failed.futureValue
      result mustBe a[CannotFindTestByOrderIdException]
    }

    "return a record" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "orderUuid", assessmentId = "assessmentUuid",
        reportId = "reportUuid", normId = "normUuid", usedForResults = true,
        testUrl = "http://testUrl.com", invitationDate = now)
      repository.insertNumericalTests(appId, List(test)).futureValue

      val startedTime = now
      repository.updateTestStartTime("orderUuid", startedTime).futureValue

      val result = repository.getTestGroupByOrderId("orderUuid").futureValue
      result mustBe MaybeSiftTestGroupWithAppId(appId, now, Some(List(test.copy(startedDateTime = Some(startedTime)))))
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
      repository.insertNumericalTests(appId, List(test)).futureValue

      val completedTime = now
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

      val newExpiryTime = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS)
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
        List(SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString)))

      val appsForSift = repository.nextApplicationsReadyForNumericTestsInvitation(10, Seq(Commercial)).futureValue
      appsForSift mustBe Nil
    }
  }

  "is sift expired" must {
    "return true when the application is expired" in {
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
        List(SchemeEvaluationResult(OperationalDelivery, EvaluationResults.Green.toString)))

      val result = repository.isSiftExpired("appId1").futureValue
      result mustBe false
    }
  }

  "get application id for order id" must {
    "throw an exception if no record is found" in {
      val result = repository.getApplicationIdForOrderId("orderId").failed.futureValue
      result mustBe a[CannotFindApplicationByOrderIdException]
    }

    "return a record" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "orderUuid", assessmentId = "assessmentUuid",
        reportId = "reportUuid", normId = "normUuid", usedForResults = true,
        testUrl = "http://testUrl.com", invitationDate = now)
      repository.insertNumericalTests(appId, List(test)).futureValue

      val result = repository.getApplicationIdForOrderId("orderUuid").futureValue
      result mustBe appId
    }
  }

  "next application for first sift reminder" must {
    "fetch applications that are eligible for the first sift reminder" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      repository.saveSiftExpiryDate(appId, now).futureValue

      // Fetch the application if the expiry date is before now plus 1 hour
      val result = repository.nextApplicationForFirstSiftReminder(1).futureValue
      result mustBe defined
    }

    "return no applications when a reminder has already been sent" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      // This progress status indicates a reminder has already been sent
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_FIRST_REMINDER).futureValue
      repository.saveSiftExpiryDate(appId, now).futureValue

      val result = repository.nextApplicationForFirstSiftReminder(1).futureValue
      result mustBe None
    }

    "return no applications if they have already expired" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      // Deliberately do not set the SIFT_FIRST_REMINDER progress status so we test SIFT_EXPIRED in isolation
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_EXPIRED).futureValue
      repository.saveSiftExpiryDate(appId, now).futureValue

      // In reality the expiry date would be in the past but we just want to check the progress status excludes the application
      val result = repository.nextApplicationForFirstSiftReminder(1).futureValue
      result mustBe None
    }

    "return no applications expiry date is after the time period" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      repository.saveSiftExpiryDate(appId, now.plusMinutes(61)).futureValue

      // Fetch the application if the expiry date is before now plus 1 hour, so it should not happen as the date is 61 minutes from now
      val result = repository.nextApplicationForFirstSiftReminder(1).futureValue
      result mustBe None
    }
  }

  "next application for sift expiry" must {
    "fetch applications that are eligible for sift expiry" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      val nanosInOneMilli = 1000000
      repository.saveSiftExpiryDate(appId, now.minusNanos(nanosInOneMilli)).futureValue

      val result = repository.nextApplicationsForSiftExpiry(1, 0).futureValue
      result.size mustBe 1
    }

    "fetch no applications if the expiry period has not elapsed" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      repository.saveSiftExpiryDate(appId, now.plusMinutes(1)).futureValue

      val result = repository.nextApplicationsForSiftExpiry(1, 0).futureValue
      result mustBe empty
    }
  }

  "next application for second sift reminder" must {
    "fetch applications that are eligible for the second sift reminder" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_FIRST_REMINDER).futureValue
      repository.saveSiftExpiryDate(appId, now).futureValue

      // Fetch the application if the expiry date is before now plus 1 hour
      val result = repository.nextApplicationForSecondSiftReminder(1).futureValue
      result mustBe defined
    }

    "return no applications when a reminder has already been sent" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_FIRST_REMINDER).futureValue
      // This progress status indicates a reminder has already been sent
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_SECOND_REMINDER).futureValue
      repository.saveSiftExpiryDate(appId, now).futureValue

      val result = repository.nextApplicationForSecondSiftReminder(1).futureValue
      result mustBe None
    }

    "return no applications if they have already expired" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_FIRST_REMINDER).futureValue
      // Deliberately do not set the SIFT_SECOND_REMINDER progress status so we test SIFT_EXPIRED in isolation
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_EXPIRED).futureValue
      repository.saveSiftExpiryDate(appId, now).futureValue

      // In reality the expiry date would be in the past but we just want to check the progress status excludes the application
      val result = repository.nextApplicationForSecondSiftReminder(1).futureValue
      result mustBe None
    }

    "return no applications expiry date is after the time period" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_FIRST_REMINDER).futureValue
      repository.saveSiftExpiryDate(appId, now.plusMinutes(61)).futureValue

      // Fetch the application if the expiry date is before now plus 1 hour, so it should not happen as the date is 61 minutes from now
      val result = repository.nextApplicationForSecondSiftReminder(1).futureValue
      result mustBe None
    }
  }

  "insert psi test result" must {
    "throw an exception if no record is found" in {
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "orderUuid", assessmentId = "assessmentUuid",
        reportId = "reportUuid", normId = "normUuid", usedForResults = true,
        testUrl = "http://testUrl.com", invitationDate = now)
      val testResult = PsiTestResult(tScore = 55.33d, rawScore = 65.32d, testReportUrl = None)

      val result = repository.insertPsiTestResult("appId", test, testResult).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }

    "save the test results" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      repository.saveSiftExpiryDate(appId, now).futureValue
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "orderUuid", assessmentId = "assessmentUuid",
        reportId = "reportUuid", normId = "normUuid", usedForResults = true,
        testUrl = "http://testUrl.com", invitationDate = now)
      repository.insertNumericalTests(appId, List(test)).futureValue
      val testResult = PsiTestResult(tScore = 55.33d, rawScore = 65.32d, testReportUrl = Some("http://testReportUrl.com"))

      repository.insertPsiTestResult(appId, test, testResult).futureValue

      val result = repository.getTestGroupByOrderId("orderUuid").futureValue
      result mustBe MaybeSiftTestGroupWithAppId(appId, now, Some(List(test.copy(testResult = Some(testResult)))))
    }
  }

  "get notification expiring sift" must {
    "return an application" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      repository.saveSiftExpiryDate(appId, now).futureValue

      val result = repository.getNotificationExpiringSift(appId).futureValue
      result mustBe defined
    }

    "return None if there is no matching application" in {
      val result = repository.getNotificationExpiringSift("appId1").futureValue
      result mustBe None
    }
  }

  "next application with results received" must {
    "fetch eligible application id" in {
      val appId = "appId1"
      insertApplicationWithSiftEntered(appId, List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED).futureValue

      val result = repository.nextApplicationWithResultsReceived.futureValue
      result mustBe Some(appId)
    }

    "fetch no results if there are no eligible applications" in {
      val result = repository.nextApplicationWithResultsReceived.futureValue
      result mustBe None
    }
  }

  "sift results exists for scheme" must {
    "return true when there are results" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Green.toString)
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue

      val result = repository.siftResultsExistsForScheme(appId, Commercial).futureValue
      result mustBe true
    }

    "return false when there are no results" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Green.toString)
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue

      val result = repository.siftResultsExistsForScheme(appId, OperationalDelivery).futureValue
      result mustBe false
    }

    "return false when there is no matching application" in {
      val result = repository.siftResultsExistsForScheme("appId1", OperationalDelivery).futureValue
      result mustBe false
    }

    "return false if no evaluation is saved" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)

      val result = repository.siftResultsExistsForScheme(appId, OperationalDelivery).futureValue
      result mustBe false
    }
  }

  "fix scheme evaluation" must {
    "update the schemes to the expected results" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Green.toString)
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue

      val updatedSchemes = SchemeEvaluationResult(Commercial, Red.toString)
      repository.fixSchemeEvaluation(appId, updatedSchemes).futureValue

      val result = repository.getSiftEvaluations(appId).futureValue
      result mustBe Seq(updatedSchemes)
    }
  }

  "fix data by removing sift phase evaluation and failure status" must {
    "correct the specified application" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Red.toString)
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAILED_AT_SIFT).futureValue

      repository.fixDataByRemovingSiftPhaseEvaluationAndFailureStatus(appId).futureValue

      val evaluations = repository.getSiftEvaluations(appId).failed.futureValue
      evaluations mustBe a[PassMarkEvaluationNotFound]

      val progressResponse = applicationRepository.findProgress(appId).futureValue
      progressResponse.siftProgressResponse.failedAtSift mustBe false

      val appStatuses = applicationRepository.getApplicationStatusForCandidates(Seq(appId)).futureValue
      appStatuses mustBe Seq(appId -> ApplicationStatus.SIFT)
    }

    "throw an exception if the specified application cannot be found" in {
      val result = repository.fixDataByRemovingSiftPhaseEvaluationAndFailureStatus("appId1").failed.futureValue
      result mustBe a[NotFoundException]
    }
  }

  "fix data by removing sift phase evaluation" must {
    "correct the specified application" in {
      val appId = "appId1"
      createSiftEligibleCandidates(appId)
      val schemeEvaluationResult = SchemeEvaluationResult(Commercial, Red.toString)
      repository.siftApplicationForScheme(appId, schemeEvaluationResult, Nil).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FAILED_AT_SIFT).futureValue

      repository.fixDataByRemovingSiftEvaluation(appId).futureValue

      val evaluations = repository.getSiftEvaluations(appId).failed.futureValue
      evaluations mustBe a[PassMarkEvaluationNotFound]

      val appStatuses = applicationRepository.getApplicationStatusForCandidates(Seq(appId)).futureValue
      appStatuses mustBe Seq(appId -> ApplicationStatus.SIFT)
    }

    "throw an exception if the specified application cannot be found" in {
      val result = repository.fixDataByRemovingSiftEvaluation("appId1").failed.futureValue
      result mustBe a[NotFoundException]
    }
  }

  private def createSiftEligibleCandidates(appAndUserId: String, resultToSave: List[SchemeEvaluationResult] = List(
    SchemeEvaluationResult(Commercial, Green.toString),
    SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Green.toString),
    SchemeEvaluationResult(OperationalDelivery, Red.toString)
  )) = {

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

    insertApplication(appAndUserId,
      ApplicationStatus.PHASE3_TESTS, None, Some(phase2TestWithResult),
      Some(phase3TestWithResult),
      schemes = List(Commercial, DiplomaticAndDevelopmentEconomics),
      phase2Evaluation = Some(phase2Evaluation))

    val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
      "phase3_version1-res", Some("phase2_version1-res"))
    val css = resultToSave
    phase3EvaluationRepo.savePassmarkEvaluation(appAndUserId, phase3Evaluation, Some(PHASE3_TESTS_PASSED), css).futureValue
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
