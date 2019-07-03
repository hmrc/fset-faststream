/*
 * Copyright 2019 HM Revenue & Customs
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

import config.MicroserviceAppConfig.{ onlineTestsGatewayConfig, testIntegrationGatewayConfig }
import config.{ NumericalTestSchedule, NumericalTestsConfig, OnlineTestsGatewayConfig, TestIntegrationGatewayConfig }
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, EmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.UnexpectedException
import model.ProgressStatuses.{ ProgressStatus, SIFT_TEST_COMPLETED, SIFT_TEST_INVITED, SIFT_TEST_RESULTS_READY }
import model._
import model.exchange.CubiksTestResultReady
import model.persisted.{ CubiksTest, PsiTest }
import model.persisted.sift.{ SiftTestGroup, SiftTestGroup2, SiftTestGroupWithAppId }
import model.stc.DataStoreEvents
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.{ SchemeRepository, SchemeYamlRepository }
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.{ ContactDetailsMongoRepository, ContactDetailsRepository }
import repositories.sift.ApplicationSiftRepository
import services.onlinetesting.CubiksSanitizer
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object NumericalTestService2 extends NumericalTestService2 {
  val applicationRepo: GeneralApplicationRepository = repositories.applicationRepository
  val applicationSiftRepo: ApplicationSiftRepository = repositories.applicationSiftRepository
  val onlineTestsGatewayClient = OnlineTestsGatewayClient
  val gatewayConfig = onlineTestsGatewayConfig
  val integrationGatewayConfig = testIntegrationGatewayConfig
  val tokenFactory = UUIDFactory
  val dateTimeFactory: DateTimeFactory = DateTimeFactory
  val eventService: StcEventService = StcEventService
  val schemeRepository = SchemeYamlRepository
  val emailClient: CSREmailClient = CSREmailClient
  val contactDetailsRepo: ContactDetailsMongoRepository = repositories.faststreamContactDetailsRepository
}


// scalastyle:off number.of.methods
trait NumericalTestService2 extends EventSink {
  def applicationRepo: GeneralApplicationRepository
  def applicationSiftRepo: ApplicationSiftRepository
  val tokenFactory: UUIDFactory
  val gatewayConfig: OnlineTestsGatewayConfig
  def testConfig: NumericalTestsConfig = gatewayConfig.numericalTests
  val integrationGatewayConfig: TestIntegrationGatewayConfig
  val onlineTestsGatewayClient: OnlineTestsGatewayClient
  val dateTimeFactory: DateTimeFactory
  def schemeRepository: SchemeRepository
  def emailClient: EmailClient
  def contactDetailsRepo: ContactDetailsRepository

  case class NumericalTestInviteData(application: NumericalTestApplication,
                                     scheduleId: Int,
                                     token: String,
                                     registration: Registration,
                                     invitation: Invitation)

  case class NumericalTestInviteData2(application: NumericalTestApplication2, inventoryId: String)

  def registerAndInviteForTests(applications: List[NumericalTestApplication2])
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val name = integrationGatewayConfig.numericalTests.tests.head // only one test for numerical tests
    val inventoryId = integrationGatewayConfig.numericalTests.inventoryIds
      .getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))

    registerAndInvite(applications, inventoryId)
  }

  private def registerAndInvite(applications: List[NumericalTestApplication2], inventoryId: String)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    applications match {
      case Nil => Future.successful(())
      case candidates =>
        val registrations = candidates.map { candidate =>
          for {
            test <- registerPsiApplicant(candidate, inventoryId)
            _ <- insertNumericalTest(candidate, test)
            _ <- emailInvitedCandidate(candidate)
            _ <- updateProgressStatuses(List(candidate.applicationId), SIFT_TEST_INVITED)
          } yield {
            Logger.warn(s"Successfully invited candidate to take numerical test with Id: " +
              s"${candidate.applicationId} - moved to $SIFT_TEST_INVITED")
          }
        }
        Future.sequence(registrations).map(_ => ())
    }
  }

  private def registerPsiApplicant(application: NumericalTestApplication2, inventoryId: String)
                                  (implicit hc: HeaderCarrier): Future[PsiTest] = {
    for {
      aoa <- registerApplicant(application, inventoryId)
    } yield {
      if (aoa.status != AssessmentOrderAcknowledgement.acknowledgedStatus) {
        val msg = s"Received response status of ${aoa.status} when registering candidate " +
          s"${application.applicationId} to phase1 tests whose inventoryId=$inventoryId"
        Logger.warn(msg)
        throw new RuntimeException(msg)
      } else {
        PsiTest(
          inventoryId = inventoryId,
          orderId = aoa.orderId,
          usedForResults = true,
          testUrl = aoa.testLaunchUrl,
          invitationDate = dateTimeFactory.nowLocalTimeZone
        )
      }
    }
  }

  private def registerApplicant(application: NumericalTestApplication2, inventoryId: String)
                               (implicit hc: HeaderCarrier): Future[AssessmentOrderAcknowledgement] = {

    val orderId = tokenFactory.generateUUID()
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val lastName = CubiksSanitizer.sanitizeFreeText(application.lastName)

    val registerCandidateRequest = RegisterCandidateRequest(
      inventoryId = inventoryId, // Read from config to identify the test we are registering for
      orderId = orderId, // Identifier we generate to uniquely identify the test
      accountId = application.testAccountId, // Candidate's account across all tests
      preferredName = preferredName,
      lastName = lastName,
      // The url psi will redirect to when the candidate completes the test
      redirectionUrl = buildRedirectionUrl(orderId, inventoryId)
    )

    onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest)
  }

  private def buildRedirectionUrl(orderId: String, inventoryId: String): String = {
    val completionBaseUrl = s"${integrationGatewayConfig.candidateAppUrl}/fset-fast-stream/sift-test/psi/phase1"
    s"$completionBaseUrl/complete/$orderId"
  }


  private def registerAndInvite(applications: List[NumericalTestApplication], schedule: NumericalTestSchedule)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applications match {
      case Nil => Future.successful(())
      case candidates =>
        val tokens = (1 to candidates.size).map(_ => tokenFactory.generateUUID())
        for {
          registeredApplicants <- registerApplicants(candidates, tokens)
          invitedApplicants <- inviteApplicants(registeredApplicants, schedule)
          _ <- insertNumericalTest(invitedApplicants)
          _ <- emailInvitedCandidates(invitedApplicants)
          _ <- updateProgressStatuses(invitedApplicants.map(_.application.applicationId), SIFT_TEST_INVITED)
        } yield {
          Logger.warn(s"Successfully invited candidates to take a sift numerical test with Ids: " +
            s"${invitedApplicants.map(_.application.applicationId)} - moved to $SIFT_TEST_INVITED")
        }
    }
  }

  private def registerApplicants(candidates: Seq[NumericalTestApplication], tokens: Seq[String])
                        (implicit hc: HeaderCarrier): Future[Map[Int, (NumericalTestApplication, String, Registration)]] = {
    onlineTestsGatewayClient.registerApplicants(candidates.size).map(_.zipWithIndex.map{
      case (registration, idx) =>
        val candidate = candidates(idx)
        (registration.userId, (candidate, tokens(idx), registration))
    }.toMap)
  }

  private def inviteApplicants(candidateData: Map[Int, (NumericalTestApplication, String, Registration)],
                       schedule: NumericalTestSchedule)(implicit hc: HeaderCarrier): Future[List[NumericalTestInviteData]] = {
    val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/sift-test"
    val invites = candidateData.values.map {
      case (application, token, registration) =>
        val completionUrl = s"$scheduleCompletionBaseUrl/complete/$token"
        val timeAdjustments = application.eTrayAdjustments.flatMap(_.timeNeeded).map { _ =>
          val absoluteTime = calculateAbsoluteTimeWithAdjustments(application)
          //TODO: Verify sectionId
          TimeAdjustments(assessmentId = schedule.assessmentId, sectionId = 1, absoluteTime = absoluteTime) :: Nil
        }.getOrElse(Nil)
        InviteApplicant(schedule.scheduleId, registration.userId, completionUrl, timeAdjustments = timeAdjustments)
    }.toList

    onlineTestsGatewayClient.inviteApplicants(invites).map(_.map { invitation =>
      val (application, token, registration) = candidateData(invitation.userId)
      NumericalTestInviteData(application, schedule.scheduleId, token, registration, invitation)
    })
  }

  private def insertNumericalTest(invitedApplicants: List[NumericalTestInviteData]): Future[Unit] = {
    val invitedApplicantsOps = invitedApplicants.map { invite =>
      val tests = List(
        CubiksTest(
          scheduleId = invite.scheduleId,
          usedForResults = true,
          cubiksUserId = invite.registration.userId,
          token = invite.token,
          testUrl = invite.invitation.authenticateUrl,
          invitationDate = dateTimeFactory.nowLocalTimeZone,
          participantScheduleId = invite.invitation.participantScheduleId
        )
      )
      upsertTests(invite.application, tests)
    }
    Future.sequence(invitedApplicantsOps).map(_ => ()) // Process List[Future[Unit]] into Future[Unit]
  }

  private def insertNumericalTest(application: NumericalTestApplication2, test: PsiTest): Future[Unit] = {
    upsertTests2(application, test :: Nil)
  }

  private def calculateAbsoluteTimeWithAdjustments(application: NumericalTestApplication): Int = {
    val baseEtrayTestDurationInMinutes = 25
    (application.eTrayAdjustments.flatMap { etrayAdjustments => etrayAdjustments.timeNeeded }.getOrElse(0)
      * baseEtrayTestDurationInMinutes / 100) + baseEtrayTestDurationInMinutes
  }

  private def upsertTests(application: NumericalTestApplication, newTests: List[CubiksTest]): Future[Unit] = {

    def upsert(applicationId: String, currentTestGroup: Option[SiftTestGroup], newTests: List[CubiksTest]) = {
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
      updatedTestGroup <- upsert(application.applicationId, currentTestGroupOpt, newTests)
      //TODO: Reset "test profile progresses" while resetting tests?
    } yield ()
  }

  private def upsertTests2(application: NumericalTestApplication2, newTests: List[PsiTest]): Future[Unit] = {

    def upsert(applicationId: String, currentTestGroup: Option[SiftTestGroup2], newTests: List[PsiTest]) = {
      currentTestGroup match {
        case Some(testGroup) if testGroup.tests.isEmpty =>
          applicationSiftRepo.insertNumericalTests2(applicationId, newTests)
        case Some(testGroup) if testGroup.tests.isDefined =>
          // TODO: Test may have been reset, change the active test here
          throw new NotImplementedError("Test may have been reset, change the active test here")
        case None =>
          throw UnexpectedException(s"Application ${application.applicationId} should have a SIFT_PHASE testGroup at this point")
      }
    }
    for {
      currentTestGroupOpt <- applicationSiftRepo.getTestGroup2(application.applicationId)
      updatedTestGroup <- upsert(application.applicationId, currentTestGroupOpt, newTests)
      //TODO: Reset "test profile progresses" while resetting tests?
    } yield ()
  }



  private def updateProgressStatuses(applicationIds: List[String], progressStatus: ProgressStatus): Future[Unit] = {
    Future.sequence(
      applicationIds.map(id => applicationRepo.addProgressStatusAndUpdateAppStatus(id, progressStatus))
    ).map(_ => ())
  }

  private def emailInvitedCandidates(invitedApplicants: List[NumericalTestInviteData]): Future[Unit] = {
    val emailFutures = invitedApplicants.map { applicant =>
      (for {
        emailAddress <- contactDetailsRepo.find(applicant.application.userId).map(_.email)
        notificationExpiringSiftOpt <- applicationSiftRepo.getNotificationExpiringSift(applicant.application.applicationId)
      } yield {
        implicit val hc = HeaderCarrier()
        val msg = s"Sending sift numeric test invite email to candidate ${applicant.application.applicationId}..."
        Logger.info(msg)
        notificationExpiringSiftOpt.map { notification =>
          emailClient.sendSiftNumericTestInvite(emailAddress, notification.preferredName, notification.expiryDate)
        }.getOrElse(throw new IllegalStateException(s"No sift notification details found for candidate ${applicant.application.applicationId}"))
      }).flatMap(identity)
    }
    Future.sequence(emailFutures).map(_ => ()) // Process the List[Future[Unit]] into single Future[Unit]
  }

  private def emailInvitedCandidate(application: NumericalTestApplication2): Future[Unit] = {
      (for {
        emailAddress <- contactDetailsRepo.find(application.userId).map(_.email)
        notificationExpiringSiftOpt <- applicationSiftRepo.getNotificationExpiringSift(application.applicationId)
      } yield {
        implicit val hc = HeaderCarrier()
        val msg = s"Sending sift numeric test invite email to candidate ${application.applicationId}..."
        Logger.info(msg)
        notificationExpiringSiftOpt.map { notification =>
          emailClient.sendSiftNumericTestInvite(emailAddress, notification.preferredName, notification.expiryDate)
        }.getOrElse(throw new IllegalStateException(s"No sift notification details found for candidate ${application.applicationId}"))
      }).flatMap(identity)
  }

  def markAsCompleted(cubiksUserId: Int)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applicationSiftRepo.updateTestCompletionTime(cubiksUserId, dateTimeFactory.nowLocalTimeZone).flatMap { _ =>
      applicationSiftRepo.getTestGroupByCubiksId(cubiksUserId).flatMap { updatedTestGroup =>
        val appId = updatedTestGroup.applicationId
        require(updatedTestGroup.tests.isDefined, s"No numerical tests exists for application: $appId")
        val tests = updatedTestGroup.tests.get
        require(tests.exists(_.usedForResults), "Active tests cannot be found")

        val activeCompletedTests = tests.forall(_.completedDateTime.isDefined)
        if(activeCompletedTests) {
          applicationRepo.addProgressStatusAndUpdateAppStatus(appId, SIFT_TEST_COMPLETED).map { _ =>
            Logger.info(s"Successfully updated to $SIFT_TEST_COMPLETED for cubiksId: $cubiksUserId and appId: $appId")
          }
        } else {
          Logger.info(s"No tests to mark as completed for cubiksId: $cubiksUserId and applicationId: $appId")
          Future.successful(())
        }
      }
    }
  }

  def markAsCompleted(token: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applicationSiftRepo.getTestGroupByToken(token).flatMap { testGroup =>
      val tests = testGroup.tests
        .getOrElse(throw UnexpectedException(s"Numerical test with token($token) not found for appId: ${testGroup.applicationId}"))
      tests.find(_.token == token)
        .map(test => markAsCompleted(test.cubiksUserId))
        .getOrElse(Future.successful(()))
    }
  }

  def markAsReportReadyToDownload(cubiksUserId: Int, reportReady: CubiksTestResultReady)
    : Future[Unit] = {
    applicationSiftRepo.updateTestReportReady(cubiksUserId, reportReady).flatMap { _ =>
      applicationSiftRepo.getTestGroupByCubiksId(cubiksUserId).flatMap { updatedTestGroup =>
        val appId = updatedTestGroup.applicationId
        require(updatedTestGroup.tests.isDefined, s"No numerical tests exists for application: $appId")
        val tests = updatedTestGroup.tests.get
        require(tests.exists(_.usedForResults), "Active tests cannot be found")

        // TODO: should we be checking resultsReadyToDownload here and not completedDateTime - see p1 & p2 impl ?
        val activeCompletedTest = tests.forall(_.completedDateTime.isDefined)
        if (activeCompletedTest) {
          applicationRepo.addProgressStatusAndUpdateAppStatus(appId, SIFT_TEST_RESULTS_READY).map { _ =>
            Logger.info(s"Successfully updated to $SIFT_TEST_RESULTS_READY for cubiksId: $cubiksUserId and appId: $appId")
          }
        } else {
          Logger.info(s"No tests to mark as results ready for cubiksId: $cubiksUserId and applicationId: $appId")
          Future.successful(())
        }
      }
    }
  }

  def nextTestGroupWithReportReady: Future[Option[SiftTestGroupWithAppId]] = {
    applicationSiftRepo.nextTestGroupWithReportReady
  }

  def retrieveTestResult(siftTestGroup: SiftTestGroupWithAppId)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    def insertTests(testResults: List[(OnlineTestCommands.TestResult, CubiksTest)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (cubiksTestResult, cubiksTest) => applicationSiftRepo.insertCubiksTestResult(
          siftTestGroup.applicationId,
          cubiksTest, model.persisted.TestResult.fromCommandObject(cubiksTestResult)
        )
      }).map(_ => ())
    }

    def maybeUpdateProgressStatus(appId: String) = {
      applicationSiftRepo.getTestGroup(appId).flatMap { eventualTestGroup =>
        val testGroup = eventualTestGroup.getOrElse(throw new Exception(s"No sift test group returned for $appId"))

        val allTestsHaveCubiksResult = testGroup.tests.isDefined && testGroup.tests.get.forall(_.testResult.isDefined)
        if (allTestsHaveCubiksResult) {
          for {
            _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED)
            _ <- eventSink {
              DataStoreEvents.SiftTestResultsReceived(appId) :: Nil
            }
          } yield {
            Logger.info(s"Successfully retrieved sift numerical results for Id $appId - " +
              s"moved to ${ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED}")
          }
        } else {
          Future.successful(())
        }
      }
    }

    val testResults = Future.sequence(siftTestGroup.activeTests.flatMap { test =>
      test.reportId.map { reportId =>
        onlineTestsGatewayClient.downloadXmlReport(reportId)
      }.map( cubiksTestResult => cubiksTestResult.map( cubiksTestResult => cubiksTestResult -> test ))
    })

    for {
      eventualTestResults <- testResults
      _ <- insertTests(eventualTestResults)
      _ <- maybeUpdateProgressStatus(siftTestGroup.applicationId)
    } yield ()
  }

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
              Logger.info(s"Candidate $appId has no schemes that require a form to be filled in so we will process this one")
              applicationId
            } else { // Candidate has schemes that require forms to be filled
              if (progressResponse.siftProgressResponse.siftFormsCompleteNumericTestPending) {
                // Forms have already been filled in so can process this candidate
                Logger.info(s"Candidate $appId has schemes that require a form to be filled in and has already " +
                  "submitted the answers so we will process this one")
                applicationId
              } else {
                Logger.info(s"Candidate $appId has schemes that require a form to be filled in and has not yet submitted " +
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
      Logger.info(s"Successfully moved $applicationId to ${ProgressStatuses.SIFT_READY}")
    }
  }
}
