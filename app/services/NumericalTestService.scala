/*
 * Copyright 2023 HM Revenue & Customs
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

package services

import com.google.inject.name.Named
import config._
import connectors.ExchangeObjects._
import connectors.{OnlineTestEmailClient, OnlineTestsGatewayClient}
import factories.{DateTimeFactory, UUIDFactory}

import javax.inject.{Inject, Singleton}
import model.Exceptions.{CannotFindTestByOrderIdException, UnexpectedException}
import model.ProgressStatuses.{ProgressStatus, SIFT_TEST_COMPLETED, SIFT_TEST_INVITED}
import model._
import model.exchange.PsiRealTimeResults
import model.persisted.sift.{MaybeSiftTestGroupWithAppId, SiftTestGroup}
import model.persisted.PsiTest
import model.stc.DataStoreEvents
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.sift.ApplicationSiftRepository
import services.onlinetesting.TextSanitizer
import services.stc.{EventSink, StcEventService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off number.of.methods
@Singleton
class NumericalTestService @Inject() (applicationRepo: GeneralApplicationRepository,
                                      applicationSiftRepo: ApplicationSiftRepository,
                                      onlineTestsGatewayClient: OnlineTestsGatewayClient,
                                      tokenFactory: UUIDFactory,
                                      appConfig: MicroserviceAppConfig,
                                      dateTimeFactory: DateTimeFactory,
                                      schemeRepository: SchemeRepository,
                                      @Named("CSREmailClient") emailClient: OnlineTestEmailClient, // Changed the type
                                      contactDetailsRepo: ContactDetailsRepository,
                                      val eventService: StcEventService
                                     )(implicit ec: ExecutionContext) extends EventSink with Logging {

  val gatewayConfig: OnlineTestsGatewayConfig = appConfig.onlineTestsGatewayConfig

  case class NumericalTestInviteData2(application: NumericalTestApplication, inventoryId: String)

  def registerAndInviteForTests(applications: List[NumericalTestApplication])
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val name = gatewayConfig.numericalTests.standard.head // only one test for numerical tests
    val inventoryId = gatewayConfig.numericalTests.tests
      .getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))

    registerAndInvite(applications, inventoryId)
  }

  private def registerAndInvite(applications: List[NumericalTestApplication], testIds: PsiTestIds)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    applications match {
      case Nil => Future.successful(())
      case candidates =>
        val registrations = candidates.map { candidate =>
          for {
            test <- registerPsiApplicant(candidate, testIds)
            _ <- insertNumericalTest(candidate, test)
            _ <- emailInvitedCandidate(candidate)
            _ <- updateProgressStatuses(List(candidate.applicationId), SIFT_TEST_INVITED)
          } yield {
            logger.warn(s"Successfully invited candidate to take numerical test with Id: " +
              s"${candidate.applicationId} - moved to $SIFT_TEST_INVITED")
          }
        }
        Future.sequence(registrations).map(_ => ())
    }
  }

  private def registerPsiApplicant(application: NumericalTestApplication, testIds: PsiTestIds)
                                  (implicit hc: HeaderCarrier): Future[PsiTest] = {
    for {
      aoa <- registerApplicant(application, testIds)
    } yield {
      if (aoa.status != AssessmentOrderAcknowledgement.acknowledgedStatus) {
        val msg = s"Received response status of ${aoa.status} when registering candidate " +
          s"${application.applicationId} to phase1 tests with=$testIds"
        logger.warn(msg)
        throw new RuntimeException(msg)
      } else {
        PsiTest(
          inventoryId = testIds.inventoryId,
          orderId = aoa.orderId,
          usedForResults = true,
          testUrl = aoa.testLaunchUrl,
          invitationDate = dateTimeFactory.nowLocalTimeZoneJavaTime.toInstant,
          assessmentId = testIds.assessmentId,
          reportId = testIds.reportId,
          normId = testIds.normId
        )
      }
    }
  }

  private def registerApplicant(application: NumericalTestApplication, testIds: PsiTestIds)
                               (implicit hc: HeaderCarrier): Future[AssessmentOrderAcknowledgement] = {

    val orderId = tokenFactory.generateUUID()
    val preferredName = TextSanitizer.sanitizeFreeText(application.preferredName)
    val lastName = TextSanitizer.sanitizeFreeText(application.lastName)

    val registerCandidateRequest = RegisterCandidateRequest(
      inventoryId = testIds.inventoryId, // Read from config to identify the test we are registering for
      orderId = orderId, // Identifier we generate to uniquely identify the test
      accountId = application.testAccountId, // Candidate's account across all tests
      preferredName = preferredName,
      lastName = lastName,
      // The url psi will redirect to when the candidate completes the test
      redirectionUrl = buildRedirectionUrl(orderId, testIds.inventoryId),
      assessmentId = testIds.assessmentId,
      reportId = testIds.reportId,
      normId = testIds.normId
    )

    onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest)
  }

  private def buildRedirectionUrl(orderId: String, inventoryId: String): String = {
    val completionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/psi/sift-test"
    s"$completionBaseUrl/complete/$orderId"
  }

  private def insertNumericalTest(application: NumericalTestApplication, test: PsiTest): Future[Unit] = {
    upsertTests(application, test :: Nil)
  }

  private def upsertTests(application: NumericalTestApplication, newTests: List[PsiTest]): Future[Unit] = {

    def upsert(applicationId: String, currentTestGroup: Option[SiftTestGroup], newTests: List[PsiTest]) = {
      currentTestGroup match {
        case Some(testGroup) if testGroup.tests.isEmpty =>
          applicationSiftRepo.insertNumericalTests(applicationId, newTests)
        case Some(testGroup) if testGroup.tests.isDefined =>
          // TODO: Test may have been reset, change the active test here
          throw new NotImplementedError("Test may have been reset, change the active test here")
        case None =>
          throw UnexpectedException(s"Application ${application.applicationId} should have a SIFT_PHASE testGroup at this point")
      }
    }
    for {
      currentTestGroupOpt <- applicationSiftRepo.getTestGroup(application.applicationId)
      _ <- upsert(application.applicationId, currentTestGroupOpt, newTests)
      //TODO: Reset "test profile progresses" while resetting tests?
    } yield ()
  }

  private def updateProgressStatuses(applicationIds: List[String], progressStatus: ProgressStatus): Future[Unit] = {
    Future.sequence(
      applicationIds.map(id => applicationRepo.addProgressStatusAndUpdateAppStatus(id, progressStatus))
    ).map(_ => ())
  }

  private def emailInvitedCandidate(application: NumericalTestApplication): Future[Unit] = {
    (for {
      emailAddress <- contactDetailsRepo.find(application.userId).map(_.email)
      notificationExpiringSiftOpt <- applicationSiftRepo.getNotificationExpiringSift(application.applicationId)
    } yield {
      implicit val hc = HeaderCarrier()
      val msg = s"Sending sift numeric test invite email to candidate ${application.applicationId}..."
      logger.info(msg)
      notificationExpiringSiftOpt.map { notification =>
        emailClient.sendSiftNumericTestInvite(emailAddress, notification.preferredName, notification.expiryDate)
      }.getOrElse(throw new IllegalStateException(s"No sift notification details found for candidate ${application.applicationId}"))
    }).flatMap(identity)
  }

  def markAsCompletedByOrderId(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applicationSiftRepo.updateTestCompletionTime(orderId, dateTimeFactory.nowLocalTimeZoneJavaTime).flatMap { _ =>
      applicationSiftRepo.getTestGroupByOrderId(orderId).flatMap { updatedTestGroup =>
        val appId = updatedTestGroup.applicationId
        require(updatedTestGroup.tests.isDefined, s"No numerical tests exists for application: $appId")
        val tests = updatedTestGroup.tests.get
        require(tests.exists(_.usedForResults), "Active tests cannot be found")

        val activeCompletedTests = tests.forall(_.completedDateTime.isDefined)
        if(activeCompletedTests) {
          applicationRepo.addProgressStatusAndUpdateAppStatus(appId, SIFT_TEST_COMPLETED).map { _ =>
            logger.info(s"Successfully updated to $SIFT_TEST_COMPLETED for orderID: $orderId and appId: $appId")
          }
        } else {
          logger.info(s"No tests to mark as completed for orderId: $orderId and applicationId: $appId")
          Future.successful(())
        }
      }
    }
  }

  //scalastyle:off method.length
  def storeRealTimeResults(orderId: String, results: PsiRealTimeResults)
                          (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    def insertResults(applicationId: String, orderId: String, testProfile: MaybeSiftTestGroupWithAppId,
                      results: PsiRealTimeResults): Future[Unit] =
      applicationSiftRepo.insertPsiTestResult(
        applicationId,
        testProfile.tests.flatMap(tests => tests.find(_.orderId == orderId))
          .getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId")),
        model.persisted.PsiTestResult.fromCommandObject(results)
      )

    def maybeUpdateProgressStatus(appId: String) = {
      applicationSiftRepo.getTestGroup(appId).flatMap { testGroupOpt =>
        val testGroup = testGroupOpt.getOrElse(throw new Exception(s"No sift test group returned for $appId"))

        val allTestsHavePsiResult = testGroup.tests.isDefined && testGroup.tests.get.forall(_.testResult.isDefined)
        if (allTestsHavePsiResult) {
          for {
            _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED)
            _ <- eventSink {
              DataStoreEvents.SiftTestResultsReceived(appId) :: Nil
            }
          } yield {
            logger.info(s"Successfully processed sift numerical results for appId:$appId, orderId:$orderId - " +
              s"moved to ${ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED}")
          }
        } else {
          Future.successful(())
        }
      }
    }

    def markTestAsCompleted(profile: MaybeSiftTestGroupWithAppId): Future[Unit] = {
      profile.tests.flatMap( tests => tests.find(_.orderId == orderId).map { test =>
        if (!test.isCompleted) {
          logger.info(s"Processing real time sift results - setting completed date on psi test whose orderId=$orderId")
          markAsCompletedByOrderId(orderId)
        }
        else {
          logger.info(s"Processing real time sift results - completed date is already set on psi test whose orderId=$orderId " +
            s"so will not mark as complete")
          Future.successful(())
        }
      }).getOrElse(throw CannotFindTestByOrderIdException(s"Processing real time sift results - test not found for orderId=$orderId"))
    }

    (for {
      appId <- applicationSiftRepo.getApplicationIdForOrderId(orderId) // throws CannotFindApplicationByOrderId
    } yield {
      for {
        profile <- applicationSiftRepo.getTestGroupByOrderId(orderId) // throws CannotFindTestByOrderId
        _ <- markTestAsCompleted(profile)
        _ <- profile.tests.flatMap ( tests => tests.find( _.orderId == orderId ).map ( test =>
          insertResults(appId, test.orderId, profile, results) ))
          .getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))

        _ <- maybeUpdateProgressStatus(appId)
      } yield ()
    }).flatMap(identity)
  }
  //scalastyle:on

  def nextApplicationWithResultsReceived: Future[Option[String]] = {
    (for {
      applicationId <- applicationSiftRepo.nextApplicationWithResultsReceived
    } yield {
      applicationId.map { appId =>
        for {
          progressResponse <- applicationRepo.findProgress(appId)
          currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(appId)
          schemesPassed = currentSchemeStatus.filter(_.result == EvaluationResults.Green.toString).map(_.schemeId).toSet
          schemesPassedRequiringSift = schemeRepository.schemes.filter( s =>
            schemesPassed.contains(s.id) && s.siftRequirement.contains(SiftRequirement.FORM)
          ).map(_.id).toSet
        } yield {
          if (schemesPassedRequiringSift.isEmpty) {
            // Candidate has no schemes that require a form to be filled so we can process the candidate
            logger.info(s"Candidate $appId has no schemes that require a form to be filled in so we will process this one")
            applicationId
          } else { // Candidate has schemes that require forms to be filled
            if (progressResponse.siftProgressResponse.siftFormsCompleteNumericTestPending) {
              // Forms have already been filled in so can process this candidate
              logger.info(s"Candidate $appId has schemes that require a form to be filled in and has already " +
                "submitted the answers so we will process this one")
              applicationId
            } else {
              logger.info(s"Candidate $appId has schemes that require a form to be filled in and has not yet submitted " +
                "the answers so not processing this one")
              None
            }
          }
        }
      }.getOrElse(Future.successful(None))
    }).flatMap(identity)
  }

  def progressToSiftReady(applicationId: String): Future[Unit] = {
    applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_READY).map { _ =>
      logger.info(s"Successfully moved $applicationId to ${ProgressStatuses.SIFT_READY}")
    }
  }
}
